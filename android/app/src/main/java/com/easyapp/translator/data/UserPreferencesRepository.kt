package com.easyapp.translator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.easyapp.translator.session.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class QualityMode(val label: String) {
    ECONOMY("Econômico"),
    BALANCED("Equilibrado"),
    QUALITY("Qualidade"),
}

/** Como a tradução do modo Tela aparece (FR-004). */
enum class OverlayStyle(val label: String) {
    IN_PLACE("No lugar do texto"),
    FLOATING("Janela flutuante"),
}

/** Tema do app (FR-029). */
enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Escuro"),
}

/** Escala de texto (FR-029). Usada tanto para o app quanto para a tradução sobre a tela. */
enum class TextSize(val label: String, val scale: Float) {
    SMALL("Pequeno", 0.9f),
    DEFAULT("Padrão", 1f),
    LARGE("Grande", 1.15f),
    HUGE("Muito grande", 1.3f),
}

/** Disposição da conversa (FR-030). */
enum class ConversationLayout(val label: String) {
    FACE_TO_FACE("Frente a frente"),
    SIDE_BY_SIDE("Lado a lado"),
}

data class UserPreferences(
    val qualityMode: QualityMode = QualityMode.BALANCED,
    val preferredMode: AppMode = AppMode.SCREEN,
    val sourceLanguage: String = "en",
    val targetLanguage: String = "pt",
    val overlayStyle: OverlayStyle = OverlayStyle.IN_PLACE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appTextSize: TextSize = TextSize.DEFAULT,
    val translationTextSize: TextSize = TextSize.DEFAULT,
    val conversationLayout: ConversationLayout = ConversationLayout.FACE_TO_FACE,
) {
    companion object {
        val DEFAULT = UserPreferences()
    }
}

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

/** Persists only interface preferences; no screenshots, audio, transcripts, or translations. */
class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.userPreferencesDataStore

    val preferences: Flow<UserPreferences> = dataStore.data.map { stored ->
        UserPreferences(
            qualityMode = enumOrDefault(stored[QUALITY_MODE], QualityMode.BALANCED),
            preferredMode = enumOrDefault(stored[PREFERRED_MODE], AppMode.SCREEN),
            sourceLanguage = stored[SOURCE_LANGUAGE]?.takeIf { it in SUPPORTED_LANGUAGES } ?: "en",
            targetLanguage = stored[TARGET_LANGUAGE]?.takeIf { it in SUPPORTED_TARGET_LANGUAGES } ?: "pt",
            overlayStyle = enumOrDefault(stored[OVERLAY_STYLE], OverlayStyle.IN_PLACE),
            themeMode = enumOrDefault(stored[THEME_MODE], ThemeMode.SYSTEM),
            appTextSize = enumOrDefault(stored[APP_TEXT_SIZE], TextSize.DEFAULT),
            translationTextSize = enumOrDefault(stored[TRANSLATION_TEXT_SIZE], TextSize.DEFAULT),
            conversationLayout = enumOrDefault(stored[CONVERSATION_LAYOUT], ConversationLayout.FACE_TO_FACE),
        )
    }

    suspend fun setQualityMode(mode: QualityMode) {
        dataStore.edit { it[QUALITY_MODE] = mode.name }
    }

    suspend fun setOverlayStyle(style: OverlayStyle) {
        dataStore.edit { it[OVERLAY_STYLE] = style.name }
    }

    suspend fun setPreferredMode(mode: AppMode) {
        dataStore.edit { it[PREFERRED_MODE] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAppTextSize(size: TextSize) {
        dataStore.edit { it[APP_TEXT_SIZE] = size.name }
    }

    suspend fun setTranslationTextSize(size: TextSize) {
        dataStore.edit { it[TRANSLATION_TEXT_SIZE] = size.name }
    }

    suspend fun setConversationLayout(layout: ConversationLayout) {
        dataStore.edit { it[CONVERSATION_LAYOUT] = layout.name }
    }

    suspend fun setLanguages(source: String, target: String) {
        if (source in SUPPORTED_LANGUAGES && target in SUPPORTED_TARGET_LANGUAGES) {
            dataStore.edit {
                it[SOURCE_LANGUAGE] = source
                it[TARGET_LANGUAGE] = target
            }
        }
    }

    /** Volta todas as escolhas de interface ao padrão (Ajustes → Privacidade). */
    suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val QUALITY_MODE = stringPreferencesKey("quality_mode")
        val PREFERRED_MODE = stringPreferencesKey("preferred_mode")
        val SOURCE_LANGUAGE = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val OVERLAY_STYLE = stringPreferencesKey("overlay_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_TEXT_SIZE = stringPreferencesKey("app_text_size")
        val TRANSLATION_TEXT_SIZE = stringPreferencesKey("translation_text_size")
        val CONVERSATION_LAYOUT = stringPreferencesKey("conversation_layout")
        val SUPPORTED_LANGUAGES = setOf("en", "es", "it", "fr", "pt")
        val SUPPORTED_TARGET_LANGUAGES = setOf("pt", "en", "es", "it", "fr")

        inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
            value?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default
    }
}
