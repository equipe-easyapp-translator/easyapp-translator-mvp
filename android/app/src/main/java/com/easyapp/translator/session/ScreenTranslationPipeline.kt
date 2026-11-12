package com.easyapp.translator.session

import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.Locale
import java.util.Collections

data class ScreenTranslation(val source: String, val translated: String, val speakerLabel: String? = null)

/** Captures only the latest frame, performs on-device OCR/translation, and keeps results in memory. */
class ScreenTranslationPipeline(
    private val windowManager: WindowManager,
    private val projection: MediaProjection,
    private val qualityMode: String,
    initialSourceLanguage: String = "en",
    initialTargetLanguage: String = "pt",
    private val onModelStatus: (String) -> Unit = {},
    private val onResults: (List<ScreenTranslation>, String?) -> Unit,
) {
    private val worker = HandlerThread("easyapp-screen-capture").apply { start() }
    private val handler = Handler(worker.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> mainHandler.post(command) }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val translators = mutableMapOf<String, Translator>()
    private val translatedCache = linkedMapOf<String, String>()
    private val originalLines = mutableMapOf<String, String>()
    private val pendingKeys = mutableSetOf<String>()
    private val visibleKeys = linkedSetOf<String>()
    private val inFlightBitmaps = Collections.synchronizedSet(mutableSetOf<Bitmap>())
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var lastFrameHash: Long? = null
    private var lastAcceptedAt = 0L
    private var captureHeight = 0
    @Volatile private var processing = false
    @Volatile private var paused = false
    @Volatile private var closed = false
    @Volatile private var generation = 0
    private var lastSource = ""
    private var readyTranslatorKey: String? = null
    private var preparingTranslatorKey: String? = null
    private var modelError: String? = null
    @Volatile private var sourceLanguage = initialSourceLanguage
    @Volatile private var targetLanguage = initialTargetLanguage

    private val capturePoll = object : Runnable {
        override fun run() {
            if (closed || paused) return
            reader?.let(::onImageAvailable)
            handler.postDelayed(this, CAPTURE_POLL_INTERVAL_MS)
        }
    }

    private var frameIntervalMs = intervalFor(qualityMode)

    fun updateQualityMode(mode: String) {
        handler.post {
            frameIntervalMs = intervalFor(mode)
            lastAcceptedAt = 0L
        }
    }

    fun updateLanguages(source: String, target: String) {
        if (TranslationLanguages.mlKitCode(source) == null || TranslationLanguages.mlKitCode(target) == null) return
        handler.post {
            if (closed || (sourceLanguage == source && targetLanguage == target)) return@post
            sourceLanguage = source
            targetLanguage = target
            generation++
            translatedCache.clear()
            originalLines.clear()
            pendingKeys.clear()
            visibleKeys.clear()
            translators.values.forEach { runCatching { it.close() } }
            translators.clear()
            lastSource = ""
            lastFrameHash = null
            lastAcceptedAt = 0L
            readyTranslatorKey = null
            preparingTranslatorKey = null
            modelError = null
            onModelStatus("Preparando modelo offline: ${languageName(source)} → ${languageName(target)}…")
            prepareSelectedModel()
            publish(message = "Idiomas atualizados. Procurando texto em ${languageName(source)}…")
        }
    }

    private fun intervalFor(mode: String) = when (mode) {
        "ECONOMY" -> 2200L
        "QUALITY" -> 500L
        else -> 1100L
    }

    fun start() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = (metrics.widthPixels / 2).coerceAtLeast(360) and -2
        val height = (metrics.heightPixels / 2).coerceAtLeast(640) and -2
        captureHeight = height
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { imageReader ->
            imageReader.setOnImageAvailableListener({ source -> onImageAvailable(source) }, handler)
            virtualDisplay = projection.createVirtualDisplay(
                "EasyApp screen translation",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler,
            )
        }
        handler.post {
            prepareSelectedModel()
            handler.removeCallbacks(capturePoll)
            handler.post(capturePoll)
        }
    }

    fun pauseAndClear() {
        paused = true
        generation++
        translatedCache.clear()
        originalLines.clear()
        pendingKeys.clear()
        visibleKeys.clear()
        lastSource = ""
        synchronized(inFlightBitmaps) {
            inFlightBitmaps.forEach(::discardBitmap)
            inFlightBitmaps.clear()
        }
        onResults(emptyList(), "Captura pausada")
        handler.post {
            handler.removeCallbacks(capturePoll)
            lastAcceptedAt = 0L
            lastFrameHash = null
            reader?.setOnImageAvailableListener(null, null)
            runCatching {
                while (true) {
                    val queued = reader?.acquireLatestImage() ?: break
                    queued.close()
                }
            }
            virtualDisplay?.setSurface(null)
        }
    }

    fun resume() {
        if (closed) return
        paused = false
        handler.post {
            lastAcceptedAt = 0L
            reader?.setOnImageAvailableListener({ source -> onImageAvailable(source) }, handler)
            virtualDisplay?.setSurface(reader?.surface)
            prepareSelectedModel()
            handler.removeCallbacks(capturePoll)
            handler.post(capturePoll)
        }
        onResults(emptyList(), "Aguardando texto na tela…")
    }

    fun closeAndClear() {
        if (closed) return
        closed = true
        paused = true
        generation++
        translatedCache.clear()
        originalLines.clear()
        pendingKeys.clear()
        visibleKeys.clear()
        lastSource = ""
        synchronized(inFlightBitmaps) {
            inFlightBitmaps.forEach(::discardBitmap)
            inFlightBitmaps.clear()
        }
        onResults(emptyList(), null)
        handler.post {
            handler.removeCallbacks(capturePoll)
            reader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.setSurface(null)
            virtualDisplay?.release()
            virtualDisplay = null
            reader?.close()
            reader = null
            translators.values.forEach { runCatching { it.close() } }
            translators.clear()
            recognizer.close()
            worker.quitSafely()
        }
    }

    private fun onImageAvailable(source: ImageReader) {
        val image = try { source.acquireLatestImage() } catch (_: RuntimeException) { null } ?: return
        if (closed || paused || processing) {
            image.close()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastAcceptedAt < frameIntervalMs) {
            image.close()
            return
        }
        val hash = try {
            sampledHash(image.planes[0].buffer, image.planes[0].rowStride, image.width, image.height)
        } catch (_: RuntimeException) {
            image.close()
            return
        }
        val bitmap = imageToBitmap(image)
        image.close()
        if (bitmap == null) return
        if (hash == lastFrameHash) {
            // Keep retrying OCR on an empty or unreadable screen; a later frame may contain text.
            if (lastSource.isNotBlank()) {
                discardBitmap(bitmap)
                return
            }
        }
        lastFrameHash = hash
        lastAcceptedAt = now
        processing = true
        val frameGeneration = generation
        synchronized(inFlightBitmaps) {
            if (closed || paused) {
                processing = false
                discardBitmap(bitmap)
                return
            }
            inFlightBitmaps += bitmap
        }
        val input = InputImage.fromBitmap(bitmap, 0)
        val task = try { recognizer.process(input) } catch (_: RuntimeException) {
            finishFrame(bitmap)
            return
        }
        task
            .addOnSuccessListener(executor) { visionText ->
                handler.post {
                    if (!closed && !paused && frameGeneration == generation) handleRecognizedText(visionText, frameGeneration)
                    finishFrame(bitmap)
                }
            }
            .addOnFailureListener(executor) {
                handler.post {
                    if (frameGeneration == generation && !closed && !paused) {
                        clearVisibleText()
                        publish(message = "Falha ao ler a tela; tentando novamente…")
                    }
                    finishFrame(bitmap)
                }
            }
    }

    private fun handleRecognizedText(text: com.google.mlkit.vision.text.Text, frameGeneration: Int) {
        val lines = text.textBlocks
            .flatMap { block -> block.lines }
            .filter { line ->
                val bounds = line.boundingBox
                bounds == null || (
                    bounds.top >= (captureHeight * 0.04f).toInt() &&
                        bounds.bottom <= (captureHeight * 0.97f).toInt()
                    )
            }
            .map { it.text.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .take(18)
            .toList()
        val keys = lines.map(::normalize).toSet()
        visibleKeys.retainAll(keys)
        translatedCache.keys.retainAll(keys)
        originalLines.keys.retainAll(keys)
        pendingKeys.retainAll(keys)
        lines.forEach { line ->
            val key = normalize(line)
            originalLines[key] = line
            visibleKeys += key
        }
        lastSource = lines.joinToString("\n")
        if (lines.isNotEmpty()) {
            if (readyTranslatorKey == selectedTranslatorKey()) translateVisibleText(frameGeneration)
            else if (modelError == null) prepareSelectedModel()
        }
        publish()
    }

    private fun selectedTranslatorKey(): String? {
        val sourceCode = TranslationLanguages.mlKitCode(sourceLanguage)
        val targetCode = TranslationLanguages.mlKitCode(targetLanguage)
        if (sourceCode == null || targetCode == null) return null
        return "$sourceCode->$targetCode"
    }

    private fun prepareSelectedModel() {
        if (closed || paused) return
        if (modelError != null) return
        val sourceCode = TranslationLanguages.mlKitCode(sourceLanguage)
        val targetCode = TranslationLanguages.mlKitCode(targetLanguage)
        if (sourceCode == null || targetCode == null) {
            modelError = "Idioma selecionado não é suportado pelo modelo local."
            publish(message = modelError)
            onModelStatus(modelError!!)
            return
        }
        val translatorKey = "$sourceCode->$targetCode"
        if (sourceCode == targetCode) {
            readyTranslatorKey = translatorKey
            preparingTranslatorKey = null
            modelError = null
            onModelStatus("Origem e destino são iguais; mostrando o texto original.")
            translateVisibleText(generation)
            return
        }
        if (readyTranslatorKey == translatorKey) {
            translateVisibleText(generation)
            return
        }
        if (preparingTranslatorKey == translatorKey) return

        preparingTranslatorKey = translatorKey
        modelError = null
        val translator = translators.getOrPut(translatorKey) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(sourceCode).setTargetLanguage(targetCode).build())
        }
        onModelStatus("Baixando ou verificando modelo offline: ${languageName(sourceLanguage)} → ${languageName(targetLanguage)}…")
        publish(message = "Preparando modelo local para ${languageName(sourceLanguage)} → ${languageName(targetLanguage)}…")
        val modelGeneration = generation
        // Default conditions permit either Wi-Fi or mobile data; do not require Wi-Fi here.
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnFailureListener(executor) { exception ->
                handler.post {
                    if (closed || modelGeneration != generation || selectedTranslatorKey() != translatorKey) return@post
                    preparingTranslatorKey = null
                    modelError = "Modelo offline indisponível para ${languageName(sourceLanguage)} → ${languageName(targetLanguage)}. Verifique a conexão e tente novamente."
                    onModelStatus(modelError!!)
                    publish(message = modelError)
                }
            }
            .addOnSuccessListener(executor) {
                handler.post {
                    if (closed || paused || modelGeneration != generation || selectedTranslatorKey() != translatorKey) return@post
                    preparingTranslatorKey = null
                    readyTranslatorKey = translatorKey
                    modelError = null
                    onModelStatus("Modelo offline pronto: ${languageName(sourceLanguage)} → ${languageName(targetLanguage)}.")
                    translateVisibleText(modelGeneration)
                    publish()
                }
            }
    }

    private fun translateVisibleText(frameGeneration: Int) {
        if (closed || paused || frameGeneration != generation) return
        val sourceCode = TranslationLanguages.mlKitCode(sourceLanguage) ?: return
        val targetCode = TranslationLanguages.mlKitCode(targetLanguage) ?: return
        val translatorKey = "$sourceCode->$targetCode"
        if (readyTranslatorKey != translatorKey) return
        modelError = null
        val translator = translators[translatorKey]
        for (key in visibleKeys.toList()) {
            if (key in translatedCache || key in pendingKeys) continue
            val sourceText = originalLines[key] ?: continue
            if (sourceCode == targetCode) {
                translatedCache[key] = sourceText
                pendingKeys.remove(key)
                continue
            }
            pendingKeys += key
            val currentKey = key
            translator?.translate(sourceText)
                ?.addOnSuccessListener(executor) { translated ->
                    handler.post {
                        if (!closed && !paused && frameGeneration == generation && currentKey in visibleKeys) {
                            translatedCache[currentKey] = translated
                            publish()
                        }
                        pendingKeys.remove(currentKey)
                    }
                }
                ?.addOnFailureListener(executor) {
                    handler.post {
                        if (frameGeneration == generation && !closed && !paused) {
                            pendingKeys.remove(currentKey)
                            modelError = "Não foi possível traduzir o texto da tela. Atualize o texto ou verifique o modelo offline."
                            onModelStatus(modelError!!)
                            publish(message = modelError)
                        }
                    }
                }
        }
    }

    private fun clearVisibleText() {
        visibleKeys.clear()
        translatedCache.clear()
        originalLines.clear()
        pendingKeys.clear()
        lastSource = ""
    }

    private fun finishFrame(bitmap: Bitmap) {
        discardBitmap(bitmap)
        inFlightBitmaps.remove(bitmap)
        processing = false
    }

    private fun publish(message: String? = null) {
        if (closed || paused) {
            onResults(emptyList(), null)
            return
        }
        val results = visibleKeys.map { key ->
            ScreenTranslation(originalLines[key] ?: key, translatedCache[key].orEmpty())
        }
        val hasUntranslatedText = visibleKeys.any { it !in translatedCache }
        onResults(results, message ?: when {
            modelError != null -> modelError
            results.isEmpty() -> "Nenhum texto detectado na tela"
            hasUntranslatedText && readyTranslatorKey != selectedTranslatorKey() -> "Preparando modelo local para ${languageName(sourceLanguage)} → ${languageName(targetLanguage)}…"
            hasUntranslatedText -> "Traduzindo texto detectado…"
            else -> null
        })
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

    private fun discardBitmap(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            runCatching { bitmap.eraseColor(android.graphics.Color.TRANSPARENT) }
            bitmap.recycle()
        }
    }

    private fun sampledHash(buffer: java.nio.ByteBuffer, rowStride: Int, width: Int, height: Int): Long {
        var hash = -0x340d631b7bdddcdbL
        val rowStep = (height / 72).coerceAtLeast(1)
        val columnStep = (width / 96).coerceAtLeast(1)
        var row = 0
        while (row < height) {
            var column = 0
            while (column < width) {
                val index = row * rowStride + column * 4
                for (channel in 0..2) hash = (hash xor (buffer.get(index + channel).toLong() and 0xff)) * 0x100000001b3L
                column += columnStep
            }
            row += rowStep
        }
        return hash
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    private fun languageName(code: String): String = TranslationLanguages.choice(code).label.lowercase(Locale.ROOT)

    companion object {
        private const val CAPTURE_POLL_INTERVAL_MS = 1_300L
    }
}
