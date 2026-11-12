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
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage
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
    private val onResults: (List<ScreenTranslation>, String?) -> Unit,
) {
    private val worker = HandlerThread("easyapp-screen-capture").apply { start() }
    private val handler = Handler(worker.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> mainHandler.post(command) }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languageIdentifier = LanguageIdentification.getClient()
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

    private var frameIntervalMs = intervalFor(qualityMode)

    fun updateQualityMode(mode: String) {
        handler.post {
            frameIntervalMs = intervalFor(mode)
            lastAcceptedAt = 0L
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
            reader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.setSurface(null)
            virtualDisplay?.release()
            virtualDisplay = null
            reader?.close()
            reader = null
            translators.values.forEach { runCatching { it.close() } }
            translators.clear()
            recognizer.close()
            languageIdentifier.close()
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
            discardBitmap(bitmap)
            return
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
                if (!closed && !paused && frameGeneration == generation) handleRecognizedText(visionText, frameGeneration)
                finishFrame(bitmap)
            }
            .addOnFailureListener(executor) { finishFrame(bitmap) }
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
            if (key !in translatedCache && key !in pendingKeys) {
                pendingKeys += key
                identifyAndTranslate(line, key, frameGeneration)
            }
            visibleKeys += key
        }
        lastSource = lines.joinToString("\n")
        publish()
    }

    private fun identifyAndTranslate(text: String, key: String, frameGeneration: Int) {
        languageIdentifier.identifyLanguage(text).addOnSuccessListener(executor) languageResult@{ language ->
            if (closed || paused || frameGeneration != generation || key !in visibleKeys) return@languageResult
            val sourceLanguage = when (language) {
                "en" -> TranslateLanguage.ENGLISH
                "es" -> TranslateLanguage.SPANISH
                else -> {
                    pendingKeys.remove(key)
                    publish(message = "O texto visível não foi identificado como inglês ou espanhol.")
                    return@languageResult
                }
            }
            val translator = translators.getOrPut(sourceLanguage) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(sourceLanguage)
                        .setTargetLanguage(TranslateLanguage.PORTUGUESE)
                        .build(),
                )
            }
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnFailureListener(executor) {
                    pendingKeys.remove(key)
                    publish(message = "Modelo de tradução indisponível; verifique a conexão.")
                }
                .addOnSuccessListener(executor) modelReady@{
                    if (closed || paused || frameGeneration != generation || key !in visibleKeys) return@modelReady
                    translator.translate(text)
                        .addOnSuccessListener(executor) translationResult@{ translated ->
                            if (!closed && !paused && frameGeneration == generation && key in visibleKeys) {
                                translatedCache[key] = translated
                                publish()
                            }
                            pendingKeys.remove(key)
                        }
                        .addOnFailureListener(executor) {
                            pendingKeys.remove(key)
                            publish(message = "Não foi possível traduzir este texto.")
                        }
                }
        }.addOnFailureListener(executor) {
            pendingKeys.remove(key)
            publish(message = "Não foi possível identificar o idioma do texto.")
        }
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
        val results = visibleKeys.mapNotNull { key -> translatedCache[key]?.let { ScreenTranslation(originalLines[key] ?: key, it) } }
        onResults(results, message ?: when {
            results.isNotEmpty() -> null
            lastSource.isBlank() -> "Aponte para texto em inglês ou espanhol"
            else -> "Detectando idioma ou preparando modelo local…"
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
}
