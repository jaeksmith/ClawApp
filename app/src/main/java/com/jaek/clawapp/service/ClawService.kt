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
import com.jaek.clawapp.AppLogger
import androidx.core.app.NotificationCompat
import com.jaek.clawapp.MainActivity
import com.jaek.clawapp.model.CatLocation
import com.jaek.clawapp.repository.CatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Cat state repository — shared with UI via binder
    val catRepository = CatRepository()

    // Location tracker — shared with UI via binder
    val locationTracker by lazy { LocationTracker(this) }

    // Observable connection state — always emits current value on collection (no race)
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PING_PHONE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Hey! Your assistant is looking for you!"
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
        catRepository.sendWsMessage = { msg -> relay?.send(msg) }
        locationTracker.sendWsMessage = { msg -> relay?.send(msg) }

        // Auto-start location tracking unless user explicitly disabled it
        val trackingEnabled = prefs.getBoolean("location_tracking_enabled", true)
        if (trackingEnabled) locationTracker.start()
    }

    fun setLocationTracking(enabled: Boolean) {
        getSharedPreferences("claw_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("location_tracking_enabled", enabled).apply()
        locationTracker.setTrackingEnabled(enabled)
    }

    // --- RelayConnection.CommandListener ---

    override fun onCommand(action: String, message: String, extra: Map<String, Any?>) {
        AppLogger.i(TAG, "Command received: action=$action message=$message")
        when (action) {
            "cat_notification" -> {
                @Suppress("UNCHECKED_CAST")
                val delivery = extra["delivery"] as? Map<String, Any?>
                deliverCatNotification(message, delivery)
            }
            "ping" -> pingPhone(message.ifEmpty { "Hey! Your assistant is looking for you!" })
            "tts" -> {
                if (ttsReady && message.isNotEmpty()) {
                    val params = Bundle().apply {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_ALARM)
                    }
                    tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "cmd_${System.currentTimeMillis()}")
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
            else -> AppLogger.w(TAG, "Unknown command action: $action")
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        _connectionState.value = connected
        updateNotification(if (connected) "Connected to Claw" else "Reconnecting...")
    }

    override fun onCatStateSnapshot(cats: Map<String, Any?>, notifications: List<Any?>, lastCatOutAt: Long?, mute: Map<String, Any?>?) {
        AppLogger.i(TAG, "Cat snapshot received: ${cats.keys}")
        catRepository.applySnapshot(cats, notifications, mute)
    }

    override fun onMuteState(until: Long?) {
        catRepository.applyMuteState(until)
    }

    override fun onCatStateChanged(catName: String, state: String, stateSetAt: Long?, source: String) {
        AppLogger.i(TAG, "Cat state push: $catName -> $state (from $source)")
        catRepository.applyStateChange(catName, state, stateSetAt)
    }

    fun isConnected(): Boolean = _connectionState.value

    /** Ask the relay to send a ping command back to this device — tests full server round-trip */
    fun requestServerTestPing() {
        val gson = com.google.gson.Gson()
        relay?.send(gson.toJson(mapOf(
            "type" to "request_test_ping",
            "message" to "Server test ping — round trip confirmed!"
        )))
    }

    /**
     * Handles the cat_notification command with combo delivery options.
     */
    private fun deliverCatNotification(message: String, delivery: Map<String, Any?>?) {
        AppLogger.i(TAG, "Cat notification: $message delivery=$delivery")

        val vibration = delivery?.get("vibration") as? Boolean ?: false
        val meow = delivery?.get("meow") as? Boolean ?: false
        val phoneSound = delivery?.get("phoneSound") as? Boolean ?: false
        val doTts = delivery?.get("tts") as? Boolean ?: false
        val bypassSilent = delivery?.get("bypassSilent") as? Boolean ?: true

        if (vibration) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
        }

        if (meow) {
            playRawSound(com.jaek.clawapp.R.raw.cat_meow, bypassSilent)
        } else if (phoneSound) {
            val soundUri = RingtoneManager.getDefaultUri(
                if (bypassSilent) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            )
            try {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(if (bypassSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    setDataSource(this@ClawService, soundUri)
                    prepare(); start()
                    setOnCompletionListener { release() }
                }
            } catch (e: Exception) { AppLogger.e(TAG, "Error playing notification sound", e) }
        }

        if (doTts && ttsReady && message.isNotEmpty()) {
            val streamType = if (bypassSilent) android.media.AudioManager.STREAM_ALARM
                            else android.media.AudioManager.STREAM_NOTIFICATION
            handler.postDelayed({
                val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType) }
                tts?.speak(message, TextToSpeech.QUEUE_ADD, params, "catnot_${System.currentTimeMillis()}")
            }, if (meow || phoneSound) 1500L else 0L)
        }

        showNotification("🐱 Cat Watch", message)
    }

    private fun playRawSound(resId: Int, bypassSilent: Boolean) {
        try {
            val mp = MediaPlayer.create(this, resId) ?: run {
                AppLogger.w(TAG, "Sound resource $resId not found"); return
            }
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(if (bypassSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            mp.start()
            mp.setOnCompletionListener { it.release() }
        } catch (e: Exception) { AppLogger.e(TAG, "Error playing raw sound", e) }
    }

    /**
     * Trigger phone ping — plays alarm sound, vibrates, and speaks a message.
     */
    private fun pingPhone(message: String) {
        AppLogger.i(TAG, "PING PHONE: $message")

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
            AppLogger.e(TAG, "Error playing alarm", e)
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
            AppLogger.i(TAG, "TTS initialized")
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
