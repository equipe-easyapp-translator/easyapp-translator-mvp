package com.easyapp.translator.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.easyapp.translator.R
import com.easyapp.translator.data.ConversationLayout
import com.easyapp.translator.data.OverlayStyle
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.data.TextSize
import com.easyapp.translator.data.ThemeMode
import com.easyapp.translator.ui.AppActions
import com.easyapp.translator.ui.AppUiState
import com.easyapp.translator.ui.components.AppMark
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.EasyCard
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.InfoNote
import com.easyapp.translator.ui.components.PageHeader
import com.easyapp.translator.ui.components.PageScaffold
import com.easyapp.translator.ui.components.SectionLabel
import com.easyapp.translator.ui.components.SegmentedControl
import com.easyapp.translator.ui.components.SettingRow
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

/** Página de Ajustes (T063): aparência, padrões de cada modo, Claude, privacidade e sobre. */
@Composable
fun SettingsPage(state: AppUiState, actions: AppActions, onOpenClaude: () -> Unit) {
    val prefs = state.preferences
    var confirmReset by remember { mutableStateOf(false) }

    PageScaffold {
        PageHeader(
            title = "Ajustes",
            subtitle = "Deixe o EasyApp do jeito que é mais confortável para você. As mudanças valem na hora.",
        )

        SectionLabel("Aparência")
        EasyCard {
            Text("Tema", style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            SegmentedControl(
                options = ThemeMode.entries,
                selected = prefs.themeMode,
                onSelect = actions::setThemeMode,
                optionDescription = { "Tema ${it.label}" },
            ) { it.label }
            HorizontalDivider(Modifier.padding(vertical = Space.xs), color = EasyTheme.colors.divider)
            Text("Tamanho do texto do app", style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            TextSizeControl(prefs.appTextSize, actions::setAppTextSize)
            Hint("Prévia: este texto acompanha o tamanho escolhido.")
        }

        SectionLabel("Tradução sobre a tela")
        EasyCard {
            Text("Tamanho da tradução", style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            TextSizeControl(prefs.translationTextSize, actions::setTranslationTextSize)
            TranslationPreview(prefs.translationTextSize)
            HorizontalDivider(Modifier.padding(vertical = Space.xs), color = EasyTheme.colors.divider)
            Text("Estilo padrão", style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            SegmentedControl(
                options = OverlayStyle.entries,
                selected = prefs.overlayStyle,
                onSelect = actions::setOverlayStyle,
                optionDescription = { "Estilo ${it.label}" },
            ) { it.label }
            Text("Qualidade padrão", style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            SegmentedControl(
                options = QualityMode.entries,
                selected = prefs.qualityMode,
                onSelect = actions::setQuality,
                optionDescription = { "Qualidade ${it.label}" },
            ) { it.label }
            Hint("O tamanho e o estilo valem a partir da próxima sessão de tela.")
        }

        SectionLabel("Conversa")
        EasyCard {
            Text("Disposição", style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            SegmentedControl(
                options = ConversationLayout.entries,
                selected = prefs.conversationLayout,
                onSelect = actions::setConversationLayout,
                optionDescription = { "Conversa ${it.label}" },
            ) { it.label }
            Hint("Voz, velocidade e volume ficam no botão Voz durante a conversa, porque dependem das vozes instaladas.")
        }

        SectionLabel("Inteligência artificial")
        EasyCard {
            SettingRow(
                title = "Claude",
                subtitle = when {
                    state.claude.usable -> "Ligado · chave ${state.claude.keyHint ?: "salva"}"
                    state.claude.hasKey -> "Desligado · chave ${state.claude.keyHint ?: "salva"}"
                    else -> "Não configurado"
                },
                icon = R.drawable.ic_ui_sparkle,
                onClick = onOpenClaude,
            )
        }

        SectionLabel("Privacidade")
        InfoNote(
            title = "O que o EasyApp guarda",
            body = "Só as suas escolhas desta página e, se você adicionar, a chave do Claude criptografada. Telas, áudio, conversas e traduções ficam na memória durante a sessão e somem ao parar.",
            icon = R.drawable.ic_ui_shield,
        )
        EasyButton(
            "Restaurar ajustes padrão",
            { confirmReset = true },
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
        )

        SectionLabel("Sobre")
        EasyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(size = 48)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text("EasyApp Translator", style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
                    Text(
                        if (state.appVersion.isBlank()) "MVP" else "Versão ${state.appVersion} · MVP",
                        style = EasyTheme.type.meta,
                        color = EasyTheme.colors.textBody,
                    )
                }
            }
            Hint("Feito por Brayan, Carlos, Sante e Tiago Morocini. Tradução no aparelho com Google ML Kit; tradução refinada opcional com Claude, da Anthropic.")
        }
    }

    if (confirmReset) {
        val colors = EasyTheme.colors
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            shape = Radius.sheet,
            containerColor = colors.surface,
            title = { Text("Restaurar os ajustes?", style = EasyTheme.type.screenTitle, color = colors.text) },
            text = {
                Text(
                    "Tema, tamanhos, idiomas, estilo e qualidade voltam ao padrão. A chave do Claude não é apagada; para isso, use a página do Claude.",
                    style = EasyTheme.type.body,
                    color = colors.textBody,
                )
            },
            confirmButton = { EasyButton("Restaurar", { confirmReset = false; actions.resetPreferences() }) },
            dismissButton = { EasyButton("Cancelar", { confirmReset = false }, kind = ButtonKind.Ghost) },
        )
    }
}

/** Quatro "A" de tamanhos crescentes; o leitor de tela lê o nome completo de cada tamanho. */
@Composable
private fun TextSizeControl(selected: TextSize, onSelect: (TextSize) -> Unit) {
    SegmentedControl(
        options = TextSize.entries,
        selected = selected,
        onSelect = onSelect,
        optionDescription = { "Tamanho ${it.label}" },
        optionSize = { (14f * it.scale).sp },
    ) { "A" }
}

/** Prévia da tradução sobre a tela no tamanho escolhido, com as cores reais da sobreposição. */
@Composable
private fun TranslationPreview(size: TextSize) {
    val colors = EasyTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(Radius.input).background(colors.surfaceAlt).padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text("See you tomorrow at the station!", style = EasyTheme.type.meta, color = colors.textMuted)
        Text(
            "Te vejo amanhã na estação!",
            modifier = Modifier.clip(Radius.chip).background(colors.ink).padding(horizontal = Space.sm, vertical = Space.xs),
            style = EasyTheme.type.body.copy(fontSize = (16f * size.scale).sp, lineHeight = (22f * size.scale).sp),
            color = colors.textOnInk,
        )
    }
}
