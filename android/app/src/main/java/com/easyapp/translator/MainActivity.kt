package com.easyapp.translator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.lifecycleScope
import com.easyapp.translator.data.UserPreferencesRepository
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.ConversationSpeaker
import com.easyapp.translator.session.ConversationStateHolder
import com.easyapp.translator.session.SessionService
import com.easyapp.translator.session.SessionStateHolder
import com.easyapp.translator.ui.EasyAppScreen
import com.easyapp.translator.ui.theme.EasyAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val preferencesRepository by lazy { UserPreferencesRepository(applicationContext) }
    private var pendingOverlayMode: AppMode? = null
    private var pendingProjectionMode: AppMode? = null
    private var pendingNotificationMode: AppMode? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val mode = pendingNotificationMode
        pendingNotificationMode = null
        if (granted && mode != null) {
            continueStart(mode)
        } else if (mode != null) {
            SessionStateHolder.stop("Ative notificações para acompanhar e parar a sessão em andamento")
        }
    }

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val mode = pendingProjectionMode
        pendingProjectionMode = null
        if (granted && mode == AppMode.CONVERSATION) {
            launchSessionService(mode)
        } else if (mode != null) {
            SessionStateHolder.stop("A permissão de microfone não foi concedida")
        }
    }

    private val projectionPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val mode = pendingProjectionMode
        pendingProjectionMode = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null && mode == AppMode.SCREEN) {
            val startIntent = SessionService.startIntent(this, mode)
                .putExtra(SessionService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(SessionService.EXTRA_RESULT_DATA, result.data)
            runCatching { startForegroundService(this, startIntent) }
                .onFailure { SessionStateHolder.stop("Não foi possível iniciar o serviço da sessão") }
        } else if (mode != null) {
            SessionStateHolder.stop("A captura de tela não foi autorizada")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by preferencesRepository.preferences.collectAsState(
                initial = com.easyapp.translator.data.UserPreferences.DEFAULT,
            )
            val session by SessionStateHolder.snapshot.collectAsState()
            val conversation by ConversationStateHolder.state.collectAsState()

            EasyAppTheme {
                EasyAppScreen(
                    preferences = preferences,
                    session = session,
                    conversation = conversation,
                    onModeSelected = { mode -> lifecycleScope.launch { preferencesRepository.setPreferredMode(mode) } },
                    onLanguageSelected = { language -> lifecycleScope.launch { preferencesRepository.setSourceLanguage(language) } },
                    onQualitySelected = { quality -> lifecycleScope.launch { preferencesRepository.setQualityMode(quality) } },
                    onStartRequested = ::requestSessionStart,
                    onPauseRequested = { sendSessionAction(SessionService.ACTION_PAUSE) },
                    onResumeRequested = { sendSessionAction(SessionService.ACTION_RESUME) },
                    onStopRequested = { sendSessionAction(SessionService.ACTION_STOP) },
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onConversationSpeakerSelected = { speaker ->
                        sendConversationValue(SessionService.ACTION_CONVERSATION_SPEAKER, SessionService.EXTRA_CONVERSATION_SPEAKER, speaker.name)
                    },
                    onConversationListen = { sendSessionAction(SessionService.ACTION_CONVERSATION_LISTEN) },
                    onConversationCancel = { sendSessionAction(SessionService.ACTION_CONVERSATION_CANCEL) },
                    onConversationRepeat = { sendSessionAction(SessionService.ACTION_CONVERSATION_REPEAT) },
                    onConversationVoiceSelected = { voiceId ->
                        sendConversationValue(SessionService.ACTION_CONVERSATION_VOICE, SessionService.EXTRA_CONVERSATION_VOICE, voiceId)
                    },
                    onConversationRateChanged = { rate -> sendConversationFloat(SessionService.ACTION_CONVERSATION_RATE, rate) },
                    onConversationVolumeChanged = { volume -> sendConversationFloat(SessionService.ACTION_CONVERSATION_VOLUME, volume) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val overlayMode = pendingOverlayMode
        if (overlayMode != null && Settings.canDrawOverlays(this)) {
            pendingOverlayMode = null
            requestProjectionConsent(overlayMode)
        }

        val session = SessionStateHolder.snapshot.value
        if (session.status != com.easyapp.translator.session.SessionStatus.IDLE) {
            when (session.mode) {
                AppMode.SCREEN -> if (!Settings.canDrawOverlays(this)) {
                    sendSessionAction(SessionService.ACTION_STOP)
                }
                AppMode.CONVERSATION -> if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    sendSessionAction(SessionService.ACTION_STOP)
                }
                null -> Unit
            }
        }
    }

    private fun requestSessionStart(mode: AppMode) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            pendingNotificationMode = mode
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        continueStart(mode)
    }

    private fun continueStart(mode: AppMode) {
        when (mode) {
            AppMode.SCREEN -> {
                if (!Settings.canDrawOverlays(this)) {
                    pendingOverlayMode = mode
                    openOverlaySettings()
                } else {
                    requestProjectionConsent(mode)
                }
            }
            AppMode.CONVERSATION -> {
                if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    pendingProjectionMode = mode
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    launchSessionService(mode)
                }
            }
        }
    }

    private fun requestProjectionConsent(mode: AppMode) {
        pendingProjectionMode = mode
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionPermission.launch(manager.createScreenCaptureIntent())
    }

    private fun launchSessionService(mode: AppMode) {
        runCatching { startForegroundService(this, SessionService.startIntent(this, mode)) }
            .onFailure { SessionStateHolder.stop("Não foi possível iniciar o serviço da sessão") }
    }

    private fun sendSessionAction(action: String) {
        runCatching { startService(SessionService.controlIntent(this, action)) }
            .onFailure { SessionStateHolder.stop("O serviço da sessão já foi encerrado") }
    }

    private fun sendConversationValue(action: String, key: String, value: String) {
        val intent = SessionService.conversationIntent(this, action).putExtra(key, value)
        runCatching { startService(intent) }
            .onFailure { SessionStateHolder.stop("O serviço da conversa já foi encerrado") }
    }

    private fun sendConversationFloat(action: String, value: Float) {
        val intent = SessionService.conversationIntent(this, action)
            .putExtra(SessionService.EXTRA_CONVERSATION_VALUE, value)
        runCatching { startService(intent) }
            .onFailure { SessionStateHolder.stop("O serviço da conversa já foi encerrado") }
    }

    private fun openOverlaySettings() {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(settingsIntent) }
            .onFailure { SessionStateHolder.stop("Não foi possível abrir as permissões de sobreposição") }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
