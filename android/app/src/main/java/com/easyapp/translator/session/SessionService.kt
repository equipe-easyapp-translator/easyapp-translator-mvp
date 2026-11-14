package com.easyapp.translator.session

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import com.easyapp.translator.MainActivity
import com.easyapp.translator.data.OverlayStyle
import com.easyapp.translator.data.UserPreferencesRepository
import com.easyapp.translator.translation.ClaudeKeyStore
import com.easyapp.translator.translation.ClaudeResult
import com.easyapp.translator.translation.ClaudeTranslator
import com.easyapp.translator.translation.ClaudeUsageHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SessionService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var currentMode: AppMode? = null
    private var ending = false
    private var screenPipeline: ScreenTranslationPipeline? = null
    private var overlay: TranslationOverlayController? = null
    private var conversationEngine: ConversationEngine? = null
    private var audioCaptionPipeline: AudioPlaybackCaptionPipeline? = null
    private var screenResults: List<ScreenTranslation> = emptyList()
    private var screenMessage: String? = null
    private var activeAudioCaption: ScreenTranslation? = null
    private var audioCaptionExpiry: Runnable? = null
    private var claude: ClaudeTranslator? = null
    private var inPlacePipeline: InPlaceSnapshotPipeline? = null
    private var inPlaceOverlay: InPlaceOverlayController? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mediaProjection = null
            if (!ending) {
                stopSession("A captura foi encerrada pelo Android ou pelo usuário")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_PAUSE -> {
                screenPipeline?.pauseAndClear()
                audioCaptionPipeline?.pause()
                AudioCaptionStateHolder.clear("Sessão pausada; buffer de áudio volátil descartado.")
                activeAudioCaption = null
                conversationEngine?.pause()
                overlay?.show(emptyList(), "Sessão pausada")
                inPlacePipeline?.pause()
                inPlaceOverlay?.setPaused(true)
                SessionStateHolder.pause()
                updateNotification()
            }
            ACTION_RESUME -> {
                SessionStateHolder.resume()
                screenPipeline?.resume()
                audioCaptionPipeline?.resume()
                if (currentMode == AppMode.CONVERSATION) {
                    ConversationStateHolder.update { it.copy(status = "Conversa retomada. Escolha quem fala e inicie um turno.") }
                }
                overlay?.show(emptyList(), "Aguardando texto na tela…")
                inPlacePipeline?.resume()
                inPlaceOverlay?.setPaused(false)
                updateNotification()
            }
            ACTION_CONVERSATION_LISTEN -> mainHandler.post {
                val engine = conversationEngine
                if (engine == null) {
                    ConversationStateHolder.update {
                        it.copy(isListening = false, status = "Preparando o reconhecimento de voz. Aguarde um instante e tente novamente.")
                    }
                } else {
                    engine.listenForTurn()
                }
            }
            ACTION_CONVERSATION_CANCEL -> conversationEngine?.cancelTurn()
            ACTION_CONVERSATION_SPEAKER -> intent.getStringExtra(EXTRA_CONVERSATION_SPEAKER)
                ?.let { value -> ConversationSpeaker.entries.firstOrNull { it.name == value } }
                ?.let { conversationEngine?.setSpeaker(it) }
            ACTION_CONVERSATION_REPEAT -> conversationEngine?.repeatLastTranslation()
            ACTION_CONVERSATION_VOICE -> intent.getStringExtra(EXTRA_CONVERSATION_VOICE)
                ?.let { conversationEngine?.selectVoice(it) }
            ACTION_CONVERSATION_RATE -> conversationEngine?.setSpeechRate(intent.getFloatExtra(EXTRA_CONVERSATION_VALUE, 1f))
            ACTION_CONVERSATION_VOLUME -> conversationEngine?.setVolume(intent.getFloatExtra(EXTRA_CONVERSATION_VALUE, 1f))
            ACTION_CLEAR_AUDIO_HISTORY -> {
                audioCaptionPipeline?.clearHistory() ?: AudioCaptionStateHolder.clearHistory()
                activeAudioCaption = null
                audioCaptionExpiry?.let(mainHandler::removeCallbacks)
                audioCaptionExpiry = null
                refreshOverlay()
            }
            ACTION_STOP -> stopSession("Sessão encerrada pelo usuário")
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startSession(startIntent: Intent) {
        val mode = startIntent.getStringExtra(EXTRA_MODE)
            ?.let { value -> AppMode.entries.firstOrNull { it.name == value } }
            ?: return stopSession("Modo inválido")
        currentMode = mode
        ending = false

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = when (mode) {
                    AppMode.SCREEN -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    AppMode.CONVERSATION -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            if (mode == AppMode.SCREEN) {
                val resultCode = startIntent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = startIntent.projectionResultData()
                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    stopSession("A permissão de captura não foi concedida")
                    return
                }

                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = manager.getMediaProjection(resultCode, resultData)
                if (projection == null) {
                    stopSession("O Android não forneceu a projeção de tela")
                    return
                }
                projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
                mediaProjection = projection
            }

            SessionStateHolder.start(mode)
            ClaudeUsageHolder.reset()
            if (mode == AppMode.SCREEN) {
                claude = createClaudeTranslator()
                screenResults = emptyList()
                screenMessage = "Iniciando captura da tela…"
                if (startIntent.getBooleanExtra(EXTRA_CAPTURE_INTERNAL_AUDIO, false)) startAudioCaptionPipeline()
                else AudioCaptionStateHolder.clear("Legendas de áudio desativadas; OCR de tela continua ativo.")
                startScreenPipeline()
            }
            if (mode == AppMode.CONVERSATION) startConversationEngine()
            updateNotification()
        } catch (exception: SecurityException) {
            stopSession("O Android não autorizou o serviço desta sessão")
        } catch (exception: RuntimeException) {
            stopSession("Não foi possível iniciar a sessão")
        }
    }

    private fun stopSession(message: String) {
        ending = true
        screenPipeline?.closeAndClear()
        screenPipeline = null
        audioCaptionPipeline?.close()
        audioCaptionPipeline = null
        screenResults = emptyList()
        activeAudioCaption = null
        audioCaptionExpiry?.let(mainHandler::removeCallbacks)
        audioCaptionExpiry = null
        conversationEngine?.close()
        conversationEngine = null
        overlay?.hide()
        overlay = null
        inPlacePipeline?.close()
        inPlacePipeline = null
        inPlaceOverlay?.hide()
        inPlaceOverlay = null
        closeClaude()
        // O consumo é zerado ao encerrar (FR-024); o total fica só na mensagem final.
        val usage = ClaudeUsageHolder.usage.value
        ClaudeUsageHolder.reset()
        val summary = if (usage.requests > 0) {
            " · Claude: ${usage.requests} chamadas, ${usage.inputTokens + usage.outputTokens} tokens, ~US$ ${"%.4f".format(java.util.Locale.US, usage.estimatedCostUsd)}"
        } else ""
        SessionStateHolder.stop(message + summary)
        val projection = mediaProjection
        mediaProjection = null
        if (projection != null) {
            runCatching { projection.stop() }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        ensureNotificationChannel()
        val snapshot = SessionStateHolder.snapshot.value
        val paused = snapshot.status == SessionStatus.PAUSED
        val modeLabel = currentMode?.title ?: "EasyApp"
        val pauseAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val pauseLabel = if (paused) "Retomar" else "Pausar"
        val detail = when {
            paused -> "$modeLabel · toque para retomar"
            currentMode == AppMode.SCREEN -> "$modeLabel · OCR e tradução no aparelho"
            else -> "$modeLabel · fala processada offline por turnos"
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
        builder.setSmallIcon(com.easyapp.translator.R.drawable.ic_notification)
        builder.setColor(getColor(com.easyapp.translator.R.color.brand))
        builder.setContentTitle("EasyApp · ${if (paused) "Pausada" else "Sessão ativa"}")
        builder.setContentText(detail)
        builder.setContentIntent(
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN_APP,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        builder.setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
        builder.setOngoing(true)
        builder.setOnlyAlertOnce(true)
        builder.addAction(0, pauseLabel, servicePendingIntent(pauseAction))
        builder.addAction(0, "Parar", servicePendingIntent(ACTION_STOP))
        return builder.build()
    }

    private fun servicePendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, SessionService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sessão de tradução",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Estado e controles da sessão ativa do EasyApp"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        screenPipeline?.closeAndClear()
        screenPipeline = null
        audioCaptionPipeline?.close()
        audioCaptionPipeline = null
        conversationEngine?.close()
        conversationEngine = null
        overlay?.hide()
        overlay = null
        inPlacePipeline?.close()
        inPlacePipeline = null
        inPlaceOverlay?.hide()
        inPlaceOverlay = null
        closeClaude()
        serviceScope.cancel()
        val projection = mediaProjection
        mediaProjection = null
        if (projection != null) runCatching { projection.stop() }
        if (!ending && SessionStateHolder.snapshot.value.status != SessionStatus.IDLE) {
            SessionStateHolder.stop("Serviço da sessão encerrado")
        }
        super.onDestroy()
    }

    private fun startScreenPipeline() {
        val activeProjection = mediaProjection ?: return
        serviceScope.launch {
            val initial = UserPreferencesRepository(applicationContext).preferences.first()
            if (ending) return@launch
            val textScale = initial.translationTextSize.scale
            if (initial.overlayStyle == OverlayStyle.IN_PLACE) startInPlacePipeline(activeProjection, textScale)
            else startFloatingPipeline(activeProjection, textScale)
        }
    }

    /** Estilo "No lugar do texto" (História 7): só lê a tela quando o usuário toca em Traduzir. */
    private fun startInPlacePipeline(activeProjection: MediaProjection, textScale: Float) {
        val windowManager = getSystemService(WindowManager::class.java)
        var pipelineRef: InPlaceSnapshotPipeline? = null
        val controller = InPlaceOverlayController(
            context = this,
            windowManager = windowManager,
            onTranslate = {
                val currentController = inPlaceOverlay
                pipelineRef?.requestSnapshot { currentController?.hideForCapture() }
            },
            onCleared = { pipelineRef?.stopWatching() },
            onStop = { startService(controlIntent(this, ACTION_STOP)) },
            onDragStarted = { pipelineRef?.holdWatch() },
            onDragEnded = { pipelineRef?.releaseWatch() },
            textScale = textScale,
        )
        inPlaceOverlay = controller
        serviceScope.launch {
            val preferences = UserPreferencesRepository(applicationContext)
            var settings = preferences.preferences.first()
            SessionStateHolder.updateTranslationModelStatus(
                "${TranslationLanguages.choice(settings.sourceLanguage).label} → ${TranslationLanguages.choice(settings.targetLanguage).label}: toque em Traduzir no botão flutuante.",
            )
            val pipeline = InPlaceSnapshotPipeline(
                windowManager = windowManager,
                projection = activeProjection,
                initialSourceLanguage = settings.sourceLanguage,
                initialTargetLanguage = settings.targetLanguage,
                initialQualityMode = settings.qualityMode.name,
                claude = claude,
                onStatus = { message -> if (!ending) inPlaceOverlay?.setStatus(message) },
                onTranslated = { items, message -> if (!ending) inPlaceOverlay?.showTranslations(items, message) },
                onScreenChanged = { if (!ending) inPlaceOverlay?.onScreenChanged() },
            )
            pipelineRef = pipeline
            inPlacePipeline = pipeline
            if (SessionStateHolder.snapshot.value.status == SessionStatus.ACTIVE && !ending) {
                try {
                    pipeline.start()
                    controller.show()
                    launch {
                        preferences.preferences
                            .distinctUntilChanged()
                            .collect { updated ->
                                SessionStateHolder.updateLanguages(updated.sourceLanguage, updated.targetLanguage)
                                if (updated != settings) {
                                    pipeline.updateConfiguration(updated.sourceLanguage, updated.targetLanguage, updated.qualityMode.name)
                                }
                                settings = updated
                            }
                    }
                } catch (_: RuntimeException) {
                    stopSession("Não foi possível iniciar a captura da tela")
                }
            } else {
                pipeline.close()
            }
        }
    }

    private fun startFloatingPipeline(activeProjection: MediaProjection, textScale: Float) {
        val windowManager = getSystemService(WindowManager::class.java)
        val overlayController = TranslationOverlayController(
            this,
            windowManager,
            onPauseOrResume = {
                val action = if (SessionStateHolder.snapshot.value.status == SessionStatus.PAUSED) ACTION_RESUME else ACTION_PAUSE
                startService(controlIntent(this, action))
            },
            onStop = { startService(controlIntent(this, ACTION_STOP)) },
            onExplain = claude?.let { { item: ScreenTranslation -> explain(item) } },
            textScale = textScale,
        )
        overlay = overlayController
        serviceScope.launch {
            val preferences = UserPreferencesRepository(applicationContext)
            var settings = preferences.preferences.first()
            SessionStateHolder.updateTranslationModelStatus(
                "${TranslationLanguages.choice(settings.sourceLanguage).label} → ${TranslationLanguages.choice(settings.targetLanguage).label}: modelo será verificado ao traduzir.",
            )
            val pipeline = ScreenTranslationPipeline(
                windowManager = windowManager,
                projection = activeProjection,
                qualityMode = settings.qualityMode.name,
                initialSourceLanguage = settings.sourceLanguage,
                initialTargetLanguage = settings.targetLanguage,
                claude = claude,
                onModelStatus = { message ->
                    mainHandler.post {
                        if (!ending) SessionStateHolder.updateTranslationModelStatus(message)
                    }
                },
            ) { results, message ->
                mainHandler.post {
                    if (!ending && SessionStateHolder.snapshot.value.status != SessionStatus.IDLE) {
                        screenResults = results
                        screenMessage = message
                        refreshOverlay()
                    }
                }
            }
            screenPipeline = pipeline
            if (SessionStateHolder.snapshot.value.status == SessionStatus.ACTIVE && !ending) {
                try {
                    pipeline.start()
                    overlayController.show(emptyList(), "Iniciando captura e preparando modelo offline…")
                    launch {
                        preferences.preferences
                            .distinctUntilChanged()
                            .collect { updated ->
                                SessionStateHolder.updateLanguages(updated.sourceLanguage, updated.targetLanguage)
                                if (updated.qualityMode != settings.qualityMode) pipeline.updateQualityMode(updated.qualityMode.name)
                                if (updated.sourceLanguage != settings.sourceLanguage || updated.targetLanguage != settings.targetLanguage) {
                                    pipeline.updateLanguages(updated.sourceLanguage, updated.targetLanguage)
                                }
                                settings = updated
                            }
                    }
                } catch (_: RuntimeException) {
                    stopSession("Não foi possível iniciar a captura da tela")
                }
            } else {
                pipeline.closeAndClear()
            }
        }
    }

    private fun startAudioCaptionPipeline() {
        val activeProjection = mediaProjection ?: return
        serviceScope.launch {
            val preferences = UserPreferencesRepository(applicationContext).preferences
            var settings = preferences.first().let { Triple(it.sourceLanguage, it.targetLanguage, it.qualityMode.name) }
            val pipeline = AudioPlaybackCaptionPipeline(applicationContext, activeProjection, settings.first, settings.second, settings.third) { update ->
                if (ending || SessionStateHolder.snapshot.value.status == SessionStatus.IDLE) return@AudioPlaybackCaptionPipeline
                update.caption?.let { caption ->
                    activeAudioCaption = caption
                    audioCaptionExpiry?.let(mainHandler::removeCallbacks)
                    val expiry = Runnable { activeAudioCaption = null; refreshOverlay() }
                    audioCaptionExpiry = expiry
                    mainHandler.postDelayed(expiry, 7000L)
                }
                // Audio state is shown in its own card; keep the overlay focused on screen OCR.
                refreshOverlay()
            }
            audioCaptionPipeline = pipeline
            pipeline.start()
            preferences.map { Triple(it.sourceLanguage, it.targetLanguage, it.qualityMode.name) }
                .distinctUntilChanged()
                .collect { next ->
                    if (next != settings && !ending && SessionStateHolder.snapshot.value.status == SessionStatus.ACTIVE) {
                        settings = next
                        SessionStateHolder.updateLanguages(settings.first, settings.second)
                        audioCaptionPipeline?.updateConfiguration(settings.first, settings.second, settings.third)
                    }
                }
        }
    }

    /** Só existe com chave salva, consentimento aceito e opção ligada (FR-019/FR-020). */
    private fun createClaudeTranslator(): ClaudeTranslator? {
        val store = ClaudeKeyStore(applicationContext)
        if (!store.usable) return null
        val key = store.load() ?: return null
        return runCatching { ClaudeTranslator(key) }.getOrNull()
    }

    private fun closeClaude() {
        val current = claude ?: return
        claude = null
        // Fechar o cliente HTTP pode bloquear; não segura a thread principal.
        Thread { current.close() }.start()
    }

    /** "O que isso quer dizer?" — só roda quando o usuário toca numa linha (FR-022). */
    private fun explain(item: ScreenTranslation) {
        val translator = claude ?: return
        val snapshot = SessionStateHolder.snapshot.value
        overlay?.showExplanation(item.source, "Perguntando ao Claude…")
        serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                translator.explain(item.source, item.translated, snapshot.sourceLanguage, snapshot.targetLanguage)
            }
            if (ending) return@launch
            val text = when (result) {
                is ClaudeResult.Ok -> result.value
                is ClaudeResult.Failed -> "Não foi possível explicar agora: ${result.reason}."
            }
            overlay?.showExplanation(item.source, text)
        }
    }

    private fun refreshOverlay(message: String? = screenMessage) {
        val combined = buildList {
            activeAudioCaption?.let(::add)
            addAll(screenResults)
        }
        overlay?.show(combined, message)
    }

    private fun startConversationEngine() {
        conversationEngine?.close()
        val engine = ConversationEngine(applicationContext) {
            stopSession("A permissão do microfone foi revogada; a conversa foi encerrada.")
        }
        conversationEngine = engine
        val preferences = UserPreferencesRepository(applicationContext).preferences
        serviceScope.launch {
            val initial = preferences.first()
            engine.initialize(initial.sourceLanguage, initial.targetLanguage)
            preferences
                .map { it.sourceLanguage to it.targetLanguage }
                .distinctUntilChanged()
                .collect { (source, target) ->
                    SessionStateHolder.updateLanguages(source, target)
                    engine.setLanguages(source, target)
                }
        }
    }

    private fun Intent.projectionResultData(): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(EXTRA_RESULT_DATA)
    }

    companion object {
        const val ACTION_START = "com.easyapp.translator.session.START"
        const val ACTION_PAUSE = "com.easyapp.translator.session.PAUSE"
        const val ACTION_RESUME = "com.easyapp.translator.session.RESUME"
        const val ACTION_STOP = "com.easyapp.translator.session.STOP"
        const val ACTION_CONVERSATION_LISTEN = "com.easyapp.translator.session.CONVERSATION_LISTEN"
        const val ACTION_CONVERSATION_CANCEL = "com.easyapp.translator.session.CONVERSATION_CANCEL"
        const val ACTION_CONVERSATION_SPEAKER = "com.easyapp.translator.session.CONVERSATION_SPEAKER"
        const val ACTION_CONVERSATION_REPEAT = "com.easyapp.translator.session.CONVERSATION_REPEAT"
        const val ACTION_CONVERSATION_VOICE = "com.easyapp.translator.session.CONVERSATION_VOICE"
        const val ACTION_CONVERSATION_RATE = "com.easyapp.translator.session.CONVERSATION_RATE"
        const val ACTION_CONVERSATION_VOLUME = "com.easyapp.translator.session.CONVERSATION_VOLUME"
        const val ACTION_CLEAR_AUDIO_HISTORY = "com.easyapp.translator.session.CLEAR_AUDIO_HISTORY"
        const val EXTRA_CONVERSATION_SPEAKER = "conversation_speaker"
        const val EXTRA_CONVERSATION_VOICE = "conversation_voice"
        const val EXTRA_CONVERSATION_VALUE = "conversation_value"
        const val EXTRA_MODE = "session_mode"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_CAPTURE_INTERNAL_AUDIO = "capture_internal_audio"

        private const val CHANNEL_ID = "translation_session"
        private const val NOTIFICATION_ID = 1201
        private const val REQUEST_OPEN_APP = 1202

        fun startIntent(context: Context, mode: AppMode): Intent =
            Intent(context, SessionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name)

        fun controlIntent(context: Context, action: String): Intent =
            Intent(context, SessionService::class.java).setAction(action)

        fun conversationIntent(context: Context, action: String): Intent =
            Intent(context, SessionService::class.java).setAction(action)
    }
}
