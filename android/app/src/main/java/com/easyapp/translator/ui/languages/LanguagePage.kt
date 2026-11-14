package com.easyapp.translator.ui.languages

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.easyapp.translator.R
import com.easyapp.translator.session.TranslationLanguages
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.SubPageTopBar
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

/** Qual lado do par está sendo escolhido. */
enum class LanguageSide { SOURCE, TARGET }

/**
 * Página de idiomas (T062). O nome aparece no próprio idioma em destaque, para
 * quem não lê português achar o seu. Escolher o idioma do outro lado inverte o par.
 */
@Composable
fun LanguagePage(
    title: String,
    side: LanguageSide,
    source: String,
    target: String,
    onSelected: (source: String, target: String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = EasyTheme.colors
    val current = if (side == LanguageSide.SOURCE) source else target
    val other = if (side == LanguageSide.SOURCE) target else source
    val options = if (side == LanguageSide.SOURCE) TranslationLanguages.sourceChoices else TranslationLanguages.targetChoices
    Column(Modifier.fillMaxSize().background(colors.paper)) {
        SubPageTopBar(title, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Space.lg)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            options.forEach { option ->
                val selected = option.code == current
                val isOther = option.code == other
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(Radius.card)
                        .background(if (selected) colors.brandBg else colors.surface)
                        .border(if (selected) 2.dp else 1.dp, if (selected) colors.brand else colors.line, Radius.card)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                when {
                                    selected -> onBack()
                                    // Mesmo idioma dos dois lados não traduz nada: inverte o par.
                                    isOther -> onSelected(target, source)
                                    side == LanguageSide.SOURCE -> onSelected(option.code, target)
                                    else -> onSelected(source, option.code)
                                }
                            },
                        )
                        .padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(Radius.icon).background(if (selected) colors.surface else colors.surfaceAlt),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(option.code.uppercase(), style = EasyTheme.type.label, color = if (selected) colors.brand else colors.textBody)
                    }
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(option.nativeName, style = EasyTheme.type.rowTitle, color = colors.text)
                        val subtitle = buildString {
                            if (option.label != option.nativeName) append(option.label)
                            if (isOther) {
                                if (isNotEmpty()) append(" · ")
                                append("está do outro lado; escolher inverte o par")
                            }
                        }
                        if (subtitle.isNotEmpty()) Text(subtitle, style = EasyTheme.type.rowSub, color = colors.textMuted)
                    }
                    if (selected) {
                        Icon(painterResource(R.drawable.ic_ui_check), contentDescription = "Selecionado", tint = colors.brand)
                    }
                }
            }
            Spacer(Modifier.size(Space.sm))
            Hint("Na primeira tradução, o modelo offline do par escolhido é baixado (cerca de 30 MB). Depois, funciona sem internet.")
        }
    }
}
