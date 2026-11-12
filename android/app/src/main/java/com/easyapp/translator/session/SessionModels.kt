package com.easyapp.translator.session

enum class AppMode(val title: String, val description: String) {
    SCREEN("Tradução de Tela e Apps", "Texto em sobreposição e legendas quando a captura for permitida."),
    CONVERSATION("Conversa Presencial por Voz", "Fale por turnos e ouça a tradução no idioma escolhido."),
}

enum class SessionStatus {
    IDLE,
    ACTIVE,
    PAUSED,
}

data class SessionSnapshot(
    val status: SessionStatus = SessionStatus.IDLE,
    val mode: AppMode? = null,
    val message: String = "Pronto para iniciar",
)
