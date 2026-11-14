package com.easyapp.translator.session

import com.google.mlkit.nl.translate.TranslateLanguage
import com.easyapp.translator.R
import com.easyapp.translator.str

/**
 * [label]: nome em português (interface). [nativeName]: nome no próprio idioma,
 * para quem não lê português reconhecer o seu na lista (persona Marco).
 */
/** [flag]: bandeira (emoji) do país da variante usada pelo app: pt-BR, en-US, es-ES, it-IT, fr-FR. */
data class LanguageChoice(val code: String, private val labelRes: Int, val tag: String, val nativeName: String, val flag: String) {
    /** Nome no idioma da interface. */
    val label: String get() = str(labelRes)

    /** Nome para o meio de uma frase: minúsculo em português e italiano, maiúsculo em inglês. */
    val inSentence: String get() = if (str(R.string.language_names_lowercase) == "true") label.lowercase() else label
}

object TranslationLanguages {
    val sourceChoices = listOf(
        LanguageChoice("en", R.string.lang_en, "en-US", "English", "🇺🇸"),
        LanguageChoice("es", R.string.lang_es, "es-ES", "Español", "🇪🇸"),
        LanguageChoice("it", R.string.lang_it, "it-IT", "Italiano", "🇮🇹"),
        LanguageChoice("fr", R.string.lang_fr, "fr-FR", "Français", "🇫🇷"),
        LanguageChoice("pt", R.string.lang_pt, "pt-BR", "Português", "🇧🇷"),
    )

    val targetChoices = listOf(
        LanguageChoice("pt", R.string.lang_pt, "pt-BR", "Português", "🇧🇷"),
        LanguageChoice("en", R.string.lang_en, "en-US", "English", "🇺🇸"),
        LanguageChoice("es", R.string.lang_es, "es-ES", "Español", "🇪🇸"),
        LanguageChoice("it", R.string.lang_it, "it-IT", "Italiano", "🇮🇹"),
        LanguageChoice("fr", R.string.lang_fr, "fr-FR", "Français", "🇫🇷"),
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
