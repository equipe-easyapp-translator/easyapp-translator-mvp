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
import androidx.core.app.NotificationCompat
import com.easyapp.translator.MainActivity
import com.easyapp.translator.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SessionService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var currentMode: AppMode? = null
    private var ending = false
    private var screenPipeline: ScreenTranslationPipeline? = null
    private var overlay: TranslationOverlayController? = null
    private var conversationEngine: ConversationEngine? = null
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
                conversationEngine?.pause()
                overlay?.show(emptyList(), "Sessão pausada")
                SessionStateHolder.pause()
                updateNotification()
            }
            ACTION_RESUME -> {
                SessionStateHolder.resume()
                screenPipeline?.resume()
                if (currentMode == AppMode.CONVERSATION) {
                    ConversationStateHolder.update { it.copy(status = "Conversa retomada. Escolha quem fala e inicie um turno.") }
                }
                overlay?.show(emptyList(), "Aguardando texto na tela…")
                updateNotification()
            }
            ACTION_CONVERSATION_LISTEN -> conversationEngine?.listenForTurn()
            ACTION_CONVERSATION_CANCEL -> conversationEngine?.cancelTurn()
            ACTION_CONVERSATION_SPEAKER -> intent.getStringExtra(EXTRA_CONVERSATION_SPEAKER)
                ?.let { value -> ConversationSpeaker.entries.firstOrNull { it.name == value } }
                ?.let { conversationEngine?.setSpeaker(it) }
            ACTION_CONVERSATION_REPEAT -> conversationEngine?.repeatLastTranslation()
            ACTION_CONVERSATION_VOICE -> intent.getStringExtra(EXTRA_CONVERSATION_VOICE)
                ?.let { conversationEngine?.selectVoice(it) }
            ACTION_CONVERSATION_RATE -> conversationEngine?.setSpeechRate(intent.getFloatExtra(EXTRA_CONVERSATION_VALUE, 1f))
            ACTION_CONVERSATION_VOLUME -> conversationEngine?.setVolume(intent.getFloatExtra(EXTRA_CONVERSATION_VALUE, 1f))
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
                mediaProjection = manager.getMediaProjection(resultCode, resultData).also { projection ->
                    projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
                }
            }

            SessionStateHolder.start(mode)
            if (mode == AppMode.SCREEN) startScreenPipeline()
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
        conversationEngine?.close()
        conversationEngine = null
        overlay?.hide()
        overlay = null
        SessionStateHolder.stop(message)
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_translate)
            .setContentTitle("EasyApp · ${if (paused) "Pausada" else "Sessão ativa"}")
            .setContentText(detail)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    REQUEST_OPEN_APP,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, pauseLabel, servicePendingIntent(pauseAction))
            .addAction(0, "Parar", servicePendingIntent(ACTION_STOP))
            .build()
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
        conversationEngine?.close()
        conversationEngine = null
        overlay?.hide()
        overlay = null
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
        val windowManager = getSystemService(WindowManager::class.java)
        val overlayController = TranslationOverlayController(
            this,
            windowManager,
            onPauseOrResume = {
                val action = if (SessionStateHolder.snapshot.value.status == SessionStatus.PAUSED) ACTION_RESUME else ACTION_PAUSE
                startService(controlIntent(this, action))
            },
            onStop = { startService(controlIntent(this, ACTION_STOP)) },
        )
        overlay = overlayController
        serviceScope.launch {
            val preferences = UserPreferencesRepository(applicationContext)
            val pipeline = ScreenTranslationPipeline(
                windowManager = windowManager,
                projection = activeProjection,
                qualityMode = "BALANCED",
            ) { results, message ->
                mainHandler.post {
                    if (!ending && SessionStateHolder.snapshot.value.status != SessionStatus.IDLE) {
                        overlayController.show(results, message)
                    }
                }
            }
            screenPipeline = pipeline
            if (SessionStateHolder.snapshot.value.status == SessionStatus.ACTIVE && !ending) {
                try {
                    pipeline.start()
                    overlayController.show(emptyList(), "Aponte para texto em inglês ou espanhol")
                    launch {
                        preferences.preferences.map { it.qualityMode.name }
                            .distinctUntilChanged()
                            .collect { mode -> pipeline.updateQualityMode(mode) }
                    }
                } catch (_: RuntimeException) {
                    stopSession("Não foi possível iniciar a captura da tela")
                }
            } else {
                pipeline.closeAndClear()
            }
        }
    }

    private fun startConversationEngine() {
        conversationEngine?.close()
        val engine = ConversationEngine(applicationContext) {
            stopSession("A permissão do microfone foi revogada; a conversa foi encerrada.")
        }
        conversationEngine = engine
        engine.initialize("en")
        serviceScope.launch {
            UserPreferencesRepository(applicationContext).preferences
                .map { it.sourceLanguage }
                .distinctUntilChanged()
                .collect { language -> engine.setSourceLanguage(language) }
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
        const val EXTRA_CONVERSATION_SPEAKER = "conversation_speaker"
        const val EXTRA_CONVERSATION_VOICE = "conversation_voice"
        const val EXTRA_CONVERSATION_VALUE = "conversation_value"
        const val EXTRA_MODE = "session_mode"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"

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
