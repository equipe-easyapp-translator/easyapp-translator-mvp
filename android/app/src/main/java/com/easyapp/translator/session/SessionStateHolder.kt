package com.easyapp.translator.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory session state only. Translation content and transcripts are never stored here. */
object SessionStateHolder {
    private val mutableSnapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = mutableSnapshot.asStateFlow()

    fun start(mode: AppMode) {
        mutableSnapshot.value = SessionSnapshot(
            status = SessionStatus.ACTIVE,
            mode = mode,
            message = "Sessão preparada; captura e reconhecimento serão conectados nas próximas fases.",
        )
    }

    fun pause() {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            status = SessionStatus.PAUSED,
            message = "Sessão pausada",
        )
    }

    fun resume() {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            status = SessionStatus.ACTIVE,
            message = "Sessão retomada; captura e reconhecimento serão conectados nas próximas fases.",
        )
    }

    fun stop(message: String = "Sessão encerrada") {
        mutableSnapshot.value = SessionSnapshot(message = message)
    }
}
