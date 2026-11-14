package com.easyapp.translator.ui

import com.easyapp.translator.PermissionUiState
import com.easyapp.translator.data.ConversationLayout
import com.easyapp.translator.data.OverlayStyle
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.data.TextSize
import com.easyapp.translator.data.ThemeMode
import com.easyapp.translator.data.UserPreferences
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.AudioCaptionSnapshot
import com.easyapp.translator.session.ConversationSnapshot
import com.easyapp.translator.session.ConversationSpeaker
import com.easyapp.translator.session.SessionSnapshot
import com.easyapp.translator.translation.ClaudeUsage
import com.easyapp.translator.ui.home.ClaudeUiState

/** Tudo o que a interface mostra, num único objeto (T059). */
data class AppUiState(
    val preferences: UserPreferences = UserPreferences.DEFAULT,
    val session: SessionSnapshot = SessionSnapshot(),
    val conversation: ConversationSnapshot = ConversationSnapshot(),
    val audioCaptions: AudioCaptionSnapshot = AudioCaptionSnapshot(),
    val permissions: PermissionUiState = PermissionUiState(),
    val screenAudioCaptionsSelected: Boolean = false,
    val showPrivacyNotice: Boolean = false,
    val claude: ClaudeUiState = ClaudeUiState(),
    val claudeUsage: ClaudeUsage = ClaudeUsage(),
    val appVersion: String = "",
)

/** Tudo o que a interface pode pedir; implementado pela MainActivity. */
interface AppActions {
    fun rememberMode(mode: AppMode)
    fun setLanguages(source: String, target: String)
    fun setQuality(mode: QualityMode)
    fun setOverlayStyle(style: OverlayStyle)
    fun setThemeMode(mode: ThemeMode)
    fun setAppTextSize(size: TextSize)
    fun setTranslationTextSize(size: TextSize)
    fun setConversationLayout(layout: ConversationLayout)
    fun setScreenAudioCaptions(enabled: Boolean)
    fun resetPreferences()

    fun start(mode: AppMode)
    fun pause()
    fun resume()
    fun stop()
    fun requestPermissions()
    fun openOverlaySettings()
    fun acceptPrivacyNotice()
    fun declinePrivacyNotice()

    /** Escolhe quem fala e já começa a ouvir (um botão por pessoa, FR-030). */
    fun speak(speaker: ConversationSpeaker)
    fun cancelListening()
    fun repeatTranslation()
    fun selectVoice(voiceId: String)
    fun setSpeechRate(rate: Float)
    fun setVolume(volume: Float)
    fun clearAudioCaptions()

    fun claudeSaveKey(key: String)
    fun claudeAcceptConsent()
    fun claudeSetEnabled(enabled: Boolean)
    fun claudeRemoveKey()
    fun claudeTest()
}
