package com.easyapp.translator.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easyapp.translator.R
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.session.SessionStatus
import com.easyapp.translator.ui.AppActions
import com.easyapp.translator.ui.AppUiState
import com.easyapp.translator.ui.components.InfoNote
import com.easyapp.translator.ui.components.PageScaffold
import com.easyapp.translator.ui.components.SectionLabel
import com.easyapp.translator.ui.components.SubPageTopBar
import com.easyapp.translator.ui.home.ClaudeSetupCard
import com.easyapp.translator.ui.home.ClaudeUsageCard
import com.easyapp.translator.ui.theme.EasyTheme

/** Página do Claude: o que faz, a chave, o custo e o gasto da sessão. */
@Composable
fun ClaudePage(state: AppUiState, actions: AppActions, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(EasyTheme.colors.paper)) {
        SubPageTopBar("Tradução com Claude", onBack)
        PageScaffold(Modifier.weight(1f)) {
            ClaudeSetupCard(
                state = state.claude,
                qualitySelected = state.preferences.qualityMode == QualityMode.QUALITY,
                onSaveKey = actions::claudeSaveKey,
                onConsentAccepted = actions::claudeAcceptConsent,
                onEnabledChanged = actions::claudeSetEnabled,
                onRemoveKey = actions::claudeRemoveKey,
                onTest = actions::claudeTest,
            )
            if (state.claude.usable && state.session.status != SessionStatus.IDLE) {
                SectionLabel("Sessão atual")
                ClaudeUsageCard(state.claudeUsage)
            }
            SectionLabel("Como funciona")
            InfoNote(
                title = "Quando o Claude é usado",
                body = "Na tradução de tela com qualidade \"Qualidade\": as frases da tela vão juntas, com contexto, e voltam traduzidas como uma pessoa traduziria. Na janela flutuante, também ao tocar numa frase para entender o que ela quer dizer.",
                icon = R.drawable.ic_ui_sparkle,
            )
            InfoNote(
                title = "Quanto custa",
                body = "O modelo é o Claude Haiku 5.5, o mais econômico. Uma tela cheia custa cerca de US$ 0,00025, e uma hora de uso contínuo fica em torno de US$ 0,10 a 0,15. O app mostra o gasto real de cada sessão.",
                icon = R.drawable.ic_ui_info,
            )
            InfoNote(
                title = "Privacidade",
                body = "Só o texto lido da tela é enviado, nunca a imagem nem o áudio. A Anthropic apaga esses dados em até 30 dias e, por padrão, não os usa para treinar modelos. Sem internet ou com algum erro, o app volta para a tradução offline.",
                icon = R.drawable.ic_ui_shield,
            )
        }
    }
}
