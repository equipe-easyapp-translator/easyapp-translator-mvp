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

/** Estado do Claude para a interface. A chave em si nunca chega aqui, só o final dela. */
data class ClaudeUiState(
    val hasKey: Boolean = false,
    val keyHint: String? = null,
    val consentAccepted: Boolean = false,
    val enabled: Boolean = true,
    val testing: Boolean = false,
    val testMessage: String? = null,
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
        CardHeader("Tradução refinada com Claude", icon = R.drawable.ic_ui_messages) {
            when {
                !state.hasKey -> StatusBadge("Opcional", Tone.Neutral)
                state.usable -> StatusBadge("Ligado", Tone.Positive)
                else -> StatusBadge("Desligado", Tone.Neutral)
            }
        }
        Hint(
            "Traduz a tela com contexto, entende gírias e explica frases quando você toca nelas. " +
                "Usa a sua própria chave da Anthropic e só funciona com internet; sem chave, tudo continua offline.",
        )
        if (state.hasKey) {
            StatusRow("Chave", state.keyHint ?: "salva", Tone.Positive)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usar no modo Qualidade", Modifier.weight(1f), style = EasyTheme.type.body, color = colors.textBody)
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
                Hint("Para traduzir com o Claude, escolha a qualidade \"Qualidade\" na página Tela. Na janela flutuante, o toque para explicar funciona em qualquer qualidade.", color = colors.warn)
            }
            state.testMessage?.let { Hint(it, color = if (it.startsWith("Funcionou")) colors.brand else colors.warn) }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                EasyButton(
                    if (state.testing) "Testando…" else "Testar",
                    onTest,
                    Modifier.weight(1f),
                    kind = ButtonKind.Tonal,
                    enabled = !state.testing,
                )
                EasyButton("Remover chave", { confirmRemove = true }, Modifier.weight(1f), kind = ButtonKind.Outline)
            }
            Hint("Dica: defina um limite de gasto mensal no Console da Anthropic (Settings → Limits).")
        } else {
            EasyButton(
                "Adicionar minha chave",
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
            title = { Text("Remover a chave?", style = EasyTheme.type.screenTitle, color = colors.text) },
            text = { Text("A chave é apagada deste aparelho e o app volta a traduzir só offline.", style = EasyTheme.type.body, color = colors.textBody) },
            confirmButton = { EasyButton("Remover", { confirmRemove = false; onRemoveKey() }) },
            dismissButton = { EasyButton("Cancelar", { confirmRemove = false }, kind = ButtonKind.Ghost) },
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
        title = { Text("Enviar texto para o Claude", style = EasyTheme.type.screenTitle, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                listOf(
                    "Com o Claude ligado, o texto lido da tela (nunca a imagem nem o áudio) é enviado à Anthropic para ser traduzido ou explicado.",
                    "A Anthropic apaga esses dados em até 30 dias e, por padrão, não os usa para treinar modelos.",
                    "Evite usar o modo Qualidade em telas com senhas, dados bancários ou conversas privadas.",
                    "O custo vai para a conta da sua chave. Com o Claude Haiku 5.5, uma hora de uso contínuo fica em torno de US$ 0,10 a 0,15.",
                ).forEach { Text(it, style = EasyTheme.type.body, color = colors.textBody) }
            }
        },
        confirmButton = { EasyButton("Entendi, continuar", onAccept) },
        dismissButton = { EasyButton("Agora não", onDecline, kind = ButtonKind.Ghost) },
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
        title = { Text("Sua chave da Anthropic", style = EasyTheme.type.screenTitle, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "Crie uma chave em console.anthropic.com → API Keys e cole aqui. Ela fica criptografada só neste aparelho.",
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
                    Hint("Chaves da Anthropic começam com sk-ant-.", color = colors.warn)
                }
            }
        },
        confirmButton = { EasyButton("Salvar", { onSave(key.trim()) }, enabled = looksValid) },
        dismissButton = { EasyButton("Cancelar", onDismiss, kind = ButtonKind.Ghost) },
    )
}

/** Gasto da sessão em andamento (FR-024). */
@Composable
internal fun ClaudeUsageCard(usage: ClaudeUsage) {
    val colors = EasyTheme.colors
    EasyCard {
        CardHeader("Claude nesta sessão", icon = R.drawable.ic_ui_messages) {
            StatusBadge(if (usage.lastError == null) "Ligado" else "Com aviso", if (usage.lastError == null) Tone.Positive else Tone.Warning)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            UsageFigure("Chamadas", usage.requests.toString())
            UsageFigure("Tokens", "%,d".format(Locale("pt", "BR"), usage.inputTokens + usage.outputTokens))
            UsageFigure("Custo estimado", "US$ " + "%.4f".format(Locale("pt", "BR"), usage.estimatedCostUsd))
        }
        usage.lastError?.let { Hint("Último aviso: $it. As linhas afetadas usam tradução offline.", color = colors.warn) }
        if (usage.requests == 0 && usage.lastError == null) {
            Hint("Ainda sem chamadas. O Claude traduz no modo Qualidade e explica quando você toca numa linha da janela flutuante.")
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
