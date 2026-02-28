package com.jaek.clawapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.jaek.clawapp.MainActivity
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that attempts to restart ClawService if it's not running.
 * Used by: BootReceiver, FCM wake, periodic backstop check.
 *
 * On Android 14+, starting a FGS from background is restricted. If the direct
 * start fails (or isn't allowed), we fall back to showing a "tap to restore"
 * notification that the user can tap to bring the app up and restart the service.
 */
class ServiceRestartWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val TAG = "ServiceRestartWorker"
        const val WORK_NAME_BOOT = "service_restart_boot"
        const val WORK_NAME_PERIODIC = "service_keepalive"
        const val RESTORE_CHANNEL_ID = "claw_restore"
        const val RESTORE_NOTIFICATION_ID = 200

        /** Enqueue a one-time restart attempt (boot or FCM wake). */
        fun enqueueOnce(context: Context, tag: String = "service_restart") {
            val request = OneTimeWorkRequestBuilder<ServiceRestartWorker>()
                .addTag(tag)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
        }

        /** Enqueue a periodic 15-min keepalive check (idempotent — safe to call repeatedly). */
        fun enqueuePeriodicIfNeeded(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceRestartWorker>(15, TimeUnit.MINUTES)
                .addTag(WORK_NAME_PERIODIC)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,   // don't reset if already scheduled
                    request
                )
        }
    }

    override fun doWork(): Result {
        val prefs = context.getSharedPreferences("claw_settings", Context.MODE_PRIVATE)
        val url = prefs.getString("relay_url", null)

        if (url.isNullOrBlank()) {
            Log.w(TAG, "No relay URL configured — skipping restart")
            return Result.success()
        }

        return try {
            Log.i(TAG, "Attempting to start ClawService")
            context.startForegroundService(Intent(context, ClawService::class.java))
            Log.i(TAG, "ClawService start issued")
            Result.success()
        } catch (e: Exception) {
            // Android blocked the background FGS start — show tap-to-restore notification
            Log.w(TAG, "FGS start blocked: ${e.message} — showing restore notification")
            showRestoreNotification()
            Result.success() // not a worker failure; we handled it gracefully
        }
    }

    private fun showRestoreNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists
        val channel = NotificationChannel(
            RESTORE_CHANNEL_ID,
            "Claw Restore",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Tap to reconnect ClawApp to the relay" }
        nm.createNotificationChannel(channel)

        // Tap → launch MainActivity (which starts the service)
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RESTORE_CHANNEL_ID)
            .setContentTitle("ClawApp disconnected")
            .setContentText("Tap to reconnect to the relay")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(RESTORE_NOTIFICATION_ID, notification)
    }
}
