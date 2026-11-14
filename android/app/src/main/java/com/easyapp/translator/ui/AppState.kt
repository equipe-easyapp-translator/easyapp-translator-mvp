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

/**
 * Idioma da interface. Português, italiano e inglês aparecem no próprio idioma
 * (é assim que cada pessoa procura o seu); só "Sistema" é traduzido.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    PORTUGUESE("pt-BR"),
    ITALIAN("it"),
    ENGLISH("en"),
    ;

    /** 📱 para "Sistema" (segue o celular); a bandeira da variante usada nos outros. */
    val flag: String
        get() = when (this) {
            SYSTEM -> "📱"
            PORTUGUESE -> "🇧🇷"
            ITALIAN -> "🇮🇹"
            ENGLISH -> "🇺🇸"
        }

    val label: String
        get() = when (this) {
            SYSTEM -> com.easyapp.translator.str(com.easyapp.translator.R.string.theme_system)
            PORTUGUESE -> "Português"
            ITALIAN -> "Italiano"
            ENGLISH -> "English"
        }
}

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
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    /** Primeira abertura: a tela de boas-vindas pede o idioma antes de tudo. */
    val needsLanguageChoice: Boolean = false,
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
    /** Aplica o idioma e recarrega a interface. */
    fun setAppLanguage(language: AppLanguage)
    /** A pessoa escolheu o idioma na boas-vindas; não mostrar de novo. */
    fun completeLanguageChoice()

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
