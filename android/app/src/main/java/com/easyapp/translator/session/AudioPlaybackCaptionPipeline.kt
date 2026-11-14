package com.easyapp.translator.session

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.sqrt
import com.easyapp.translator.R
import com.easyapp.translator.str

data class AudioCaptionUpdate(
    val status: String,
    val capturing: Boolean,
    val voiceActive: Boolean = false,
    val partial: String = "",
    val caption: ScreenTranslation? = null,
)

/** Captures only playback that Android/source apps permit. Audio and recognition buffers are session-only. */
class AudioPlaybackCaptionPipeline(
    private val context: Context,
    private val projection: MediaProjection,
    private var sourceLanguage: String,
    private var targetLanguage: String,
    private var qualityMode: String,
    private val onUpdate: (AudioCaptionUpdate) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var recorder: AudioRecord? = null
    private var recognizer: SpeechRecognizer? = null
    private var translator: Translator? = null
    private var pipeRead: ParcelFileDescriptor? = null
    private var pipeWrite: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var closed = false
    @Volatile private var configurationGeneration = 0
    private var turn = 0
    private var lastFinal = ""
    private var lastVoiceAt = 0L
    private var vadActive = false

    fun start() {
        if (closed || running) return
        if (Build.VERSION.SDK_INT < 29) {
            emit(str(R.string.audio_needs_android10), false)
            return
        }
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) throw IllegalStateException("AudioRecord indisponível")
            val record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(min, CHUNK_BYTES * 4))
                .setAudioPlaybackCaptureConfig(config)
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Captura não inicializada")
            recorder = record
            running = true
            record.startRecording()
            startRecognizer()
            emit(if (Build.VERSION.SDK_INT >= 33 && recognizer != null) str(R.string.audio_started) else str(R.string.audio_needs_android13), true)
            worker.execute { readAudio(record) }
        } catch (_: SecurityException) {
            stopCapture()
            emit(str(R.string.audio_blocked), false)
        } catch (_: RuntimeException) {
            stopCapture()
            emit(str(R.string.audio_start_failed), false)
        }
    }

    fun pause() {
        stopCapture()
        emit(str(R.string.audio_paused), false)
    }

    fun resume() = start()

    fun clearHistory() = AudioCaptionStateHolder.clearHistory()

    fun updateConfiguration(nextSource: String, nextTarget: String, nextQuality: String) {
        if (sourceLanguage == nextSource && targetLanguage == nextTarget && qualityMode == nextQuality) return
        val wasRunning = running
        configurationGeneration++
        stopCapture()
        sourceLanguage = nextSource
        targetLanguage = nextTarget
        qualityMode = nextQuality
        translator?.close()
        translator = null
        lastFinal = ""
        if (wasRunning) start()
    }

    fun close() {
        if (closed) return
        closed = true
        stopCapture()
        translator?.close()
        translator = null
        worker.shutdownNow()
        AudioCaptionStateHolder.clear()
    }

    private fun startRecognizer() {
        if (Build.VERSION.SDK_INT < 33) return
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return
        try {
            val pair = ParcelFileDescriptor.createPipe()
            pipeRead = pair[0]
            pipeWrite = pair[1]
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { speech ->
                speech.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) {
                        if (running && !closed) emit(str(R.string.audio_recognition_failed, error), true, vadActive)
                    }
                    override fun onResults(results: android.os.Bundle?) = accept(results, true)
                    override fun onPartialResults(partialResults: android.os.Bundle?) = accept(partialResults, false)
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                    override fun onSegmentResults(segmentResults: android.os.Bundle) = accept(segmentResults, true)
                    override fun onEndOfSegmentedSession() {
                        if (running && !closed) main.post { runCatching { speech.startListening(recognitionIntent()) } }
                    }
                })
                speech.startListening(recognitionIntent())
            }
        } catch (_: RuntimeException) {
            pipeRead?.close(); pipeRead = null
            pipeWrite?.close(); pipeWrite = null
            recognizer = null
        }
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, TranslationLanguages.choice(sourceLanguage).tag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipeRead)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
    }

    private fun readAudio(record: AudioRecord) {
        val bytes = ByteArray(CHUNK_BYTES)
        val output = pipeWrite?.let { runCatching { FileOutputStream(it.fileDescriptor) }.getOrNull() }
        while (running && !closed) {
            val count = try { record.read(bytes, 0, bytes.size, AudioRecord.READ_BLOCKING) } catch (_: RuntimeException) { -1 }
            if (count <= 0) {
                if (running) main.post { emit(str(R.string.audio_interrupted), false) }
                break
            }
            var sum = 0.0
            var i = 0
            while (i + 1 < count) {
                val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
                sum += sample.toDouble() * sample
                i += 2
            }
            val rms = if (count > 0) sqrt(sum / (count / 2).coerceAtLeast(1)) else 0.0
            val now = System.currentTimeMillis()
            val threshold = when (qualityMode) { "ECONOMY" -> 1100.0; "QUALITY" -> 500.0; else -> 750.0 }
            if (rms >= threshold) lastVoiceAt = now
            val hangover = when (qualityMode) { "ECONOMY" -> 900L; "QUALITY" -> 450L; else -> 650L }
            val active = now - lastVoiceAt < hangover
            if (active != vadActive) {
                vadActive = active
                main.post { if (running) emit(if (active) str(R.string.audio_activity) else str(R.string.audio_pause_detected), true, active) }
            }
            if (active) runCatching { output?.write(bytes, 0, count) }
            bytes.fill(0, 0, count)
            if (now - lastVoiceAt > 8000L && now % 3000L < 120L) main.post {
                if (running) emit(str(R.string.audio_none), true, false)
            }
        }
        bytes.fill(0)
        runCatching { output?.flush() }
        runCatching { output?.close() }
    }

    private fun accept(bundle: android.os.Bundle?, final: Boolean) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (text.isBlank()) return
        if (!final) {
            AudioCaptionStateHolder.update { it.copy(partialText = text, vadActive = vadActive) }
            main.post { if (running) emit(str(R.string.audio_transcribing), true, vadActive, text) }
            return
        }
        val normalized = text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (normalized == lastFinal) return
        lastFinal = normalized
        val src = TranslationLanguages.mlKitCode(sourceLanguage) ?: return
        val dest = TranslationLanguages.mlKitCode(targetLanguage) ?: return
        val currentConfiguration = configurationGeneration
        val localTranslator = translator ?: Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dest).build()).also { translator = it }
        SessionStateHolder.updateTranslationModelStatus(
            str(R.string.model_checking, TranslationLanguages.choice(sourceLanguage).label, TranslationLanguages.choice(targetLanguage).label),
        )
        localTranslator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener modelReady@{ localTranslator.translate(text).addOnSuccessListener translatedResult@{ translated ->
                if (closed || !running || currentConfiguration != configurationGeneration) return@translatedResult
                SessionStateHolder.updateTranslationModelStatus(
                    str(R.string.model_ready, TranslationLanguages.choice(sourceLanguage).label, TranslationLanguages.choice(targetLanguage).label),
                )
                val nextTurn = ++turn
                AudioCaptionStateHolder.add(text, translated, nextTurn)
                main.post { if (running) emit(str(R.string.audio_caption_turn, nextTurn), true, vadActive, "", ScreenTranslation(text, translated, str(R.string.audio_label_turn, nextTurn))) }
            }.addOnFailureListener { if (!closed && running && currentConfiguration == configurationGeneration) main.post { emit(str(R.string.audio_translate_failed), running, vadActive) } } }
            .addOnFailureListener modelFailed@{
                if (closed || !running || currentConfiguration != configurationGeneration) return@modelFailed
                SessionStateHolder.updateTranslationModelStatus(
                    str(R.string.model_unavailable_connect, TranslationLanguages.choice(sourceLanguage).label, TranslationLanguages.choice(targetLanguage).label),
                )
                main.post { emit(str(R.string.audio_model_missing), running, vadActive) }
            }
    }

    private fun stopCapture() {
        running = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { pipeWrite?.close() }; pipeWrite = null
        runCatching { pipeRead?.close() }; pipeRead = null
        vadActive = false
        AudioCaptionStateHolder.update { it.copy(isCapturing = false, vadActive = false, partialText = "") }
    }

    private fun emit(status: String, capturing: Boolean, active: Boolean = false, partial: String = "", caption: ScreenTranslation? = null) {
        AudioCaptionStateHolder.update { it.copy(status = status, isCapturing = capturing, vadActive = active, partialText = partial) }
        onUpdate(AudioCaptionUpdate(status, capturing, active, partial, caption))
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_BYTES = 3200
    }
}
