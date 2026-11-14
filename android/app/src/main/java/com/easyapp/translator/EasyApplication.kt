package com.easyapp.translator

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes

/**
 * Guarda o contexto da aplicação para o código fora das telas (serviço, pipelines,
 * mensagens de estado) ler textos traduzidos com [str].
 */
class EasyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        refreshLocale()
    }

    companion object {
        private lateinit var instance: EasyApplication

        /** Contexto com o idioma do app aplicado (ver [AppLocale]). */
        lateinit var appContext: Context
            private set

        /** Chamado ao trocar o idioma no Android 11–12, antes de recriar a tela. */
        fun refreshLocale() {
            appContext = AppLocale.wrap(instance)
        }
    }
}

/** Texto da interface no idioma atual do app (português, italiano ou inglês). */
fun str(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) EasyApplication.appContext.getString(id)
    else EasyApplication.appContext.getString(id, *args)
