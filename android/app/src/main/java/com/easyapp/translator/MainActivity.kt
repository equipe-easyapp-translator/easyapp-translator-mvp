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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.lifecycleScope
import com.easyapp.translator.data.ConversationLayout
import com.easyapp.translator.data.OverlayStyle
import com.easyapp.translator.data.QualityMode
import com.easyapp.translator.data.TextSize
import com.easyapp.translator.data.ThemeMode
import com.easyapp.translator.data.UserPreferences
import com.easyapp.translator.data.UserPreferencesRepository
import com.easyapp.translator.session.AppMode
import com.easyapp.translator.session.AudioCaptionStateHolder
import com.easyapp.translator.session.ConversationSpeaker
import com.easyapp.translator.session.ConversationStateHolder
import com.easyapp.translator.session.SessionService
import com.easyapp.translator.session.SessionStateHolder
import com.easyapp.translator.translation.ClaudeKeyStore
import com.easyapp.translator.translation.ClaudeResult
import com.easyapp.translator.translation.ClaudeTranslator
import com.easyapp.translator.translation.ClaudeUsageHolder
import android.app.LocaleManager
import android.os.LocaleList
import com.easyapp.translator.ui.AppActions
import com.easyapp.translator.ui.AppLanguage
import com.easyapp.translator.ui.AppUiState
import com.easyapp.translator.ui.home.ClaudeUiState
import com.easyapp.translator.ui.navigation.AppRoot
import com.easyapp.translator.ui.theme.EasyAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.easyapp.translator.R
import com.easyapp.translator.str

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
    private val claudeKeyStore by lazy { ClaudeKeyStore(applicationContext) }
    private var claudeUiState by mutableStateOf(ClaudeUiState())
    private var currentOverlayStyle = OverlayStyle.IN_PLACE

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissionState()
        val mode = pendingRuntimeMode
        pendingRuntimeMode = null
        if (mode != null) {
            when {
                permissionUiState.notificationsRequired && !permissionUiState.notificationsGranted ->
                    SessionStateHolder.stop(str(R.string.main_need_notifications))
                mode == AppMode.CONVERSATION && !permissionUiState.recordAudioGranted ->
                    SessionStateHolder.stop(str(R.string.main_mic_denied))
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
                .putExtra(
                    SessionService.EXTRA_CAPTURE_INTERNAL_AUDIO,
                    // Legendas de áudio dependem da janela flutuante; no estilo "No lugar do texto" ficam desligadas.
                    screenAudioCaptionsSelected &&
                        currentOverlayStyle == OverlayStyle.FLOATING &&
                        hasPermission(Manifest.permission.RECORD_AUDIO),
                )
            runCatching { startForegroundService(this, startIntent) }
                .onFailure { SessionStateHolder.stop(str(R.string.main_service_failed)) }
        } else if (mode != null) {
            SessionStateHolder.stop(str(R.string.main_capture_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ícones da barra de status/navegação acompanham o tema claro/escuro do sistema;
        // a tela aplica safeDrawingPadding para não ficar por baixo delas.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        refreshPermissionState()
        refreshClaudeState()
        showPrivacyNotice = !getSharedPreferences(PRIVACY_PREFS, MODE_PRIVATE)
            .getBoolean(PRIVACY_NOTICE_ACCEPTED, false)
        lifecycleScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                SessionStateHolder.updateLanguages(preferences.sourceLanguage, preferences.targetLanguage)
                currentOverlayStyle = preferences.overlayStyle
            }
        }
        val appVersion = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        // A tela é recriada quando o idioma do app muda, então basta ler uma vez aqui.
        val appLanguage = currentAppLanguage()
        setContent {
            val preferences by preferencesRepository.preferences.collectAsState(initial = UserPreferences.DEFAULT)
            val session by SessionStateHolder.snapshot.collectAsState()
            val conversation by ConversationStateHolder.state.collectAsState()
            val audioCaptions by AudioCaptionStateHolder.state.collectAsState()
            val claudeUsage by ClaudeUsageHolder.usage.collectAsState()
            val dark = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // O tema do app pode ser diferente do sistema; os ícones das barras precisam acompanhar.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            EasyAppTheme(darkTheme = dark, textScale = preferences.appTextSize.scale) {
                AppRoot(
                    state = AppUiState(
                        preferences = preferences,
                        session = session,
                        conversation = conversation,
                        audioCaptions = audioCaptions,
                        permissions = permissionUiState,
                        screenAudioCaptionsSelected = screenAudioCaptionsSelected,
                        showPrivacyNotice = showPrivacyNotice,
                        claude = claudeUiState,
                        claudeUsage = claudeUsage,
                        appVersion = appVersion,
                        appLanguage = appLanguage,
                        appLanguageSelectable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    ),
                    actions = appActions,
                )
            }
        }
    }

    /** Ações da interface (T059): cada uma vira uma preferência salva ou um comando para o serviço. */
    private val appActions = object : AppActions {
        override fun rememberMode(mode: AppMode) {
            lifecycleScope.launch { preferencesRepository.setPreferredMode(mode) }
        }

        override fun setLanguages(source: String, target: String) {
            SessionStateHolder.updateLanguages(source, target)
            lifecycleScope.launch { preferencesRepository.setLanguages(source, target) }
        }

        override fun setQuality(mode: QualityMode) {
            lifecycleScope.launch { preferencesRepository.setQualityMode(mode) }
        }

        override fun setOverlayStyle(style: OverlayStyle) {
            lifecycleScope.launch { preferencesRepository.setOverlayStyle(style) }
        }

        override fun setThemeMode(mode: ThemeMode) {
            lifecycleScope.launch { preferencesRepository.setThemeMode(mode) }
        }

        override fun setAppTextSize(size: TextSize) {
            lifecycleScope.launch { preferencesRepository.setAppTextSize(size) }
        }

        override fun setTranslationTextSize(size: TextSize) {
            lifecycleScope.launch { preferencesRepository.setTranslationTextSize(size) }
        }

        override fun setConversationLayout(layout: ConversationLayout) {
            lifecycleScope.launch { preferencesRepository.setConversationLayout(layout) }
        }

        override fun setScreenAudioCaptions(enabled: Boolean) {
            screenAudioCaptionsSelected = enabled
        }

        override fun resetPreferences() {
            lifecycleScope.launch { preferencesRepository.resetAll() }
        }

        /** O Android 13+ guarda a escolha e recria a tela no novo idioma. */
        override fun setAppLanguage(language: AppLanguage) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val locales = if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
            runCatching { getSystemService(LocaleManager::class.java).applicationLocales = locales }
        }

        override fun start(mode: AppMode) = requestSessionStart(mode)
        override fun pause() = sendSessionAction(SessionService.ACTION_PAUSE)
        override fun resume() = sendSessionAction(SessionService.ACTION_RESUME)
        override fun stop() = sendSessionAction(SessionService.ACTION_STOP)
        override fun requestPermissions() = requestRuntimePermissions()
        override fun openOverlaySettings() = this@MainActivity.openOverlaySettings()

        override fun acceptPrivacyNotice() {
            getSharedPreferences(PRIVACY_PREFS, MODE_PRIVATE).edit()
                .putBoolean(PRIVACY_NOTICE_ACCEPTED, true).apply()
            showPrivacyNotice = false
            requestRuntimePermissions()
        }

        override fun declinePrivacyNotice() = finish()

        override fun speak(speaker: ConversationSpeaker) {
            // O serviço processa na ordem: primeiro troca quem fala, depois começa a ouvir.
            sendConversationValue(SessionService.ACTION_CONVERSATION_SPEAKER, SessionService.EXTRA_CONVERSATION_SPEAKER, speaker.name)
            sendSessionAction(SessionService.ACTION_CONVERSATION_LISTEN)
        }

        override fun cancelListening() = sendSessionAction(SessionService.ACTION_CONVERSATION_CANCEL)
        override fun repeatTranslation() = sendSessionAction(SessionService.ACTION_CONVERSATION_REPEAT)

        override fun selectVoice(voiceId: String) =
            sendConversationValue(SessionService.ACTION_CONVERSATION_VOICE, SessionService.EXTRA_CONVERSATION_VOICE, voiceId)

        override fun setSpeechRate(rate: Float) = sendConversationFloat(SessionService.ACTION_CONVERSATION_RATE, rate)
        override fun setVolume(volume: Float) = sendConversationFloat(SessionService.ACTION_CONVERSATION_VOLUME, volume)
        override fun clearAudioCaptions() = sendSessionAction(SessionService.ACTION_CLEAR_AUDIO_HISTORY)

        override fun claudeSaveKey(key: String) = saveClaudeKey(key)

        override fun claudeAcceptConsent() {
            claudeKeyStore.consentAccepted = true
            refreshClaudeState()
        }

        override fun claudeSetEnabled(enabled: Boolean) {
            claudeKeyStore.enabled = enabled
            refreshClaudeState()
        }

        override fun claudeRemoveKey() {
            claudeKeyStore.clear()
            refreshClaudeState()
        }

        override fun claudeTest() = testClaude()
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
                    SessionStateHolder.stop(str(R.string.main_grant_mic))
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
            .onFailure { SessionStateHolder.stop(str(R.string.main_service_failed)) }
    }

    private fun sendSessionAction(action: String) {
        runCatching { startService(SessionService.controlIntent(this, action)) }
            .onFailure { SessionStateHolder.stop(str(R.string.main_service_ended)) }
    }

    private fun sendConversationValue(action: String, key: String, value: String) {
        val intent = SessionService.conversationIntent(this, action).putExtra(key, value)
        runCatching { startService(intent) }
            .onFailure { SessionStateHolder.stop(str(R.string.main_conversation_ended)) }
    }

    private fun sendConversationFloat(action: String, value: Float) {
        val intent = SessionService.conversationIntent(this, action)
            .putExtra(SessionService.EXTRA_CONVERSATION_VALUE, value)
        runCatching { startService(intent) }
            .onFailure { SessionStateHolder.stop(str(R.string.main_conversation_ended)) }
    }

    private fun openOverlaySettings() {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(settingsIntent) }
            .onFailure { SessionStateHolder.stop(str(R.string.main_overlay_settings_failed)) }
    }

    private fun currentAppLanguage(): AppLanguage {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return AppLanguage.SYSTEM
        val locales = runCatching { getSystemService(LocaleManager::class.java).applicationLocales }.getOrNull()
        if (locales == null || locales.isEmpty) return AppLanguage.SYSTEM
        return when (locales[0].language) {
            "pt" -> AppLanguage.PORTUGUESE
            "it" -> AppLanguage.ITALIAN
            "en" -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM
        }
    }

    private fun refreshClaudeState(testing: Boolean = false, testMessage: String? = null, testSucceeded: Boolean = false) {
        claudeUiState = ClaudeUiState(
            hasKey = claudeKeyStore.hasKey,
            keyHint = claudeKeyStore.hint,
            consentAccepted = claudeKeyStore.consentAccepted,
            enabled = claudeKeyStore.enabled,
            testing = testing,
            testMessage = testMessage,
            testSucceeded = testSucceeded,
        )
    }

    private fun saveClaudeKey(key: String) {
        val saved = runCatching { claudeKeyStore.save(key) }.isSuccess
        refreshClaudeState(testMessage = if (saved) null else str(R.string.claude_key_save_failed))
        if (saved) testClaude()
    }

    /**
     * Uma tradução mínima para confirmar a chave; custa uma fração de centavo.
     * Traduz para o idioma da interface, para o resultado fazer sentido para quem lê.
     */
    private fun testClaude() {
        val key = claudeKeyStore.load()
        if (key == null) {
            refreshClaudeState(testMessage = str(R.string.claude_key_read_failed))
            return
        }
        refreshClaudeState(testing = true)
        val uiLanguage = resources.configuration.locales[0].language
        val (sample, from, to) = when (uiLanguage) {
            "en" -> Triple("Olá, tudo bem?", "pt", "en")
            "it" -> Triple("Hello!", "en", "it")
            else -> Triple("Hello!", "en", "pt")
        }
        lifecycleScope.launch {
            val (message, ok) = withContext(Dispatchers.IO) {
                runCatching {
                    val translator = ClaudeTranslator(key)
                    try {
                        when (val result = translator.translateLines(listOf(sample), from, to)) {
                            is ClaudeResult.Ok -> str(R.string.claude_test_ok, result.value.firstOrNull() ?: "?") to true
                            is ClaudeResult.Failed -> str(R.string.claude_test_failed, result.reason) to false
                        }
                    } finally {
                        translator.close()
                    }
                }.getOrElse { str(R.string.claude_test_client_failed) to false }
            }
            if (SessionStateHolder.snapshot.value.status == com.easyapp.translator.session.SessionStatus.IDLE) {
                // O teste não conta como gasto de uma sessão.
                ClaudeUsageHolder.reset()
            }
            refreshClaudeState(testMessage = message, testSucceeded = ok)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val PRIVACY_PREFS = "first_run_consent"
        private const val PRIVACY_NOTICE_ACCEPTED = "permission_privacy_notice_accepted"
    }
}
