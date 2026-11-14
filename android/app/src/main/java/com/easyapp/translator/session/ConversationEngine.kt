package com.easyapp.translator.session

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import com.easyapp.translator.R
import com.easyapp.translator.str

/** Manages one explicit speech turn at a time. All text and audio remain transient in process memory. */
class ConversationEngine(
    context: Context,
    private val onMicrophonePermissionRevoked: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val translators = mutableMapOf<String, Translator>()
    private var recognizer: SpeechRecognizer? = null
    private var usingOfflineRecognizer = false
    private var textToSpeech: TextToSpeech? = null
    private var initialized = false
    private var listenWhenReady = false
    private var closed = false
    private var generation = 0
    private var utteranceSequence = 0L
    private var activeUtteranceId: String? = null

    fun initialize(sourceLanguage: String, targetLanguage: String = "pt") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { initialize(sourceLanguage, targetLanguage) }
            return
        }
        checkMainThread()
        if (closed || initialized) return
        initialized = true
        ConversationStateHolder.update { it.copy(sourceLanguage = sourceLanguage, targetLanguage = targetLanguage) }
        initializeSpeechRecognizer()
        initializeTextToSpeech()
        if (listenWhenReady) {
            listenWhenReady = false
            listenForTurn()
        }
    }

    fun setLanguages(source: String, target: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { setLanguages(source, target) }
            return
        }
        if (TranslationLanguages.mlKitCode(source) == null || TranslationLanguages.mlKitCode(target) == null) return
        generation++
        stopListening(clearPartial = true)
        activeUtteranceId = null
        textToSpeech?.stop()
        ConversationStateHolder.update { it.copy(sourceLanguage = source, targetLanguage = target, translatedText = "", finalTranscript = "", repeatText = "", isSpeaking = false) }
        refreshOfflineVoices()
    }

    fun setSpeaker(speaker: ConversationSpeaker) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { setSpeaker(speaker) }
            return
        }
        checkMainThread()
        generation++
        stopListening(clearPartial = true)
        activeUtteranceId = null
        textToSpeech?.stop()
        ConversationStateHolder.update {
            it.copy(
                speaker = speaker,
                partialTranscript = "",
                finalTranscript = "",
                translatedText = "",
                isListening = false,
                isSpeaking = false,
                status = if (it.speechRecognizerAvailable) {
                    str(R.string.conv_speak_now, languageName(inputLanguage(it.sourceLanguage, it.targetLanguage, speaker)))
                } else speechUnavailableMessage(),
            )
        }
        refreshOfflineVoices()
    }

    fun listenForTurn() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { listenForTurn() }
            return
        }
        checkMainThread()
        if (closed) return
        if (!initialized) {
            listenWhenReady = true
            updateStatus(str(R.string.conv_preparing_mic))
            return
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus(str(R.string.conv_mic_revoked))
            return
        }
        if (recognizer == null) initializeSpeechRecognizer()
        val currentRecognizer = recognizer
        if (currentRecognizer == null) {
            ConversationStateHolder.update {
                it.copy(isListening = false, speechRecognizerAvailable = false, status = speechUnavailableMessage())
            }
            return
        }
        generation++
        activeUtteranceId = null
        textToSpeech?.stop()
        val speaker = ConversationStateHolder.state.value.speaker
        val snapshot = ConversationStateHolder.state.value
        val selectedSource = snapshot.sourceLanguage
        val selectedTarget = snapshot.targetLanguage
        val languageTag = inputLanguageTag(inputLanguage(selectedSource, selectedTarget, speaker))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, usingOfflineRecognizer)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        ConversationStateHolder.update {
            it.copy(
                partialTranscript = "",
                finalTranscript = "",
                translatedText = "",
                isListening = true,
                isSpeaking = false,
                status = str(R.string.conv_listening_in, languageName(inputLanguage(it.sourceLanguage, it.targetLanguage, speaker))),
            )
        }
        val turn = generation
        try {
            currentRecognizer.setRecognitionListener(createRecognitionListener(turn, speaker, selectedSource, selectedTarget, intent, usingOfflineRecognizer))
            currentRecognizer.startListening(intent)
        } catch (_: SecurityException) {
            ConversationStateHolder.update { it.copy(isListening = false, status = str(R.string.conv_mic_blocked)) }
        } catch (_: RuntimeException) {
            ConversationStateHolder.update { it.copy(isListening = false, status = str(R.string.conv_recognizer_failed_start)) }
        }
    }

    fun pause() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { pause() }
            return
        }
        checkMainThread()
        generation++
        activeUtteranceId = null
        stopListening(clearPartial = true)
        textToSpeech?.stop()
        ConversationStateHolder.update { it.copy(isListening = false, isSpeaking = false, status = str(R.string.conv_paused)) }
    }

    fun cancelTurn() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { cancelTurn() }
            return
        }
        checkMainThread()
        generation++
        stopListening(clearPartial = true)
        ConversationStateHolder.update { it.copy(isListening = false, status = str(R.string.conv_turn_cancelled)) }
    }

    fun selectVoice(voiceId: String) {
        val voice = textToSpeech?.voices?.firstOrNull { it.name == voiceId && !it.isNetworkConnectionRequired }
        if (voice == null) {
            ConversationStateHolder.update { it.copy(status = str(R.string.conv_voice_unavailable)) }
            return
        }
        val result = textToSpeech?.setVoice(voice) ?: TextToSpeech.ERROR
        ConversationStateHolder.update {
            it.copy(
                selectedVoiceId = if (result == TextToSpeech.SUCCESS) voice.name else it.selectedVoiceId,
                status = if (result == TextToSpeech.SUCCESS) str(R.string.conv_voice_selected, voice.name) else str(R.string.conv_voice_select_failed),
            )
        }
    }

    fun setSpeechRate(rate: Float) {
        val bounded = rate.coerceIn(0.65f, 1.35f)
        textToSpeech?.setSpeechRate(bounded)
        ConversationStateHolder.update { it.copy(speechRate = bounded) }
    }

    fun setVolume(volume: Float) {
        ConversationStateHolder.update { it.copy(volume = volume.coerceIn(0.1f, 1f)) }
    }

    fun repeatLastTranslation() {
        val state = ConversationStateHolder.state.value
        if (state.repeatText.isBlank()) {
            updateStatus(str(R.string.conv_nothing_to_repeat))
            return
        }
        speak(state.repeatText, state.repeatLanguageTag)
    }

    fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        generation++
        activeUtteranceId = null
        stopListening(clearPartial = true)
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        recognizer?.destroy()
        recognizer = null
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
        ConversationStateHolder.clear()
    }

    private fun initializeSpeechRecognizer() {
        checkMainThread()
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus(str(R.string.conv_allow_mic))
            return
        }
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                ConversationStateHolder.update {
                    it.copy(speechRecognizerAvailable = false, offlineSpeechAvailable = false, status = str(R.string.conv_no_recognizer))
                }
                return
            }
            val canUseOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            if (canUseOnDevice) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                usingOfflineRecognizer = true
                ConversationStateHolder.update {
                    it.copy(speechRecognizerAvailable = true, offlineSpeechAvailable = true, status = str(R.string.conv_offline_ready))
                }
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                usingOfflineRecognizer = false
                ConversationStateHolder.update {
                    it.copy(speechRecognizerAvailable = true, offlineSpeechAvailable = false, status = str(R.string.conv_offline_unavailable_default))
                }
            }
        } catch (_: UnsupportedOperationException) {
            createStandardRecognizer(str(R.string.conv_offline_unavailable_trying))
        } catch (_: RuntimeException) {
            createStandardRecognizer(str(R.string.conv_offline_failed_trying))
        }
    }

    private fun createStandardRecognizer(statusMessage: String) {
        checkMainThread()
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                ConversationStateHolder.update { it.copy(speechRecognizerAvailable = false, offlineSpeechAvailable = false, status = speechUnavailableMessage()) }
                return
            }
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            usingOfflineRecognizer = false
            ConversationStateHolder.update {
                it.copy(speechRecognizerAvailable = true, offlineSpeechAvailable = false, status = statusMessage)
            }
        } catch (_: RuntimeException) {
            recognizer = null
            ConversationStateHolder.update { it.copy(speechRecognizerAvailable = false, offlineSpeechAvailable = false, status = str(R.string.conv_default_failed_start)) }
        }
    }

    private fun initializeTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(appContext) { result ->
                main.post {
                    if (closed) return@post
                    if (result != TextToSpeech.SUCCESS) {
                        ConversationStateHolder.update {
                            it.copy(offlineTtsAvailable = false, availableVoices = emptyList(), status = str(R.string.conv_tts_failed))
                        }
                    } else {
                        textToSpeech?.setOnUtteranceProgressListener(utteranceListener)
                        refreshOfflineVoices()
                    }
                }
            }
        } catch (_: RuntimeException) {
            ConversationStateHolder.update { it.copy(offlineTtsAvailable = false, status = str(R.string.conv_no_tts)) }
        }
    }

    private fun createRecognitionListener(
        turnGeneration: Int,
        speaker: ConversationSpeaker,
        selectedSource: String,
        selectedTarget: String,
        originalIntent: Intent,
        allowSystemFallback: Boolean,
    ) = object : RecognitionListener {
        private var fallbackAttempted = false
        private fun isCurrentTurn() = !closed && turnGeneration == generation

        override fun onReadyForSpeech(params: Bundle?) {
            if (isCurrentTurn()) updateStatus(str(R.string.conv_listening_now))
        }
        override fun onBeginningOfSpeech() {
            if (isCurrentTurn()) updateStatus(str(R.string.conv_hearing))
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (isCurrentTurn()) updateStatus(str(R.string.conv_processing))
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isCurrentTurn()) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            ConversationStateHolder.update { it.copy(partialTranscript = partial, status = if (partial.isBlank()) str(R.string.conv_listening) else str(R.string.conv_partial)) }
        }

        override fun onResults(results: Bundle?) {
            if (!isCurrentTurn()) return
            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
            ConversationStateHolder.update { it.copy(isListening = false, partialTranscript = "", finalTranscript = transcript) }
            if (transcript.isBlank()) {
                updateStatus(str(R.string.conv_no_speech_closer))
            } else {
                translateTurn(transcript, speaker, selectedSource, selectedTarget, turnGeneration)
            }
        }

        override fun onError(error: Int) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                main.post { onError(error) }
                return
            }
            if (!isCurrentTurn()) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                onMicrophonePermissionRevoked()
                return
            }
            if (allowSystemFallback && !fallbackAttempted && shouldTrySystemFallback(error)) {
                fallbackAttempted = true
                if (startSystemFallback(this, originalIntent, turnGeneration)) return
            }
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> str(R.string.conv_no_speech)
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> when {
                    fallbackAttempted -> str(R.string.conv_lang_unsupported_default, error)
                    allowSystemFallback -> str(R.string.conv_lang_offline_missing, error)
                    else -> str(R.string.conv_lang_unsupported, error)
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> when {
                    fallbackAttempted -> str(R.string.conv_both_failed, error)
                    allowSystemFallback -> str(R.string.conv_offline_retry_default, error)
                    else -> str(R.string.conv_default_failed, error)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> str(R.string.conv_busy_service)
                else -> str(R.string.conv_failed_generic, error)
            }
            ConversationStateHolder.update { it.copy(isListening = false, partialTranscript = "", status = message) }
        }
    }

    private fun shouldTrySystemFallback(error: Int): Boolean = error in setOf(
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    )

    private fun startSystemFallback(listener: RecognitionListener, originalIntent: Intent, turnGeneration: Int): Boolean {
        checkMainThread()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext) || turnGeneration != generation || closed) return false
        return try {
            val fallback = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer?.let { previous ->
                runCatching { previous.cancel() }
                runCatching { previous.destroy() }
            }
            recognizer = fallback
            usingOfflineRecognizer = false
            fallback.setRecognitionListener(listener)
            ConversationStateHolder.update {
                it.copy(isListening = true, speechRecognizerAvailable = true, offlineSpeechAvailable = false, status = str(R.string.conv_trying_default))
            }
            val fallbackIntent = Intent(originalIntent).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            fallback.startListening(fallbackIntent)
            true
        } catch (_: RuntimeException) {
            ConversationStateHolder.update { it.copy(isListening = false, status = str(R.string.conv_fallback_failed)) }
            false
        }
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post {
                if (utteranceId == activeUtteranceId) ConversationStateHolder.update { it.copy(isSpeaking = true, status = str(R.string.conv_reading)) }
            }
        }
        override fun onDone(utteranceId: String?) {
            main.post {
                if (utteranceId == activeUtteranceId) ConversationStateHolder.update { it.copy(isSpeaking = false, status = str(R.string.conv_done)) }
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)
        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post {
                if (utteranceId == activeUtteranceId) ConversationStateHolder.update { it.copy(isSpeaking = false, status = str(R.string.conv_voice_failed)) }
            }
        }
    }

    private fun translateTurn(
        transcript: String,
        speaker: ConversationSpeaker,
        selectedSource: String,
        selectedTarget: String,
        turnGeneration: Int,
    ) {
        val inputLanguage = inputLanguage(selectedSource, selectedTarget, speaker)
        val outputLanguage = if (speaker == ConversationSpeaker.USER_PORTUGUESE) selectedSource else selectedTarget
        val source = TranslationLanguages.mlKitCode(inputLanguage) ?: return
        val target = TranslationLanguages.mlKitCode(outputLanguage) ?: return
        val targetLocale = TranslationLanguages.choice(outputLanguage).tag
        if (source == target) {
            ConversationStateHolder.update {
                it.copy(translatedText = transcript, repeatText = transcript, repeatLanguageTag = targetLocale, status = str(R.string.conv_same_language))
            }
            recordTurn(speaker, transcript, transcript, inputLanguage, outputLanguage)
            return
        }
        val translator = translators.getOrPut("$source->$target") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
        ConversationStateHolder.update { it.copy(status = str(R.string.conv_preparing_model)) }
        SessionStateHolder.updateTranslationModelStatus(str(R.string.model_checking, languageName(inputLanguage), languageName(outputLanguage)))
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnFailureListener {
                if (turnGeneration == generation && !closed) {
                    SessionStateHolder.updateTranslationModelStatus(str(R.string.model_unavailable_connect, languageName(inputLanguage), languageName(outputLanguage)))
                    ConversationStateHolder.update {
                        it.copy(status = str(R.string.conv_model_failed))
                    }
                }
            }
            .addOnSuccessListener modelReady@{
                if (turnGeneration != generation || closed) return@modelReady
                SessionStateHolder.updateTranslationModelStatus(str(R.string.model_ready, languageName(inputLanguage), languageName(outputLanguage)))
                translator.translate(transcript)
                    .addOnSuccessListener translatedResult@{ translated ->
                        if (turnGeneration != generation || closed) return@translatedResult
                        ConversationStateHolder.update {
                            it.copy(
                                translatedText = translated,
                                repeatText = translated,
                                repeatLanguageTag = targetLocale,
                                status = if (it.offlineTtsAvailable) str(R.string.conv_ready_reading) else str(R.string.conv_ready_no_voice),
                            )
                        }
                        recordTurn(speaker, transcript, translated, inputLanguage, outputLanguage)
                        if (ConversationStateHolder.state.value.offlineTtsAvailable) speak(translated, targetLocale)
                    }
                    .addOnFailureListener {
                        if (turnGeneration == generation && !closed) updateStatus(str(R.string.conv_translate_failed))
                    }
            }
    }

    private fun recordTurn(
        speaker: ConversationSpeaker,
        original: String,
        translated: String,
        inputLanguage: String,
        outputLanguage: String,
    ) {
        val turn = ConversationTurn(System.nanoTime(), speaker, original, translated, inputLanguage, outputLanguage)
        ConversationStateHolder.update { it.copy(turns = (it.turns + turn).takeLast(MAX_TURNS)) }
    }

    private fun speak(text: String, localeTag: String) {
        val engine = textToSpeech
        val snapshot = ConversationStateHolder.state.value
        val requestedLanguage = localeTag.substringBefore('-').lowercase(Locale.ROOT)
        val voices = engine?.voices.orEmpty().filter { !it.isNetworkConnectionRequired && it.locale.language == requestedLanguage }
        val voice = voices.firstOrNull { it.name == snapshot.selectedVoiceId } ?: voices.firstOrNull()
        if (engine == null || voice == null) {
            updateStatus(str(R.string.conv_no_voice_lang))
            return
        }
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) {
            updateStatus(str(R.string.conv_voice_activate_failed))
            return
        }
        engine.setSpeechRate(snapshot.speechRate)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, snapshot.volume) }
        val id = "easyapp-${++utteranceSequence}"
        activeUtteranceId = id
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.ERROR) {
            ConversationStateHolder.update { it.copy(isSpeaking = false, status = str(R.string.conv_voice_synth_failed)) }
        }
    }

    private fun refreshOfflineVoices() {
        val engine = textToSpeech ?: return
        val snapshot = ConversationStateHolder.state.value
        val targetLanguage = if (snapshot.speaker == ConversationSpeaker.USER_PORTUGUESE) snapshot.sourceLanguage else snapshot.targetLanguage
        val allOffline = engine.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        val choices = allOffline.filter { it.locale.language == targetLanguage }
            .sortedWith(compareBy<Voice>({ if (targetLanguage == "pt" && it.locale.country == "BR") 0 else 1 }, { it.name }))
            .map { voice -> OfflineVoiceOption(voice.name, "${voice.locale.displayName} · ${voice.name}", voice.locale.toLanguageTag()) }
        val oldSelection = snapshot.selectedVoiceId
        val selected = choices.firstOrNull { it.id == oldSelection }?.id ?: choices.firstOrNull()?.id
        if (selected != null) {
            engine.voices?.firstOrNull { it.name == selected }?.let(engine::setVoice)
        } else {
            val locale = Locale.forLanguageTag(TranslationLanguages.choice(targetLanguage).tag)
            engine.setLanguage(locale)
        }
        ConversationStateHolder.update {
            it.copy(
                availableVoices = choices,
                selectedVoiceId = selected,
                offlineTtsAvailable = selected != null,
                status = if (selected == null) str(R.string.conv_no_voice_in, languageName(targetLanguage)) else it.status,
            )
        }
    }

    private fun stopListening(clearPartial: Boolean) {
        checkMainThread()
        if (ConversationStateHolder.state.value.isListening) runCatching { recognizer?.cancel() }
        if (clearPartial) ConversationStateHolder.update { it.copy(partialTranscript = "", isListening = false) }
    }

    private fun speechUnavailableMessage(): String =
        str(R.string.conv_speech_unavailable)

    private fun updateStatus(message: String) {
        ConversationStateHolder.update { it.copy(status = message) }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "ConversationEngine deve ser chamado na thread principal" }
    }

    private fun inputLanguageTag(language: String) = TranslationLanguages.choice(language).tag

    private fun inputLanguage(source: String, target: String, speaker: ConversationSpeaker) =
        if (speaker == ConversationSpeaker.USER_PORTUGUESE) target else source

    private fun languageName(language: String) = TranslationLanguages.choice(language).inSentence

    private companion object {
        const val MAX_TURNS = 50
    }
}
