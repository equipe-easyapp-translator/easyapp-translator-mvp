package com.easyapp.translator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.easyapp.translator.session.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.easyapp.translator.R
import com.easyapp.translator.str

enum class QualityMode(private val labelRes: Int) {
    ECONOMY(R.string.quality_economy),
    BALANCED(R.string.quality_balanced),
    QUALITY(R.string.quality_quality),
    ;

    val label: String get() = str(labelRes)
}

/** Como a tradução do modo Tela aparece (FR-004). */
enum class OverlayStyle(private val labelRes: Int) {
    IN_PLACE(R.string.style_in_place),
    FLOATING(R.string.style_floating),
    ;

    val label: String get() = str(labelRes)
}

/** Tema do app (FR-029). */
enum class ThemeMode(private val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    ;

    val label: String get() = str(labelRes)
}

/** Escala de texto (FR-029). Usada tanto para o app quanto para a tradução sobre a tela. */
enum class TextSize(private val labelRes: Int, val scale: Float) {
    SMALL(R.string.size_small, 0.9f),
    DEFAULT(R.string.size_default, 1f),
    LARGE(R.string.size_large, 1.15f),
    HUGE(R.string.size_huge, 1.3f),
    ;

    val label: String get() = str(labelRes)
}

/** Disposição da conversa (FR-030). */
enum class ConversationLayout(private val labelRes: Int) {
    FACE_TO_FACE(R.string.layout_face),
    SIDE_BY_SIDE(R.string.layout_side),
    ;

    val label: String get() = str(labelRes)
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
            sourceLanguage = stored[SOURCE_LANGUAGE]?.takeIf { it in SUPPORTED_LANGUAGES } ?: defaultPair().first,
            targetLanguage = stored[TARGET_LANGUAGE]?.takeIf { it in SUPPORTED_TARGET_LANGUAGES } ?: defaultPair().second,
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

        /**
         * Par inicial a partir do idioma do celular: o idioma do aparelho é o "seu"
         * (destino). Um brasileiro traduz do inglês; um italiano, um inglês ou quem
         * usa outro idioma traduz do português, que é o que encontra no Brasil.
         */
        fun defaultPair(): Pair<String, String> = when (val device = java.util.Locale.getDefault().language) {
            "pt" -> "en" to "pt"
            in SUPPORTED_TARGET_LANGUAGES -> "pt" to device
            else -> "pt" to "en"
        }

        inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
            value?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default
    }
}
