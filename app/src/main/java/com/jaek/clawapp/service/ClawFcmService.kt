package com.jaek.clawapp.service

import android.app.NotificationChannel
import android.content.Intent
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles FCM messages — used to wake the app and trigger commands
 * even when the app/service isn't running.
 * 
 * Directly handles the alarm/notification without starting the full
 * foreground service, to avoid Android's background restrictions.
 */
class ClawFcmService : FirebaseMessagingService() {

    companion object {
        const val TAG = "ClawFcmService"
        const val CHANNEL_ID = "claw_alerts"
        const val NOTIFICATION_ID = 100
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val action = data["action"] ?: return
        val msg = data["message"] ?: ""

        Log.i(TAG, "FCM received: action=$action message=$msg")

        when (action) {
            "ping" -> handlePing(msg)
            "notify" -> showNotification(data["title"] ?: "Claw", msg)
            "wake" -> {
                // FCM doorbell: start the service so it reconnects via WS and gets queued commands
                Log.i(TAG, "FCM wake received — starting ClawService")
                try {
                    startForegroundService(Intent(this, ClawService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service from FCM wake", e)
                }
            }
            else -> {
                Log.w(TAG, "Unknown FCM action: $action — starting service anyway")
                try { startForegroundService(Intent(this, ClawService::class.java)) } catch (_: Exception) {}
            }
        }
    }

    private fun handlePing(message: String) {
        val msg = message.ifEmpty { "Hey! Claw is looking for you!" }

        // Create alert notification channel (high importance for heads-up)
        ensureNotificationChannel()

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
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ClawFcmService, alarmUri)
                prepare()
                start()
            }
            // Stop after 5 seconds
            handler.postDelayed({
                try {
                    if (player.isPlaying) player.stop()
                    player.release()
                } catch (_: Exception) {}
            }, 5000)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm from FCM", e)
        }

        // Show heads-up notification
        showNotification("📍 Claw is looking for you!", msg)
    }

    private fun showNotification(title: String, text: String) {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Claw Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent alerts from Claw"
            enableVibration(true)
            setBypassDnd(true)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed: $token")
        getSharedPreferences("claw_settings", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }
}
