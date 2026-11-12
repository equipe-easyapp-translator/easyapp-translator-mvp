package com.easyapp.translator.session

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
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
import com.google.mlkit.nl.translate.TranslateLanguage
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.sqrt

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
    private var language: String,
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
    private var turn = 0
    private var lastFinal = ""
    private var lastVoiceAt = 0L
    private var vadActive = false

    fun start() {
        if (closed || running) return
        if (Build.VERSION.SDK_INT < 29) {
            emit("Captura de áudio interno exige Android 10 ou superior; OCR de tela continua ativo.", false)
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
            emit(if (Build.VERSION.SDK_INT >= 33 && recognizer != null) "Captura de reprodução iniciada; aguardando fala permitida pelo app de origem." else "Captura/VAD ativos. Legendas por fala de áudio interno exigem Android 13+ e serviço de reconhecimento compatível.", true)
            worker.execute { readAudio(record) }
        } catch (_: SecurityException) {
            stopCapture()
            emit("Áudio interno indisponível: permissão ou política do Android bloqueou a captura. OCR continua ativo.", false)
        } catch (_: RuntimeException) {
            stopCapture()
            emit("Não foi possível iniciar o áudio interno; o app de origem pode não permitir captura. OCR continua ativo.", false)
        }
    }

    fun pause() {
        stopCapture()
        emit("Sessão pausada; buffer temporário de áudio descartado.", false)
    }

    fun resume() = start()

    fun clearHistory() = AudioCaptionStateHolder.clearHistory()

    fun updateConfiguration(nextLanguage: String, nextQuality: String) {
        if (language != nextLanguage) {
            language = nextLanguage
            translator?.close()
            translator = null
            lastFinal = ""
        }
        qualityMode = nextQuality
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
                        if (running && !closed) emit("Reconhecimento offline indisponível ou sem fala reconhecível (erro $error); áudio não foi salvo.", true, vadActive)
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
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "es") "es-ES" else "en-US")
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
                if (running) main.post { emit("O fluxo de áudio interno foi interrompido pelo Android ou pelo app de origem.", false) }
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
                main.post { if (running) emit(if (active) "Atividade de áudio detectada; aguardando texto." else "Pausa de áudio; aguardando próximo turno.", true, active) }
            }
            if (active) runCatching { output?.write(bytes, 0, count) }
            bytes.fill(0, 0, count)
            if (now - lastVoiceAt > 8000L && now % 3000L < 120L) main.post {
                if (running) emit("Sem áudio capturável. A origem pode estar silenciosa ou bloquear a captura (DRM/política); o Android não distingue esses casos.", true, false)
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
            main.post { if (running) emit("Transcrevendo áudio interno…", true, vadActive, text) }
            return
        }
        val normalized = text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (normalized == lastFinal) return
        lastFinal = normalized
        val src = if (language == "es") TranslateLanguage.SPANISH else TranslateLanguage.ENGLISH
        val localTranslator = translator ?: Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(TranslateLanguage.PORTUGUESE).build()).also { translator = it }
        localTranslator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { localTranslator.translate(text).addOnSuccessListener { translated ->
                if (closed || !running) return@addOnSuccessListener
                val nextTurn = ++turn
                AudioCaptionStateHolder.add(text, translated, nextTurn)
                main.post { if (running) emit("Legenda de áudio interno · turno $nextTurn", true, vadActive, "", ScreenTranslation(text, translated, "Áudio interno · turno $nextTurn")) }
            }.addOnFailureListener { main.post { emit("Falha na tradução local da legenda; baixe o modelo quando houver conexão.", running, vadActive) } } }
            .addOnFailureListener { main.post { emit("Modelo local de tradução ausente; conecte-se para baixá-lo. Nenhum áudio foi enviado ou salvo.", running, vadActive) } }
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
