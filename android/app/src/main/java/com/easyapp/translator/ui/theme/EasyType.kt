@file:OptIn(ExperimentalTextApi::class)

package com.easyapp.translator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.easyapp.translator.R

/**
 * Archivo, a fonte de interface do Panela. O arquivo é a fonte variável oficial
 * do Google Fonts (`res/font/archivo.ttf`, licença OFL em `docs/licencas/`); cada
 * peso abaixo fixa o eixo `wght` dela.
 */
private fun archivo(weight: FontWeight) = Font(
    resId = R.font.archivo,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Archivo = FontFamily(
    archivo(FontWeight.Normal),
    archivo(FontWeight.Medium),
    archivo(FontWeight.SemiBold),
    archivo(FontWeight.Bold),
)

/** Escala tipográfica do Panela (`app_typography.dart`). As cores vêm de [EasyColors] no uso. */
@Immutable
data class EasyType(
    /** Rótulo maiúsculo pequeno (chip, badge). */
    val label: TextStyle,
    /** Rótulo de seção maiúsculo ("IDIOMAS", "SESSÃO"). */
    val overline: TextStyle,
    /** Metadado / texto de apoio. */
    val meta: TextStyle,
    /** Texto corrido. */
    val body: TextStyle,
    /** Nome de linha de lista, título de cartão. */
    val rowTitle: TextStyle,
    /** Subtítulo de linha de lista. */
    val rowSub: TextStyle,
    /** Texto de botão. */
    val button: TextStyle,
    /** Título de tela. */
    val screenTitle: TextStyle,
    /** Título de destaque (abertura da tela inicial). */
    val display: TextStyle,
    /** Texto traduzido em destaque. */
    val translation: TextStyle,
)

private fun style(size: Float, weight: FontWeight, letterSpacing: Float = 0f, lineHeight: Float? = null) = TextStyle(
    fontFamily = Archivo,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = letterSpacing.sp,
    lineHeight = lineHeight?.sp ?: (size * 1.35f).sp,
)

val DefaultEasyType = EasyType(
    label = style(11f, FontWeight.SemiBold, letterSpacing = 0.6f),
    overline = style(12f, FontWeight.Medium, letterSpacing = 0.84f),
    meta = style(12.5f, FontWeight.Normal, lineHeight = 17f),
    body = style(13.5f, FontWeight.Normal, lineHeight = 20f),
    rowTitle = style(14.5f, FontWeight.SemiBold, lineHeight = 19f),
    rowSub = style(11.5f, FontWeight.Normal, lineHeight = 17f),
    button = style(14.5f, FontWeight.SemiBold),
    screenTitle = style(21f, FontWeight.Bold, letterSpacing = -0.4f, lineHeight = 26f),
    display = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.03).em,
        lineHeight = 32.sp,
    ),
    translation = style(19f, FontWeight.Medium, letterSpacing = -0.2f, lineHeight = 26f),
)

val LocalEasyType = staticCompositionLocalOf { DefaultEasyType }
