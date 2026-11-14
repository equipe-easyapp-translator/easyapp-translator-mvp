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
import androidx.compose.ui.unit.dp
import com.easyapp.translator.R
import com.easyapp.translator.session.AudioCaptionSnapshot
import com.easyapp.translator.session.ConversationSnapshot
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.CardHeader
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.EasyCard
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.Overline
import com.easyapp.translator.ui.components.StatusBadge
import com.easyapp.translator.ui.components.Tone
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

/** Voz, velocidade e volume da tradução falada (folha "Voz" da conversa). */
@Composable
internal fun VoiceOptions(
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

    Overline("Voz")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.availableVoices.firstOrNull { it.id == state.selectedVoiceId }?.label ?: "Nenhuma voz offline instalada",
            modifier = Modifier.weight(1f),
            style = EasyTheme.type.body,
            color = colors.text,
        )
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
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { slider() }
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
