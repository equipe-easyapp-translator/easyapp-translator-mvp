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
import com.google.mlkit.nl.translate.TranslateLanguage
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
    private var textToSpeech: TextToSpeech? = null
    private var initialized = false
    private var closed = false
    private var generation = 0
    private var utteranceSequence = 0L
    private var activeUtteranceId: String? = null

    fun initialize(sourceLanguage: String) {
        checkMainThread()
        if (closed || initialized) return
        initialized = true
        ConversationStateHolder.update { it.copy(sourceLanguage = sourceLanguage) }
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    fun setSourceLanguage(language: String) {
        if (language !in setOf("en", "es")) return
        ConversationStateHolder.update { it.copy(sourceLanguage = language) }
        refreshOfflineVoices()
    }

    fun setSpeaker(speaker: ConversationSpeaker) {
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
                status = if (it.offlineSpeechAvailable) {
                    "Agora fale ${if (speaker == ConversationSpeaker.USER_PORTUGUESE) "em português" else "em ${languageName(it.sourceLanguage)}"}."
                } else speechUnavailableMessage(),
            )
        }
        refreshOfflineVoices()
    }

    fun listenForTurn() {
        checkMainThread()
        if (closed) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus("A permissão do microfone foi revogada. Reabra a conversa e autorize o microfone.")
            return
        }
        val currentRecognizer = recognizer
        if (currentRecognizer == null) {
            updateStatus(speechUnavailableMessage())
            return
        }
        generation++
        activeUtteranceId = null
        textToSpeech?.stop()
        val speaker = ConversationStateHolder.state.value.speaker
        val selectedLanguage = ConversationStateHolder.state.value.sourceLanguage
        val languageTag = inputLanguageTag(speaker, selectedLanguage)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        ConversationStateHolder.update {
            it.copy(
                partialTranscript = "",
                finalTranscript = "",
                translatedText = "",
                isListening = true,
                isSpeaking = false,
                status = "Ouvindo em ${languageName(it.sourceLanguage, speaker)}…",
            )
        }
        currentRecognizer.setRecognitionListener(createRecognitionListener(generation, speaker, selectedLanguage))
        try {
            currentRecognizer.startListening(intent)
        } catch (_: SecurityException) {
            ConversationStateHolder.update { it.copy(isListening = false, status = "O Android bloqueou o acesso ao microfone.") }
        } catch (_: RuntimeException) {
            ConversationStateHolder.update { it.copy(isListening = false, status = "O serviço de reconhecimento não conseguiu iniciar. Tente novamente.") }
        }
    }

    fun pause() {
        generation++
        activeUtteranceId = null
        stopListening(clearPartial = true)
        textToSpeech?.stop()
        ConversationStateHolder.update { it.copy(isListening = false, isSpeaking = false, status = "Conversa pausada.") }
    }

    fun cancelTurn() {
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
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus("Autorize o microfone para iniciar uma conversa.")
            return
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                updateStatus("Reconhecimento local por voz requer Android 12 ou posterior. Atualize o Android para conversar com processamento no aparelho.")
                return
            }
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                updateStatus("Nenhum serviço de reconhecimento está instalado. Instale ou habilite um serviço de voz do Android.")
                return
            }
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                updateStatus("O reconhecimento offline não está disponível. Instale os pacotes de voz offline nas Configurações do Android.")
                return
            }
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext).also { service ->
                ConversationStateHolder.update {
                    it.copy(offlineSpeechAvailable = true, status = "Reconhecimento offline pronto. Escolha quem fala e inicie um turno.")
                }
            }
        } catch (_: UnsupportedOperationException) {
            ConversationStateHolder.update { it.copy(offlineSpeechAvailable = false, status = speechUnavailableMessage()) }
        } catch (_: RuntimeException) {
            ConversationStateHolder.update { it.copy(offlineSpeechAvailable = false, status = "O serviço de reconhecimento offline falhou ao iniciar.") }
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
        selectedLanguage: String,
    ) = object : RecognitionListener {
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
                translateTurn(transcript, speaker, selectedLanguage, turnGeneration)
            }
        }

        override fun onError(error: Int) {
            if (!isCurrentTurn()) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                onMicrophonePermissionRevoked()
                return
            }
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não detectei fala. Fale mais perto do microfone e tente novamente."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "O pacote offline deste idioma não está instalado. Baixe-o em Configurações do Android → Sistema → Idiomas e entrada → Reconhecimento de fala."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "O serviço offline não concluiu o reconhecimento. Verifique o pacote de voz offline e tente de novo; o EasyApp não guarda o áudio."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O serviço de voz ainda está ocupado. Aguarde um instante e tente novamente."
                else -> "O reconhecimento de fala falhou (erro $error). Verifique o serviço de voz do Android e tente novamente."
            }
            ConversationStateHolder.update { it.copy(isListening = false, partialTranscript = "", status = message) }
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

    private fun translateTurn(transcript: String, speaker: ConversationSpeaker, selectedLanguage: String, turnGeneration: Int) {
        val (source, target, targetLocale) = if (speaker == ConversationSpeaker.USER_PORTUGUESE) {
            Triple(TranslateLanguage.PORTUGUESE, languageCode(selectedLanguage), outputLocale(selectedLanguage))
        } else {
            Triple(languageCode(selectedLanguage), TranslateLanguage.PORTUGUESE, "pt-BR")
        }
        val translator = translators.getOrPut("$source->$target") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
        ConversationStateHolder.update { it.copy(status = "Preparando modelo de tradução local…") }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnFailureListener {
                if (turnGeneration == generation && !closed) {
                    ConversationStateHolder.update {
                        it.copy(status = "Não foi possível obter o modelo local. Conecte-se para baixá-lo e tente novamente.")
                    }
                }
            }
            .addOnSuccessListener modelReady@{
                if (turnGeneration != generation || closed) return@modelReady
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
        val targetLanguage = if (snapshot.speaker == ConversationSpeaker.USER_PORTUGUESE) snapshot.sourceLanguage else "pt"
        val allOffline = engine.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        val choices = allOffline.filter { it.locale.language == targetLanguage }
            .sortedWith(compareBy<Voice>({ if (targetLanguage == "pt" && it.locale.country == "BR") 0 else 1 }, { it.name }))
            .map { voice -> OfflineVoiceOption(voice.name, "${voice.locale.displayName} · ${voice.name}", voice.locale.toLanguageTag()) }
        val oldSelection = snapshot.selectedVoiceId
        val selected = choices.firstOrNull { it.id == oldSelection }?.id ?: choices.firstOrNull()?.id
        if (selected != null) {
            engine.voices?.firstOrNull { it.name == selected }?.let(engine::setVoice)
        } else {
            val locale = Locale.forLanguageTag(if (targetLanguage == "pt") "pt-BR" else outputLocale(targetLanguage))
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
        if (ConversationStateHolder.state.value.isListening) runCatching { recognizer?.cancel() }
        if (clearPartial) ConversationStateHolder.update { it.copy(partialTranscript = "", isListening = false) }
    }

    private fun speechUnavailableMessage(): String =
        "Reconhecimento de fala offline indisponível. Ative um serviço SpeechRecognizer e instale os pacotes offline de português, inglês e espanhol nas Configurações do Android."

    private fun updateStatus(message: String) {
        ConversationStateHolder.update { it.copy(status = message) }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "ConversationEngine deve ser chamado na thread principal" }
    }

    private fun inputLanguageTag(speaker: ConversationSpeaker, foreignLanguage: String) = when (speaker) {
        ConversationSpeaker.USER_PORTUGUESE -> "pt-BR"
        ConversationSpeaker.INTERLOCUTOR -> if (foreignLanguage == "es") "es-ES" else "en-US"
    }

    private fun languageCode(language: String) = if (language == "es") TranslateLanguage.SPANISH else TranslateLanguage.ENGLISH
    private fun outputLocale(language: String) = if (language == "es") "es-ES" else "en-US"
    private fun languageName(language: String, speaker: ConversationSpeaker? = null): String = when {
        speaker == ConversationSpeaker.USER_PORTUGUESE -> "português brasileiro"
        speaker == ConversationSpeaker.INTERLOCUTOR -> if (language == "es") "espanhol" else "inglês"
        language == "pt" -> "português"
        language == "es" -> "espanhol"
        else -> "inglês"
    }
}
