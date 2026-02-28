package com.jaek.clawapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts ClawService automatically on device boot.
 * Uses WorkManager so Android 14+ background-start restrictions are handled gracefully.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            Log.i("BootReceiver", "Boot completed — enqueueing service restart via WorkManager")
            ServiceRestartWorker.enqueueOnce(context, ServiceRestartWorker.WORK_NAME_BOOT)
            // Also (re)schedule the periodic keepalive check
            ServiceRestartWorker.enqueuePeriodicIfNeeded(context)
        }
    }
}
