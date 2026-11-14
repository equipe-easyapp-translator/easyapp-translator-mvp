package com.easyapp.translator.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.easyapp.translator.R
import com.easyapp.translator.session.TranslationLanguages
import com.easyapp.translator.ui.theme.EasyColors
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space
import com.easyapp.translator.str

/**
 * Página rolável com ação fixa embaixo (opcional). A ação fica sempre visível,
 * então o usuário nunca precisa procurar o botão de começar no fim da lista.
 */
@Composable
fun PageScaffold(
    modifier: Modifier = Modifier,
    bottomAction: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EasyTheme.colors
    Column(modifier.fillMaxSize().background(colors.paper)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            content = content,
        )
        if (bottomAction != null) {
            HorizontalDivider(color = colors.line)
            Column(
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = Space.lg, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                content = bottomAction,
            )
        }
    }
}

/** Título de página de primeiro nível (Tela, Conversa, Ajustes). */
@Composable
fun PageHeader(title: String, subtitle: String, modifier: Modifier = Modifier, overline: String? = null) {
    val colors = EasyTheme.colors
    Column(modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.xs), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (overline != null) Overline(overline, color = colors.brand)
        Text(title, modifier = Modifier.semantics { heading() }, style = EasyTheme.type.display, color = colors.text)
        Text(subtitle, style = EasyTheme.type.body, color = colors.textBody)
    }
}

/** Barra de topo das páginas secundárias, com Voltar. */
@Composable
fun SubPageTopBar(title: String, onBack: () -> Unit) {
    val colors = EasyTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.paper).padding(horizontal = Space.xs, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouch)) {
            Icon(painterResource(R.drawable.ic_ui_arrow_left), contentDescription = str(R.string.cd_back), tint = colors.text)
        }
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = EasyTheme.type.screenTitle,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(color = colors.line)
}

/** Rótulo de seção dentro de uma página, marcado como título para leitores de tela. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Overline(text, modifier.padding(top = Space.sm).semantics { heading() }, color = EasyTheme.colors.textMuted)
}

/**
 * Par de idiomas grande e tocável. Cada lado abre a página de idiomas;
 * o botão do meio inverte o par.
 */
@Composable
fun LanguageBar(
    leftTitle: String,
    leftCode: String,
    rightTitle: String,
    rightCode: String,
    onPickLeft: () -> Unit,
    onPickRight: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EasyTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(colors.surface)
            .border(1.dp, colors.line, Radius.card)
            .padding(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageSlot(leftTitle, leftCode, onPickLeft, Modifier.weight(1f))
        IconButton(onClick = onSwap, modifier = Modifier.size(Space.minTouch)) {
            Box(
                Modifier.size(40.dp).clip(Radius.pill).background(colors.brandBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_ui_swap), contentDescription = str(R.string.cd_swap), tint = colors.brand, modifier = Modifier.size(20.dp))
            }
        }
        LanguageSlot(rightTitle, rightCode, onPickRight, Modifier.weight(1f), alignEnd = true)
    }
}

@Composable
private fun LanguageSlot(title: String, code: String, onClick: () -> Unit, modifier: Modifier, alignEnd: Boolean = false) {
    val colors = EasyTheme.colors
    val choice = TranslationLanguages.choice(code)
    Column(
        modifier
            .clip(Radius.input)
            .clickable(role = Role.Button, onClickLabel = str(R.string.cd_change, title), onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.sm, vertical = Space.sm),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title.uppercase(), style = EasyTheme.type.label, color = colors.textMuted, maxLines = 1)
        Text(
            choice.label,
            style = EasyTheme.type.rowTitle.copy(fontSize = EasyTheme.type.screenTitle.fontSize * 0.9f),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (choice.nativeName != choice.label) {
            Text(choice.nativeName, style = EasyTheme.type.rowSub, color = colors.textMuted, maxLines = 1)
        }
    }
}

/**
 * Controle segmentado (2 a 4 opções). [optionLabel] é o texto visível;
 * [optionDescription] é lido pelo leitor de tela quando o texto visível é um símbolo.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionDescription: (T) -> String = { it.toString() },
    optionSize: (T) -> TextUnit = { TextUnit.Unspecified },
    optionLabel: (T) -> String,
) {
    val colors = EasyTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.input)
            .background(colors.surfaceAlt)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(if (isSelected) colors.surface else colors.surfaceAlt, label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(Radius.chip)
                    .background(background)
                    .then(if (isSelected) Modifier.border(1.dp, colors.line, Radius.chip) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .semantics { contentDescription = optionDescription(option) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val base = EasyTheme.type.button.copy(fontSize = EasyTheme.type.meta.fontSize)
                val size = optionSize(option)
                Text(
                    optionLabel(option),
                    style = if (size == TextUnit.Unspecified) base else base.copy(fontSize = size),
                    color = if (isSelected) colors.text else colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Linha de ajuste/navegação: ícone, título, subtítulo e um elemento à direita (padrão: seta). */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = EasyTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Radius.input)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBubble(icon, size = 36.dp)
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = EasyTheme.type.rowTitle, color = colors.text)
            if (subtitle != null) Text(subtitle, style = EasyTheme.type.meta, color = colors.textBody)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(painterResource(R.drawable.ic_ui_chevron_right), contentDescription = null, tint = colors.textMuted)
        }
    }
}

/**
 * Cartão de escolha com ilustração (estilo da tradução, layout da conversa).
 * A ilustração é desenhada em Canvas com as cores do tema, então acompanha claro/escuro.
 */
@Composable
fun IllustratedChoice(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: DrawScope.(EasyColors) -> Unit,
) {
    val colors = EasyTheme.colors
    val border by animateColorAsState(if (selected) colors.brand else colors.line, label = "choice-border")
    Column(
        modifier
            .clip(Radius.card)
            .background(if (selected) colors.brandBg else colors.surface)
            .border(if (selected) 2.dp else 1.dp, border, Radius.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.05f).clip(Radius.input).background(if (selected) colors.surface else colors.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(Space.sm)) { illustration(colors) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = EasyTheme.type.rowTitle, color = colors.text, maxLines = 2)
            if (selected) {
                Icon(painterResource(R.drawable.ic_ui_check), contentDescription = str(R.string.cd_selected), tint = colors.brand, modifier = Modifier.size(18.dp))
            }
        }
        Text(description, style = EasyTheme.type.rowSub, color = colors.textBody)
    }
}

/** Faixa escura de destaque (sessão ativa). Usa `ink`, como os cabeçalhos do Panela. */
@Composable
fun InkPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = EasyTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(colors.ink)
            .border(1.dp, colors.inkEdge, Radius.card)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

/** O ícone do app em miniatura: a camada da frente do ícone adaptativo sobre o verde da marca. */
@Composable
fun AppMark(size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size.dp).clip(Radius.icon).background(EasyTheme.colors.brandSolid),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize((size * 1.5f).dp),
        )
    }
}

/** Ponto colorido para estados ao vivo. */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(Radius.pill).background(color))
}

/** Área central com conteúdo empilhado e espaçado (estados vazios). */
@Composable
fun CenteredColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}
