package com.easyapp.translator.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.easyapp.translator.R
import com.easyapp.translator.data.OverlayStyle
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.SessionStatus
import com.easyapp.translator.ui.AppActions
import com.easyapp.translator.ui.AppUiState
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.EasyCard
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.IllustratedChoice
import com.easyapp.translator.ui.components.InfoNote
import com.easyapp.translator.ui.components.InkPanel
import com.easyapp.translator.ui.components.LanguageBar
import com.easyapp.translator.ui.components.LiveDot
import com.easyapp.translator.ui.components.PageHeader
import com.easyapp.translator.ui.components.PageScaffold
import com.easyapp.translator.ui.components.SectionLabel
import com.easyapp.translator.ui.components.SegmentedControl
import com.easyapp.translator.ui.components.SettingRow
import com.easyapp.translator.ui.components.StatusBadge
import com.easyapp.translator.ui.components.Tone
import com.easyapp.translator.ui.components.floatingIllustration
import com.easyapp.translator.ui.components.inPlaceIllustration
import com.easyapp.translator.ui.home.AudioCaptionsCard
import com.easyapp.translator.ui.home.ClaudeUsageCard
import com.easyapp.translator.ui.home.PermissionsCard
import com.easyapp.translator.ui.home.ScreenAudioCaptionsOption
import com.easyapp.translator.ui.home.StartExplanationDialog
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Space

/** Página do modo Tela (T060). Sem sessão: preparação. Com sessão: painel da sessão. */
@Composable
fun ScreenPage(
    state: AppUiState,
    actions: AppActions,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onOpenClaude: () -> Unit,
) {
    val session = state.session
    val active = session.mode == AppMode.SCREEN && session.status != SessionStatus.IDLE
    val busyElsewhere = session.mode == AppMode.CONVERSATION && session.status != SessionStatus.IDLE
    var showStart by remember { mutableStateOf(false) }
    val prefs = state.preferences
    val audioCaptions = state.screenAudioCaptionsSelected && prefs.overlayStyle == OverlayStyle.FLOATING

    val startAction: @Composable ColumnScope.() -> Unit = {
        if (busyElsewhere) Hint("Encerre a conversa por voz antes de traduzir a tela.", color = EasyTheme.colors.warn)
        EasyButton(
            "Iniciar tradução de tela",
            { showStart = true },
            Modifier.fillMaxWidth(),
            enabled = !busyElsewhere,
            icon = R.drawable.ic_ui_play,
        )
    }
    PageScaffold(bottomAction = if (active) null else startAction) {
        if (active) {
            ScreenSessionPanel(state, actions)
        } else {
            PageHeader(
                title = "Traduzir a tela",
                subtitle = "Leia em português o que aparece em qualquer app: conversas, sites, cardápios, avisos.",
                overline = "Modo Tela",
            )
        }

        LanguageBar(
            leftTitle = "Traduzir de",
            leftCode = prefs.sourceLanguage,
            rightTitle = "Para",
            rightCode = prefs.targetLanguage,
            onPickLeft = onPickSource,
            onPickRight = onPickTarget,
            onSwap = { actions.setLanguages(prefs.targetLanguage, prefs.sourceLanguage) },
        )

        if (active) {
            if (state.claude.usable) ClaudeUsageCard(state.claudeUsage)
            if (audioCaptions) AudioCaptionsCard(state.audioCaptions, session.status == SessionStatus.ACTIVE, actions::clearAudioCaptions)
            SectionLabel("Qualidade da leitura")
            QualitySelector(prefs.qualityMode, actions::setQuality)
            Hint("O estilo de exibição e o tamanho do texto valem a partir da próxima sessão.")
            return@PageScaffold
        }

        SectionLabel("Como a tradução aparece")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IllustratedChoice(
                title = OverlayStyle.IN_PLACE.label,
                description = "Toque num botão e cada frase é trocada pela tradução, no mesmo lugar.",
                selected = prefs.overlayStyle == OverlayStyle.IN_PLACE,
                onClick = { actions.setOverlayStyle(OverlayStyle.IN_PLACE) },
                modifier = Modifier.weight(1f),
            ) { inPlaceIllustration(it) }
            IllustratedChoice(
                title = OverlayStyle.FLOATING.label,
                description = "Uma janela acompanha a tela e mostra as traduções o tempo todo.",
                selected = prefs.overlayStyle == OverlayStyle.FLOATING,
                onClick = { actions.setOverlayStyle(OverlayStyle.FLOATING) },
                modifier = Modifier.weight(1f),
            ) { floatingIllustration(it) }
        }
        if (prefs.overlayStyle == OverlayStyle.FLOATING) {
            ScreenAudioCaptionsOption(selected = state.screenAudioCaptionsSelected, onSelected = actions::setScreenAudioCaptions)
        }

        SectionLabel("Qualidade da leitura")
        QualitySelector(prefs.qualityMode, actions::setQuality)

        SectionLabel("Inteligência artificial")
        EasyCard {
            SettingRow(
                title = "Tradução refinada com Claude",
                subtitle = when {
                    state.claude.usable && prefs.qualityMode == QualityMode.QUALITY -> "Ligada · entende gírias e contexto"
                    state.claude.usable -> "Configurada · escolha Qualidade para usar"
                    state.claude.hasKey -> "Desligada"
                    else -> "Opcional · usa a sua chave da Anthropic"
                },
                icon = R.drawable.ic_ui_sparkle,
                onClick = onOpenClaude,
            )
        }

        val missing = (prefs.overlayStyle == OverlayStyle.FLOATING && audioCaptions && !state.permissions.recordAudioGranted) ||
            !state.permissions.overlayGranted ||
            (state.permissions.notificationsRequired && !state.permissions.notificationsGranted)
        if (missing) {
            SectionLabel("Antes de começar")
            PermissionsCard(state.permissions, AppMode.SCREEN, actions::requestPermissions, actions::openOverlaySettings)
        }

        InfoNote(
            title = "Tudo fica no aparelho",
            body = "A tela é lida e traduzida no celular. Nada é salvo: ao parar, as capturas e traduções são apagadas.",
            icon = R.drawable.ic_ui_shield,
        )
    }

    if (showStart) {
        StartExplanationDialog(
            mode = AppMode.SCREEN,
            audioCaptionsSelected = audioCaptions,
            onConfirm = {
                showStart = false
                actions.start(AppMode.SCREEN)
            },
            onDismiss = { showStart = false },
        )
    }
}

@Composable
private fun QualitySelector(selected: QualityMode, onSelect: (QualityMode) -> Unit) {
    EasyCard {
        SegmentedControl(
            options = QualityMode.entries,
            selected = selected,
            onSelect = onSelect,
            optionDescription = { "Qualidade ${it.label}" },
        ) { it.label }
        Hint(
            when (selected) {
                QualityMode.ECONOMY -> "Lê a tela com menos frequência. Gasta menos bateria; bom para textos que mudam pouco."
                QualityMode.BALANCED -> "O equilíbrio entre rapidez e bateria. Recomendado para o dia a dia."
                QualityMode.QUALITY -> "Lê a tela com mais frequência e, com o Claude ligado, traduz com contexto."
            },
        )
    }
}

/** Painel da sessão ativa: estado ao vivo, como usar e os controles principais. */
@Composable
private fun ScreenSessionPanel(state: AppUiState, actions: AppActions) {
    val colors = EasyTheme.colors
    val session = state.session
    val paused = session.status == SessionStatus.PAUSED
    val inPlace = state.preferences.overlayStyle == OverlayStyle.IN_PLACE
    InkPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(if (paused) colors.warn else BrandOnInk)
            Spacer(Modifier.width(Space.xs))
            Text(
                if (paused) "PAUSADA" else "TRADUZINDO A TELA",
                style = EasyTheme.type.label,
                color = colors.mutedOnInk,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(if (inPlace) "No lugar" else "Janela", Tone.Neutral)
        }
        Text(
            if (inPlace) "Abra o app e toque em Traduzir" else "Abra o app que quer entender",
            style = EasyTheme.type.screenTitle,
            color = colors.textOnInk,
        )
        Text(
            if (inPlace) {
                "O botão flutuante traduz a tela na hora. Toque em qualquer ponto para voltar ao original; se a tela mudar, a tradução some sozinha."
            } else {
                "A janela flutuante acompanha a tela. Arraste pelo título; toque nele para recolher ou expandir."
            },
            style = EasyTheme.type.body,
            color = colors.mutedOnInk,
        )
        if (session.message.isNotBlank()) Text(session.message, style = EasyTheme.type.meta, color = colors.mutedOnInk)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (paused) {
                EasyButton("Retomar", actions::resume, Modifier.weight(1f), icon = R.drawable.ic_ui_play)
            } else {
                EasyButton("Pausar", actions::pause, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = R.drawable.ic_ui_pause)
            }
            EasyButton("Parar", actions::stop, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = R.drawable.ic_ui_stop)
        }
    }
}

private val BrandOnInk = androidx.compose.ui.graphics.Color(0xFF4EC294)
