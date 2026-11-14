package com.easyapp.translator.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.easyapp.translator.R
import com.easyapp.translator.str

/** In-memory session state only. Translation content and transcripts are never stored here. */
object SessionStateHolder {
    private val mutableSnapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = mutableSnapshot.asStateFlow()

    fun start(mode: AppMode) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            status = SessionStatus.ACTIVE,
            mode = mode,
            message = if (mode == AppMode.SCREEN) str(R.string.session_screen_active) else str(R.string.session_conversation_ready),
            translationModelStatus = str(R.string.model_status_default),
        )
    }

    fun pause() {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            status = SessionStatus.PAUSED,
            message = str(R.string.session_paused),
        )
    }

    fun resume() {
        val mode = mutableSnapshot.value.mode
        mutableSnapshot.value = mutableSnapshot.value.copy(
            status = SessionStatus.ACTIVE,
            message = if (mode == AppMode.SCREEN) str(R.string.session_screen_resumed) else str(R.string.session_resumed),
        )
    }

    fun stop(message: String = str(R.string.session_ended)) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            status = SessionStatus.IDLE,
            mode = null,
            message = message,
            translationModelStatus = str(R.string.model_status_default),
        )
    }

    fun updateLanguages(source: String, target: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(sourceLanguage = source, targetLanguage = target)
    }

    fun updateTranslationModelStatus(status: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(translationModelStatus = status)
    }
}
