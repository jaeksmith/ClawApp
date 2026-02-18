package com.jaek.clawapp.service

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jaek.clawapp.api.ClawApi

/**
 * Handles FCM messages — used to wake the app and trigger commands
 * even when the app/service isn't running.
 */
class ClawFcmService : FirebaseMessagingService() {

    companion object {
        const val TAG = "ClawFcmService"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val action = data["action"] ?: return
        val msg = data["message"] ?: ""

        Log.i(TAG, "FCM received: action=$action message=$msg")

        // Start the foreground service if not running, and deliver the command
        val intent = Intent(this, ClawService::class.java).apply {
            this.action = when (action) {
                "ping" -> ClawService.ACTION_PING_PHONE
                else -> ClawService.ACTION_PING_PHONE
            }
            putExtra(ClawService.EXTRA_MESSAGE, msg)
        }

        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service from FCM", e)
        }
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed: $token")
        // Send new token to relay so it can reach us
        sendTokenToRelay(token)
    }

    private fun sendTokenToRelay(token: String) {
        val prefs = getSharedPreferences("claw_settings", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        // The relay URL for HTTP registration - relay will pick this up
        // when the WebSocket connects (token sent in register message)
        Log.i(TAG, "FCM token saved locally, will sync on next WS connect")
    }
}
