package com.easyapp.translator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Escala de espaçamento e raios do Panela (`app_spacing.dart`). */
object Space {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp

    /** Alvo mínimo de toque (botão, linha de lista). */
    val minTouch = 48.dp
}

object Radius {
    val chip = RoundedCornerShape(11.dp)
    val input = RoundedCornerShape(14.dp)
    val card = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(15.dp)
    val sheet = RoundedCornerShape(32.dp)
    val icon = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(percent = 50)
}
