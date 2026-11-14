package com.easyapp.translator

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.easyapp.translator.ui.AppLanguage
import java.util.Locale

/**
 * Idioma da interface em qualquer versão do Android (FR-033, História 9).
 *
 * - Android 13+: usa o idioma por app do próprio sistema (`LocaleManager`), que
 *   também aparece em Configurações → Apps → EasyApp → Idioma.
 * - Android 11 e 12: o sistema não tem essa opção, então o app guarda a escolha
 *   e aplica o idioma nos contextos da Activity e da aplicação.
 */
object AppLocale {
    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"
    private const val KEY_CHOSEN = "chosen"

    private val usesSystemPerAppLanguage get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Idioma do celular ao abrir o app, para voltar a ele ao escolher "Sistema" no Android 11–12. */
    private val systemDefault: Locale = Locale.getDefault()

    /** A tela de boas-vindas aparece até a pessoa escolher um idioma uma vez. */
    fun hasChosen(context: Context): Boolean = prefs(context).getBoolean(KEY_CHOSEN, false)

    fun markChosen(context: Context) {
        prefs(context).edit().putBoolean(KEY_CHOSEN, true).apply()
    }

    fun current(context: Context): AppLanguage {
        val tag = if (usesSystemPerAppLanguage) {
            runCatching { context.getSystemService(LocaleManager::class.java).applicationLocales }
                .getOrNull()
                ?.takeUnless { it.isEmpty }
                ?.get(0)
                ?.language
                .orEmpty()
        } else {
            storedTag(context).substringBefore('-')
        }
        return when (tag) {
            "pt" -> AppLanguage.PORTUGUESE
            "it" -> AppLanguage.ITALIAN
            "en" -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM
        }
    }

    /**
     * Guarda e aplica a escolha. Devolve `true` quando quem chamou precisa recriar a
     * tela (Android 11–12); no 13+, o próprio sistema recria a Activity.
     */
    fun apply(context: Context, language: AppLanguage): Boolean {
        if (usesSystemPerAppLanguage) {
            val locales = if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
            runCatching { context.getSystemService(LocaleManager::class.java).applicationLocales = locales }
            return false
        }
        prefs(context).edit().putString(KEY_TAG, language.tag).apply()
        return true
    }

    /** Contexto com o idioma escolhido (só faz diferença no Android 11–12). */
    fun wrap(base: Context): Context {
        if (usesSystemPerAppLanguage) return base
        val tag = storedTag(base)
        if (tag.isEmpty()) {
            Locale.setDefault(systemDefault)
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        // Também vale para formatação de números e para o par de idiomas inicial.
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    private fun storedTag(context: Context): String = prefs(context).getString(KEY_TAG, "").orEmpty()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
