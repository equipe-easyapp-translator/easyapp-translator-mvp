package com.easyapp.translator.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.easyapp.translator.R
import com.easyapp.translator.translation.ClaudeUsage
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.CardHeader
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.EasyCard
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.StatusBadge
import com.easyapp.translator.ui.components.StatusRow
import com.easyapp.translator.ui.components.Tone
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space
import java.util.Locale
import com.easyapp.translator.str

/** Estado do Claude para a interface. A chave em si nunca chega aqui, só o final dela. */
data class ClaudeUiState(
    val hasKey: Boolean = false,
    val keyHint: String? = null,
    val consentAccepted: Boolean = false,
    val enabled: Boolean = true,
    val testing: Boolean = false,
    val testMessage: String? = null,
    /** Resultado do último teste; decide a cor da mensagem sem depender do idioma do texto. */
    val testSucceeded: Boolean = false,
) {
    val usable: Boolean get() = hasKey && consentAccepted && enabled
}

/** Cartão de configuração (passo opcional do modo Tela). */
@Composable
internal fun ClaudeSetupCard(
    state: ClaudeUiState,
    qualitySelected: Boolean,
    onSaveKey: (String) -> Unit,
    onConsentAccepted: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onRemoveKey: () -> Unit,
    onTest: () -> Unit,
) {
    val colors = EasyTheme.colors
    var showConsent by remember { mutableStateOf(false) }
    var showKeyEntry by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    EasyCard {
        CardHeader(str(R.string.claude_row_title), icon = R.drawable.ic_ui_messages) {
            when {
                !state.hasKey -> StatusBadge(str(R.string.badge_optional), Tone.Neutral)
                state.usable -> StatusBadge(str(R.string.badge_on), Tone.Positive)
                else -> StatusBadge(str(R.string.badge_off), Tone.Neutral)
            }
        }
        Hint(
            str(R.string.claude_card_intro) +
                "",
        )
        if (state.hasKey) {
            StatusRow(str(R.string.claude_key_label), state.keyHint ?: str(R.string.claude_key_saved), Tone.Positive)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.claude_use_quality), Modifier.weight(1f), style = EasyTheme.type.body, color = colors.textBody)
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.brandSolid,
                        uncheckedTrackColor = colors.surfaceAlt,
                        uncheckedBorderColor = colors.lineStrong,
                        uncheckedThumbColor = colors.textMuted,
                    ),
                )
            }
            if (state.enabled && !qualitySelected) {
                Hint(str(R.string.claude_choose_quality), color = colors.warn)
            }
            state.testMessage?.let { Hint(it, color = if (state.testSucceeded) colors.brand else colors.warn) }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                EasyButton(
                    if (state.testing) str(R.string.claude_testing) else str(R.string.claude_test),
                    onTest,
                    Modifier.weight(1f),
                    kind = ButtonKind.Tonal,
                    enabled = !state.testing,
                )
                EasyButton(str(R.string.claude_remove), { confirmRemove = true }, Modifier.weight(1f), kind = ButtonKind.Outline)
            }
            Hint(str(R.string.claude_tip_limit))
        } else {
            EasyButton(
                str(R.string.claude_add_key),
                { if (state.consentAccepted) showKeyEntry = true else showConsent = true },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Tonal,
            )
        }
    }

    if (showConsent) {
        ClaudeConsentDialog(
            onAccept = {
                showConsent = false
                onConsentAccepted()
                showKeyEntry = true
            },
            onDecline = { showConsent = false },
        )
    }
    if (showKeyEntry) {
        ClaudeKeyDialog(
            onSave = { key ->
                showKeyEntry = false
                onSaveKey(key)
            },
            onDismiss = { showKeyEntry = false },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            shape = Radius.sheet,
            containerColor = colors.surface,
            title = { Text(str(R.string.claude_remove_title), style = EasyTheme.type.screenTitle, color = colors.text) },
            text = { Text(str(R.string.claude_remove_body), style = EasyTheme.type.body, color = colors.textBody) },
            confirmButton = { EasyButton(str(R.string.action_remove), { confirmRemove = false; onRemoveKey() }) },
            dismissButton = { EasyButton(str(R.string.action_cancel), { confirmRemove = false }, kind = ButtonKind.Ghost) },
        )
    }
}

/** Aviso antes do primeiro uso (FR-018, T045): o que sai do aparelho e o que a Anthropic faz com isso. */
@Composable
private fun ClaudeConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    val colors = EasyTheme.colors
    AlertDialog(
        onDismissRequest = onDecline,
        shape = Radius.sheet,
        containerColor = colors.surface,
        title = { Text(str(R.string.consent_title), style = EasyTheme.type.screenTitle, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                listOf(
                    str(R.string.consent_p1),
                    str(R.string.consent_p2),
                    str(R.string.consent_p3),
                    str(R.string.consent_p4),
                ).forEach { Text(it, style = EasyTheme.type.body, color = colors.textBody) }
            }
        },
        confirmButton = { EasyButton(str(R.string.consent_accept), onAccept) },
        dismissButton = { EasyButton(str(R.string.action_not_now), onDecline, kind = ButtonKind.Ghost) },
    )
}

@Composable
private fun ClaudeKeyDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = EasyTheme.colors
    var key by remember { mutableStateOf("") }
    val looksValid = key.trim().startsWith("sk-ant-") && key.trim().length > 20
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Radius.sheet,
        containerColor = colors.surface,
        title = { Text(str(R.string.key_title), style = EasyTheme.type.screenTitle, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    str(R.string.key_body),
                    style = EasyTheme.type.body,
                    color = colors.textBody,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("sk-ant-…", style = EasyTheme.type.body) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    shape = Radius.input,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brand,
                        unfocusedBorderColor = colors.lineStrong,
                        cursorColor = colors.brand,
                    ),
                )
                if (key.isNotBlank() && !looksValid) {
                    Hint(str(R.string.key_invalid), color = colors.warn)
                }
            }
        },
        confirmButton = { EasyButton(str(R.string.action_save), { onSave(key.trim()) }, enabled = looksValid) },
        dismissButton = { EasyButton(str(R.string.action_cancel), onDismiss, kind = ButtonKind.Ghost) },
    )
}

/** Gasto da sessão em andamento (FR-024). */
@Composable
internal fun ClaudeUsageCard(usage: ClaudeUsage) {
    val colors = EasyTheme.colors
    EasyCard {
        CardHeader(str(R.string.usage_title), icon = R.drawable.ic_ui_messages) {
            StatusBadge(if (usage.lastError == null) str(R.string.badge_on) else str(R.string.badge_warning), if (usage.lastError == null) Tone.Positive else Tone.Warning)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            UsageFigure(str(R.string.usage_calls), usage.requests.toString())
            UsageFigure(str(R.string.usage_tokens), "%,d".format(Locale.getDefault(), usage.inputTokens + usage.outputTokens))
            UsageFigure(str(R.string.usage_cost), "US$ " + "%.4f".format(Locale.getDefault(), usage.estimatedCostUsd))
        }
        usage.lastError?.let { Hint(str(R.string.usage_last_warning, it), color = colors.warn) }
        if (usage.requests == 0 && usage.lastError == null) {
            Hint(str(R.string.usage_none))
        }
    }
}

@Composable
private fun UsageFigure(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = EasyTheme.type.label, color = EasyTheme.colors.textMuted)
        Text(value, style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
    }
}
