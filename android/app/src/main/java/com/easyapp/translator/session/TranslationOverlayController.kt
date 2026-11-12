package com.easyapp.translator.session

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** In-memory overlay. The translation band disappears when OCR no longer sees its source text. */
class TranslationOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onPauseOrResume: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(0xE619211D.toInt(), 18f)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }
    private val status = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        text = "EasyApp · procurando texto"
        setPadding(dp(4), dp(8), dp(4), dp(8))
        contentDescription = "Toque para recolher ou expandir; arraste para mover a janela EasyApp"
        isClickable = true
    }
    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val collapseButton = actionButton("−") { toggleCollapsed() }.apply {
        contentDescription = "Recolher ou expandir painel da tradução"
    }
    private val translationBand = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(0xF22A4636.toInt(), 14f)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    private val translationScroll = ScrollView(context)
    private val translations = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val controls = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val pauseButton = actionButton("Pausar") { onPauseOrResume() }
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SECURE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(12)
        y = dp(120)
        width = dp(380).coerceAtMost((context.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(240)))
    }
    private var attached = false
    // Each controller is created for a new screen-translation session, which starts expanded.
    private var collapsed = false
    private var fontSize = 16f
    private var backgroundAlpha = 92
    private var alphaControl: SeekBar? = null
    private var hasResults = false

    init {
        translationScroll.addView(translations)
        translationBand.addView(
            translationScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180)),
        )
        controls.addView(actionButton("−") { setFontSize(fontSize - 1f) })
        controls.addView(actionButton("A+") { setFontSize(fontSize + 1f) })
        controls.addView(pauseButton)
        controls.addView(actionButton("Fechar") { onStop() })
        val opacityControl = SeekBar(context).apply {
            max = 100
            progress = backgroundAlpha
            contentDescription = "Transparência da sobreposição"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    backgroundAlpha = progress.coerceAtLeast(35)
                    updateAlpha()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        alphaControl = opacityControl
        header.addView(status, LinearLayout.LayoutParams(0, dp(40), 1f))
        status.setOnClickListener { toggleCollapsed() }
        header.addView(collapseButton)
        root.addView(header)
        root.addView(translationBand, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(controls)
        root.addView(opacityControl, LinearLayout.LayoutParams(-1, dp(28)))
        installDragHandle()
        updateAlpha()
    }

    fun show(results: List<ScreenTranslation>, message: String?) {
        if (!Settings.canDrawOverlays(context)) {
            hide()
            return
        }
        if (!attached) {
            try {
                windowManager.addView(root, params)
                attached = true
            } catch (_: WindowManager.BadTokenException) {
                return
            } catch (_: SecurityException) {
                return
            } catch (_: RuntimeException) {
                return
            }
        }
        status.text = message ?: "EasyApp · tradução ao vivo"
        pauseButton.text = if (message?.contains("pausad", ignoreCase = true) == true) "Retomar" else "Pausar"
        hasResults = results.isNotEmpty()
        translationBand.visibility = if (!collapsed && hasResults) View.VISIBLE else View.GONE
        translations.removeAllViews()
        results.take(8).forEach { item ->
            item.speakerLabel?.let { speaker ->
                translations.addView(TextView(context).apply {
                    text = speaker
                    setTextColor(0xFFB9DCC6.toInt())
                    textSize = 11f
                    setPadding(0, dp(3), 0, 0)
                })
            }
            val label = TextView(context).apply {
                text = item.translated.ifBlank { item.source }
                setTextColor(Color.WHITE)
                textSize = fontSize
                setPadding(0, dp(3), 0, dp(3))
                contentDescription = "Tradução de: ${item.source}"
            }
            translations.addView(label)
        }
    }

    fun hide() {
        if (attached) {
            runCatching { windowManager.removeView(root) }
            attached = false
        }
        translations.removeAllViews()
        translationBand.visibility = View.GONE
    }

    private fun setFontSize(size: Float) {
        fontSize = size.coerceIn(12f, 28f)
        for (index in 0 until translations.childCount) {
            (translations.getChildAt(index) as? TextView)?.textSize = fontSize
        }
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        collapseButton.text = if (collapsed) "+" else "−"
        translationBand.visibility = if (!collapsed && hasResults) View.VISIBLE else View.GONE
        controls.visibility = if (collapsed) View.GONE else View.VISIBLE
        alphaControl?.visibility = if (collapsed) View.GONE else View.VISIBLE
        params.width = dp(380).coerceAtMost(
            (context.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(240)),
        )
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        root.requestLayout()
        if (attached) runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun installDragHandle() {
        var downX = 0f
        var downY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        status.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    dragging = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!dragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        dragging = true
                    }
                    if (!dragging) return@setOnTouchListener true
                    val metrics = context.resources.displayMetrics
                    params.x = (initialX + deltaX).toInt().coerceIn(0, (metrics.widthPixels - root.width).coerceAtLeast(0))
                    params.y = (initialY + deltaY).toInt().coerceIn(0, (metrics.heightPixels - root.height).coerceAtLeast(0))
                    if (attached) runCatching { windowManager.updateViewLayout(root, params) }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!dragging) status.performClick()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> true
            }
        }
    }

    private fun updateAlpha() {
        root.background = rounded(Color.argb(backgroundAlpha * 255 / 100, 22, 33, 29), 18f)
        translationBand.background = rounded(Color.argb(backgroundAlpha * 255 / 100, 42, 70, 54), 14f)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 11f
        minHeight = dp(38)
        minimumHeight = dp(38)
        setPadding(dp(3), 0, dp(3), 0)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    private fun dp(value: Int) = (value * density).toInt()
}
