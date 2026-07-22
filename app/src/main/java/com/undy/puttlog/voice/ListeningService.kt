package com.undy.puttlog.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.undy.puttlog.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val NOTIFICATION_CHANNEL_ID = "listening"
private const val NOTIFICATION_ID = 1
private const val ACTION_STOP = "com.undy.puttlog.voice.ACTION_STOP"

/**
 * Foreground service that owns the [ContinuousSpeechRecognizer] so listening survives the phone
 * being locked/pocketed: a visible Activity is what keeps a process out of Android's background
 * bucket, and without a foreground service the OS revokes mic access and can freeze/kill the
 * process once the screen turns off. Deliberately NOT started sticky and stops itself from
 * [onTaskRemoved] — it's meant to outlive the screen turning off or the user pressing home, not
 * outlive the app being swiped away from recents.
 */
class ListeningService : Service() {

    private val binder = LocalBinder()
    private var recognizer: ContinuousSpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    var onResult: ((List<String>) -> Unit)? = null
    var onUnavailable: (() -> Unit)? = null

    inner class LocalBinder : android.os.Binder() {
        fun getService(): ListeningService = this@ListeningService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startListening() {
        if (recognizer == null) {
            recognizer = ContinuousSpeechRecognizer(
                context = this,
                onResult = { candidates -> onResult?.invoke(candidates) },
                onListeningChanged = { listening -> _isListening.value = listening },
                onUnavailable = {
                    onUnavailable?.invoke()
                    stopListening()
                }
            )
        }
        acquireWakeLock()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        recognizer?.start()
    }

    fun stopListening() {
        recognizer?.stop()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopListening()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopListening()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PuttLog:listening").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L) // safety timeout: 10h, well beyond any real session
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PuttLog listening")
            .setContentText("Say a distance and \"make\" or \"miss\"")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }
}
