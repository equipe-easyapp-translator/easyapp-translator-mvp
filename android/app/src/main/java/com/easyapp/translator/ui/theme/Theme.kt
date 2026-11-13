package com.easyapp.translator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Tema do EasyApp. Os componentes próprios leem [EasyTheme.colors]/[EasyTheme.type];
 * o `colorScheme` do Material 3 é derivado dos mesmos tokens para que diálogos,
 * menus, sliders e switches combinem com o resto sem estilo manual.
 */
@Composable
fun EasyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkEasyColors else LightEasyColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.brand,
            onPrimary = Color(0xFF0B1F17),
            primaryContainer = colors.brandBg,
            onPrimaryContainer = colors.brand,
            secondary = colors.brand,
            onSecondary = Color(0xFF0B1F17),
            secondaryContainer = colors.brandBg,
            onSecondaryContainer = colors.text,
            background = colors.paper,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.textMuted,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceAlt,
            surfaceContainerHighest = colors.surfaceAlt,
            outline = colors.lineStrong,
            outlineVariant = colors.line,
            error = colors.warn,
        )
    } else {
        lightColorScheme(
            primary = colors.brandSolid,
            onPrimary = Color.White,
            primaryContainer = colors.brandBg,
            onPrimaryContainer = colors.brand,
            secondary = colors.brand,
            onSecondary = Color.White,
            secondaryContainer = colors.brandBg,
            onSecondaryContainer = colors.text,
            background = colors.paper,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.textMuted,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surfaceAlt,
            outline = colors.lineStrong,
            outlineVariant = colors.line,
            error = colors.warn,
        )
    }
    val type = DefaultEasyType
    val materialType = Typography(
        bodyLarge = type.body.copy(fontSize = type.rowTitle.fontSize),
        bodyMedium = type.body,
        bodySmall = type.meta,
        labelLarge = type.button,
        labelMedium = type.meta,
        labelSmall = type.label,
        titleLarge = type.screenTitle,
        titleMedium = type.rowTitle,
        titleSmall = type.rowTitle,
        headlineSmall = type.screenTitle,
    )
    CompositionLocalProvider(
        LocalEasyColors provides colors,
        LocalEasyType provides type,
    ) {
        MaterialTheme(colorScheme = scheme, typography = materialType, content = content)
    }
}

object EasyTheme {
    val colors: EasyColors
        @Composable @ReadOnlyComposable get() = LocalEasyColors.current

    val type: EasyType
        @Composable @ReadOnlyComposable get() = LocalEasyType.current
}
