package com.easyapp.translator.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Radius
import com.easyapp.translator.ui.theme.Space

/** Aviso do primeiro uso, antes de qualquer pedido de permissão. Não fecha tocando fora. */
@Composable
internal fun PrivacyNoticeDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    EasyDialog(
        title = "Antes de começar",
        paragraphs = listOf(
            "O EasyApp traduz no próprio aparelho. O que aparece na tela, o áudio e as traduções ficam só durante a sessão e são apagados quando você para.",
            "Vamos pedir o microfone (para a conversa por voz e, se você ativar, as legendas de áudio) e as notificações (para você ver e parar a sessão a qualquer momento).",
            "A captura de tela e a janela sobre outros apps são pedidas separadamente, só quando você usar o modo Tela.",
        ),
        confirm = "Concordo e continuar",
        dismiss = "Sair",
        onConfirm = onAccept,
        onDismiss = onDecline,
        dismissOnOutside = false,
    )
}

@Composable
internal fun StartExplanationDialog(
    mode: AppMode,
    audioCaptionsSelected: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val paragraphs = when {
        mode == AppMode.CONVERSATION -> listOf(
            "O EasyApp vai usar o microfone para ouvir cada turno. Quando o aparelho tem o pacote de idioma offline, a fala é processada sem internet.",
            "Transcrições e traduções ficam só na sessão e são apagadas ao parar.",
        )
        audioCaptionsSelected -> listOf(
            "O Android vai pedir autorização para compartilhar a tela. O texto visível é lido e traduzido no aparelho.",
            "As legendas de áudio que você ativou usam o microfone e exigem Android 13 ou superior. Alguns apps bloqueiam a captura do som; nesses casos a tradução do texto continua funcionando.",
        )
        else -> listOf(
            "O Android vai pedir autorização para compartilhar a tela. O texto visível é lido e traduzido, e a tradução aparece sobre a tela, no estilo que você escolheu.",
            "Apps com proteção de conteúdo (como alguns de vídeo e banco) podem aparecer em preto e não ser traduzidos.",
        )
    }
    EasyDialog(
        title = if (mode == AppMode.SCREEN) "Traduzir a tela" else "Conversa por voz",
        paragraphs = paragraphs,
        confirm = "Continuar",
        dismiss = "Agora não",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun EasyDialog(
    title: String,
    paragraphs: List<String>,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissOnOutside: Boolean = true,
) {
    val colors = EasyTheme.colors
    AlertDialog(
        onDismissRequest = { if (dismissOnOutside) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissOnOutside, dismissOnClickOutside = dismissOnOutside),
        shape = Radius.sheet,
        containerColor = colors.surface,
        title = { Text(title, style = EasyTheme.type.screenTitle, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                paragraphs.forEach { Text(it, style = EasyTheme.type.body, color = colors.textBody) }
            }
        },
        confirmButton = { EasyButton(confirm, onConfirm) },
        dismissButton = { EasyButton(dismiss, onDismiss, kind = ButtonKind.Ghost) },
    )
}
