package com.easyapp.translator.session

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

/**
 * Interface do estilo "No lugar do texto" (História 7): um botão flutuante e
 * arrastável com Traduzir/Limpar e Parar, e uma camada de tela cheia que desenha
 * cada tradução sobre a linha original.
 *
 * As duas janelas usam `FLAG_SECURE`, para a captura de tela nunca ler a própria
 * tradução de volta. A camada é opaca, e o Android 12+ bloqueia toques que
 * atravessam janelas opacas de outro app; por isso, como no Google Lens, tocar
 * em qualquer ponto fecha a tradução e devolve o app de origem.
 */
class InPlaceOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTranslate: () -> Unit,
    private val onCleared: () -> Unit,
    private val onStop: () -> Unit,
    private val onDragStarted: () -> Unit,
    private val onDragEnded: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val restoreSafety = Runnable { restoreAfterCapture() }

    private val translateButton = Button(context).apply {
        text = "Traduzir"
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(BRAND, 14f)
        stateListAnimator = null
        minHeight = dp(44)
        minimumHeight = dp(44)
        setPadding(dp(16), 0, dp(16), 0)
        contentDescription = "Traduzir o texto desta tela"
        setOnClickListener { onTranslateClicked() }
    }
    private val stopButton = Button(context).apply {
        text = "✕"
        textSize = 14f
        setTextColor(TEXT_ON_INK)
        background = rounded(INK_ALT, 14f)
        stateListAnimator = null
        minWidth = dp(44)
        minimumWidth = dp(44)
        minHeight = dp(44)
        minimumHeight = dp(44)
        setPadding(0, 0, 0, 0)
        contentDescription = "Parar a tradução de tela"
        setOnClickListener { onStop() }
    }
    private val dragHandle = TextView(context).apply {
        text = "⠿"
        textSize = 18f
        setTextColor(MUTED_ON_INK)
        gravity = Gravity.CENTER
        setPadding(dp(4), 0, dp(8), 0)
        contentDescription = "Arraste para mover o botão"
    }
    private val status = TextView(context).apply {
        textSize = 11.5f
        setTextColor(MUTED_ON_INK)
        maxWidth = dp(220)
        setPadding(dp(4), dp(6), dp(4), 0)
        text = "Preparando…"
    }
    private val bubble = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(INK, 20f)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dragHandle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
            addView(translateButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
            addView(stopButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6) })
        }
        addView(row)
        addView(status)
    }
    private val bubbleParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SECURE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(12)
        y = context.resources.displayMetrics.heightPixels - dp(240)
    }

    private val layer = TranslationLayerView(context) { clearTranslations(notify = true) }
    private val layerParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SECURE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // Coordenadas da camada = coordenadas reais da tela, sem descontar barras do sistema.
        fitInsetsTypes = 0
    }

    private var bubbleAttached = false
    private var layerAttached = false
    private var hiddenForCapture = false
    private var paused = false
    private var busy = false

    init {
        installDrag()
    }

    fun show() {
        if (!Settings.canDrawOverlays(context)) return
        attachBubble()
    }

    fun setStatus(text: String) {
        status.text = text
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (value) {
            clearTranslations(notify = false)
            busy = false
            restoreAfterCapture()
            status.text = "Pausado. Retome pela notificação ou pelo app."
        } else {
            status.text = InPlaceSnapshotPipeline.READY_STATUS
        }
        refreshButton()
    }

    /** Tira o botão e a camada da tela para o quadro ser lido sem elas. */
    fun hideForCapture() {
        hiddenForCapture = true
        detachLayer()
        detachBubble()
        // Se a resposta nunca chegar (sessão pausada no meio, por exemplo), o botão volta sozinho.
        main.removeCallbacks(restoreSafety)
        main.postDelayed(restoreSafety, 20_000L)
    }

    fun showTranslations(items: List<PlacedTranslation>, message: String?) {
        busy = false
        restoreAfterCapture()
        if (items.isNotEmpty() && !paused) {
            layer.items = items
            attachLayer()
            // A camada entrou por cima; o botão precisa voltar para cima dela.
            detachBubble()
            attachBubble()
            val online = items.count { it.online }
            status.text = message ?: buildString {
                append("${items.size} trechos traduzidos")
                if (online > 0) append(" · Claude ✦")
                append(". Toque na tela para voltar ao original.")
            }
        } else {
            status.text = message ?: "Nada para traduzir nesta tela."
        }
        refreshButton()
    }

    /** A tela mudou por baixo (rolagem, mensagem nova): a tradução já não corresponde. */
    fun onScreenChanged() {
        if (!layerAttached) return
        clearTranslations(notify = false)
        status.text = "A tela mudou. Toque em Traduzir de novo."
    }

    fun hide() {
        main.removeCallbacks(restoreSafety)
        detachLayer()
        detachBubble()
    }

    private fun onTranslateClicked() {
        if (paused || busy) return
        if (layerAttached) {
            clearTranslations(notify = true)
            return
        }
        busy = true
        status.text = "Lendo a tela…"
        refreshButton()
        onTranslate()
    }

    private fun clearTranslations(notify: Boolean) {
        val wasShowing = layerAttached
        detachLayer()
        layer.items = emptyList()
        if (wasShowing) status.text = InPlaceSnapshotPipeline.READY_STATUS
        refreshButton()
        if (notify && wasShowing) onCleared()
    }

    private fun restoreAfterCapture() {
        main.removeCallbacks(restoreSafety)
        if (!hiddenForCapture) return
        hiddenForCapture = false
        busy = false
        attachBubble()
        refreshButton()
    }

    private fun refreshButton() {
        translateButton.text = when {
            busy -> "Traduzindo…"
            layerAttached -> "Limpar"
            else -> "Traduzir"
        }
        translateButton.isEnabled = !paused && !busy
        translateButton.alpha = if (translateButton.isEnabled) 1f else 0.6f
    }

    private fun attachBubble() {
        if (bubbleAttached || hiddenForCapture || !Settings.canDrawOverlays(context)) return
        bubbleAttached = runCatching { windowManager.addView(bubble, bubbleParams) }.isSuccess
    }

    private fun detachBubble() {
        if (!bubbleAttached) return
        runCatching { windowManager.removeView(bubble) }
        bubbleAttached = false
    }

    private fun attachLayer() {
        if (layerAttached || !Settings.canDrawOverlays(context)) return
        layerAttached = runCatching { windowManager.addView(layer, layerParams) }.isSuccess
    }

    private fun detachLayer() {
        if (!layerAttached) return
        runCatching { windowManager.removeView(layer) }
        layerAttached = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installDrag() {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        onDragStarted()
                    }
                    if (dragging) {
                        val metrics = context.resources.displayMetrics
                        bubbleParams.x = (startX + dx).toInt().coerceIn(0, (metrics.widthPixels - bubble.width).coerceAtLeast(0))
                        bubbleParams.y = (startY + dy).toInt().coerceIn(0, (metrics.heightPixels - bubble.height).coerceAtLeast(0))
                        if (bubbleAttached) runCatching { windowManager.updateViewLayout(bubble, bubbleParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) onDragEnded()
                    dragging = false
                    true
                }
                else -> true
            }
        }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        val INK = 0xF21B1D18.toInt()
        val INK_ALT = 0xFF33362C.toInt()
        val BRAND = 0xFF0E6B4D.toInt()
        val TEXT_ON_INK = 0xFFF4F1EA.toInt()
        val MUTED_ON_INK = 0xFF8E9186.toInt()
    }
}

/** Desenha cada tradução sobre a linha original, com o fundo e a cor de texto amostrados. */
@SuppressLint("ViewConstructor")
private class TranslationLayerView(context: Context, private val onTouched: () -> Unit) : View(context) {
    var items: List<PlacedTranslation> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val density = context.resources.displayMetrics.density
    private val minTextPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, context.resources.displayMetrics)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val location = IntArray(2)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // A janela deveria começar em (0,0) da tela; se o sistema a deslocar, compensa.
        getLocationOnScreen(location)
        val offsetX = location[0].toFloat()
        val offsetY = location[1].toFloat()
        val pad = 2f * density
        for (item in items) {
            val left = item.bounds.left - offsetX
            val top = item.bounds.top - offsetY
            val width = max(item.bounds.width(), 1)
            val height = max(item.bounds.height(), 1).toFloat()
            textPaint.color = item.foreground
            val layout = fitText(item.text, width, height)
            val drawHeight = max(height, layout.height.toFloat())
            rect.set(left - pad, top - pad, left + width + pad, top + drawHeight + pad)
            backgroundPaint.color = item.background or 0xFF000000.toInt()
            canvas.drawRoundRect(rect, 4f * density, 4f * density, backgroundPaint)
            canvas.save()
            canvas.translate(left, top + (drawHeight - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /** Diminui a fonte até a tradução caber na altura da linha (com folga de 25%) ou chegar a 9 sp. */
    private fun fitText(text: String, width: Int, height: Float): StaticLayout {
        var size = height * 0.78f
        var layout: StaticLayout
        while (true) {
            textPaint.textSize = size.coerceAtLeast(minTextPx)
            layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(4)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            if (layout.height <= height * 1.25f || size <= minTextPx) return layout
            size *= 0.9f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouched()
        return true
    }
}
