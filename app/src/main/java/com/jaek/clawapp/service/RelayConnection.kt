package com.jaek.clawapp.service

import android.util.Log
import com.jaek.clawapp.AppLogger
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Maintains a persistent WebSocket connection to the Claw relay service.
 * Auto-reconnects on disconnect.
 */
class RelayConnection(
    private val url: String,
    private val deviceInfo: Map<String, String> = emptyMap(),
    private val fcmToken: String? = null
) {
    companion object {
        const val TAG = "RelayConnection"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
    }

    interface CommandListener {
        fun onCommand(action: String, message: String, extra: Map<String, Any?>)
        fun onConnectionChanged(connected: Boolean)
        fun onCatStateSnapshot(cats: Map<String, Any?>, notifications: List<Any?>, lastCatOutAt: Long?, mute: Map<String, Any?>?)
        fun onCatStateChanged(catName: String, state: String, stateSetAt: Long?, source: String)
        fun onMuteState(until: Long?)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES) // No read timeout for WS
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private var ws: WebSocket? = null
    private var listener: CommandListener? = null
    private var reconnectDelay = RECONNECT_DELAY_MS
    private var shouldReconnect = true
    private var isConnected = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // Context reference for Tailscale nudge notification (set via setContext)
    private var context: android.content.Context? = null
    private var consecutiveFailures = 0
    private val TAILSCALE_NUDGE_AFTER = 3  // show nudge after this many consecutive failures

    fun setContext(ctx: android.content.Context) { context = ctx }

    fun setCommandListener(l: CommandListener) {
        listener = l
    }

    fun connect() {
        shouldReconnect = true
        doConnect()
    }

    fun send(message: String): Boolean {
        return if (ws != null && isConnected) {
            ws?.send(message)
            true
        } else {
            AppLogger.w(TAG, "send() called but not connected")
            false
        }
    }

    fun disconnect() {
        shouldReconnect = false
        handler.removeCallbacksAndMessages(null)
        ws?.close(1000, "app disconnect")
        ws = null
        updateConnected(false)
    }

    private fun doConnect() {
        AppLogger.i(TAG, "Connecting to $url")
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                AppLogger.i(TAG, "WebSocket connected")
                reconnectDelay = RECONNECT_DELAY_MS
                consecutiveFailures = 0
                updateConnected(true)

                // Register with device info
                val reg = mutableMapOf<String, Any?>("type" to "register", "info" to deviceInfo)
                if (fcmToken != null) reg["fcmToken"] = fcmToken
                webSocket.send(gson.toJson(reg))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = gson.fromJson(text, Map::class.java) as Map<String, Any?>
                    val type = msg["type"] as? String ?: return

                    when (type) {
                        "command" -> {
                            val action = msg["action"] as? String ?: return
                            val message = msg["message"] as? String ?: ""
                            val commandId = msg["commandId"] as? String
                            AppLogger.i(TAG, "Command received: action=$action commandId=$commandId")

                            handler.post {
                                listener?.onCommand(action, message, msg)
                            }

                            // Ack
                            if (commandId != null) {
                                webSocket.send(gson.toJson(mapOf(
                                    "type" to "ack",
                                    "commandId" to commandId
                                )))
                            }
                        }
                        "ping" -> {
                            webSocket.send(gson.toJson(mapOf("type" to "pong")))
                        }
                        "welcome" -> {
                            AppLogger.i(TAG, "Registered as ${msg["clientId"]}")
                        }
                        "cat_state_snapshot" -> {
                            @Suppress("UNCHECKED_CAST")
                            val catsMap = msg["cats"] as? Map<String, Any?> ?: emptyMap()
                            @Suppress("UNCHECKED_CAST")
                            val notifs = msg["notifications"] as? List<Any?> ?: emptyList()
                            val lastOut = (msg["lastCatOutAt"] as? Double)?.toLong()
                            @Suppress("UNCHECKED_CAST")
                            val muteRaw = msg["mute"] as? Map<String, Any?>
                            handler.post { listener?.onCatStateSnapshot(catsMap, notifs, lastOut, muteRaw) }
                        }
                        "cat_state_changed" -> {
                            val catName = msg["catName"] as? String ?: return
                            val state = msg["state"] as? String ?: return
                            val stateSetAt = (msg["stateSetAt"] as? Double)?.toLong()
                            val source = msg["source"] as? String ?: "server"
                            handler.post { listener?.onCatStateChanged(catName, state, stateSetAt, source) }
                        }
                        "mute_state", "mute_ack" -> {
                            @Suppress("UNCHECKED_CAST")
                            val muteRaw = msg["mute"] as? Map<String, Any?>
                            val until = (muteRaw?.get("until") as? Double)?.toLong()
                            handler.post { listener?.onMuteState(until) }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error handling message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.i(TAG, "WebSocket closed: $code $reason")
                updateConnected(false)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.e(TAG, "WebSocket failure: ${t.message}")
                updateConnected(false)
                consecutiveFailures++
                if (consecutiveFailures == TAILSCALE_NUDGE_AFTER) {
                    showTailscaleNudge()
                }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        AppLogger.i(TAG, "Reconnecting in ${reconnectDelay}ms")
        handler.postDelayed({ doConnect() }, reconnectDelay)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    private fun updateConnected(connected: Boolean) {
        if (connected != isConnected) {
            isConnected = connected
            handler.post { listener?.onConnectionChanged(connected) }
        }
    }

    fun isConnected(): Boolean = isConnected

    /**
     * Shows a notification suggesting the user check Tailscale when the relay
     * has been unreachable for several consecutive attempts.
     * Includes an "Open Tailscale" action button and a "Dismiss" action.
     */
    private fun showTailscaleNudge() {
        val ctx = context ?: return
        AppLogger.w(TAG, "Relay unreachable after $consecutiveFailures attempts — suggesting Tailscale check")

        val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "claw_tailscale_nudge"
        val channel = android.app.NotificationChannel(
            channelId, "Network Alerts", android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts when relay is unreachable" }
        nm.createNotificationChannel(channel)

        // Open Tailscale intent
        val tailscaleIntent = ctx.packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
        val tailscalePi = if (tailscaleIntent != null) {
            android.app.PendingIntent.getActivity(
                ctx, 0,
                tailscaleIntent.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val builder = androidx.core.app.NotificationCompat.Builder(ctx, channelId)
            .setContentTitle("ClawApp: Relay unreachable")
            .setContentText("Can't reach the relay. Is Tailscale connected?")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)

        if (tailscalePi != null) {
            builder.addAction(0, "Open Tailscale", tailscalePi)
        } else {
            builder.setContentText("Can't reach the relay. Tailscale may not be installed or connected.")
        }

        nm.notify(201, builder.build())
    }
}
