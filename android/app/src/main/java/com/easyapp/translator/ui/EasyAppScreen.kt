package com.easyapp.translator.ui

import com.easyapp.translator.PermissionUiState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.data.UserPreferences
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.AudioCaptionSnapshot
import com.easyapp.translator.session.ConversationSnapshot
import com.easyapp.translator.session.ConversationSpeaker
import com.easyapp.translator.session.SessionSnapshot
import com.easyapp.translator.session.SessionStatus
import com.easyapp.translator.session.TranslationLanguages

private val Page = Color(0xFFF5F4EF)
private val Green = Color(0xFF26734E)
private val Ink = Color(0xFF202820)
private val Muted = Color(0xFF72776E)

@Composable
fun EasyAppScreen(
    preferences: UserPreferences,
    session: SessionSnapshot,
    conversation: ConversationSnapshot,
    audioCaptions: AudioCaptionSnapshot,
    permissionState: PermissionUiState,
    showPrivacyNotice: Boolean,
    onModeSelected: (AppMode) -> Unit,
    onLanguagesSelected: (String, String) -> Unit,
    onQualitySelected: (QualityMode) -> Unit,
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
) {
    var showPermissionExplanation by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Page) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header()
            Intro()
            PermissionStatusCard(
                state = permissionState,
                onRequestRuntime = onRequestRuntimePermissions,
                onOpenOverlaySettings = onOpenOverlaySettings,
            )
            SessionCard(
                session = session,
                onStart = { showPermissionExplanation = true },
                onPause = onPauseRequested,
                onResume = onResumeRequested,
                onStop = onStopRequested,
            )
            if (session.mode == AppMode.CONVERSATION) {
                ConversationCard(
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
            }
            if (session.mode == AppMode.SCREEN) {
                AudioCaptionsCard(audioCaptions, session.status == SessionStatus.ACTIVE, onClearAudioCaptions)
            }
            LanguagePairSelector(
                source = preferences.sourceLanguage,
                target = preferences.targetLanguage,
                modelStatus = if (session.status == SessionStatus.IDLE) {
                    "${languageLabel(preferences.sourceLanguage)} → ${languageLabel(preferences.targetLanguage)} será verificado ao traduzir."
                } else session.translationModelStatus,
                onLanguagesSelected = onLanguagesSelected,
            )
            SectionTitle("Escolha como quer traduzir")
            AppMode.entries.forEach { mode ->
                ModeCard(
                    mode = mode,
                    selected = preferences.preferredMode == mode,
                    enabled = session.status == SessionStatus.IDLE,
                    onSelect = { onModeSelected(mode) },
                )
            }
            PreferencesCard(preferences.qualityMode, onQualitySelected)
            PrivacyNote()
            Text(
                "A conversa funciona por turnos e usa reconhecimento e vozes offline disponíveis no aparelho. Nenhum histórico de conversa é salvo.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }

    if (showPrivacyNotice) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Permissões e privacidade") },
            text = {
                Text("Antes de solicitar permissões: RECORD_AUDIO habilita conversa por voz e tentativa opcional de captura de áudio interno; no modo Tela o Android pode encaminhar o microfone se o reconhecedor não aceitar áudio PCM externo. POST_NOTIFICATIONS mantém visível o serviço ativo. Sobreposição e compartilhamento de tela serão solicitados separadamente ao usar o modo Tela. O conteúdo processado não é salvo após a sessão.")
            },
            confirmButton = {
                TextButton(onClick = onPrivacyNoticeAccepted) { Text("Concordo e continuar") }
            },
            dismissButton = {
                TextButton(onClick = onPrivacyNoticeDeclined) { Text("Sair") }
            },
        )
    }

    if (showPermissionExplanation) {
        val mode = preferences.preferredMode
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text(if (mode == AppMode.SCREEN) "Prepare a captura da tela" else "Prepare a conversa por voz") },
            text = {
                Text(
                    if (mode == AppMode.SCREEN) {
                        "O Android pedirá autorização para compartilhar a tela e acesso RECORD_AUDIO. Essa permissão é exigida pelo sistema para tentar capturar reprodução de outros apps. A transcrição exige Android 13+ e serviço local compatível com áudio PCM externo; o Android pode recorrer ao microfone se o serviço não suportar essa entrada. Se não quiser essa possibilidade, negue RECORD_AUDIO: o OCR de tela ainda pode ser usado. Muitos apps também bloqueiam a reprodução."
                    } else {
                        "O EasyApp pedirá acesso ao microfone. A fala é processada pelo serviço offline do Android quando disponível; instale os pacotes de idioma offline. Transcrições e traduções ficam apenas na sessão e são apagadas ao parar."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionExplanation = false
                    onStartRequested(mode)
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) { Text("Agora não") }
            },
        )
    }
}

@Composable
private fun PermissionStatusCard(
    state: PermissionUiState,
    onRequestRuntime: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Permissões do aparelho")
            PermissionStatusLine("Microfone / captura de áudio", state.recordAudioGranted)
            PermissionStatusLine(
                "Notificações",
                state.notificationsGranted,
                if (state.notificationsRequired) null else "Não necessária nesta versão do Android",
            )
            PermissionStatusLine("Sobreposição sobre outros apps", state.overlayGranted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRequestRuntime, modifier = Modifier.weight(1f)) {
                    Text("Solicitar permissões")
                }
                Button(onClick = onOpenOverlaySettings, modifier = Modifier.weight(1f), enabled = !state.overlayGranted) {
                    Text(if (state.overlayGranted) "Sobreposição ativa" else "Ativar sobreposição")
                }
            }
            Text("A permissão de tela será pedida pelo Android ao iniciar esse modo.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun PermissionStatusLine(label: String, granted: Boolean, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink)
        Text(
            detail ?: if (granted) "Concedida" else "Pendente",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted || detail != null) Green else Color(0xFF9A6B28),
        )
    }
}

@Composable
private fun AudioCaptionsCard(state: AudioCaptionSnapshot, enabled: Boolean, onClear: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Legendas de áudio interno")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClear, enabled = enabled && state.history.isNotEmpty()) { Text("Limpar agora") }
            }
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = Muted)
            if (state.vadActive) Text("Atividade de áudio detectada", color = Green, style = MaterialTheme.typography.labelMedium)
            if (state.partialText.isNotBlank()) Text("… ${state.partialText}", color = Ink, style = MaterialTheme.typography.bodyMedium)
            state.history.takeLast(4).forEach { item ->
                Column(Modifier.fillMaxWidth().background(Page, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text("Áudio interno · turno ${item.turn}", color = Green, style = MaterialTheme.typography.labelSmall)
                    Text(item.source, color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(item.translated, color = Ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Buffer recente somente em RAM; é descartado ao parar a sessão.", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConversationCard(
    state: ConversationSnapshot,
    sourceLanguage: String,
    targetLanguage: String,
    enabled: Boolean,
    onSpeakerSelected: (ConversationSpeaker) -> Unit,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onRepeat: () -> Unit,
    onVoiceSelected: (String) -> Unit,
    onRateChanged: (Float) -> Unit,
    onVolumeChanged: (Float) -> Unit,
) {
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Conversa por turnos")
            Text("Qual idioma está sendo falado neste turno?", style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.speaker == ConversationSpeaker.USER_PORTUGUESE,
                    onClick = { onSpeakerSelected(ConversationSpeaker.USER_PORTUGUESE) },
                    enabled = enabled && !state.isListening,
                    label = { Text("Origem · ${languageLabel(sourceLanguage)}") },
                )
                FilterChip(
                    selected = state.speaker == ConversationSpeaker.INTERLOCUTOR,
                    onClick = { onSpeakerSelected(ConversationSpeaker.INTERLOCUTOR) },
                    enabled = enabled && !state.isListening,
                    label = { Text("Destino · ${languageLabel(targetLanguage)}") },
                )
            }
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = if (state.offlineSpeechAvailable) Green else Muted)
            if (state.partialTranscript.isNotBlank() || state.finalTranscript.isNotBlank()) {
                val speakerLabel = if (state.speaker == ConversationSpeaker.USER_PORTUGUESE) "Você" else "Interlocutor"
                Text("${if (state.partialTranscript.isNotBlank()) "$speakerLabel · ao vivo" else "$speakerLabel disse"}: ${state.partialTranscript.ifBlank { state.finalTranscript }}", style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
            if (state.translatedText.isNotBlank()) {
                val outputLanguage = if (state.speaker == ConversationSpeaker.USER_PORTUGUESE) targetLanguage else sourceLanguage
                Text("Tradução · ${languageLabel(outputLanguage)}", style = MaterialTheme.typography.labelMedium, color = Green, fontWeight = FontWeight.Bold)
                Text(state.translatedText, style = MaterialTheme.typography.titleMedium, color = Ink)
            }
            if (state.isListening) {
                OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Parar escuta") }
            } else {
                Button(onClick = onListen, enabled = enabled && state.offlineSpeechAvailable, modifier = Modifier.fillMaxWidth()) {
                    Text("Ouvir turno em ${languageLabel(if (state.speaker == ConversationSpeaker.USER_PORTUGUESE) sourceLanguage else targetLanguage)}")
                }
            }
            OutlinedButton(
                onClick = onRepeat,
                enabled = enabled && state.repeatText.isNotBlank() && state.offlineTtsAvailable,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isSpeaking) "Repetir tradução" else "Repetir áudio traduzido") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Voz offline", style = MaterialTheme.typography.labelMedium, color = Ink)
                    Text(
                        state.availableVoices.firstOrNull { it.id == state.selectedVoiceId }?.label ?: "Nenhuma voz compatível instalada",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                Box {
                    TextButton(onClick = { voiceMenuExpanded = true }, enabled = enabled && state.availableVoices.isNotEmpty()) { Text("Escolher") }
                    DropdownMenu(expanded = voiceMenuExpanded, onDismissRequest = { voiceMenuExpanded = false }) {
                        state.availableVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = { Text(voice.label) },
                                onClick = { voiceMenuExpanded = false; onVoiceSelected(voice.id) },
                            )
                        }
                    }
                }
            }
            Text("Velocidade · ${"%.2f".format(state.speechRate)}×", style = MaterialTheme.typography.labelMedium, color = Ink)
            Slider(value = state.speechRate, onValueChange = onRateChanged, valueRange = 0.65f..1.35f, steps = 6, enabled = enabled && state.offlineTtsAvailable)
            Text("Volume · ${(state.volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Ink)
            Slider(value = state.volume, onValueChange = onVolumeChanged, valueRange = 0.1f..1f, steps = 8, enabled = enabled && state.offlineTtsAvailable)
            Text("Reconhecimento e síntese: ${if (state.offlineSpeechAvailable) "offline" else "indisponíveis"} / ${if (state.offlineTtsAvailable) "voz offline disponível" else "sem voz offline compatível"}.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color(0xFFDDEBE0), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("EA", color = Green, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text("EasyApp", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
            Text("SCREENTRANSLATOR · MVP", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Spacer(Modifier.weight(1f))
        Text("ANDROID", style = MaterialTheme.typography.labelSmall, color = Green, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Intro() {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("SEU COMPANHEIRO DE IDIOMAS", style = MaterialTheme.typography.labelSmall, color = Green, fontWeight = FontWeight.Bold)
        Text("Entenda a tela.\nConverse sem barreiras.", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.Medium)
        Text("Um app, dois modos de tradução em português brasileiro.", style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun SessionCard(
    session: SessionSnapshot,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SESSÃO", style = MaterialTheme.typography.labelSmall, color = Green, fontWeight = FontWeight.Bold)
                    Text(session.mode?.title ?: "Pronto para começar", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                StatusBadge(session.status)
            }
            Text(session.message, style = MaterialTheme.typography.bodySmall, color = Muted)
            when (session.status) {
                SessionStatus.IDLE -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sessão") }
                SessionStatus.ACTIVE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) { Text("Pausar") }
                    Button(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Parar") }
                }
                SessionStatus.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Retomar") }
                    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Parar") }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SessionStatus) {
    val (label, color) = when (status) {
        SessionStatus.IDLE -> "PRONTO" to Color(0xFF777B72)
        SessionStatus.ACTIVE -> "ATIVA" to Green
        SessionStatus.PAUSED -> "PAUSADA" to Color(0xFF9A6B28)
    }
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.10f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ModeCard(mode: AppMode, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFEAF2EC) else Color.White,
            disabledContainerColor = if (selected) Color(0xFFEAF2EC) else Color.White,
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Green) else null,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(if (selected) Color.White else Page, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (mode == AppMode.SCREEN) "▤" else "♫", color = Green, style = MaterialTheme.typography.titleMedium)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(mode.title, style = MaterialTheme.typography.titleSmall, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Text(if (selected) "✓" else "○", color = Green, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LanguagePairSelector(
    source: String,
    target: String,
    modelStatus: String,
    onLanguagesSelected: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    val sourceOptions = TranslationLanguages.sourceChoices
    val targetOptions = TranslationLanguages.targetChoices.filter { it.code != "fr" || source == "fr" || target == "fr" }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("Idiomas de tradução")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LanguageDropdown(
                    title = "Origem",
                    selected = source,
                    options = sourceOptions,
                    expanded = expanded == "source",
                    modifier = Modifier.weight(1f),
                    onExpand = { expanded = if (expanded == "source") null else "source" },
                    onDismiss = { expanded = null },
                    onSelected = { code -> expanded = null; onLanguagesSelected(code, target) },
                )
                TextButton(
                    onClick = { onLanguagesSelected(target, source) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.size(44.dp),
                ) { Text("⇄", style = MaterialTheme.typography.titleLarge, color = Green) }
                LanguageDropdown(
                    title = "Destino",
                    selected = target,
                    options = targetOptions,
                    expanded = expanded == "target",
                    modifier = Modifier.weight(1f),
                    onExpand = { expanded = if (expanded == "target") null else "target" },
                    onDismiss = { expanded = null },
                    onSelected = { code -> expanded = null; onLanguagesSelected(source, code) },
                )
            }
            Text("Modelo offline · $modelStatus", style = MaterialTheme.typography.bodySmall, color = if (modelStatus.contains("pronto", true)) Green else Muted)
        }
    }
}

@Composable
private fun LanguageDropdown(
    title: String,
    selected: String,
    options: List<com.easyapp.translator.session.LanguageChoice>,
    expanded: Boolean,
    modifier: Modifier,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    Box(modifier) {
        OutlinedButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = Muted)
                Text(languageLabel(selected), style = MaterialTheme.typography.labelLarge, color = Ink)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelected(option.code) })
            }
        }
    }
}

private fun languageLabel(code: String) = TranslationLanguages.choice(code).label

@Composable
private fun PreferencesCard(selected: QualityMode, onSelected: (QualityMode) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("Modo de qualidade")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QualityMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelected(mode) },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Text("A seleção é salva no aparelho. Conteúdo traduzido não é persistido.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFE9EFE8), RoundedCornerShape(13.dp)).padding(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("◈", color = Green, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text("Você controla cada sessão", style = MaterialTheme.typography.labelLarge, color = Ink, fontWeight = FontWeight.SemiBold)
            Text("As permissões são solicitadas ao iniciar cada modo. Pare pelo app ou pela notificação.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
