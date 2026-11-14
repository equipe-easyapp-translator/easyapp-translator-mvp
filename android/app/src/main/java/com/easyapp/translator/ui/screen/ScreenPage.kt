package com.easyapp.translator.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.easyapp.translator.R
import com.easyapp.translator.data.OverlayStyle
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.SessionStatus
import com.easyapp.translator.ui.AppActions
import com.easyapp.translator.ui.AppUiState
import com.easyapp.translator.ui.components.ButtonKind
import com.easyapp.translator.ui.components.EasyButton
import com.easyapp.translator.ui.components.EasyCard
import com.easyapp.translator.ui.components.Hint
import com.easyapp.translator.ui.components.IllustratedChoice
import com.easyapp.translator.ui.components.InfoNote
import com.easyapp.translator.ui.components.InkPanel
import com.easyapp.translator.ui.components.LanguageBar
import com.easyapp.translator.ui.components.LiveDot
import com.easyapp.translator.ui.components.PageHeader
import com.easyapp.translator.ui.components.PageScaffold
import com.easyapp.translator.ui.components.SectionLabel
import com.easyapp.translator.ui.components.SegmentedControl
import com.easyapp.translator.ui.components.SettingRow
import com.easyapp.translator.ui.components.StatusBadge
import com.easyapp.translator.ui.components.Tone
import com.easyapp.translator.ui.components.floatingIllustration
import com.easyapp.translator.ui.components.inPlaceIllustration
import com.easyapp.translator.ui.home.AudioCaptionsCard
import com.easyapp.translator.ui.home.ClaudeUsageCard
import com.easyapp.translator.ui.home.PermissionsCard
import com.easyapp.translator.ui.home.ScreenAudioCaptionsOption
import com.easyapp.translator.ui.home.StartExplanationDialog
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.ui.theme.Space
import com.easyapp.translator.str

/** Página do modo Tela (T060). Sem sessão: preparação. Com sessão: painel da sessão. */
@Composable
fun ScreenPage(
    state: AppUiState,
    actions: AppActions,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onOpenClaude: () -> Unit,
) {
    val session = state.session
    val active = session.mode == AppMode.SCREEN && session.status != SessionStatus.IDLE
    val busyElsewhere = session.mode == AppMode.CONVERSATION && session.status != SessionStatus.IDLE
    var showStart by remember { mutableStateOf(false) }
    val prefs = state.preferences
    val audioCaptions = state.screenAudioCaptionsSelected && prefs.overlayStyle == OverlayStyle.FLOATING

    val startAction: @Composable ColumnScope.() -> Unit = {
        if (busyElsewhere) Hint(str(R.string.screen_busy), color = EasyTheme.colors.warn)
        EasyButton(
            str(R.string.screen_start),
            { showStart = true },
            Modifier.fillMaxWidth(),
            enabled = !busyElsewhere,
            icon = R.drawable.ic_ui_play,
        )
    }
    PageScaffold(bottomAction = if (active) null else startAction) {
        if (active) {
            ScreenSessionPanel(state, actions)
        } else {
            PageHeader(
                title = str(R.string.screen_title),
                subtitle = str(R.string.screen_subtitle),
                overline = str(R.string.screen_overline),
            )
        }

        LanguageBar(
            leftTitle = str(R.string.lang_page_from),
            leftCode = prefs.sourceLanguage,
            rightTitle = str(R.string.lang_to_short),
            rightCode = prefs.targetLanguage,
            onPickLeft = onPickSource,
            onPickRight = onPickTarget,
            onSwap = { actions.setLanguages(prefs.targetLanguage, prefs.sourceLanguage) },
        )

        if (active) {
            if (state.claude.usable) ClaudeUsageCard(state.claudeUsage)
            if (audioCaptions) AudioCaptionsCard(state.audioCaptions, session.status == SessionStatus.ACTIVE, actions::clearAudioCaptions)
            SectionLabel(str(R.string.section_quality))
            QualitySelector(prefs.qualityMode, actions::setQuality)
            Hint(str(R.string.screen_next_session))
            return@PageScaffold
        }

        SectionLabel(str(R.string.section_style))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IllustratedChoice(
                title = OverlayStyle.IN_PLACE.label,
                description = str(R.string.style_in_place_desc),
                selected = prefs.overlayStyle == OverlayStyle.IN_PLACE,
                onClick = { actions.setOverlayStyle(OverlayStyle.IN_PLACE) },
                modifier = Modifier.weight(1f),
            ) { inPlaceIllustration(it) }
            IllustratedChoice(
                title = OverlayStyle.FLOATING.label,
                description = str(R.string.style_floating_desc),
                selected = prefs.overlayStyle == OverlayStyle.FLOATING,
                onClick = { actions.setOverlayStyle(OverlayStyle.FLOATING) },
                modifier = Modifier.weight(1f),
            ) { floatingIllustration(it) }
        }
        if (prefs.overlayStyle == OverlayStyle.FLOATING) {
            ScreenAudioCaptionsOption(selected = state.screenAudioCaptionsSelected, onSelected = actions::setScreenAudioCaptions)
        }

        SectionLabel(str(R.string.section_quality))
        QualitySelector(prefs.qualityMode, actions::setQuality)

        SectionLabel(str(R.string.section_ai))
        EasyCard {
            SettingRow(
                title = str(R.string.claude_row_title),
                subtitle = when {
                    state.claude.usable && prefs.qualityMode == QualityMode.QUALITY -> str(R.string.claude_row_on)
                    state.claude.usable -> str(R.string.claude_row_configured)
                    state.claude.hasKey -> str(R.string.claude_row_off)
                    else -> str(R.string.claude_row_optional)
                },
                icon = R.drawable.ic_ui_sparkle,
                onClick = onOpenClaude,
            )
        }

        val missing = (prefs.overlayStyle == OverlayStyle.FLOATING && audioCaptions && !state.permissions.recordAudioGranted) ||
            !state.permissions.overlayGranted ||
            (state.permissions.notificationsRequired && !state.permissions.notificationsGranted)
        if (missing) {
            SectionLabel(str(R.string.section_before))
            PermissionsCard(state.permissions, AppMode.SCREEN, actions::requestPermissions, actions::openOverlaySettings)
        }

        InfoNote(
            title = str(R.string.screen_private_title),
            body = str(R.string.screen_private_body),
            icon = R.drawable.ic_ui_shield,
        )
    }

    if (showStart) {
        StartExplanationDialog(
            mode = AppMode.SCREEN,
            audioCaptionsSelected = audioCaptions,
            onConfirm = {
                showStart = false
                actions.start(AppMode.SCREEN)
            },
            onDismiss = { showStart = false },
        )
    }
}

@Composable
private fun QualitySelector(selected: QualityMode, onSelect: (QualityMode) -> Unit) {
    EasyCard {
        SegmentedControl(
            options = QualityMode.entries,
            selected = selected,
            onSelect = onSelect,
            optionDescription = { str(R.string.cd_quality, it.label) },
        ) { it.label }
        Hint(
            when (selected) {
                QualityMode.ECONOMY -> str(R.string.quality_economy_desc)
                QualityMode.BALANCED -> str(R.string.quality_balanced_desc)
                QualityMode.QUALITY -> str(R.string.quality_quality_desc)
            },
        )
    }
}

/** Painel da sessão ativa: estado ao vivo, como usar e os controles principais. */
@Composable
private fun ScreenSessionPanel(state: AppUiState, actions: AppActions) {
    val colors = EasyTheme.colors
    val session = state.session
    val paused = session.status == SessionStatus.PAUSED
    val inPlace = state.preferences.overlayStyle == OverlayStyle.IN_PLACE
    InkPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(if (paused) colors.warn else BrandOnInk)
            Spacer(Modifier.width(Space.xs))
            Text(
                if (paused) str(R.string.panel_paused) else str(R.string.panel_translating),
                style = EasyTheme.type.label,
                color = colors.mutedOnInk,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(if (inPlace) str(R.string.badge_in_place) else str(R.string.badge_window), Tone.Neutral)
        }
        Text(
            if (inPlace) str(R.string.panel_inplace_title) else str(R.string.panel_floating_title),
            style = EasyTheme.type.screenTitle,
            color = colors.textOnInk,
        )
        Text(
            if (inPlace) {
                str(R.string.panel_inplace_body)
            } else {
                str(R.string.panel_floating_body)
            },
            style = EasyTheme.type.body,
            color = colors.mutedOnInk,
        )
        if (session.message.isNotBlank()) Text(session.message, style = EasyTheme.type.meta, color = colors.mutedOnInk)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (paused) {
                EasyButton(str(R.string.action_resume), actions::resume, Modifier.weight(1f), icon = R.drawable.ic_ui_play)
            } else {
                EasyButton(str(R.string.action_pause), actions::pause, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = R.drawable.ic_ui_pause)
            }
            EasyButton(str(R.string.action_stop), actions::stop, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = R.drawable.ic_ui_stop)
        }
    }
}

private val BrandOnInk = androidx.compose.ui.graphics.Color(0xFF4EC294)
