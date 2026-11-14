package com.easyapp.translator.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.easyapp.translator.R
import com.easyapp.translator.ui.AppLanguage
import com.easyapp.translator.ui.components.AppMark
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space
import java.util.Locale

/*
 * Os textos destas telas são fixos nos três idiomas de propósito: quem abre o app
 * pela primeira vez ainda não escolheu o seu, e o aviso "aplicando" aparece no idioma
 * escolhido antes de a interface trocar.
 */

private val options = listOf(AppLanguage.SYSTEM, AppLanguage.PORTUGUESE, AppLanguage.ITALIAN, AppLanguage.ENGLISH)

/** Idioma que vale para [language]; "Sistema" usa o do celular. */
private fun effectiveCode(language: AppLanguage): String = when (language) {
    AppLanguage.PORTUGUESE -> "pt"
    AppLanguage.ITALIAN -> "it"
    AppLanguage.ENGLISH -> "en"
    AppLanguage.SYSTEM -> Locale.getDefault().language.takeIf { it == "pt" || it == "it" } ?: "en"
}

private fun continueLabel(language: AppLanguage) = when (effectiveCode(language)) {
    "pt" -> "Continuar"
    "it" -> "Continua"
    else -> "Continue"
}

private fun applyingLabel(language: AppLanguage) = when (effectiveCode(language)) {
    "pt" -> "Aplicando o idioma…"
    "it" -> "Applico la lingua…"
    else -> "Applying the language…"
}

private fun optionTitle(language: AppLanguage) = when (language) {
    AppLanguage.SYSTEM -> "Idioma do celular · Lingua del telefono · Phone language"
    AppLanguage.PORTUGUESE -> "Português"
    AppLanguage.ITALIAN -> "Italiano"
    AppLanguage.ENGLISH -> "English"
}

/** Primeira tela do app (História 9): escolher o idioma antes de qualquer outra coisa. */
@Composable
fun LanguageWelcomePage(current: AppLanguage, onConfirm: (AppLanguage) -> Unit) {
    val colors = EasyTheme.colors
    var selected by remember { mutableStateOf(current) }
    Column(Modifier.fillMaxSize().background(colors.paper)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg, vertical = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            AppMark(size = 64)
            Text(
                "Bem-vindo · Benvenuto · Welcome",
                modifier = Modifier.semantics { heading() },
                style = EasyTheme.type.display,
                color = colors.text,
            )
            Text(
                "Escolha o idioma do app\nScegli la lingua dell'app\nChoose the app language",
                style = EasyTheme.type.body.copy(lineHeight = EasyTheme.type.translation.lineHeight),
                color = colors.textBody,
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                options.forEach { option ->
                    LanguageOptionRow(option, selected == option) { selected = option }
                }
            }
            Hint("Você pode trocar depois em Ajustes · Puoi cambiarla in Impostazioni · You can change it later in Settings")
        }
        HorizontalDivider(color = colors.line)
        Box(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = Space.lg, vertical = Space.md)) {
            EasyButton(continueLabel(selected), { onConfirm(selected) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LanguageOptionRow(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val colors = EasyTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(Radius.card)
            .background(if (selected) colors.brandBg else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.brand else colors.line, Radius.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(optionTitle(language), Modifier.weight(1f), style = EasyTheme.type.rowTitle, color = colors.text)
        if (selected) {
            Spacer(Modifier.width(Space.sm))
            Icon(painterResource(R.drawable.ic_ui_check), contentDescription = null, tint = colors.brand)
        }
    }
}

/** Aviso curto, já no idioma escolhido, enquanto o app recarrega a interface. */
@Composable
fun ApplyingLanguageScreen(language: AppLanguage) {
    val colors = EasyTheme.colors
    Column(
        Modifier.fillMaxSize().background(colors.paper).padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppMark(size = 72)
        Spacer(Modifier.size(Space.xl))
        CircularProgressIndicator(color = colors.brand, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Spacer(Modifier.size(Space.md))
        Text(applyingLabel(language), style = EasyTheme.type.screenTitle, color = colors.text, textAlign = TextAlign.Center)
    }
}
