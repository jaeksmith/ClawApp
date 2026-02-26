package com.jaek.clawapp.repository

import android.util.Log
import com.google.gson.Gson
import com.jaek.clawapp.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages cat state for the UI.
 * State is bootstrapped via WebSocket snapshot on connect,
 * and updated via WS pushes. State changes are sent via WS.
 *
 * HTTP API (port 18791) is server-local only — not reachable from phone.
 * All app comms go through WebSocket (port 18790).
 */
class CatRepository {
    companion object {
        const val TAG = "CatRepository"
    }

    private val gson = Gson()

    // Observable state for UI
    private val _cats = MutableStateFlow<Map<String, CatState>>(emptyMap())
    val cats: StateFlow<Map<String, CatState>> = _cats.asStateFlow()

    private val _notifications = MutableStateFlow<List<CatNotification>>(emptyList())
    val notifications: StateFlow<List<CatNotification>> = _notifications.asStateFlow()

    // Callback to send WS messages
    var sendWsMessage: ((String) -> Unit)? = null

    fun applySnapshot(catsMap: Map<String, Any?>, notifsList: List<Any?>) {
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
        Log.i(TAG, "Cat snapshot applied: ${parsedCats.keys}")
    }

    fun applyStateChange(catName: String, newState: String, stateSetAt: Long?) {
        val current = _cats.value.toMutableMap()
        val cat = current[catName] ?: return
        current[catName] = cat.copy(
            state = CatLocation.fromString(newState),
            stateSetAt = stateSetAt
        )
        _cats.value = current
        Log.i(TAG, "Cat state updated via push: $catName -> $newState")
    }

    // Send state change via WebSocket
    fun setCatState(catName: String, location: CatLocation) {
        val msg = gson.toJson(mapOf(
            "type" to "cat_state_update",
            "catName" to catName,
            "state" to CatLocation.toServerString(location)
        ))
        sendWsMessage?.invoke(msg) ?: Log.w(TAG, "WS not ready, can't send state change")

        // Optimistic local update
        applyStateChange(catName, CatLocation.toServerString(location), System.currentTimeMillis())
    }

    fun getCat(name: String): CatState? = _cats.value[name]

    fun getAllCats(): List<CatState> = _cats.value.values.toList()
        .sortedBy { it.name }
}
