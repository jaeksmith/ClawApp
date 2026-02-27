package com.jaek.clawapp.repository

import android.util.Log
import com.google.gson.Gson
import com.jaek.clawapp.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CatRepository {
    companion object { const val TAG = "CatRepository" }

    private val gson = Gson()

    private val _cats = MutableStateFlow<Map<String, CatState>>(emptyMap())
    val cats: StateFlow<Map<String, CatState>> = _cats.asStateFlow()

    private val _notifications = MutableStateFlow<List<CatNotification>>(emptyList())
    val notifications: StateFlow<List<CatNotification>> = _notifications.asStateFlow()

    private val _mute = MutableStateFlow(MuteState())
    val mute: StateFlow<MuteState> = _mute.asStateFlow()

    var sendWsMessage: ((String) -> Unit)? = null

    fun applySnapshot(catsMap: Map<String, Any?>, notifsList: List<Any?>, muteRaw: Map<String, Any?>? = null) {
        val parsedCats = catsMap.mapNotNull { (name, raw) ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as? Map<String, Any?> ?: return@mapNotNull null
            name to CatState(
                name = name,
                state = CatLocation.fromString(m["state"] as? String),
                stateSetAt = (m["stateSetAt"] as? Double)?.toLong(),
                outdoorOnly = m["outdoorOnly"] as? Boolean ?: false,
                image = m["image"] as? String
            )
        }.toMap()
        _cats.value = parsedCats

        val parsedNotifs = notifsList.mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as? Map<String, Any?> ?: return@mapNotNull null
            parseNotification(m)
        }
        _notifications.value = parsedNotifs

        if (muteRaw != null) applyMuteRaw(muteRaw)

        Log.i(TAG, "Snapshot applied: cats=${parsedCats.keys}, notifs=${parsedNotifs.size}")
    }

    fun applyStateChange(catName: String, newState: String, stateSetAt: Long?) {
        val current = _cats.value.toMutableMap()
        val cat = current[catName] ?: return
        current[catName] = cat.copy(
            state = CatLocation.fromString(newState),
            stateSetAt = stateSetAt
        )
        _cats.value = current
    }

    fun applyMuteState(until: Long?) {
        _mute.value = MuteState(until = until)
    }

    private fun applyMuteRaw(raw: Map<String, Any?>) {
        val until = (raw["until"] as? Double)?.toLong()
        _mute.value = MuteState(until = until)
    }

    fun setCatState(catName: String, location: CatLocation) {
        val msg = gson.toJson(mapOf(
            "type" to "cat_state_update",
            "catName" to catName,
            "state" to CatLocation.toServerString(location)
        ))
        sendWsMessage?.invoke(msg) ?: Log.w(TAG, "WS not ready")
        // Optimistic local update
        applyStateChange(catName, CatLocation.toServerString(location), System.currentTimeMillis())
    }

    fun setMute(until: Long?) {
        val msg = gson.toJson(mapOf("type" to "set_mute", "until" to until))
        sendWsMessage?.invoke(msg) ?: Log.w(TAG, "WS not ready")
        // Optimistic local update
        _mute.value = MuteState(until = until)
    }

    // Notification CRUD via WS (server does the real work and broadcasts back)
    fun addNotification(notif: CatNotification) {
        val msg = gson.toJson(mapOf("type" to "add_notification", "notification" to notif))
        sendWsMessage?.invoke(msg)
    }

    fun removeNotification(id: String) {
        val msg = gson.toJson(mapOf("type" to "remove_notification", "id" to id))
        sendWsMessage?.invoke(msg)
    }

    fun getAllCats(): List<CatState> = _cats.value.values.toList().sortedBy { it.name }
    fun getCat(name: String): CatState? = _cats.value[name]

    private fun parseNotification(m: Map<String, Any?>): CatNotification? {
        val id = m["id"] as? String ?: return null
        val type = m["type"] as? String ?: return null
        @Suppress("UNCHECKED_CAST")
        val delivRaw = m["delivery"] as? Map<String, Any?>
        val delivery = if (delivRaw != null) DeliveryOptions(
            vibration = delivRaw["vibration"] as? Boolean ?: false,
            meow = delivRaw["meow"] as? Boolean ?: false,
            phoneSound = delivRaw["phoneSound"] as? Boolean ?: false,
            phoneSoundUri = delivRaw["phoneSoundUri"] as? String,
            tts = delivRaw["tts"] as? Boolean ?: false,
            ttsText = delivRaw["ttsText"] as? String ?: "{cats} have been outside.",
            bypassSilent = delivRaw["bypassSilent"] as? Boolean ?: true
        ) else DeliveryOptions(tts = true, phoneSound = true)

        return CatNotification(
            id = id,
            type = type,
            initialDelayMinutes = (m["initialDelayMinutes"] as? Double)?.toInt() ?: 60,
            maxDelayMinutes = (m["maxDelayMinutes"] as? Double)?.toInt() ?: 240,
            absoluteTime = m["absoluteTime"] as? String,
            message = m["message"] as? String ?: "{cats} have been outside.",
            delivery = delivery
        )
    }
}
