package com.easyapp.translator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EasyAppColors = lightColorScheme(
    primary = Color(0xFF26734E),
    onPrimary = Color.White,
    secondary = Color(0xFF52735B),
    background = Color(0xFFF5F4EF),
    surface = Color.White,
    onSurface = Color(0xFF202820),
    onBackground = Color(0xFF202820),
)

@Composable
fun EasyAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EasyAppColors,
        content = content,
    )
}
