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
import com.easyapp.translator.ui.AppLanguage
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
import com.easyapp.translator.str

/** Página de Ajustes (T063): aparência, padrões de cada modo, Claude, privacidade e sobre. */
@Composable
fun SettingsPage(
    state: AppUiState,
    actions: AppActions,
    onOpenClaude: () -> Unit,
    /** Mostra o aviso "aplicando" e recarrega a interface no idioma escolhido. */
    onChangeLanguage: (AppLanguage) -> Unit,
) {
    val prefs = state.preferences
    var confirmReset by remember { mutableStateOf(false) }

    PageScaffold {
        PageHeader(
            title = str(R.string.tab_settings),
            subtitle = str(R.string.settings_subtitle),
        )

        SectionLabel(str(R.string.settings_language))
        // Funciona em qualquer Android: no 13+ pelo idioma por app do sistema, no 11–12 pelo próprio app.
        EasyCard {
            SegmentedControl(
                options = AppLanguage.entries,
                selected = state.appLanguage,
                onSelect = onChangeLanguage,
                optionDescription = { str(R.string.cd_app_language, it.label) },
            ) { "${it.flag}\n${it.label}" }
            Hint(str(R.string.settings_language_hint))
        }

        SectionLabel(str(R.string.settings_appearance))
        EasyCard {
            Text(str(R.string.settings_theme), style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            SegmentedControl(
                options = ThemeMode.entries,
                selected = prefs.themeMode,
                onSelect = actions::setThemeMode,
                optionDescription = { str(R.string.cd_theme, it.label) },
            ) { it.label }
            HorizontalDivider(Modifier.padding(vertical = Space.xs), color = EasyTheme.colors.divider)
            Text(str(R.string.settings_app_text), style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            TextSizeControl(prefs.appTextSize, actions::setAppTextSize)
            Hint(str(R.string.settings_preview))
        }

        SectionLabel(str(R.string.settings_overlay_section))
        EasyCard {
            Text(str(R.string.settings_translation_size), style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            TextSizeControl(prefs.translationTextSize, actions::setTranslationTextSize)
            TranslationPreview(prefs.translationTextSize)
            HorizontalDivider(Modifier.padding(vertical = Space.xs), color = EasyTheme.colors.divider)
            Text(str(R.string.settings_default_style), style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            SegmentedControl(
                options = OverlayStyle.entries,
                selected = prefs.overlayStyle,
                onSelect = actions::setOverlayStyle,
                optionDescription = { str(R.string.cd_style, it.label) },
            ) { it.label }
            Text(str(R.string.settings_default_quality), style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            SegmentedControl(
                options = QualityMode.entries,
                selected = prefs.qualityMode,
                onSelect = actions::setQuality,
                optionDescription = { str(R.string.cd_quality, it.label) },
            ) { it.label }
            Hint(str(R.string.settings_next_session))
        }

        SectionLabel(str(R.string.tab_conversation))
        EasyCard {
            Text(str(R.string.settings_layout), style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
            SegmentedControl(
                options = ConversationLayout.entries,
                selected = prefs.conversationLayout,
                onSelect = actions::setConversationLayout,
                optionDescription = { str(R.string.cd_layout, it.label) },
            ) { it.label }
            Hint(str(R.string.settings_voice_hint))
        }

        SectionLabel(str(R.string.section_ai))
        EasyCard {
            SettingRow(
                title = "Claude",
                subtitle = when {
                    state.claude.usable -> str(R.string.settings_claude_on, state.claude.keyHint ?: str(R.string.claude_key_saved))
                    state.claude.hasKey -> str(R.string.settings_claude_off, state.claude.keyHint ?: str(R.string.claude_key_saved))
                    else -> str(R.string.settings_claude_none)
                },
                icon = R.drawable.ic_ui_sparkle,
                onClick = onOpenClaude,
            )
        }

        SectionLabel(str(R.string.settings_privacy))
        InfoNote(
            title = str(R.string.settings_stored_title),
            body = str(R.string.settings_stored_body),
            icon = R.drawable.ic_ui_shield,
        )
        EasyButton(
            str(R.string.settings_reset),
            { confirmReset = true },
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
        )

        SectionLabel(str(R.string.settings_about))
        EasyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(size = 48)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text("EasyApp Translator", style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
                    Text(
                        if (state.appVersion.isBlank()) "MVP" else str(R.string.settings_version, state.appVersion),
                        style = EasyTheme.type.meta,
                        color = EasyTheme.colors.textBody,
                    )
                }
            }
            Hint(str(R.string.settings_credits))
        }
    }

    if (confirmReset) {
        val colors = EasyTheme.colors
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            shape = Radius.sheet,
            containerColor = colors.surface,
            title = { Text(str(R.string.settings_reset_title), style = EasyTheme.type.screenTitle, color = colors.text) },
            text = {
                Text(
                    str(R.string.settings_reset_body),
                    style = EasyTheme.type.body,
                    color = colors.textBody,
                )
            },
            confirmButton = { EasyButton(str(R.string.action_restore), { confirmReset = false; actions.resetPreferences() }) },
            dismissButton = { EasyButton(str(R.string.action_cancel), { confirmReset = false }, kind = ButtonKind.Ghost) },
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
        optionDescription = { str(R.string.cd_size, it.label) },
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
        Text(str(R.string.preview_source), style = EasyTheme.type.meta, color = colors.textMuted)
        Text(
            str(R.string.preview_translation),
            modifier = Modifier.clip(Radius.chip).background(colors.ink).padding(horizontal = Space.sm, vertical = Space.xs),
            style = EasyTheme.type.body.copy(fontSize = (16f * size.scale).sp, lineHeight = (22f * size.scale).sp),
            color = colors.textOnInk,
        )
    }
}
