package com.jaek.clawapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts ClawService automatically on device boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Boot completed — starting ClawService")
            val serviceIntent = Intent(context, ClawService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
