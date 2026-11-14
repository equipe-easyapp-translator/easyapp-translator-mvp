package com.easyapp.translator.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.easyapp.translator.PermissionUiState
import com.easyapp.translator.R
import com.easyapp.translator.data.OverlayStyle
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.data.UserPreferences
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.AudioCaptionSnapshot
import com.easyapp.translator.session.ConversationSnapshot
import com.easyapp.translator.session.ConversationSpeaker
import com.easyapp.translator.session.SessionSnapshot
import com.easyapp.translator.session.SessionStatus
import com.easyapp.translator.translation.ClaudeUsage
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.InfoNote
import com.easyapp.translator.ui.components.Overline
import com.easyapp.translator.ui.components.StatusBadge
import com.easyapp.translator.ui.components.Tone
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

/**
 * Tela única do app. Sem sessão, mostra o passo a passo de configuração
 * (modo → idiomas → qualidade → permissões → iniciar). Com sessão ativa ou
 * pausada, os controles da sessão e do modo em uso sobem para o topo.
 */
@Composable
fun HomeScreen(
    preferences: UserPreferences,
    session: SessionSnapshot,
    conversation: ConversationSnapshot,
    audioCaptions: AudioCaptionSnapshot,
    permissionState: PermissionUiState,
    screenAudioCaptionsSelected: Boolean,
    showPrivacyNotice: Boolean,
    onModeSelected: (AppMode) -> Unit,
    onLanguagesSelected: (String, String) -> Unit,
    onQualitySelected: (QualityMode) -> Unit,
    onOverlayStyleSelected: (OverlayStyle) -> Unit,
    onStartRequested: (AppMode) -> Unit,
    onPauseRequested: () -> Unit,
    onResumeRequested: () -> Unit,
    onStopRequested: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onPrivacyNoticeAccepted: () -> Unit,
    onPrivacyNoticeDeclined: () -> Unit,
    onConversationSpeakerSelected: (ConversationSpeaker) -> Unit,
    onConversationListen: () -> Unit,
    onConversationCancel: () -> Unit,
    onConversationRepeat: () -> Unit,
    onConversationVoiceSelected: (String) -> Unit,
    onConversationRateChanged: (Float) -> Unit,
    onConversationVolumeChanged: (Float) -> Unit,
    onClearAudioCaptions: () -> Unit,
    onScreenAudioCaptionsSelected: (Boolean) -> Unit,
    claude: ClaudeUiState,
    claudeUsage: ClaudeUsage,
    onClaudeSaveKey: (String) -> Unit,
    onClaudeConsentAccepted: () -> Unit,
    onClaudeEnabledChanged: (Boolean) -> Unit,
    onClaudeRemoveKey: () -> Unit,
    onClaudeTest: () -> Unit,
) {
    var showStartExplanation by remember { mutableStateOf(false) }
    val colors = EasyTheme.colors
    val idle = session.status == SessionStatus.IDLE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        TopBar(session.status)

        if (idle) {
            Intro()
            StepTitle(1, "Como você quer traduzir")
            AppMode.entries.forEach { mode ->
                ModeCard(mode = mode, selected = preferences.preferredMode == mode, onSelect = { onModeSelected(mode) })
            }
            if (preferences.preferredMode == AppMode.SCREEN) {
                Overline("Como mostrar a tradução")
                OverlayStyleCard(preferences.overlayStyle, onOverlayStyleSelected)
                if (preferences.overlayStyle == OverlayStyle.FLOATING) {
                    ScreenAudioCaptionsOption(selected = screenAudioCaptionsSelected, onSelected = onScreenAudioCaptionsSelected)
                }
            }

            StepTitle(2, "Idiomas")
            LanguageCard(
                source = preferences.sourceLanguage,
                target = preferences.targetLanguage,
                modelStatus = null,
                onLanguagesSelected = onLanguagesSelected,
            )

            if (preferences.preferredMode == AppMode.SCREEN) {
                StepTitle(3, "Qualidade da leitura")
                QualityCard(preferences.qualityMode, onQualitySelected)
                ClaudeSetupCard(
                    state = claude,
                    qualitySelected = preferences.qualityMode == QualityMode.QUALITY,
                    onSaveKey = onClaudeSaveKey,
                    onConsentAccepted = onClaudeConsentAccepted,
                    onEnabledChanged = onClaudeEnabledChanged,
                    onRemoveKey = onClaudeRemoveKey,
                    onTest = onClaudeTest,
                )
            }

            PermissionsCard(
                state = permissionState,
                mode = preferences.preferredMode,
                onRequestRuntime = onRequestRuntimePermissions,
                onOpenOverlaySettings = onOpenOverlaySettings,
            )

            EasyButton(
                text = if (preferences.preferredMode == AppMode.SCREEN) "Iniciar tradução de tela" else "Iniciar conversa",
                onClick = { showStartExplanation = true },
                modifier = Modifier.fillMaxWidth(),
                icon = R.drawable.ic_ui_play,
            )
        } else {
            SessionCard(
                session = session,
                onPause = onPauseRequested,
                onResume = onResumeRequested,
                onStop = onStopRequested,
            )
            when (session.mode) {
                AppMode.CONVERSATION -> ConversationCard(
                    state = conversation,
                    sourceLanguage = preferences.sourceLanguage,
                    targetLanguage = preferences.targetLanguage,
                    enabled = session.status == SessionStatus.ACTIVE,
                    onSpeakerSelected = onConversationSpeakerSelected,
                    onListen = onConversationListen,
                    onCancel = onConversationCancel,
                    onRepeat = onConversationRepeat,
                    onVoiceSelected = onConversationVoiceSelected,
                    onRateChanged = onConversationRateChanged,
                    onVolumeChanged = onConversationVolumeChanged,
                )
                AppMode.SCREEN -> {
                    if (preferences.overlayStyle == OverlayStyle.IN_PLACE) {
                        InfoNote(
                            title = "Toque em Traduzir no botão flutuante",
                            body = "Abra o app que quer entender (o WhatsApp, por exemplo) e toque em Traduzir: cada frase é coberta pela tradução. Toque na tela para voltar ao original; se a tela mudar, a tradução some sozinha.",
                            icon = R.drawable.ic_ui_smartphone,
                            tone = Tone.Positive,
                        )
                    } else {
                        InfoNote(
                            title = "A tradução aparece na janela flutuante",
                            body = "Abra o app que quer entender. Arraste a janela pelo título e toque nele para recolher ou expandir.",
                            icon = R.drawable.ic_ui_smartphone,
                            tone = Tone.Positive,
                        )
                    }
                    if (claude.usable) ClaudeUsageCard(claudeUsage)
                    if (screenAudioCaptionsSelected && preferences.overlayStyle == OverlayStyle.FLOATING) {
                        AudioCaptionsCard(audioCaptions, session.status == SessionStatus.ACTIVE, onClearAudioCaptions)
                    }
                }
                null -> Unit
            }

            Overline("Ajustes da sessão")
            LanguageCard(
                source = preferences.sourceLanguage,
                target = preferences.targetLanguage,
                modelStatus = session.translationModelStatus,
                onLanguagesSelected = onLanguagesSelected,
            )
            if (session.mode == AppMode.SCREEN) {
                QualityCard(preferences.qualityMode, onQualitySelected)
            }
        }

        InfoNote(
            title = "Você controla cada sessão",
            body = "Tudo é processado no aparelho e nada fica salvo: capturas, áudio e traduções são apagados ao parar. Pare pelo app ou pela notificação.",
            icon = R.drawable.ic_ui_shield,
        )
        Spacer(Modifier.height(Space.sm))
    }

    if (showPrivacyNotice) {
        PrivacyNoticeDialog(onAccept = onPrivacyNoticeAccepted, onDecline = onPrivacyNoticeDeclined)
    }

    if (showStartExplanation) {
        val mode = preferences.preferredMode
        StartExplanationDialog(
            mode = mode,
            audioCaptionsSelected = screenAudioCaptionsSelected && preferences.overlayStyle == OverlayStyle.FLOATING,
            onConfirm = {
                showStartExplanation = false
                onStartRequested(mode)
            },
            onDismiss = { showStartExplanation = false },
        )
    }
}

@Composable
private fun TopBar(status: SessionStatus) {
    val colors = EasyTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppMark(size = 40)
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text("EasyApp", style = EasyTheme.type.rowTitle.copy(fontSize = EasyTheme.type.screenTitle.fontSize * 0.86f), color = colors.text)
            Text("Tradutor de tela e conversa", style = EasyTheme.type.rowSub, color = colors.textMuted)
        }
        when (status) {
            SessionStatus.IDLE -> StatusBadge("Pronto", Tone.Neutral)
            SessionStatus.ACTIVE -> StatusBadge("Ativa", Tone.Positive)
            SessionStatus.PAUSED -> StatusBadge("Pausada", Tone.Warning)
        }
    }
}

/** O ícone do app em miniatura: a camada da frente do ícone adaptativo sobre o verde da marca. */
@Composable
fun AppMark(size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size.dp).clip(Radius.icon).background(EasyTheme.colors.brandSolid),
        contentAlignment = Alignment.Center,
    ) {
        // O desenho ocupa ~60% do canvas de 108 dp; ampliar e recortar deixa o símbolo legível.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize((size * 1.5f).dp),
        )
    }
}

@Composable
private fun Intro() {
    val colors = EasyTheme.colors
    Column(Modifier.padding(top = Space.sm, bottom = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Overline("Seu companheiro de idiomas", color = colors.brand)
        Text("Entenda a tela.\nConverse sem barreiras.", style = EasyTheme.type.display, color = colors.text)
        Text(
            "Um app, dois modos: traduza o texto de qualquer app ou converse por voz com quem fala outro idioma.",
            style = EasyTheme.type.body,
            color = colors.textBody,
        )
    }
}

@Composable
private fun StepTitle(number: Int, title: String) {
    Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(Radius.pill).background(EasyTheme.colors.ink),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = EasyTheme.type.label, color = EasyTheme.colors.textOnInk)
        }
        Spacer(Modifier.width(Space.sm))
        Overline(title, color = EasyTheme.colors.text)
    }
}

@Composable
private fun SessionCard(
    session: SessionSnapshot,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = EasyTheme.colors
    com.easyapp.translator.ui.components.EasyCard {
        Overline("Sessão")
        Text(session.mode?.title ?: "Sessão", style = EasyTheme.type.screenTitle, color = colors.text)
        Text(session.message, style = EasyTheme.type.meta, color = colors.textBody)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (session.status == SessionStatus.PAUSED) {
                EasyButton("Retomar", onResume, Modifier.weight(1f), icon = R.drawable.ic_ui_play)
            } else {
                EasyButton("Pausar", onPause, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = R.drawable.ic_ui_pause)
            }
            EasyButton("Parar", onStop, Modifier.weight(1f), kind = ButtonKind.Outline, icon = R.drawable.ic_ui_stop)
        }
    }
}
