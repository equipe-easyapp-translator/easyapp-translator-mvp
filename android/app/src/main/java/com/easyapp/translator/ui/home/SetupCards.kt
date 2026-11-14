package com.easyapp.translator.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easyapp.translator.PermissionUiState
import com.easyapp.translator.R
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.CardHeader
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.EasyCard
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.IconBubble
import com.easyapp.translator.ui.components.StatusRow
import com.easyapp.translator.ui.components.Tone
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Space

/** Cartões de preparação compartilhados pelas páginas Tela e Conversa. */

@Composable
internal fun ScreenAudioCaptionsOption(selected: Boolean, onSelected: (Boolean) -> Unit) {
    val colors = EasyTheme.colors
    EasyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(R.drawable.ic_ui_captions, size = 32.dp)
            Column(Modifier.weight(1f).padding(horizontal = Space.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Legendas do áudio de outros apps", style = EasyTheme.type.rowTitle, color = colors.text)
                Hint("Opcional. Funciona só quando o app de origem permite a captura; o texto da tela é traduzido de qualquer forma.")
            }
            Switch(
                checked = selected,
                onCheckedChange = onSelected,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.brandSolid,
                    uncheckedTrackColor = colors.surfaceAlt,
                    uncheckedBorderColor = colors.lineStrong,
                    uncheckedThumbColor = colors.textMuted,
                ),
            )
        }
    }
}

/** Mostra só as permissões que o modo precisa, com os botões para resolver o que falta. */
@Composable
internal fun PermissionsCard(
    state: PermissionUiState,
    mode: AppMode,
    onRequestRuntime: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    val needsMic = mode == AppMode.CONVERSATION
    val runtimeMissing = (needsMic && !state.recordAudioGranted) || (state.notificationsRequired && !state.notificationsGranted)
    val overlayMissing = mode == AppMode.SCREEN && !state.overlayGranted
    EasyCard {
        CardHeader("Permissões", icon = R.drawable.ic_ui_shield)
        if (needsMic) PermissionLine("Microfone", state.recordAudioGranted)
        if (state.notificationsRequired) PermissionLine("Notificações (para parar a sessão)", state.notificationsGranted)
        if (mode == AppMode.SCREEN) {
            PermissionLine("Aparecer sobre outros apps", state.overlayGranted)
            Hint("A autorização para capturar a tela é pedida pelo Android ao iniciar.")
        }
        if (runtimeMissing || overlayMissing) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (runtimeMissing) {
                    EasyButton("Permitir", onRequestRuntime, Modifier.weight(1f), kind = ButtonKind.Tonal)
                }
                if (overlayMissing) {
                    EasyButton("Abrir configuração", onOpenOverlaySettings, Modifier.weight(1f), kind = ButtonKind.Tonal)
                }
            }
        }
    }
}

@Composable
private fun PermissionLine(label: String, granted: Boolean) {
    StatusRow(label, if (granted) "Concedida" else "Pendente", if (granted) Tone.Positive else Tone.Warning)
}
