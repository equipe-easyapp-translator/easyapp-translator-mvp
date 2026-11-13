package com.easyapp.translator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens de cor do EasyApp, portados do design system do Panela
 * (`design_handoff_panela_redesign`, `app_colors.dart`).
 *
 * Regra semântica: o verde ([brand]) só aparece na marca, na ação primária e em
 * estado positivo (sessão ativa, permissão concedida, modelo pronto). Nunca em
 * fundo de tela, cabeçalho ou texto neutro — se tudo for verde, nada se destaca.
 *
 * Mapa de nomes Panela → EasyApp: `receber` → [brand], `receberSolid` →
 * [brandSolid], `receberBg` → [brandBg], `pagar` → [warn], `pagarBg` → [warnBg].
 */
@Immutable
data class EasyColors(
    /** Fundo de tela. */
    val paper: Color,
    /** Fundo de cartão. */
    val surface: Color,
    /** Chip, ícone em bolha, trilho de controle. */
    val surfaceAlt: Color,
    /** Faixa escura (overlay, botão escuro). */
    val ink: Color,
    /** Botão ou chip secundário sobre [ink]. */
    val inkAlt: Color,
    /** Borda de cartão. */
    val line: Color,
    /** Borda de campo e de botão de contorno. */
    val lineStrong: Color,
    /** Fundo do botão secundário preenchido (areia quente). */
    val tonal: Color,
    /** Separador interno de cartão. */
    val divider: Color,
    /** Texto principal. */
    val text: Color,
    /** Texto corrido. */
    val textBody: Color,
    /** Rótulo, metadado, texto de apoio. */
    val textMuted: Color,
    /** Texto sobre [ink]. */
    val textOnInk: Color,
    /** Subtítulo sobre [ink]. */
    val mutedOnInk: Color,
    /** Marca, ação primária (texto/ícone/borda), estado positivo. */
    val brand: Color,
    /** Preenchimento da ação primária com texto claro por cima. */
    val brandSolid: Color,
    /** Fundo de estado positivo e de item selecionado. */
    val brandBg: Color,
    /** Alerta: permissão pendente, sessão pausada, erro recuperável. */
    val warn: Color,
    /** Fundo de alerta. */
    val warnBg: Color,
    /** Borda inferior de faixa [ink] (só no escuro, onde ink e paper quase se confundem). */
    val inkEdge: Color,
    val isDark: Boolean,
)

val LightEasyColors = EasyColors(
    paper = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFEFB),
    surfaceAlt = Color(0xFFF0EDE3),
    ink = Color(0xFF1B1D18),
    inkAlt = Color(0xFF26281F),
    line = Color(0xFFE2DED3),
    lineStrong = Color(0xFFD8D4C8),
    tonal = Color(0xFFECE5D4),
    divider = Color(0xFFF0EDE3),
    text = Color(0xFF1B1D18),
    textBody = Color(0xFF4B4E46),
    textMuted = Color(0xFF85887D),
    textOnInk = Color(0xFFF4F1EA),
    mutedOnInk = Color(0xFF8E9186),
    brand = Color(0xFF0E6B4D),
    brandSolid = Color(0xFF0E6B4D),
    brandBg = Color(0xFFDFEEE4),
    warn = Color(0xFFB0472A),
    warnBg = Color(0xFFF7E4DC),
    inkEdge = Color(0x00000000),
    isDark = false,
)

val DarkEasyColors = EasyColors(
    paper = Color(0xFF191A15),
    surface = Color(0xFF22241D),
    surfaceAlt = Color(0xFF2B2D24),
    ink = Color(0xFF0F100C),
    inkAlt = Color(0xFF26281F),
    line = Color(0xFF33362C),
    lineStrong = Color(0xFF3D4036),
    tonal = Color(0xFF36382F),
    divider = Color(0xFF2B2D24),
    text = Color(0xFFF0EEE6),
    textBody = Color(0xFFC7C9BF),
    textMuted = Color(0xFF82857A),
    textOnInk = Color(0xFFF4F1EA),
    mutedOnInk = Color(0xFF8E9186),
    brand = Color(0xFF4EC294),
    // No escuro o preenchimento continua o verde profundo: #4EC294 com texto
    // claro por cima fica em ~1.9:1 de contraste.
    brandSolid = Color(0xFF0E6B4D),
    brandBg = Color(0xFF14251D),
    warn = Color(0xFFE98B64),
    warnBg = Color(0xFF3A2419),
    inkEdge = Color(0xFF2B2D24),
    isDark = true,
)

val LocalEasyColors = staticCompositionLocalOf { LightEasyColors }
