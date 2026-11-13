package com.easyapp.translator.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

/** Cartão padrão: fundo `surface`, borda `line`, sem sombra (a borda é que separa do fundo). */
@Composable
fun EasyCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(Space.md + 2.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EasyTheme.colors
    val clickModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(if (selected) colors.brandBg else colors.surface)
            .border(1.dp, if (selected) colors.brand else colors.line, Radius.card)
            .then(clickModifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

/** Rótulo de seção em maiúsculas ("IDIOMAS", "SESSÃO"). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = EasyTheme.colors.textMuted) {
    Text(text.uppercase(), modifier = modifier, style = EasyTheme.type.overline, color = color)
}

/** Título de cartão com rótulo opcional à direita. */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBubble(icon, size = 32.dp)
            Spacer(Modifier.width(Space.sm))
        }
        Text(title, modifier = Modifier.weight(1f), style = EasyTheme.type.rowTitle, color = EasyTheme.colors.text)
        trailing()
    }
}

/** Ícone dentro de um quadrado arredondado (`surfaceAlt`, ou `surface` quando o cartão está selecionado). */
@Composable
fun IconBubble(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = EasyTheme.colors.brand,
    background: Color = EasyTheme.colors.surfaceAlt,
) {
    Box(
        modifier = modifier.size(size).clip(Radius.icon).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

enum class ButtonKind { Primary, Tonal, Outline, Ghost }

/**
 * Botão do design system. `Primary` é a única ação verde da tela; `Tonal` (areia)
 * é a ação secundária preenchida; `Outline` e `Ghost` para ações de menor peso.
 */
@Composable
fun EasyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val colors = EasyTheme.colors
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = EasyTheme.type.button, textAlign = TextAlign.Center)
    }
    val sized = modifier.heightIn(min = Space.minTouch)
    val padding = PaddingValues(horizontal = Space.md, vertical = Space.sm)
    when (kind) {
        ButtonKind.Primary -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = Radius.button,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.brandSolid,
                contentColor = Color.White,
                disabledContainerColor = colors.surfaceAlt,
                disabledContentColor = colors.textMuted,
            ),
            content = content,
        )
        ButtonKind.Tonal -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = Radius.button,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.tonal,
                contentColor = colors.text,
                disabledContainerColor = colors.surfaceAlt,
                disabledContentColor = colors.textMuted,
            ),
            content = content,
        )
        ButtonKind.Outline -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = Radius.button,
            contentPadding = padding,
            border = BorderStroke(1.dp, if (enabled) colors.lineStrong else colors.line),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colors.text,
                disabledContentColor = colors.textMuted,
            ),
            content = content,
        )
        ButtonKind.Ghost -> TextButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = Radius.button,
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(
                contentColor = colors.brand,
                disabledContentColor = colors.textMuted,
            ),
            content = content,
        )
    }
}

enum class Tone { Neutral, Positive, Warning }

/** Selo de estado com ponto colorido ("ATIVA", "PAUSADA", "CONCEDIDA"). */
@Composable
fun StatusBadge(label: String, tone: Tone, modifier: Modifier = Modifier) {
    val colors = EasyTheme.colors
    val (fg, bg) = when (tone) {
        Tone.Neutral -> colors.textMuted to colors.surfaceAlt
        Tone.Positive -> colors.brand to colors.brandBg
        Tone.Warning -> colors.warn to colors.warnBg
    }
    Row(
        modifier = modifier.clip(Radius.pill).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(label.uppercase(), style = EasyTheme.type.label, color = fg)
    }
}

/** Chip selecionável (modo de qualidade, quem fala). */
@Composable
fun EasyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = EasyTheme.colors
    val fg = when {
        !enabled -> colors.textMuted
        selected -> colors.brand
        else -> colors.textBody
    }
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(Radius.chip)
            .background(if (selected) colors.brandBg else colors.surface)
            .border(1.dp, if (selected) colors.brand else colors.lineStrong, Radius.chip)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = EasyTheme.type.button.copy(fontSize = EasyTheme.type.meta.fontSize), color = fg, textAlign = TextAlign.Center)
    }
}

/** Linha de estado: rótulo à esquerda, selo à direita. */
@Composable
fun StatusRow(label: String, value: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = EasyTheme.type.body, color = EasyTheme.colors.textBody)
        StatusBadge(value, tone)
    }
}

/** Nota informativa em bloco (`surfaceAlt`), com ícone. */
@Composable
fun InfoNote(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    tone: Tone = Tone.Neutral,
) {
    val colors = EasyTheme.colors
    val (bg, iconTint) = when (tone) {
        Tone.Neutral -> colors.surfaceAlt to colors.brand
        Tone.Positive -> colors.brandBg to colors.brand
        Tone.Warning -> colors.warnBg to colors.warn
    }
    Row(
        modifier = modifier.fillMaxWidth().clip(Radius.card).background(bg).padding(Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = EasyTheme.type.rowTitle.copy(fontSize = EasyTheme.type.body.fontSize), color = colors.text)
            Text(body, style = EasyTheme.type.meta, color = colors.textBody)
        }
    }
}

/** Texto de apoio padrão. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = EasyTheme.colors.textMuted) {
    Text(text, modifier = modifier, style = EasyTheme.type.meta, color = color)
}
