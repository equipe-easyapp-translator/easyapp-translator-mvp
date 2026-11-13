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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.lifecycleScope
import com.easyapp.translator.data.UserPreferencesRepository
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.AudioCaptionStateHolder
import com.easyapp.translator.session.ConversationSpeaker
import com.easyapp.translator.session.ConversationStateHolder
import com.easyapp.translator.session.SessionService
import com.easyapp.translator.session.SessionStateHolder
import com.easyapp.translator.ui.home.HomeScreen
import com.easyapp.translator.ui.theme.EasyAppTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class PermissionUiState(
    val recordAudioGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
    val notificationsRequired: Boolean = false,
    val overlayGranted: Boolean = false,
)

class MainActivity : ComponentActivity() {
    private val preferencesRepository by lazy { UserPreferencesRepository(applicationContext) }
    private var pendingOverlayMode: AppMode? = null
    private var pendingProjectionMode: AppMode? = null
    private var pendingRuntimeMode: AppMode? = null
    private var screenAudioCaptionsSelected by mutableStateOf(false)
    private var permissionUiState by mutableStateOf(PermissionUiState())
    private var showPrivacyNotice by mutableStateOf(false)

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissionState()
        val mode = pendingRuntimeMode
        pendingRuntimeMode = null
        if (mode != null) {
            when {
                permissionUiState.notificationsRequired && !permissionUiState.notificationsGranted ->
                    SessionStateHolder.stop("Ative notificações para acompanhar e parar a sessão em andamento")
                mode == AppMode.CONVERSATION && !permissionUiState.recordAudioGranted ->
                    SessionStateHolder.stop("A permissão de microfone não foi concedida")
                else -> continueStartAfterRuntimePermissions(mode)
            }
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
                .putExtra(SessionService.EXTRA_CAPTURE_INTERNAL_AUDIO, screenAudioCaptionsSelected && hasPermission(Manifest.permission.RECORD_AUDIO))
            runCatching { startForegroundService(this, startIntent) }
                .onFailure { SessionStateHolder.stop("Não foi possível iniciar o serviço da sessão") }
        } else if (mode != null) {
            SessionStateHolder.stop("A captura de tela não foi autorizada")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ícones da barra de status/navegação acompanham o tema claro/escuro do sistema;
        // a tela aplica safeDrawingPadding para não ficar por baixo delas.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        refreshPermissionState()
        showPrivacyNotice = !getSharedPreferences(PRIVACY_PREFS, MODE_PRIVATE)
            .getBoolean(PRIVACY_NOTICE_ACCEPTED, false)
        lifecycleScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                SessionStateHolder.updateLanguages(preferences.sourceLanguage, preferences.targetLanguage)
            }
        }
        setContent {
            val preferences by preferencesRepository.preferences.collectAsState(
                initial = com.easyapp.translator.data.UserPreferences.DEFAULT,
            )
            val session by SessionStateHolder.snapshot.collectAsState()
            val conversation by ConversationStateHolder.state.collectAsState()
            val audioCaptions by AudioCaptionStateHolder.state.collectAsState()

            EasyAppTheme {
                HomeScreen(
                    preferences = preferences,
                    session = session,
                    conversation = conversation,
                    audioCaptions = audioCaptions,
                    permissionState = permissionUiState,
                    screenAudioCaptionsSelected = screenAudioCaptionsSelected,
                    showPrivacyNotice = showPrivacyNotice,
                    onModeSelected = { mode -> lifecycleScope.launch { preferencesRepository.setPreferredMode(mode) } },
                    onLanguagesSelected = { source, target ->
                        SessionStateHolder.updateLanguages(source, target)
                        lifecycleScope.launch { preferencesRepository.setLanguages(source, target) }
                    },
                    onQualitySelected = { quality -> lifecycleScope.launch { preferencesRepository.setQualityMode(quality) } },
                    onStartRequested = ::requestSessionStart,
                    onPauseRequested = { sendSessionAction(SessionService.ACTION_PAUSE) },
                    onResumeRequested = { sendSessionAction(SessionService.ACTION_RESUME) },
                    onStopRequested = { sendSessionAction(SessionService.ACTION_STOP) },
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onRequestRuntimePermissions = { requestRuntimePermissions() },
                    onPrivacyNoticeAccepted = {
                        getSharedPreferences(PRIVACY_PREFS, MODE_PRIVATE).edit()
                            .putBoolean(PRIVACY_NOTICE_ACCEPTED, true).apply()
                        showPrivacyNotice = false
                        requestRuntimePermissions()
                    },
                    onPrivacyNoticeDeclined = { finish() },
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
                    onClearAudioCaptions = { sendSessionAction(SessionService.ACTION_CLEAR_AUDIO_HISTORY) },
                    onScreenAudioCaptionsSelected = { selected -> screenAudioCaptionsSelected = selected },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        val overlayMode = pendingOverlayMode
        if (overlayMode != null && Settings.canDrawOverlays(this)) {
            pendingOverlayMode = null
            continueStartAfterRuntimePermissions(overlayMode)
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
        continueStart(mode)
    }

    private fun continueStart(mode: AppMode) {
        val missing = missingRuntimePermissions()
        if (missing.isNotEmpty()) {
            requestRuntimePermissions(mode)
        } else {
            continueStartAfterRuntimePermissions(mode)
        }
    }

    private fun continueStartAfterRuntimePermissions(mode: AppMode) {
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
                    SessionStateHolder.stop("Conceda acesso ao microfone para iniciar a conversa por voz")
                } else {
                    launchSessionService(mode)
                }
            }
        }
    }

    private fun requestRuntimePermissions(mode: AppMode? = null) {
        refreshPermissionState()
        val missing = missingRuntimePermissions()
        if (missing.isEmpty()) return
        pendingRuntimeMode = mode
        runtimePermissions.launch(missing.toTypedArray())
    }

    private fun missingRuntimePermissions(): List<String> = buildList {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPermissionState() {
        permissionUiState = PermissionUiState(
            recordAudioGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = Build.VERSION.SDK_INT < 33 || hasPermission(Manifest.permission.POST_NOTIFICATIONS),
            notificationsRequired = Build.VERSION.SDK_INT >= 33,
            overlayGranted = Settings.canDrawOverlays(this),
        )
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

    companion object {
        private const val PRIVACY_PREFS = "first_run_consent"
        private const val PRIVACY_NOTICE_ACCEPTED = "permission_privacy_notice_accepted"
    }
}
