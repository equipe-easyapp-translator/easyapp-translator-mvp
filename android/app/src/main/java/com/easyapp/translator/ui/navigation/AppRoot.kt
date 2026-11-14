package com.easyapp.translator.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.easyapp.translator.R
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.SessionStatus
import com.easyapp.translator.ui.AppActions
import com.easyapp.translator.ui.AppUiState
import com.easyapp.translator.ui.conversation.ConversationPage
import com.easyapp.translator.ui.home.PrivacyNoticeDialog
import com.easyapp.translator.ui.languages.LanguagePage
import com.easyapp.translator.ui.languages.LanguageSide
import com.easyapp.translator.ui.screen.ScreenPage
import com.easyapp.translator.ui.settings.ClaudePage
import com.easyapp.translator.ui.settings.SettingsPage
import com.easyapp.translator.ui.theme.EasyTheme
import com.easyapp.translator.str

/** Páginas de primeiro nível, na barra inferior (FR-028). */
enum class Tab(private val labelRes: Int, val icon: Int) {
    SCREEN(R.string.tab_screen, R.drawable.ic_ui_smartphone),
    CONVERSATION(R.string.tab_conversation, R.drawable.ic_ui_messages),
    SETTINGS(R.string.tab_settings, R.drawable.ic_ui_settings),
    ;

    val label: String get() = str(labelRes)
}

/** Páginas secundárias, abertas por cima de uma aba e fechadas com Voltar. */
private sealed interface SubPage {
    data class Languages(val title: String, val side: LanguageSide) : SubPage
    data object Claude : SubPage
}

/**
 * Raiz da interface (T059). A navegação é um estado simples: a aba atual e uma
 * pilha de páginas secundárias. Assim não há dependência nova, e o botão Voltar
 * fecha a página de cima antes de sair do app.
 */
@Composable
fun AppRoot(state: AppUiState, actions: AppActions) {
    val colors = EasyTheme.colors
    val session = state.session
    var tab by rememberSaveable {
        mutableStateOf(if (state.preferences.preferredMode == AppMode.CONVERSATION) Tab.CONVERSATION else Tab.SCREEN)
    }
    var stack by remember { mutableStateOf(listOf<SubPage>()) }
    val top = stack.lastOrNull()
    var lastTop by remember { mutableStateOf<SubPage?>(null) }
    LaunchedEffect(top) { if (top != null) lastTop = top }
    val push: (SubPage) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { stack = stack.dropLast(1) }

    // Uma sessão que começa (pelo app ou pela notificação) leva para a página do seu modo.
    LaunchedEffect(session.mode, session.status) {
        if (session.status != SessionStatus.IDLE) {
            when (session.mode) {
                AppMode.SCREEN -> tab = Tab.SCREEN
                AppMode.CONVERSATION -> tab = Tab.CONVERSATION
                null -> Unit
            }
        }
    }
    BackHandler(enabled = top != null) { pop() }

    val conversationActive = session.mode == AppMode.CONVERSATION && session.status != SessionStatus.IDLE
    // Durante a conversa, a página ocupa a tela toda: a barra inferior atrapalharia o modo frente a frente.
    val showBottomBar = top == null && !(tab == Tab.CONVERSATION && conversationActive)

    Scaffold(
        containerColor = colors.paper,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                    Tab.entries.forEach { item ->
                        val live = session.status != SessionStatus.IDLE && (
                            (item == Tab.SCREEN && session.mode == AppMode.SCREEN) ||
                                (item == Tab.CONVERSATION && session.mode == AppMode.CONVERSATION)
                            )
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = {
                                tab = item
                                when (item) {
                                    Tab.SCREEN -> actions.rememberMode(AppMode.SCREEN)
                                    Tab.CONVERSATION -> actions.rememberMode(AppMode.CONVERSATION)
                                    Tab.SETTINGS -> Unit
                                }
                            },
                            icon = {
                                BadgedBox(badge = { if (live) Badge(containerColor = colors.brand) }) {
                                    Icon(painterResource(item.icon), contentDescription = null)
                                }
                            },
                            label = { Text(item.label, style = EasyTheme.type.label.copy(letterSpacing = EasyTheme.type.meta.letterSpacing)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.brand,
                                selectedTextColor = colors.text,
                                indicatorColor = colors.brandBg,
                                unselectedIconColor = colors.textMuted,
                                unselectedTextColor = colors.textMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(colors.paper)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { current ->
                when (current) {
                    Tab.SCREEN -> ScreenPage(
                        state = state,
                        actions = actions,
                        onPickSource = { push(SubPage.Languages(str(R.string.lang_page_from), LanguageSide.SOURCE)) },
                        onPickTarget = { push(SubPage.Languages(str(R.string.lang_page_to), LanguageSide.TARGET)) },
                        onOpenClaude = { push(SubPage.Claude) },
                    )
                    Tab.CONVERSATION -> ConversationPage(
                        state = state,
                        actions = actions,
                        // Na conversa, o seu idioma é o de destino e o da outra pessoa é o de origem.
                        onPickYourLanguage = { push(SubPage.Languages(str(R.string.lang_page_yours), LanguageSide.TARGET)) },
                        onPickTheirLanguage = { push(SubPage.Languages(str(R.string.lang_page_theirs), LanguageSide.SOURCE)) },
                    )
                    Tab.SETTINGS -> SettingsPage(state, actions, onOpenClaude = { push(SubPage.Claude) })
                }
            }

            AnimatedVisibility(
                visible = top != null,
                enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(180)),
            ) {
                // Na animação de saída `top` já é nulo; desenha a última página aberta.
                when (val page = top ?: lastTop) {
                    is SubPage.Languages -> LanguagePage(
                        title = page.title,
                        side = page.side,
                        source = state.preferences.sourceLanguage,
                        target = state.preferences.targetLanguage,
                        onSelected = { source, target ->
                            actions.setLanguages(source, target)
                            pop()
                        },
                        onBack = pop,
                    )
                    SubPage.Claude -> ClaudePage(state, actions, onBack = pop)
                    null -> Unit
                }
            }
        }
    }

    if (state.showPrivacyNotice) {
        PrivacyNoticeDialog(onAccept = actions::acceptPrivacyNotice, onDecline = actions::declinePrivacyNotice)
    }
}
