package com.easyapp.translator.session

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.easyapp.translator.translation.ClaudeResult
import com.easyapp.translator.translation.ClaudeTranslator
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import com.easyapp.translator.R
import com.easyapp.translator.str

/** Uma linha traduzida, já na posição da tela (pixels reais), com as cores amostradas em volta. */
data class PlacedTranslation(
    val bounds: Rect,
    val text: String,
    val background: Int,
    val foreground: Int,
    val online: Boolean,
)

/**
 * Estilo "No lugar do texto" (História 7). Diferente de [ScreenTranslationPipeline],
 * não faz OCR contínuo (FR-025): mantém a captura ligada, lê um único quadro quando
 * [requestSnapshot] é chamado e, depois de mostrar a tradução, só compara o
 * "resumo" dos quadros seguintes para avisar quando a tela mudou (FR-027).
 *
 * Todo o estado vive na thread `handler`; a chamada ao Claude roda num executor próprio.
 */
class InPlaceSnapshotPipeline(
    private val windowManager: WindowManager,
    private val projection: MediaProjection,
    initialSourceLanguage: String,
    initialTargetLanguage: String,
    initialQualityMode: String,
    private val claude: ClaudeTranslator?,
    private val onStatus: (String) -> Unit,
    private val onTranslated: (List<PlacedTranslation>, String?) -> Unit,
    private val onScreenChanged: () -> Unit,
) {
    private enum class State { IDLE, CAPTURING, PROCESSING, WATCHING, PAUSED }

    private data class SnapshotLine(val text: String, val key: String, val box: Rect, val background: Int, val foreground: Int)

    private val worker = HandlerThread("easyapp-in-place").apply { start() }
    private val handler = Handler(worker.looper)
    private val main = Handler(Looper.getMainLooper())
    private val workerExecutor = Executor { command -> handler.post(command) }
    private val claudeExecutor = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Cache da sessão: linha normalizada → (tradução, veio do Claude). Evita pagar duas vezes pela mesma linha. */
    private val cache = HashMap<String, Pair<String, Boolean>>()
    private var translator: Translator? = null
    private var translatorKey: String? = null

    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var screenWidth = 0
    private var screenHeight = 0

    @Volatile private var sourceLanguage = initialSourceLanguage
    @Volatile private var targetLanguage = initialTargetLanguage
    @Volatile private var qualityMode = initialQualityMode
    @Volatile private var closed = false

    private var state = State.IDLE
    private var generation = 0
    private var lastImage: Image? = null
    private var snapshotHash: Long? = null
    private var baselineHash: Long? = null
    private var watchFrom = 0L
    private var claudeDisabled = false
    private var claudeNotice: String? = null

    fun start() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        captureWidth = (metrics.widthPixels / 2).coerceAtLeast(360) and -2
        captureHeight = (metrics.heightPixels / 2).coerceAtLeast(640) and -2
        reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2).also { imageReader ->
            imageReader.setOnImageAvailableListener({ source -> onFrame(source) }, handler)
            virtualDisplay = projection.createVirtualDisplay(
                "EasyApp in-place translation",
                captureWidth,
                captureHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler,
            )
        }
        handler.post { prepareModel() }
    }

    /**
     * Lê a tela daquele instante. [hideOwnWindows] roda na thread principal e deve tirar
     * da tela as janelas do próprio app (botão e camada) antes de o quadro ser lido.
     */
    fun requestSnapshot(hideOwnWindows: () -> Unit) {
        handler.post {
            if (closed || state == State.CAPTURING || state == State.PROCESSING || state == State.PAUSED) return@post
            state = State.CAPTURING
            generation++
            claudeNotice = null
            val requestGeneration = generation
            main.post {
                hideOwnWindows()
                handler.postDelayed({ grabSnapshot(requestGeneration) }, SETTLE_MS)
            }
        }
    }

    /** A camada foi fechada pelo usuário: parar de vigiar mudanças. */
    fun stopWatching() {
        handler.post { if (state == State.WATCHING) state = State.IDLE }
    }

    /** Durante o arraste do botão a tela muda de propósito; não conta como mudança de conteúdo. */
    fun holdWatch() {
        handler.post { watchFrom = Long.MAX_VALUE }
    }

    fun releaseWatch() {
        handler.post { watchFrom = System.currentTimeMillis() + RESETTLE_MS }
    }

    fun pause() {
        handler.post {
            generation++
            state = State.PAUSED
        }
    }

    fun resume() {
        handler.post { if (state == State.PAUSED) state = State.IDLE }
    }

    fun updateConfiguration(source: String, target: String, quality: String) {
        handler.post {
            qualityMode = quality
            if (source == sourceLanguage && target == targetLanguage) return@post
            sourceLanguage = source
            targetLanguage = target
            cache.clear()
            translator?.let { runCatching { it.close() } }
            translator = null
            translatorKey = null
            prepareModel()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        generation++
        claudeExecutor.shutdownNow()
        handler.post {
            lastImage?.close()
            lastImage = null
            reader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.setSurface(null)
            virtualDisplay?.release()
            virtualDisplay = null
            reader?.close()
            reader = null
            translator?.let { runCatching { it.close() } }
            translator = null
            cache.clear()
            recognizer.close()
            worker.quitSafely()
        }
    }

    // ---- Captura ---------------------------------------------------------

    private fun onFrame(source: ImageReader) {
        // Enquanto espera o quadro sem as janelas do app, deixa a fila para grabSnapshot().
        if (closed || state == State.CAPTURING) return
        val image = try { source.acquireLatestImage() } catch (_: RuntimeException) { null } ?: return
        if (state == State.WATCHING) {
            val hash = runCatching { contentHash(image) }.getOrNull()
            val now = System.currentTimeMillis()
            if (hash != null) {
                when {
                    now < watchFrom || baselineHash == null -> baselineHash = hash
                    hash != baselineHash -> {
                        state = State.IDLE
                        baselineHash = null
                        main.post { if (!closed) onScreenChanged() }
                    }
                }
            }
        }
        // Guarda o quadro mais recente: se as janelas do app não aparecem na captura,
        // escondê-las não gera quadro novo e este é o que vale.
        lastImage?.close()
        lastImage = image
    }

    private fun grabSnapshot(requestGeneration: Int) {
        if (closed || state != State.CAPTURING || requestGeneration != generation) return
        val fresh = try { reader?.acquireLatestImage() } catch (_: RuntimeException) { null }
        val image = if (fresh != null) {
            lastImage?.close()
            lastImage = null
            fresh
        } else {
            lastImage.also { lastImage = null }
        }
        if (image == null) {
            finish(emptyList(), str(R.string.inplace_read_failed), requestGeneration)
            return
        }
        val hash = runCatching { contentHash(image) }.getOrNull()
        val bitmap = imageToBitmap(image)
        image.close()
        if (bitmap == null) {
            finish(emptyList(), str(R.string.inplace_read_failed), requestGeneration)
            return
        }
        snapshotHash = hash
        state = State.PROCESSING
        main.post { onStatus(str(R.string.inplace_reading_text)) }
        val task = try { recognizer.process(InputImage.fromBitmap(bitmap, 0)) } catch (_: RuntimeException) {
            bitmap.recycle()
            finish(emptyList(), str(R.string.inplace_text_failed), requestGeneration)
            return
        }
        task
            .addOnSuccessListener(workerExecutor) { text ->
                val lines = text.textBlocks
                    .flatMap { block -> block.lines }
                    .mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        val content = line.text.trim()
                        // Ignora a barra de status (relógio, bateria) e fragmentos de um caractere.
                        if (box.bottom < captureHeight * STATUS_BAR_FRACTION || content.length < 2) return@mapNotNull null
                        val (background, foreground) = sampleColors(bitmap, box)
                        SnapshotLine(content, normalize(content), Rect(box), background, foreground)
                    }
                    .take(MAX_LINES)
                bitmap.recycle()
                if (requestGeneration != generation || closed) return@addOnSuccessListener
                if (lines.isEmpty()) finish(emptyList(), str(R.string.inplace_no_text), requestGeneration)
                else translateSnapshot(lines, requestGeneration)
            }
            .addOnFailureListener(workerExecutor) {
                bitmap.recycle()
                finish(emptyList(), str(R.string.inplace_text_failed), requestGeneration)
            }
    }

    // ---- Tradução --------------------------------------------------------

    private fun claudeUsable() = claude != null && qualityMode == "QUALITY" && !claudeDisabled

    private fun translateSnapshot(lines: List<SnapshotLine>, requestGeneration: Int) {
        if (sourceLanguage == targetLanguage) {
            finish(emptyList(), str(R.string.same_language_short), requestGeneration)
            return
        }
        val missing = lines.filter { it.key !in cache }.distinctBy { it.key }
        val translatorClient = claude
        if (missing.isEmpty() || !claudeUsable() || translatorClient == null) {
            translateWithMlKit(lines, requestGeneration)
            return
        }
        main.post { onStatus(str(R.string.claude_translating)) }
        val batch = missing.take(CLAUDE_MAX_LINES)
        val source = sourceLanguage
        val target = targetLanguage
        try {
            claudeExecutor.execute {
                val result = translatorClient.translateLines(batch.map { it.text }, source, target)
                handler.post {
                    if (requestGeneration != generation || closed) return@post
                    when (result) {
                        is ClaudeResult.Ok -> result.value.forEachIndexed { index, translated ->
                            if (translated != null) cache[batch[index].key] = translated to true
                        }
                        is ClaudeResult.Failed -> {
                            if (result.permanent) claudeDisabled = true
                            claudeNotice = str(R.string.claude_unavailable_used_offline, result.reason)
                        }
                    }
                    // O que o Claude não devolveu cai para o ML Kit (FR-023).
                    translateWithMlKit(lines, requestGeneration)
                }
            }
        } catch (_: RuntimeException) {
            translateWithMlKit(lines, requestGeneration)
        }
    }

    private fun translateWithMlKit(lines: List<SnapshotLine>, requestGeneration: Int) {
        val missing = lines.filter { it.key !in cache }.distinctBy { it.key }
        if (missing.isEmpty()) {
            finish(place(lines), claudeNotice, requestGeneration)
            return
        }
        val client = ensureTranslator()
        if (client == null) {
            finish(place(lines), str(R.string.no_offline_model_pair), requestGeneration)
            return
        }
        main.post { onStatus(str(R.string.translating_short)) }
        client.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener(workerExecutor) {
                if (requestGeneration != generation || closed) return@addOnSuccessListener
                var remaining = missing.size
                missing.forEach { line ->
                    client.translate(line.text)
                        .addOnSuccessListener(workerExecutor) { translated ->
                            cache[line.key] = translated to false
                            remaining--
                            if (remaining == 0) finish(place(lines), claudeNotice, requestGeneration)
                        }
                        .addOnFailureListener(workerExecutor) {
                            remaining--
                            if (remaining == 0) finish(place(lines), claudeNotice, requestGeneration)
                        }
                }
            }
            .addOnFailureListener(workerExecutor) {
                finish(place(lines), str(R.string.model_unavailable_retry), requestGeneration)
            }
    }

    /** Converte as linhas traduzidas para a posição da tela; não cobre o que já está no idioma de destino. */
    private fun place(lines: List<SnapshotLine>): List<PlacedTranslation> {
        val scaleX = screenWidth / captureWidth.toFloat()
        val scaleY = screenHeight / captureHeight.toFloat()
        return lines.mapNotNull { line ->
            val (translated, online) = cache[line.key] ?: return@mapNotNull null
            if (translated.isBlank() || normalize(translated) == line.key) return@mapNotNull null
            PlacedTranslation(
                bounds = Rect(
                    (line.box.left * scaleX).toInt(),
                    (line.box.top * scaleY).toInt(),
                    (line.box.right * scaleX).toInt(),
                    (line.box.bottom * scaleY).toInt(),
                ),
                text = translated,
                background = line.background,
                foreground = line.foreground,
                online = online,
            )
        }
    }

    private fun finish(placed: List<PlacedTranslation>, message: String?, requestGeneration: Int) {
        if (requestGeneration != generation || closed) return
        if (placed.isEmpty()) {
            state = State.IDLE
        } else {
            state = State.WATCHING
            baselineHash = snapshotHash
            watchFrom = System.currentTimeMillis() + WATCH_SETTLE_MS
        }
        main.post { if (!closed) onTranslated(placed, message) }
    }

    private fun ensureTranslator(): Translator? {
        val source = TranslationLanguages.mlKitCode(sourceLanguage) ?: return null
        val target = TranslationLanguages.mlKitCode(targetLanguage) ?: return null
        val key = "$source->$target"
        if (translatorKey == key) translator?.let { return it }
        translator?.let { runCatching { it.close() } }
        return Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
            .also {
                translator = it
                translatorKey = key
            }
    }

    private fun prepareModel() {
        if (closed || sourceLanguage == targetLanguage) return
        val client = ensureTranslator() ?: return
        main.post { onStatus(str(R.string.model_preparing_short)) }
        client.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener(workerExecutor) { main.post { if (!closed) onStatus(READY_STATUS) } }
            .addOnFailureListener(workerExecutor) {
                main.post {
                    if (!closed) {
                        onStatus(if (claude != null) READY_STATUS else str(R.string.model_missing_connect))
                    }
                }
            }
    }

    // ---- Imagem ----------------------------------------------------------

    /**
     * Cor de fundo: a cor mais frequente numa moldura logo em volta da linha.
     * Cor do texto: escura ou clara, a que contrastar com esse fundo.
     */
    private fun sampleColors(bitmap: Bitmap, box: Rect): Pair<Int, Int> {
        val buckets = HashMap<Int, IntArray>()
        fun sample(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return
            val color = bitmap.getPixel(x, y)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val entry = buckets.getOrPut(bucket) { IntArray(4) }
            entry[0]++
            entry[1] += r
            entry[2] += g
            entry[3] += b
        }
        val pad = 2
        val left = box.left - pad
        val right = box.right + pad
        val top = box.top - pad
        val bottom = box.bottom + pad
        for (i in 0..15) {
            val x = left + (right - left) * i / 15
            sample(x, top)
            sample(x, bottom)
        }
        for (i in 0..5) {
            val y = top + (bottom - top) * i / 5
            sample(left, y)
            sample(right, y)
        }
        val best = buckets.values.maxByOrNull { it[0] } ?: return DEFAULT_BACKGROUND to DEFAULT_FOREGROUND
        val background = Color.rgb(best[1] / best[0], best[2] / best[0], best[3] / best[0])
        val luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255.0
        val foreground = if (luminance > 0.55) DARK_TEXT else LIGHT_TEXT
        return background to foreground
    }

    /** Resumo do conteúdo, sem a barra de status (relógio) e a de navegação, para detectar mudança. */
    private fun contentHash(image: Image): Long {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        var hash = -0x340d631b7bdddcdbL
        val top = (image.height * 0.06f).toInt()
        val bottom = (image.height * 0.92f).toInt()
        val rowStep = ((bottom - top) / 72).coerceAtLeast(1)
        val columnStep = (image.width / 96).coerceAtLeast(1)
        var row = top
        while (row < bottom) {
            var column = 0
            while (column < image.width) {
                val index = row * rowStride + column * 4
                for (channel in 0..2) hash = (hash xor (buffer.get(index + channel).toLong() and 0xff)) * 0x100000001b3L
                column += columnStep
            }
            row += rowStep
        }
        return hash
    }

    private fun imageToBitmap(image: Image): Bitmap? = try {
        val plane = image.planes[0]
        val paddedWidth = plane.rowStride / plane.pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        try {
            padded.copyPixelsFromBuffer(plane.buffer.duplicate())
            if (paddedWidth == image.width) padded
            else Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
        } catch (exception: RuntimeException) {
            padded.recycle()
            throw exception
        }
    } catch (_: RuntimeException) { null }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    companion object {
        val READY_STATUS: String get() = str(R.string.inplace_ready)
        private const val SETTLE_MS = 220L
        private const val WATCH_SETTLE_MS = 800L
        private const val RESETTLE_MS = 500L
        private const val STATUS_BAR_FRACTION = 0.04f
        private const val MAX_LINES = 60
        private const val CLAUDE_MAX_LINES = 40
        private val DEFAULT_BACKGROUND = Color.WHITE
        private val DARK_TEXT = 0xFF1B1D18.toInt()
        private val LIGHT_TEXT = 0xFFF4F1EA.toInt()
        private val DEFAULT_FOREGROUND = 0xFF1B1D18.toInt()
    }
}
