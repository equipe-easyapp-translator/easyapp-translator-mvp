package com.easyapp.translator.session

import com.google.mlkit.nl.translate.TranslateLanguage

data class LanguageChoice(val code: String, val label: String, val tag: String)

object TranslationLanguages {
    val sourceChoices = listOf(
        LanguageChoice("en", "Inglês", "en-US"),
        LanguageChoice("es", "Espanhol", "es-ES"),
        LanguageChoice("it", "Italiano", "it-IT"),
        LanguageChoice("fr", "Francês", "fr-FR"),
        LanguageChoice("pt", "Português", "pt-BR"),
    )

    val targetChoices = listOf(
        LanguageChoice("pt", "Português", "pt-BR"),
        LanguageChoice("en", "Inglês", "en-US"),
        LanguageChoice("es", "Espanhol", "es-ES"),
        LanguageChoice("it", "Italiano", "it-IT"),
        // French is exposed as a target only when the inversion button needs it.
        LanguageChoice("fr", "Francês", "fr-FR"),
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
