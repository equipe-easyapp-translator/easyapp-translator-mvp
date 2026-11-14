package com.easyapp.translator.ui.conversation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.easyapp.translator.R
import com.easyapp.translator.data.ConversationLayout
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.ConversationSnapshot
import com.easyapp.translator.session.ConversationSpeaker
import com.easyapp.translator.session.ConversationTurn
import com.easyapp.translator.session.SessionStatus
import com.easyapp.translator.session.TranslationLanguages
import com.easyapp.translator.ui.AppActions
import com.easyapp.translator.ui.AppUiState
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.CenteredColumn
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.IllustratedChoice
import com.easyapp.translator.ui.components.InfoNote
import com.easyapp.translator.ui.components.LanguageBar
import com.easyapp.translator.ui.components.PageHeader
import com.easyapp.translator.ui.components.PageScaffold
import com.easyapp.translator.ui.components.SectionLabel
import com.easyapp.translator.ui.components.faceToFaceIllustration
import com.easyapp.translator.ui.components.sideBySideIllustration
import com.easyapp.translator.ui.home.PermissionsCard
import com.easyapp.translator.ui.home.StartExplanationDialog
import com.easyapp.translator.ui.home.VoiceOptions
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

/** Página do modo Conversa (T061). */
@Composable
fun ConversationPage(
    state: AppUiState,
    actions: AppActions,
    onPickYourLanguage: () -> Unit,
    onPickTheirLanguage: () -> Unit,
) {
    val session = state.session
    val active = session.mode == AppMode.CONVERSATION && session.status != SessionStatus.IDLE
    if (active) {
        ConversationSession(state, actions)
    } else {
        ConversationSetup(state, actions, onPickYourLanguage, onPickTheirLanguage)
    }
}

@Composable
private fun ConversationSetup(
    state: AppUiState,
    actions: AppActions,
    onPickYourLanguage: () -> Unit,
    onPickTheirLanguage: () -> Unit,
) {
    val prefs = state.preferences
    val busyElsewhere = state.session.mode == AppMode.SCREEN && state.session.status != SessionStatus.IDLE
    var showStart by remember { mutableStateOf(false) }
    val startAction: @Composable ColumnScope.() -> Unit = {
        if (busyElsewhere) Hint("Pare a tradução de tela antes de começar uma conversa.", color = EasyTheme.colors.warn)
        EasyButton(
            "Começar conversa",
            { showStart = true },
            Modifier.fillMaxWidth(),
            enabled = !busyElsewhere,
            icon = R.drawable.ic_ui_mic,
        )
    }
    PageScaffold(bottomAction = startAction) {
        PageHeader(
            title = "Conversar por voz",
            subtitle = "Cada um fala no seu idioma e ouve a tradução no seu. Funciona sem internet quando o aparelho tem os pacotes de voz.",
            overline = "Modo Conversa",
        )
        // O idioma de destino é o seu; o de origem é o da outra pessoa.
        LanguageBar(
            leftTitle = "Você fala",
            leftCode = prefs.targetLanguage,
            rightTitle = "A outra pessoa",
            rightCode = prefs.sourceLanguage,
            onPickLeft = onPickYourLanguage,
            onPickRight = onPickTheirLanguage,
            onSwap = { actions.setLanguages(prefs.targetLanguage, prefs.sourceLanguage) },
        )

        SectionLabel("Como vocês vão usar o celular")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IllustratedChoice(
                title = ConversationLayout.FACE_TO_FACE.label,
                description = "Celular na mesa entre vocês; cada metade da tela fica virada para uma pessoa.",
                selected = prefs.conversationLayout == ConversationLayout.FACE_TO_FACE,
                onClick = { actions.setConversationLayout(ConversationLayout.FACE_TO_FACE) },
                modifier = Modifier.weight(1f),
            ) { faceToFaceIllustration(it) }
            IllustratedChoice(
                title = ConversationLayout.SIDE_BY_SIDE.label,
                description = "Uma conversa em balões, como num app de mensagens, com um botão para cada um.",
                selected = prefs.conversationLayout == ConversationLayout.SIDE_BY_SIDE,
                onClick = { actions.setConversationLayout(ConversationLayout.SIDE_BY_SIDE) },
                modifier = Modifier.weight(1f),
            ) { sideBySideIllustration(it) }
        }

        if (!state.permissions.recordAudioGranted || (state.permissions.notificationsRequired && !state.permissions.notificationsGranted)) {
            SectionLabel("Antes de começar")
            PermissionsCard(state.permissions, AppMode.CONVERSATION, actions::requestPermissions, actions::openOverlaySettings)
        }

        InfoNote(
            title = "Para funcionar sem internet",
            body = "Baixe os idiomas em Configurações do Android → Sistema → Idiomas → Reconhecimento de fala e Texto para voz. Os nomes dos menus mudam conforme o fabricante.",
            icon = R.drawable.ic_ui_info,
        )
    }

    if (showStart) {
        StartExplanationDialog(
            mode = AppMode.CONVERSATION,
            audioCaptionsSelected = false,
            onConfirm = {
                showStart = false
                actions.start(AppMode.CONVERSATION)
            },
            onDismiss = { showStart = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationSession(state: AppUiState, actions: AppActions) {
    val colors = EasyTheme.colors
    val conversation = state.conversation
    val prefs = state.preferences
    val enabled = state.session.status == SessionStatus.ACTIVE
    var showVoice by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(colors.paper)) {
        if (prefs.conversationLayout == ConversationLayout.FACE_TO_FACE) {
            // A metade de cima gira 180°: a outra pessoa lê do lado dela da mesa.
            PersonPanel(
                speaker = ConversationSpeaker.INTERLOCUTOR,
                languageCode = prefs.sourceLanguage,
                conversation = conversation,
                enabled = enabled,
                actions = actions,
                modifier = Modifier.weight(1f).graphicsLayer { rotationZ = 180f },
            )
            ControlStrip(state, actions, onVoice = { showVoice = true })
            PersonPanel(
                speaker = ConversationSpeaker.USER_PORTUGUESE,
                languageCode = prefs.targetLanguage,
                conversation = conversation,
                enabled = enabled,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        } else {
            ControlStrip(state, actions, onVoice = { showVoice = true })
            TurnList(conversation, Modifier.weight(1f))
            HorizontalDivider(color = colors.line)
            Row(
                Modifier.fillMaxWidth().background(colors.surface).padding(Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                SpeakButton(ConversationSpeaker.USER_PORTUGUESE, "Você", prefs.targetLanguage, conversation, enabled, actions, Modifier.weight(1f))
                SpeakButton(ConversationSpeaker.INTERLOCUTOR, "Outra pessoa", prefs.sourceLanguage, conversation, enabled, actions, Modifier.weight(1f))
            }
        }
    }

    if (showVoice) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showVoice = false },
            sheetState = sheetState,
            containerColor = colors.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text("Voz da tradução", style = EasyTheme.type.screenTitle, color = colors.text)
                VoiceOptions(conversation, enabled, actions::selectVoice, actions::setSpeechRate, actions::setVolume)
                EasyButton(
                    "Repetir a última tradução",
                    actions::repeatTranslation,
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Tonal,
                    enabled = enabled && conversation.repeatText.isNotBlank(),
                    icon = R.drawable.ic_ui_repeat,
                )
            }
        }
    }
}

/** Faixa do meio (ou do topo): estado da conversa e controles da sessão. */
@Composable
private fun ControlStrip(state: AppUiState, actions: AppActions, onVoice: () -> Unit) {
    val colors = EasyTheme.colors
    val paused = state.session.status == SessionStatus.PAUSED
    Column(
        Modifier.fillMaxWidth().background(colors.ink).padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            if (paused) "Conversa pausada." else state.conversation.status,
            style = EasyTheme.type.meta,
            color = colors.mutedOnInk,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (paused) {
                EasyButton("Retomar", actions::resume, Modifier.weight(1f), icon = R.drawable.ic_ui_play)
            } else {
                EasyButton("Pausar", actions::pause, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = R.drawable.ic_ui_pause)
            }
            EasyButton("Voz", onVoice, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = R.drawable.ic_ui_sliders)
            EasyButton("Encerrar", actions::stop, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = R.drawable.ic_ui_stop)
        }
    }
}

/**
 * Metade da tela de uma pessoa: o idioma dela, a última tradução feita para ela
 * (o que a outra pessoa disse) e o botão grande de falar.
 */
@Composable
private fun PersonPanel(
    speaker: ConversationSpeaker,
    languageCode: String,
    conversation: ConversationSnapshot,
    enabled: Boolean,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val colors = EasyTheme.colors
    val language = TranslationLanguages.choice(languageCode)
    val isYou = speaker == ConversationSpeaker.USER_PORTUGUESE
    val listeningHere = conversation.isListening && conversation.speaker == speaker
    val forThisReader = conversation.turns.lastOrNull { it.speaker != speaker }
    Column(
        modifier
            .fillMaxWidth()
            .background(if (isYou) colors.brandBg else colors.surface)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            language.nativeName,
            style = EasyTheme.type.label.copy(fontSize = EasyTheme.type.meta.fontSize),
            color = if (isYou) colors.brand else colors.textMuted,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            when {
                listeningHere -> Text(
                    conversation.partialTranscript.ifBlank { "…" },
                    style = EasyTheme.type.translation,
                    color = colors.textBody,
                )
                forThisReader != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        forThisReader.translated,
                        style = EasyTheme.type.translation.copy(fontSize = EasyTheme.type.translation.fontSize * 1.25f),
                        color = colors.text,
                    )
                    Text(forThisReader.original, style = EasyTheme.type.meta, color = colors.textMuted)
                }
                else -> Text(
                    if (isYou) "Toque no microfone e fale em ${language.label.lowercase()}." else greetingFor(languageCode),
                    style = EasyTheme.type.body,
                    color = colors.textMuted,
                )
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(
                listening = listeningHere,
                enabled = enabled && (!conversation.isListening || listeningHere),
                label = if (listeningHere) stopLabel(languageCode) else speakLabel(languageCode),
                onClick = { if (listeningHere) actions.cancelListening() else actions.speak(speaker) },
            )
        }
    }
}

/** Botão redondo grande; pulsa enquanto ouve. */
@Composable
private fun MicButton(listening: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = EasyTheme.colors
    val pulse = rememberInfiniteTransition(label = "mic")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "mic-scale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box(
            Modifier
                .size(84.dp)
                .scale(if (listening) scale else 1f)
                .clip(Radius.pill)
                .background(if (enabled) (if (listening) colors.warn else colors.brandSolid) else colors.surfaceAlt)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (listening) R.drawable.ic_ui_stop else R.drawable.ic_ui_mic),
                contentDescription = null,
                tint = if (enabled) androidx.compose.ui.graphics.Color.White else colors.textMuted,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(label, style = EasyTheme.type.button, color = colors.text)
    }
}

/** Botão de falar do layout lado a lado. */
@Composable
private fun SpeakButton(
    speaker: ConversationSpeaker,
    who: String,
    languageCode: String,
    conversation: ConversationSnapshot,
    enabled: Boolean,
    actions: AppActions,
    modifier: Modifier,
) {
    val listeningHere = conversation.isListening && conversation.speaker == speaker
    val language = TranslationLanguages.choice(languageCode)
    EasyButton(
        text = if (listeningHere) "Parar" else "$who · ${language.nativeName}",
        onClick = { if (listeningHere) actions.cancelListening() else actions.speak(speaker) },
        modifier = modifier,
        kind = if (speaker == ConversationSpeaker.USER_PORTUGUESE) ButtonKind.Primary else ButtonKind.Tonal,
        enabled = enabled && (!conversation.isListening || listeningHere),
        icon = if (listeningHere) R.drawable.ic_ui_stop else R.drawable.ic_ui_mic,
    )
}

/** Histórico da sessão em balões (FR-031), rolando até o último turno. */
@Composable
private fun TurnList(conversation: ConversationSnapshot, modifier: Modifier) {
    val colors = EasyTheme.colors
    val listState = rememberLazyListState()
    LaunchedEffect(conversation.turns.size) {
        if (conversation.turns.isNotEmpty()) listState.animateScrollToItem(conversation.turns.lastIndex)
    }
    if (conversation.turns.isEmpty() && !conversation.isListening) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CenteredColumn {
                Icon(painterResource(R.drawable.ic_ui_messages), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                Text("A conversa aparece aqui", style = EasyTheme.type.rowTitle, color = colors.text)
                Hint("Toque no botão de quem vai falar e fale perto do microfone.")
            }
        }
        return
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(conversation.turns, key = { it.id }) { turn -> TurnBubble(turn) }
        if (conversation.isListening) {
            item(key = "live") {
                LiveBubble(conversation.speaker, conversation.partialTranscript)
            }
        }
    }
}

@Composable
private fun TurnBubble(turn: ConversationTurn) {
    val colors = EasyTheme.colors
    val mine = turn.speaker == ConversationSpeaker.USER_PORTUGUESE
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(Radius.card)
                .background(if (mine) colors.brandBg else colors.surface)
                .border(1.dp, if (mine) colors.brandBg else colors.line, Radius.card)
                .padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                (if (mine) "Você" else "Outra pessoa") + " · " + TranslationLanguages.choice(turn.outputLanguage).nativeName,
                style = EasyTheme.type.label,
                color = if (mine) colors.brand else colors.textMuted,
            )
            Text(turn.translated, style = EasyTheme.type.translation, color = colors.text)
            Text(turn.original, style = EasyTheme.type.meta, color = colors.textMuted)
        }
    }
}

@Composable
private fun LiveBubble(speaker: ConversationSpeaker, partial: String) {
    val colors = EasyTheme.colors
    val mine = speaker == ConversationSpeaker.USER_PORTUGUESE
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            partial.ifBlank { "Ouvindo…" },
            modifier = Modifier
                .widthIn(max = 320.dp)
                .heightIn(min = 44.dp)
                .clip(Radius.card)
                .border(1.dp, colors.lineStrong, Radius.card)
                .padding(Space.md),
            style = EasyTheme.type.body,
            color = colors.textBody,
        )
    }
}

/** Convite curto no idioma da outra pessoa, para ela saber que pode falar. */
private fun greetingFor(code: String) = when (code) {
    "en" -> "Tap the microphone and speak in English."
    "es" -> "Toca el micrófono y habla en español."
    "it" -> "Tocca il microfono e parla in italiano."
    "fr" -> "Touchez le micro et parlez en français."
    else -> "Toque no microfone e fale."
}

private fun speakLabel(code: String) = when (code) {
    "en" -> "Speak"
    "es" -> "Hablar"
    "it" -> "Parla"
    "fr" -> "Parler"
    else -> "Falar"
}

private fun stopLabel(code: String) = when (code) {
    "en" -> "Stop"
    "es" -> "Parar"
    "it" -> "Ferma"
    "fr" -> "Arrêter"
    else -> "Parar"
}
