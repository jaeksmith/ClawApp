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
            Log.i("BootReceiver", "Boot completed — attempting Tailscale launch, then enqueueing service restart")

            // Try to kick Tailscale into life before we need it
            // (can't force VPN start, but launching it prompts it to reconnect)
            try {
                val tsIntent = context.packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
                if (tsIntent != null) {
                    // We can't start Activities from background on Android 10+, but
                    // Tailscale may expose a broadcast or service — try both silently
                    try {
                        context.sendBroadcast(Intent("com.tailscale.ipn.CONNECT").apply {
                            setPackage("com.tailscale.ipn")
                        })
                        Log.i("BootReceiver", "Sent Tailscale CONNECT broadcast")
                    } catch (e: Exception) {
                        Log.w("BootReceiver", "Tailscale broadcast failed: ${e.message}")
                    }
                } else {
                    Log.w("BootReceiver", "Tailscale not installed")
                }
            } catch (e: Exception) {
                Log.w("BootReceiver", "Tailscale launch attempt failed: ${e.message}")
            }

            // Delay service start slightly to give Tailscale a moment to connect
            ServiceRestartWorker.enqueueOnce(context, ServiceRestartWorker.WORK_NAME_BOOT)
            // Also (re)schedule the periodic keepalive check
            ServiceRestartWorker.enqueuePeriodicIfNeeded(context)
        }
    }
}
