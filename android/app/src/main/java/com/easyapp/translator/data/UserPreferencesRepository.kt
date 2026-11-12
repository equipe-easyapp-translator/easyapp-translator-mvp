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

data class UserPreferences(
    val qualityMode: QualityMode = QualityMode.BALANCED,
    val preferredMode: AppMode = AppMode.SCREEN,
    val sourceLanguage: String = "en",
    val targetLanguage: String = "pt",
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
            qualityMode = stored[QUALITY_MODE]
                ?.let { value -> QualityMode.entries.firstOrNull { it.name == value } }
                ?: QualityMode.BALANCED,
            preferredMode = stored[PREFERRED_MODE]
                ?.let { value -> AppMode.entries.firstOrNull { it.name == value } }
                ?: AppMode.SCREEN,
            sourceLanguage = stored[SOURCE_LANGUAGE]?.takeIf { it in SUPPORTED_LANGUAGES } ?: "en",
            targetLanguage = stored[TARGET_LANGUAGE]?.takeIf { it in SUPPORTED_TARGET_LANGUAGES } ?: "pt",
        )
    }

    suspend fun setQualityMode(mode: QualityMode) {
        dataStore.edit { it[QUALITY_MODE] = mode.name }
    }

    suspend fun setPreferredMode(mode: AppMode) {
        dataStore.edit { it[PREFERRED_MODE] = mode.name }
    }

    suspend fun setLanguages(source: String, target: String) {
        if (source in SUPPORTED_LANGUAGES && target in SUPPORTED_TARGET_LANGUAGES) {
            dataStore.edit {
                it[SOURCE_LANGUAGE] = source
                it[TARGET_LANGUAGE] = target
            }
        }
    }

    private companion object {
        val QUALITY_MODE = stringPreferencesKey("quality_mode")
        val PREFERRED_MODE = stringPreferencesKey("preferred_mode")
        val SOURCE_LANGUAGE = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val SUPPORTED_LANGUAGES = setOf("en", "es", "it", "fr", "pt")
        val SUPPORTED_TARGET_LANGUAGES = setOf("pt", "en", "es", "it", "fr")
    }
}
