package com.easyapp.translator.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.easyapp.translator.ui.theme.EasyColors

/*
 * Ilustrações dos cartões de escolha. Cada uma desenha um celular estilizado
 * no espaço disponível, com as cores do tema; não há texto, só formas, para
 * funcionar em qualquer idioma.
 */

/** Celular centralizado; devolve o retângulo interno da tela (origem e tamanho). */
private fun DrawScope.phone(colors: EasyColors): Pair<Offset, Size> {
    val phoneHeight = size.height
    val phoneWidth = (phoneHeight * 0.56f).coerceAtMost(size.width)
    val left = (size.width - phoneWidth) / 2f
    val radius = phoneWidth * 0.14f
    drawRoundRect(colors.surface, Offset(left, 0f), Size(phoneWidth, phoneHeight), CornerRadius(radius))
    drawRoundRect(colors.lineStrong, Offset(left, 0f), Size(phoneWidth, phoneHeight), CornerRadius(radius), style = Stroke(width = phoneWidth * 0.035f))
    val inset = phoneWidth * 0.08f
    return Offset(left + inset, inset * 1.4f) to Size(phoneWidth - inset * 2, phoneHeight - inset * 2.8f)
}

private fun DrawScope.bar(color: Color, x: Float, y: Float, width: Float, height: Float) {
    drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(height / 2f))
}

/** "No lugar do texto": balões de conversa em que algumas linhas foram trocadas (em verde). */
fun DrawScope.inPlaceIllustration(colors: EasyColors) {
    val (origin, screen) = phone(colors)
    val line = screen.height * 0.035f
    val bubbleWidth = screen.width * 0.72f
    var y = origin.y + screen.height * 0.05f
    repeat(3) { index ->
        val incoming = index % 2 == 0
        val bubbleX = if (incoming) origin.x else origin.x + screen.width - bubbleWidth
        val bubbleHeight = screen.height * 0.22f
        drawRoundRect(
            if (incoming) colors.surfaceAlt else colors.brandBg,
            Offset(bubbleX, y),
            Size(bubbleWidth, bubbleHeight),
            CornerRadius(line * 2f),
        )
        val pad = bubbleWidth * 0.1f
        // A primeira linha de cada balão aparece "traduzida": coberta pela cor da marca.
        bar(colors.brand, bubbleX + pad, y + bubbleHeight * 0.28f, bubbleWidth * 0.62f, line * 1.3f)
        bar(colors.lineStrong, bubbleX + pad, y + bubbleHeight * 0.6f, bubbleWidth * 0.42f, line)
        y += bubbleHeight + screen.height * 0.07f
    }
}

/** "Janela flutuante": texto original na tela e um painel escuro com a tradução por cima. */
fun DrawScope.floatingIllustration(colors: EasyColors) {
    val (origin, screen) = phone(colors)
    val line = screen.height * 0.035f
    var y = origin.y + screen.height * 0.06f
    repeat(6) { index ->
        bar(colors.lineStrong, origin.x, y, screen.width * (if (index % 2 == 0) 0.9f else 0.65f), line)
        y += line * 2.6f
    }
    val panelTop = origin.y + screen.height * 0.58f
    val panelHeight = screen.height * 0.36f
    drawRoundRect(colors.ink, Offset(origin.x - screen.width * 0.04f, panelTop), Size(screen.width * 1.08f, panelHeight), CornerRadius(line * 2.4f))
    bar(colors.textOnInk, origin.x + screen.width * 0.06f, panelTop + panelHeight * 0.28f, screen.width * 0.7f, line * 1.2f)
    bar(colors.mutedOnInk, origin.x + screen.width * 0.06f, panelTop + panelHeight * 0.56f, screen.width * 0.5f, line)
}

/** "Frente a frente": a tela dividida, a metade de cima virada para a outra pessoa. */
fun DrawScope.faceToFaceIllustration(colors: EasyColors) {
    val (origin, screen) = phone(colors)
    val half = screen.height / 2f
    val line = screen.height * 0.035f
    drawRoundRect(colors.surfaceAlt, origin, Size(screen.width, half - line), CornerRadius(line * 1.6f))
    drawRoundRect(colors.brandBg, Offset(origin.x, origin.y + half + line), Size(screen.width, half - line), CornerRadius(line * 1.6f))
    val mic = screen.width * 0.13f
    // Metade de cima: botão perto da borda de cima, linhas alinhadas à direita (texto "de cabeça para baixo").
    drawCircle(colors.textMuted, mic, Offset(origin.x + screen.width / 2f, origin.y + mic * 1.6f))
    bar(colors.lineStrong, origin.x + screen.width * 0.3f, origin.y + half * 0.62f, screen.width * 0.6f, line)
    bar(colors.lineStrong, origin.x + screen.width * 0.5f, origin.y + half * 0.62f + line * 2.2f, screen.width * 0.4f, line)
    // Metade de baixo: linhas à esquerda e botão perto da borda de baixo.
    bar(colors.brand, origin.x + screen.width * 0.1f, origin.y + half + half * 0.22f, screen.width * 0.6f, line * 1.2f)
    bar(colors.lineStrong, origin.x + screen.width * 0.1f, origin.y + half + half * 0.22f + line * 2.4f, screen.width * 0.4f, line)
    drawCircle(colors.brand, mic, Offset(origin.x + screen.width / 2f, origin.y + screen.height - mic * 1.6f))
}

/** "Lado a lado": uma conversa em balões, como num app de mensagens. */
fun DrawScope.sideBySideIllustration(colors: EasyColors) {
    val (origin, screen) = phone(colors)
    val line = screen.height * 0.035f
    var y = origin.y + screen.height * 0.04f
    listOf(false, true, false, true).forEach { mine ->
        val width = screen.width * (if (mine) 0.62f else 0.7f)
        val x = if (mine) origin.x + screen.width - width else origin.x
        val height = screen.height * 0.15f
        drawRoundRect(if (mine) colors.brandBg else colors.surfaceAlt, Offset(x, y), Size(width, height), CornerRadius(line * 2f))
        bar(if (mine) colors.brand else colors.lineStrong, x + width * 0.12f, y + height * 0.4f, width * 0.6f, line)
        y += height + screen.height * 0.05f
    }
    val mic = screen.width * 0.11f
    drawCircle(colors.textMuted, mic, Offset(origin.x + screen.width * 0.28f, origin.y + screen.height - mic * 1.4f))
    drawCircle(colors.brand, mic, Offset(origin.x + screen.width * 0.72f, origin.y + screen.height - mic * 1.4f))
}
