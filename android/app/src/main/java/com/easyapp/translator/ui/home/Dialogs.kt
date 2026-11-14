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
import com.easyapp.translator.R
import com.easyapp.translator.str

/** Aviso do primeiro uso, antes de qualquer pedido de permissão. Não fecha tocando fora. */
@Composable
internal fun PrivacyNoticeDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    EasyDialog(
        title = str(R.string.section_before),
        paragraphs = listOf(
            str(R.string.privacy_p1),
            str(R.string.privacy_p2),
            str(R.string.privacy_p3),
        ),
        confirm = str(R.string.privacy_accept),
        dismiss = str(R.string.action_exit),
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
            str(R.string.start_conv_p1),
            str(R.string.start_conv_p2),
        )
        audioCaptionsSelected -> listOf(
            str(R.string.start_screen_p1_audio),
            str(R.string.start_screen_p2_audio),
        )
        else -> listOf(
            str(R.string.start_screen_p1),
            str(R.string.start_screen_p2),
        )
    }
    EasyDialog(
        title = if (mode == AppMode.SCREEN) str(R.string.screen_title) else str(R.string.mode_conversation_title),
        paragraphs = paragraphs,
        confirm = str(R.string.action_continue),
        dismiss = str(R.string.action_not_now),
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
