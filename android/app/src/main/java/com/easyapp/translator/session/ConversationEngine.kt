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
                    "Agora fale em ${languageName(inputLanguage(it.sourceLanguage, it.targetLanguage, speaker))}."
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
            updateStatus("Preparando o microfone e o serviço de reconhecimento…")
            return
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus("A permissão do microfone foi revogada. Reabra a conversa e autorize o microfone.")
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
                status = "Ouvindo... Fale agora em ${languageName(inputLanguage(it.sourceLanguage, it.targetLanguage, speaker))}.",
            )
        }
        val turn = generation
        try {
            currentRecognizer.setRecognitionListener(createRecognitionListener(turn, speaker, selectedSource, selectedTarget, intent, usingOfflineRecognizer))
            currentRecognizer.startListening(intent)
        } catch (_: SecurityException) {
            ConversationStateHolder.update { it.copy(isListening = false, status = "O Android bloqueou o acesso ao microfone.") }
        } catch (_: RuntimeException) {
            ConversationStateHolder.update { it.copy(isListening = false, status = "O serviço de reconhecimento não conseguiu iniciar. Tente novamente.") }
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
        ConversationStateHolder.update { it.copy(isListening = false, isSpeaking = false, status = "Conversa pausada.") }
    }

    fun cancelTurn() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { cancelTurn() }
            return
        }
        checkMainThread()
        generation++
        stopListening(clearPartial = true)
        ConversationStateHolder.update { it.copy(isListening = false, status = "Turno cancelado. Você pode tentar novamente.") }
    }

    fun selectVoice(voiceId: String) {
        val voice = textToSpeech?.voices?.firstOrNull { it.name == voiceId && !it.isNetworkConnectionRequired }
        if (voice == null) {
            ConversationStateHolder.update { it.copy(status = "Essa voz offline não está mais disponível.") }
            return
        }
        val result = textToSpeech?.setVoice(voice) ?: TextToSpeech.ERROR
        ConversationStateHolder.update {
            it.copy(
                selectedVoiceId = if (result == TextToSpeech.SUCCESS) voice.name else it.selectedVoiceId,
                status = if (result == TextToSpeech.SUCCESS) "Voz selecionada: ${voice.name}." else "Não foi possível selecionar essa voz.",
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
            updateStatus("Ainda não há uma tradução para repetir.")
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
            updateStatus("Autorize o microfone para iniciar uma conversa.")
            return
        }
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                ConversationStateHolder.update {
                    it.copy(speechRecognizerAvailable = false, offlineSpeechAvailable = false, status = "Nenhum serviço de reconhecimento de fala está instalado ou habilitado no Android.")
                }
                return
            }
            val canUseOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            if (canUseOnDevice) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                usingOfflineRecognizer = true
                ConversationStateHolder.update {
                    it.copy(speechRecognizerAvailable = true, offlineSpeechAvailable = true, status = "Reconhecimento offline pronto. Escolha quem fala e toque em Ouvir turno.")
                }
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                usingOfflineRecognizer = false
                ConversationStateHolder.update {
                    it.copy(speechRecognizerAvailable = true, offlineSpeechAvailable = false, status = "Reconhecimento offline indisponível; será usado o serviço padrão de voz do Android.")
                }
            }
        } catch (_: UnsupportedOperationException) {
            createStandardRecognizer("Reconhecimento offline indisponível; tentando o serviço padrão do Android.")
        } catch (_: RuntimeException) {
            createStandardRecognizer("Falha ao iniciar o reconhecimento offline; tentando o serviço padrão do Android.")
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
            ConversationStateHolder.update { it.copy(speechRecognizerAvailable = false, offlineSpeechAvailable = false, status = "O serviço padrão de reconhecimento de fala também falhou ao iniciar.") }
        }
    }

    private fun initializeTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(appContext) { result ->
                main.post {
                    if (closed) return@post
                    if (result != TextToSpeech.SUCCESS) {
                        ConversationStateHolder.update {
                            it.copy(offlineTtsAvailable = false, availableVoices = emptyList(), status = "O mecanismo de voz não iniciou. Instale ou habilite o TextToSpeech do Android.")
                        }
                    } else {
                        textToSpeech?.setOnUtteranceProgressListener(utteranceListener)
                        refreshOfflineVoices()
                    }
                }
            }
        } catch (_: RuntimeException) {
            ConversationStateHolder.update { it.copy(offlineTtsAvailable = false, status = "Nenhum mecanismo de síntese de fala está disponível no aparelho.") }
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
            if (isCurrentTurn()) updateStatus("Ouvindo… fale agora.")
        }
        override fun onBeginningOfSpeech() {
            if (isCurrentTurn()) updateStatus("Estou ouvindo sua fala…")
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (isCurrentTurn()) updateStatus("Processando a fala offline…")
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isCurrentTurn()) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            ConversationStateHolder.update { it.copy(partialTranscript = partial, status = if (partial.isBlank()) "Ouvindo…" else "Transcrição parcial") }
        }

        override fun onResults(results: Bundle?) {
            if (!isCurrentTurn()) return
            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
            ConversationStateHolder.update { it.copy(isListening = false, partialTranscript = "", finalTranscript = transcript) }
            if (transcript.isBlank()) {
                updateStatus("Não detectei fala. Aproxime-se do microfone e tente novamente.")
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
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não detectei fala. Fale mais perto do microfone e tente novamente."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> when {
                    fallbackAttempted -> "O serviço padrão do Android também não oferece suporte a este idioma (erro $error). Baixe o pacote de voz em Configurações → Reconhecimento de fala."
                    allowSystemFallback -> "O pacote offline deste idioma não está disponível (erro $error). Tentando o serviço padrão; baixe o idioma offline se quiser usar sem rede."
                    else -> "O serviço padrão do Android não oferece suporte a este idioma (erro $error). Baixe um pacote de voz compatível."
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> when {
                    fallbackAttempted -> "O reconhecimento offline e o serviço padrão falharam (erro $error). Verifique a conexão ou instale o pacote de voz offline."
                    allowSystemFallback -> "O serviço offline não concluiu o reconhecimento (erro $error). Tentando novamente pelo serviço padrão do Android; o EasyApp não guarda o áudio."
                    else -> "O serviço padrão de reconhecimento falhou (erro $error). Verifique a conexão ou instale o pacote de voz offline."
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O serviço de voz ainda está ocupado. Aguarde um instante e tente novamente."
                else -> "O reconhecimento de fala falhou (erro $error). Verifique o serviço de voz do Android e tente novamente."
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
                it.copy(isListening = true, speechRecognizerAvailable = true, offlineSpeechAvailable = false, status = "Pacote offline indisponível; tentando o reconhecimento padrão do Android…")
            }
            val fallbackIntent = Intent(originalIntent).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            fallback.startListening(fallbackIntent)
            true
        } catch (_: RuntimeException) {
            ConversationStateHolder.update { it.copy(isListening = false, status = "O serviço offline falhou e o reconhecimento padrão do Android também não iniciou.") }
            false
        }
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post {
                if (utteranceId == activeUtteranceId) ConversationStateHolder.update { it.copy(isSpeaking = true, status = "Lendo a tradução…") }
            }
        }
        override fun onDone(utteranceId: String?) {
            main.post {
                if (utteranceId == activeUtteranceId) ConversationStateHolder.update { it.copy(isSpeaking = false, status = "Tradução pronta. Alterne o interlocutor ou repita o áudio.") }
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)
        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post {
                if (utteranceId == activeUtteranceId) ConversationStateHolder.update { it.copy(isSpeaking = false, status = "A voz selecionada falhou. Escolha uma voz offline nas opções.") }
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
                it.copy(translatedText = transcript, repeatText = transcript, repeatLanguageTag = targetLocale, status = "Origem e destino são iguais; texto original mantido.")
            }
            return
        }
        val translator = translators.getOrPut("$source->$target") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
        ConversationStateHolder.update { it.copy(status = "Preparando modelo de tradução local…") }
        SessionStateHolder.updateTranslationModelStatus("Baixando ou verificando modelo offline: ${languageName(inputLanguage)} → ${languageName(outputLanguage)}…")
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnFailureListener {
                if (turnGeneration == generation && !closed) {
                    SessionStateHolder.updateTranslationModelStatus("Modelo offline indisponível para ${languageName(inputLanguage)} → ${languageName(outputLanguage)}. Conecte-se para baixar.")
                    ConversationStateHolder.update {
                        it.copy(status = "Não foi possível obter o modelo local. Conecte-se para baixá-lo e tente novamente.")
                    }
                }
            }
            .addOnSuccessListener modelReady@{
                if (turnGeneration != generation || closed) return@modelReady
                SessionStateHolder.updateTranslationModelStatus("Modelo offline pronto: ${languageName(inputLanguage)} → ${languageName(outputLanguage)}.")
                translator.translate(transcript)
                    .addOnSuccessListener translatedResult@{ translated ->
                        if (turnGeneration != generation || closed) return@translatedResult
                        ConversationStateHolder.update {
                            it.copy(
                                translatedText = translated,
                                repeatText = translated,
                                repeatLanguageTag = targetLocale,
                                status = if (it.offlineTtsAvailable) "Tradução pronta. Lendo em voz alta…" else "Tradução pronta. Instale uma voz offline para ouvir.",
                            )
                        }
                        if (ConversationStateHolder.state.value.offlineTtsAvailable) speak(translated, targetLocale)
                    }
                    .addOnFailureListener {
                        if (turnGeneration == generation && !closed) updateStatus("A tradução local falhou. Tente novamente com uma frase mais curta.")
                    }
            }
    }

    private fun speak(text: String, localeTag: String) {
        val engine = textToSpeech
        val snapshot = ConversationStateHolder.state.value
        val requestedLanguage = localeTag.substringBefore('-').lowercase(Locale.ROOT)
        val voices = engine?.voices.orEmpty().filter { !it.isNetworkConnectionRequired && it.locale.language == requestedLanguage }
        val voice = voices.firstOrNull { it.name == snapshot.selectedVoiceId } ?: voices.firstOrNull()
        if (engine == null || voice == null) {
            updateStatus("Não há voz offline instalada para este idioma. Baixe uma voz em Configurações do Android → Texto para voz.")
            return
        }
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) {
            updateStatus("Não foi possível ativar a voz selecionada. Escolha outra voz offline.")
            return
        }
        engine.setSpeechRate(snapshot.speechRate)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, snapshot.volume) }
        val id = "easyapp-${++utteranceSequence}"
        activeUtteranceId = id
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.ERROR) {
            ConversationStateHolder.update { it.copy(isSpeaking = false, status = "A voz offline não conseguiu sintetizar esta tradução.") }
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
                status = if (selected == null) "Sem voz offline em ${languageName(targetLanguage)}. Baixe uma voz em Configurações → Texto para voz." else it.status,
            )
        }
    }

    private fun stopListening(clearPartial: Boolean) {
        checkMainThread()
        if (ConversationStateHolder.state.value.isListening) runCatching { recognizer?.cancel() }
        if (clearPartial) ConversationStateHolder.update { it.copy(partialTranscript = "", isListening = false) }
    }

    private fun speechUnavailableMessage(): String =
        "Reconhecimento de fala offline indisponível. Ative um serviço SpeechRecognizer e instale o pacote offline do idioma selecionado nas Configurações do Android."

    private fun updateStatus(message: String) {
        ConversationStateHolder.update { it.copy(status = message) }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "ConversationEngine deve ser chamado na thread principal" }
    }

    private fun inputLanguageTag(language: String) = TranslationLanguages.choice(language).tag

    private fun inputLanguage(source: String, target: String, speaker: ConversationSpeaker) =
        if (speaker == ConversationSpeaker.USER_PORTUGUESE) target else source

    private fun languageName(language: String) = TranslationLanguages.choice(language).label.lowercase(Locale.ROOT)
}
