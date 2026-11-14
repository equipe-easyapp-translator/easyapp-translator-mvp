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
import com.easyapp.translator.R
import com.easyapp.translator.str

/** In-memory overlay. The translation band disappears when OCR no longer sees its source text. */
class TranslationOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onPauseOrResume: () -> Unit,
    private val onStop: () -> Unit,
    /** "O que isso quer dizer?" (FR-022); nulo quando o Claude não está configurado. */
    private val onExplain: ((ScreenTranslation) -> Unit)? = null,
    /** Tamanho da tradução escolhido nos Ajustes (FR-029). */
    textScale: Float = 1f,
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
        text = str(R.string.overlay_searching)
        setPadding(dp(4), dp(8), dp(4), dp(8))
        contentDescription = str(R.string.overlay_header_cd)
        isClickable = true
    }
    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val collapseButton = actionButton("−") { toggleCollapsed() }.apply {
        contentDescription = str(R.string.overlay_collapse_cd)
    }
    private val translationBand = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(0xF22A4636.toInt(), 14f)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    private val translationScroll = ScrollView(context)
    private val translations = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val tapHint = TextView(context).apply {
        text = str(R.string.overlay_tap_hint)
        setTextColor(MUTED_ON_INK)
        textSize = 11f
        setPadding(0, dp(4), 0, 0)
        visibility = View.GONE
    }
    private val explanation = TextView(context).apply {
        setTextColor(TEXT_ON_INK)
        textSize = 13f
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = rounded(0xFF0E6B4D.toInt(), 10f)
        visibility = View.GONE
        contentDescription = str(R.string.overlay_explanation_cd)
        setOnClickListener { hideExplanation() }
    }
    private var explainedSource: String? = null
    private val controls = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val pauseButton = actionButton(str(R.string.action_pause)) { onPauseOrResume() }
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
        width = overlayWidth()
    }
    private var attached = false
    // Each controller is created for a new screen-translation session, which starts expanded.
    private var collapsed = false
    private var fontSize = (16f * textScale).coerceIn(12f, 28f)
    private var backgroundAlpha = 92
    private var alphaControl: SeekBar? = null
    private var hasResults = false

    init {
        translationScroll.addView(translations)
        translationBand.addView(
            translationScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        translationBand.addView(
            explanation,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) },
        )
        translationBand.addView(tapHint)
        controls.addView(actionButton("A-") { setFontSize(fontSize - 1f) }, LinearLayout.LayoutParams(0, dp(38), 1f))
        controls.addView(actionButton("A+") { setFontSize(fontSize + 1f) }, LinearLayout.LayoutParams(0, dp(38), 1f))
        controls.addView(pauseButton, LinearLayout.LayoutParams(0, dp(38), 1f))
        controls.addView(actionButton(str(R.string.action_close)) { onStop() }, LinearLayout.LayoutParams(0, dp(38), 1f))
        val opacityControl = SeekBar(context).apply {
            max = 100
            progress = backgroundAlpha
            contentDescription = str(R.string.overlay_opacity_cd)
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
        val online = results.any { it.online }
        status.text = message ?: if (online) str(R.string.overlay_claude) else str(R.string.overlay_live)
        // O estado vem da sessão, não do texto da mensagem: o texto muda com o idioma do app.
        pauseButton.text = if (SessionStateHolder.snapshot.value.status == SessionStatus.PAUSED) str(R.string.action_resume) else str(R.string.action_pause)
        hasResults = results.isNotEmpty()
        translationBand.visibility = if (!collapsed && hasResults) View.VISIBLE else View.GONE
        if (explainedSource != null && results.none { it.source == explainedSource }) hideExplanation()
        tapHint.visibility = if (onExplain != null && hasResults && explanation.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        translations.removeAllViews()
        results.take(8).forEach { item ->
            item.speakerLabel?.let { speaker ->
                translations.addView(TextView(context).apply {
                    text = speaker
                    setTextColor(BRAND_ON_INK)
                    textSize = 11f
                    setPadding(0, dp(3), 0, 0)
                })
            }
            val label = TextView(context).apply {
                text = if (item.online) "✦ ${item.translated}" else item.translated.ifBlank { item.source }
                setTextColor(TEXT_ON_INK)
                textSize = fontSize
                setPadding(0, dp(3), 0, dp(3))
                contentDescription = buildString {
                    append(str(R.string.overlay_translation_of, item.source))
                    if (item.online) append(str(R.string.overlay_by_claude))
                    if (onExplain != null && item.translated.isNotBlank()) append(str(R.string.overlay_tap_to_explain))
                }
                val explain = onExplain
                if (explain != null && item.translated.isNotBlank()) {
                    isClickable = true
                    setOnClickListener { explain(item) }
                }
            }
            translations.addView(label)
        }
    }

    /** Mostra a explicação (ou "carregando") abaixo das traduções, para a linha [source]. */
    fun showExplanation(source: String, text: String) {
        if (!attached) return
        explainedSource = source
        explanation.text = text
        explanation.visibility = View.VISIBLE
        tapHint.visibility = View.GONE
    }

    private fun hideExplanation() {
        explainedSource = null
        explanation.visibility = View.GONE
        if (onExplain != null && hasResults) tapHint.visibility = View.VISIBLE
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
        params.width = overlayWidth()
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

    // Paleta do design system: faixa `ink` (#1B1D18) com cartão `inkAlt` (#26281F).
    private fun updateAlpha() {
        val alpha = backgroundAlpha * 255 / 100
        root.background = rounded(Color.argb(alpha, 0x1B, 0x1D, 0x18), 18f)
        translationBand.background = rounded(Color.argb(alpha, 0x26, 0x28, 0x1F), 14f)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(TEXT_ON_INK)
        background = rounded(0xFF33362C.toInt(), 10f)
        stateListAnimator = null
        minHeight = dp(38)
        minimumHeight = dp(38)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(3), 0, dp(3), 0)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        val TEXT_ON_INK = 0xFFF4F1EA.toInt()
        val BRAND_ON_INK = 0xFF4EC294.toInt()
        val MUTED_ON_INK = 0xFF8E9186.toInt()
    }

    private fun overlayWidth(): Int = dp(380).coerceAtMost(
        (context.resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(1)),
    )
}
