package com.easyapp.translator

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes

/**
 * Guarda o contexto da aplicação para o código fora das telas (serviço, pipelines,
 * mensagens de estado) ler textos traduzidos com [str]. As telas usam `stringResource`.
 */
class EasyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}

/** Texto da interface no idioma atual do app (português, italiano ou inglês). */
fun str(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) EasyApplication.appContext.getString(id)
    else EasyApplication.appContext.getString(id, *args)
