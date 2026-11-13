package com.easyapp.translator.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.easyapp.translator.R
import com.easyapp.translator.session.AudioCaptionSnapshot
import com.easyapp.translator.session.ConversationSnapshot
import com.easyapp.translator.session.ConversationSpeaker
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.CardHeader
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.EasyCard
import com.easyapp.translator.ui.components.EasyChip
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.Overline
import com.easyapp.translator.ui.components.StatusBadge
import com.easyapp.translator.ui.components.Tone
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

@Composable
internal fun ConversationCard(
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
    val colors = EasyTheme.colors
    val userSpeaking = state.speaker == ConversationSpeaker.USER_PORTUGUESE
    val inputLanguage = if (userSpeaking) targetLanguage else sourceLanguage
    val outputLanguage = if (userSpeaking) sourceLanguage else targetLanguage

    EasyCard {
        CardHeader("Conversa por turnos", icon = R.drawable.ic_ui_messages) {
            if (state.isListening) StatusBadge("Ouvindo", Tone.Positive)
            else if (state.isSpeaking) StatusBadge("Falando", Tone.Positive)
        }

        Overline("Quem fala agora?")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            EasyChip(
                label = "Você · ${languageLabel(targetLanguage)}",
                selected = userSpeaking,
                onClick = { onSpeakerSelected(ConversationSpeaker.USER_PORTUGUESE) },
                enabled = enabled && !state.isListening,
                modifier = Modifier.weight(1f),
            )
            EasyChip(
                label = "Outra pessoa · ${languageLabel(sourceLanguage)}",
                selected = !userSpeaking,
                onClick = { onSpeakerSelected(ConversationSpeaker.INTERLOCUTOR) },
                enabled = enabled && !state.isListening,
                modifier = Modifier.weight(1f),
            )
        }

        Hint(state.status, color = if (state.isListening) colors.brand else colors.textBody)

        val transcript = state.partialTranscript.ifBlank { state.finalTranscript }
        if (transcript.isNotBlank()) {
            val who = if (userSpeaking) "Você" else "Outra pessoa"
            Bubble(
                label = if (state.partialTranscript.isNotBlank()) "$who · ao vivo" else "$who disse",
                text = transcript,
                background = colors.surfaceAlt,
                labelColor = colors.textMuted,
                emphasized = false,
            )
        }
        if (state.translatedText.isNotBlank()) {
            Bubble(
                label = "Tradução · ${languageLabel(outputLanguage)}",
                text = state.translatedText,
                background = colors.brandBg,
                labelColor = colors.brand,
                emphasized = true,
            )
        }

        if (state.isListening) {
            EasyButton("Parar de ouvir", onCancel, Modifier.fillMaxWidth(), kind = ButtonKind.Outline, enabled = enabled, icon = R.drawable.ic_ui_stop)
        } else {
            EasyButton("Ouvir em ${languageLabel(inputLanguage)}", onListen, Modifier.fillMaxWidth(), enabled = enabled, icon = R.drawable.ic_ui_mic)
        }
        EasyButton(
            "Repetir tradução em voz alta",
            onRepeat,
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Tonal,
            enabled = enabled && state.repeatText.isNotBlank(),
            icon = R.drawable.ic_ui_repeat,
        )

        HorizontalDivider(Modifier.padding(vertical = Space.xs), color = colors.divider)
        VoiceOptions(state, enabled, onVoiceSelected, onRateChanged, onVolumeChanged)
    }
}

@Composable
private fun Bubble(label: String, text: String, background: Color, labelColor: Color, emphasized: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(Radius.card).background(background).padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), style = EasyTheme.type.label, color = labelColor)
        Text(
            text,
            style = if (emphasized) EasyTheme.type.translation else EasyTheme.type.body,
            color = EasyTheme.colors.text,
        )
    }
}

@Composable
private fun VoiceOptions(
    state: ConversationSnapshot,
    enabled: Boolean,
    onVoiceSelected: (String) -> Unit,
    onRateChanged: (Float) -> Unit,
    onVolumeChanged: (Float) -> Unit,
) {
    val colors = EasyTheme.colors
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.brandSolid,
        activeTrackColor = colors.brandSolid,
        inactiveTrackColor = colors.surfaceAlt,
    )

    Overline("Voz da tradução")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                state.availableVoices.firstOrNull { it.id == state.selectedVoiceId }?.label ?: "Nenhuma voz offline instalada",
                style = EasyTheme.type.body,
                color = colors.text,
            )
        }
        Box {
            EasyButton(
                "Trocar",
                { voiceMenuExpanded = true },
                kind = ButtonKind.Ghost,
                enabled = enabled && state.availableVoices.isNotEmpty(),
            )
            DropdownMenu(expanded = voiceMenuExpanded, onDismissRequest = { voiceMenuExpanded = false }) {
                state.availableVoices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice.label, style = EasyTheme.type.body, color = colors.text) },
                        onClick = { voiceMenuExpanded = false; onVoiceSelected(voice.id) },
                    )
                }
            }
        }
    }
    SliderRow("Velocidade", "${"%.2f".format(state.speechRate)}×") {
        Slider(
            value = state.speechRate,
            onValueChange = onRateChanged,
            valueRange = 0.65f..1.35f,
            steps = 6,
            enabled = enabled && state.offlineTtsAvailable,
            colors = sliderColors,
        )
    }
    SliderRow("Volume", "${(state.volume * 100).toInt()}%") {
        Slider(
            value = state.volume,
            onValueChange = onVolumeChanged,
            valueRange = 0.1f..1f,
            steps = 8,
            enabled = enabled && state.offlineTtsAvailable,
            colors = sliderColors,
        )
    }
    val recognitionMode = when {
        state.offlineSpeechAvailable -> "offline"
        state.speechRecognizerAvailable -> "serviço padrão do Android"
        else -> "indisponível"
    }
    Hint("Reconhecimento: $recognitionMode · Voz: ${if (state.offlineTtsAvailable) "offline disponível" else "sem voz offline compatível"}.")
}

@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), style = EasyTheme.type.meta, color = EasyTheme.colors.textBody)
            Text(value, style = EasyTheme.type.meta, color = EasyTheme.colors.text)
        }
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) { slider() }
    }
}

@Composable
internal fun AudioCaptionsCard(state: AudioCaptionSnapshot, enabled: Boolean, onClear: () -> Unit) {
    val colors = EasyTheme.colors
    EasyCard {
        CardHeader("Legendas do áudio", icon = R.drawable.ic_ui_captions) {
            if (state.vadActive) StatusBadge("Som detectado", Tone.Positive)
        }
        Hint(state.status, color = colors.textBody)
        if (state.partialText.isNotBlank()) {
            Text("… ${state.partialText}", style = EasyTheme.type.body, color = colors.text)
        }
        state.history.takeLast(4).forEach { item ->
            Column(
                Modifier.fillMaxWidth().clip(Radius.card).background(colors.surfaceAlt).padding(Space.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("TRECHO ${item.turn}", style = EasyTheme.type.label, color = colors.textMuted)
                Text(item.source, style = EasyTheme.type.meta, color = colors.textBody)
                Text(item.translated, style = EasyTheme.type.body, color = colors.text)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hint("Os trechos ficam só na memória e somem ao parar.", Modifier.weight(1f))
            EasyButton("Limpar", onClear, kind = ButtonKind.Ghost, enabled = enabled && state.history.isNotEmpty())
        }
    }
}
