package com.easyapp.translator.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConversationSpeaker { USER_PORTUGUESE, INTERLOCUTOR }

data class OfflineVoiceOption(val id: String, val label: String, val languageTag: String)

data class ConversationSnapshot(
    val speaker: ConversationSpeaker = ConversationSpeaker.USER_PORTUGUESE,
    val sourceLanguage: String = "en",
    val targetLanguage: String = "pt",
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val translatedText: String = "",
    val repeatText: String = "",
    val repeatLanguageTag: String = "en-US",
    val status: String = "Escolha quem vai falar e toque em Ouvir turno.",
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val speechRate: Float = 1f,
    val volume: Float = 1f,
    val availableVoices: List<OfflineVoiceOption> = emptyList(),
    val selectedVoiceId: String? = null,
    val offlineSpeechAvailable: Boolean = false,
    val speechRecognizerAvailable: Boolean = false,
    val offlineTtsAvailable: Boolean = false,
)

object ConversationStateHolder {
    private val mutableState = MutableStateFlow(ConversationSnapshot())
    val state: StateFlow<ConversationSnapshot> = mutableState.asStateFlow()

    fun update(transform: (ConversationSnapshot) -> ConversationSnapshot) {
        mutableState.value = transform(mutableState.value)
    }

    fun clear(message: String = "Conversa encerrada. Nenhuma transcrição ou tradução foi salva.") {
        mutableState.value = ConversationSnapshot(status = message)
    }
}
