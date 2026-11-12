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
import androidx.core.app.NotificationCompat
import com.easyapp.translator.MainActivity
import com.easyapp.translator.R

class SessionService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var currentMode: AppMode? = null
    private var ending = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mediaProjection = null
            if (!ending) {
                SessionStateHolder.stop("A captura foi encerrada pelo Android ou pelo usuário")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_PAUSE -> {
                SessionStateHolder.pause()
                updateNotification()
            }
            ACTION_RESUME -> {
                SessionStateHolder.resume()
                updateNotification()
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
                mediaProjection = manager.getMediaProjection(resultCode, resultData).also { projection ->
                    projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
                }
            }

            SessionStateHolder.start(mode)
            updateNotification()
        } catch (exception: SecurityException) {
            stopSession("O Android não autorizou o serviço desta sessão")
        } catch (exception: RuntimeException) {
            stopSession("Não foi possível iniciar a sessão")
        }
    }

    private fun stopSession(message: String) {
        ending = true
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_translate)
            .setContentTitle("EasyApp · ${if (paused) "Pausada" else "Sessão ativa"}")
            .setContentText("$modeLabel. Captura e reconhecimento serão conectados nas próximas fases.")
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
        val projection = mediaProjection
        mediaProjection = null
        if (projection != null) runCatching { projection.stop() }
        if (!ending && SessionStateHolder.snapshot.value.status != SessionStatus.IDLE) {
            SessionStateHolder.stop("Serviço da sessão encerrado")
        }
        super.onDestroy()
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
    }
}
