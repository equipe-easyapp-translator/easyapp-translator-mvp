package com.easyapp.translator.session

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * [label]: nome em português (interface). [nativeName]: nome no próprio idioma,
 * para quem não lê português reconhecer o seu na lista (persona Marco).
 */
data class LanguageChoice(val code: String, val label: String, val tag: String, val nativeName: String)

object TranslationLanguages {
    val sourceChoices = listOf(
        LanguageChoice("en", "Inglês", "en-US", "English"),
        LanguageChoice("es", "Espanhol", "es-ES", "Español"),
        LanguageChoice("it", "Italiano", "it-IT", "Italiano"),
        LanguageChoice("fr", "Francês", "fr-FR", "Français"),
        LanguageChoice("pt", "Português", "pt-BR", "Português"),
    )

    val targetChoices = listOf(
        LanguageChoice("pt", "Português", "pt-BR", "Português"),
        LanguageChoice("en", "Inglês", "en-US", "English"),
        LanguageChoice("es", "Espanhol", "es-ES", "Español"),
        LanguageChoice("it", "Italiano", "it-IT", "Italiano"),
        LanguageChoice("fr", "Francês", "fr-FR", "Français"),
    )

    fun mlKitCode(code: String): String? = when (code) {
        "en" -> TranslateLanguage.ENGLISH
        "es" -> TranslateLanguage.SPANISH
        "it" -> TranslateLanguage.ITALIAN
        "fr" -> TranslateLanguage.FRENCH
        "pt" -> TranslateLanguage.PORTUGUESE
        else -> null
    }

    fun choice(code: String): LanguageChoice = sourceChoices.first { it.code == code }
}
