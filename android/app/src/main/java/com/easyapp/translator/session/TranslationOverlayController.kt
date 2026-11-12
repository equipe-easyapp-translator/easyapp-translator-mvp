package com.easyapp.translator.session

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
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
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = dp(72)
        width = dp(380)
    }
    private var attached = false
    private var fontSize = 16f
    private var backgroundAlpha = 92

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
        val alphaControl = SeekBar(context).apply {
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
        root.addView(status)
        root.addView(translationBand, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(controls)
        root.addView(alphaControl, LinearLayout.LayoutParams(-1, dp(28)))
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
        translationBand.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
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
