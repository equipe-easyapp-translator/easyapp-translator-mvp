package com.easyapp.translator.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.easyapp.translator.R
import com.easyapp.translator.str

data class AudioCaptionEntry(
    val id: Long,
    val source: String,
    val translated: String,
    val turn: Int,
    val timestamp: Long,
)

data class AudioCaptionSnapshot(
    val status: String = str(R.string.captions_waiting),
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
        update { it.copy(history = emptyList(), partialText = "", status = str(R.string.captions_cleared)) }
    }

    fun clear(message: String = str(R.string.captions_stopped)) {
        mutableState.value = AudioCaptionSnapshot(status = message)
    }

    private const val MAX_HISTORY = 40
}
