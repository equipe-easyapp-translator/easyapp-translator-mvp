package com.easyapp.translator.session

import com.easyapp.translator.R
import com.easyapp.translator.str

enum class AppMode(private val titleRes: Int, private val descriptionRes: Int) {
    SCREEN(R.string.mode_screen_title, R.string.mode_screen_description),
    CONVERSATION(R.string.mode_conversation_title, R.string.mode_conversation_description),
    ;

    val title: String get() = str(titleRes)
    val description: String get() = str(descriptionRes)
}

enum class SessionStatus {
    IDLE,
    ACTIVE,
    PAUSED,
}

data class SessionSnapshot(
    val status: SessionStatus = SessionStatus.IDLE,
    val mode: AppMode? = null,
    val message: String = str(R.string.session_ready),
    val sourceLanguage: String = "en",
    val targetLanguage: String = "pt",
    val translationModelStatus: String = str(R.string.model_status_default),
)
