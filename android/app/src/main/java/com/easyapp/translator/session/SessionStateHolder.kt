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
            message = if (mode == AppMode.SCREEN) "Captura ativa; procurando texto para traduzir." else "Sessão de conversa preparada.",
        )
    }

    fun pause() {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            status = SessionStatus.PAUSED,
            message = "Sessão pausada",
        )
    }

    fun resume() {
        val mode = mutableSnapshot.value.mode
        mutableSnapshot.value = mutableSnapshot.value.copy(
            status = SessionStatus.ACTIVE,
            message = if (mode == AppMode.SCREEN) "Captura retomada; procurando texto para traduzir." else "Sessão retomada.",
        )
    }

    fun stop(message: String = "Sessão encerrada") {
        mutableSnapshot.value = SessionSnapshot(message = message)
    }
}
