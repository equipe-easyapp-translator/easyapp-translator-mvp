package com.easyapp.translator.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioCaptionEntry(
    val id: Long,
    val source: String,
    val translated: String,
    val turn: Int,
    val timestamp: Long,
)

data class AudioCaptionSnapshot(
    val status: String = "Legendas de áudio interno aguardando a sessão de tela.",
    val isCapturing: Boolean = false,
    val vadActive: Boolean = false,
    val partialText: String = "",
    val history: List<AudioCaptionEntry> = emptyList(),
)

object AudioCaptionStateHolder {
    private val mutableState = MutableStateFlow(AudioCaptionSnapshot())
    val state: StateFlow<AudioCaptionSnapshot> = mutableState.asStateFlow()

    fun update(transform: (AudioCaptionSnapshot) -> AudioCaptionSnapshot) {
        mutableState.value = transform(mutableState.value)
    }

    fun add(source: String, translated: String, turn: Int) {
        if (source.isBlank() || translated.isBlank()) return
        val entry = AudioCaptionEntry(System.nanoTime(), source, translated, turn, System.currentTimeMillis())
        update { it.copy(history = (it.history + entry).takeLast(MAX_HISTORY), partialText = "") }
    }

    fun clearHistory() {
        update { it.copy(history = emptyList(), partialText = "", status = "Buffer temporário limpo.") }
    }

    fun clear(message: String = "Captura de áudio encerrada; buffer volátil descartado.") {
        mutableState.value = AudioCaptionSnapshot(status = message)
    }

    private const val MAX_HISTORY = 40
}
