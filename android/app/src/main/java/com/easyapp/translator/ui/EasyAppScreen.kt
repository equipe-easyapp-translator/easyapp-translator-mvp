package com.easyapp.translator.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.easyapp.translator.session.SessionSnapshot
import com.easyapp.translator.session.SessionStatus

private val Page = Color(0xFFF5F4EF)
private val Green = Color(0xFF26734E)
private val Ink = Color(0xFF202820)
private val Muted = Color(0xFF72776E)

@Composable
fun EasyAppScreen(
    preferences: UserPreferences,
    session: SessionSnapshot,
    onModeSelected: (AppMode) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onQualitySelected: (QualityMode) -> Unit,
    onStartRequested: (AppMode) -> Unit,
    onPauseRequested: () -> Unit,
    onResumeRequested: () -> Unit,
    onStopRequested: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
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
            SessionCard(
                session = session,
                onStart = { showPermissionExplanation = true },
                onPause = onPauseRequested,
                onResume = onResumeRequested,
                onStop = onStopRequested,
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
            if (preferences.preferredMode == AppMode.CONVERSATION) {
                LanguageSelector(preferences.sourceLanguage, onLanguageSelected)
            }
            PreferencesCard(preferences.qualityMode, onQualitySelected)
            PrivacyNote()
            Text(
                "Esta fundação implementa interface, permissões e estado da sessão. Captura de tela, OCR, transcrição e tradução serão ligados nas próximas fases.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }

    if (showPermissionExplanation) {
        val mode = preferences.preferredMode
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text(if (mode == AppMode.SCREEN) "Prepare a captura da tela" else "Prepare a conversa por voz") },
            text = {
                Text(
                    if (mode == AppMode.SCREEN) {
                        "O Android pedirá autorização para compartilhar a tela nesta sessão. O EasyApp também precisa da permissão de sobreposição para mostrar a tradução. Você pode parar a sessão pelo app ou pela notificação."
                    } else {
                        "O EasyApp pedirá acesso ao microfone para a sessão de conversa. O serviço mostra uma notificação persistente e pode ser parado a qualquer momento."
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
private fun LanguageSelector(selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Idioma da conversa")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == "en", onClick = { onSelected("en") }, label = { Text("🇺🇸 Inglês") })
            FilterChip(selected = selected == "es", onClick = { onSelected("es") }, label = { Text("🇪🇸 Espanhol") })
        }
    }
}

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
