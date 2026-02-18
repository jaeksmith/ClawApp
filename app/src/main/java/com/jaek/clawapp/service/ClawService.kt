package com.jaek.clawapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jaek.clawapp.MainActivity
import java.util.*

/**
 * Foreground service that maintains a WebSocket connection to the Claw relay.
 * Handles commands (ping/find-my-phone, etc.) pushed from the server.
 */
class ClawService : Service(), TextToSpeech.OnInitListener, RelayConnection.CommandListener {

    companion object {
        const val TAG = "ClawService"
        const val CHANNEL_ID = "claw_service"
        const val NOTIFICATION_ID = 1
        const val ACTION_PING_PHONE = "com.jaek.clawapp.PING_PHONE"
        const val EXTRA_MESSAGE = "message"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var relay: RelayConnection? = null
    private var isConnected = false

    // Listeners for UI updates
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PING_PHONE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Hey! Claw is looking for you!"
                pingPhone(message)
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                // Auto-configure from saved settings (for boot start)
                if (relay == null) {
                    val prefs = getSharedPreferences("claw_settings", Context.MODE_PRIVATE)
                    val url = prefs.getString("relay_url", null)
                    if (!url.isNullOrBlank()) {
                        configure(url)
                    }
                }
            }
        }
        return START_STICKY
    }

    fun configure(relayUrl: String) {
        relay?.disconnect()
        val prefs = getSharedPreferences("claw_settings", Context.MODE_PRIVATE)
        val fcmToken = prefs.getString("fcm_token", null)
        relay = RelayConnection(
            url = relayUrl,
            deviceInfo = mapOf(
                "device" to Build.MODEL,
                "app" to "ClawApp",
                "version" to "0.1.0"
            ),
            fcmToken = fcmToken
        ).also {
            it.setCommandListener(this)
            it.connect()
        }
    }

    // --- RelayConnection.CommandListener ---

    override fun onCommand(action: String, message: String, extra: Map<String, Any?>) {
        Log.i(TAG, "Command received: action=$action message=$message")
        when (action) {
            "ping" -> pingPhone(message.ifEmpty { "Hey! Claw is looking for you!" })
            "tts" -> {
                if (ttsReady) {
                    tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "cmd_${System.currentTimeMillis()}")
                }
            }
            "vibrate" -> {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
                )
            }
            "notify" -> {
                val title = extra["title"] as? String ?: "Claw"
                showNotification(title, message)
            }
            else -> Log.w(TAG, "Unknown command action: $action")
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        isConnected = connected
        onConnectionStateChanged?.invoke(connected)
        updateNotification(if (connected) "Connected to Claw" else "Reconnecting...")
    }

    fun isConnected(): Boolean = isConnected

    /**
     * Trigger phone ping — plays alarm sound, vibrates, and speaks a message.
     */
    private fun pingPhone(message: String) {
        Log.i(TAG, "PING PHONE: $message")

        // Vibrate
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 500, 200, 500, 200, 500),
                -1
            )
        )

        // Play alarm sound
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ClawService, alarmUri)
                prepare()
                start()
            }
            // Stop after 5 seconds
            handler.postDelayed({ stopAlarm() }, 5000)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm", e)
        }

        // Speak the message after a brief delay
        handler.postDelayed({
            if (ttsReady) {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "ping_${System.currentTimeMillis()}")
            }
        }, 1000)

        // Show a heads-up notification
        showNotification("📍 Claw is looking for you!", message)
    }

    private fun showNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
            Log.i(TAG, "TTS initialized")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Claw Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Claw connected in the background"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClawApp")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ClawService = this@ClawService
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        relay?.disconnect()
        stopAlarm()
        tts?.shutdown()
        super.onDestroy()
    }
}
