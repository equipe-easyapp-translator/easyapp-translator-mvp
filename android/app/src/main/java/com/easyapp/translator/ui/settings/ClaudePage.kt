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
import com.easyapp.translator.str

/** Página do Claude: o que faz, a chave, o custo e o gasto da sessão. */
@Composable
fun ClaudePage(state: AppUiState, actions: AppActions, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(EasyTheme.colors.paper)) {
        SubPageTopBar(str(R.string.claude_page_title), onBack)
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
                SectionLabel(str(R.string.claude_current_session))
                ClaudeUsageCard(state.claudeUsage)
            }
            SectionLabel(str(R.string.claude_how))
            InfoNote(
                title = str(R.string.claude_when_title),
                body = str(R.string.claude_when_body),
                icon = R.drawable.ic_ui_sparkle,
            )
            InfoNote(
                title = str(R.string.claude_cost_title),
                body = str(R.string.claude_cost_body),
                icon = R.drawable.ic_ui_info,
            )
            InfoNote(
                title = str(R.string.settings_privacy),
                body = str(R.string.claude_privacy_body),
                icon = R.drawable.ic_ui_shield,
            )
        }
    }
}
