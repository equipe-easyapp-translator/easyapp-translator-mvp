package com.easyapp.translator.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.easyapp.translator.PermissionUiState
import com.easyapp.translator.R
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.LanguageChoice
import com.easyapp.translator.session.TranslationLanguages
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.CardHeader
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.EasyCard
import com.easyapp.translator.ui.components.EasyChip
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.IconBubble
import com.easyapp.translator.ui.components.StatusRow
import com.easyapp.translator.ui.components.Tone
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

internal fun languageLabel(code: String) = TranslationLanguages.choice(code).label

@Composable
internal fun ModeCard(mode: AppMode, selected: Boolean, onSelect: () -> Unit) {
    val colors = EasyTheme.colors
    EasyCard(selected = selected, onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(
                icon = if (mode == AppMode.SCREEN) R.drawable.ic_ui_smartphone else R.drawable.ic_ui_messages,
                background = if (selected) colors.surface else colors.surfaceAlt,
            )
            Column(Modifier.weight(1f).padding(horizontal = Space.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(mode.title, style = EasyTheme.type.rowTitle, color = colors.text)
                Text(mode.description, style = EasyTheme.type.meta, color = colors.textBody)
            }
            Box(
                Modifier
                    .size(22.dp)
                    .clip(Radius.pill)
                    .border(2.dp, if (selected) colors.brand else colors.lineStrong, Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(painterResource(R.drawable.ic_ui_check), contentDescription = "Selecionado", tint = colors.brand, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

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

/**
 * Par de idiomas. [modelStatus] nulo significa que nenhuma sessão está ativa,
 * então o status do modelo offline ainda não foi verificado.
 */
@Composable
internal fun LanguageCard(
    source: String,
    target: String,
    modelStatus: String?,
    onLanguagesSelected: (String, String) -> Unit,
) {
    val colors = EasyTheme.colors
    var expanded by remember { mutableStateOf<String?>(null) }
    val sourceOptions = TranslationLanguages.sourceChoices
    val targetOptions = TranslationLanguages.targetChoices.filter { it.code != "fr" || source == "fr" || target == "fr" }
    EasyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LanguageField(
                title = "Traduzir de",
                selected = source,
                options = sourceOptions,
                expanded = expanded == "source",
                modifier = Modifier.weight(1f),
                onExpand = { expanded = if (expanded == "source") null else "source" },
                onDismiss = { expanded = null },
                onSelected = { code -> expanded = null; onLanguagesSelected(code, target) },
            )
            IconButton(onClick = { onLanguagesSelected(target, source) }, modifier = Modifier.padding(horizontal = 2.dp)) {
                Icon(painterResource(R.drawable.ic_ui_swap), contentDescription = "Inverter idiomas", tint = colors.brand)
            }
            LanguageField(
                title = "Para",
                selected = target,
                options = targetOptions,
                expanded = expanded == "target",
                modifier = Modifier.weight(1f),
                onExpand = { expanded = if (expanded == "target") null else "target" },
                onDismiss = { expanded = null },
                onSelected = { code -> expanded = null; onLanguagesSelected(source, code) },
            )
        }
        val status = modelStatus ?: "O modelo offline de ${languageLabel(source)} → ${languageLabel(target)} é baixado na primeira tradução e depois funciona sem internet."
        Hint(status, color = if (modelStatus?.contains("pronto", ignoreCase = true) == true) colors.brand else colors.textMuted)
    }
}

@Composable
private fun LanguageField(
    title: String,
    selected: String,
    options: List<LanguageChoice>,
    expanded: Boolean,
    modifier: Modifier,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val colors = EasyTheme.colors
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(Radius.input)
                .border(1.dp, if (expanded) colors.brand else colors.lineStrong, Radius.input)
                .clickable(role = Role.DropdownList, onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = EasyTheme.type.rowSub, color = colors.textMuted)
                Text(languageLabel(selected), style = EasyTheme.type.rowTitle, color = colors.text)
            }
            Icon(painterResource(R.drawable.ic_ui_chevron_down), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = EasyTheme.type.body, color = colors.text) },
                    onClick = { onSelected(option.code) },
                    trailingIcon = if (option.code == selected) {
                        { Icon(painterResource(R.drawable.ic_ui_check), contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
    }
}

@Composable
internal fun QualityCard(selected: QualityMode, onSelected: (QualityMode) -> Unit) {
    EasyCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            QualityMode.entries.forEach { mode ->
                EasyChip(
                    label = mode.label,
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Hint(
            when (selected) {
                QualityMode.ECONOMY -> "Lê a tela a cada ~2 s. Gasta menos bateria; bom para textos que mudam pouco."
                QualityMode.BALANCED -> "Lê a tela a cada ~1 s. O equilíbrio entre rapidez e bateria."
                QualityMode.QUALITY -> "Lê a tela a cada ~0,5 s. Acompanha legendas e textos rápidos, com mais gasto de bateria."
            },
        )
    }
}

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
        if (state.notificationsRequired) PermissionLine("Notificações (controle da sessão)", state.notificationsGranted)
        if (mode == AppMode.SCREEN) {
            PermissionLine("Janela sobre outros apps", state.overlayGranted)
            Hint("A autorização para capturar a tela é pedida pelo Android ao iniciar.")
        }
        if (runtimeMissing || overlayMissing) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (runtimeMissing) {
                    EasyButton("Permitir", onRequestRuntime, Modifier.weight(1f), kind = ButtonKind.Tonal)
                }
                if (overlayMissing) {
                    EasyButton("Ativar janela", onOpenOverlaySettings, Modifier.weight(1f), kind = ButtonKind.Tonal)
                }
            }
        }
    }
}

@Composable
private fun PermissionLine(label: String, granted: Boolean) {
    StatusRow(label, if (granted) "Concedida" else "Pendente", if (granted) Tone.Positive else Tone.Warning)
}
