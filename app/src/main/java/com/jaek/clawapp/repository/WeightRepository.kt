package com.jaek.clawapp.repository

import com.jaek.clawapp.AppLogger
import com.jaek.clawapp.model.WeightEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WeightRepository {
    companion object { const val TAG = "WeightRepository" }

    private val _entries = MutableStateFlow<List<WeightEntry>>(emptyList())
    val entries: StateFlow<List<WeightEntry>> = _entries.asStateFlow()

    var sendWsMessage: ((String) -> Unit)? = null

    fun applyEntries(raw: List<Any?>) {
        val parsed = raw.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            val date = m["date"] as? String ?: return@mapNotNull null
            val w = (m["weight"] as? Number)?.toFloat() ?: return@mapNotNull null
            WeightEntry(date = date, weight = w, notes = m["notes"] as? String)
        }.sortedBy { it.date }
        _entries.value = parsed
        AppLogger.i(TAG, "Weight entries updated: ${parsed.size}")
    }

    fun saveEntry(date: String, weight: Float, notes: String = "") {
        val msg = com.google.gson.Gson().toJson(mapOf(
            "type" to "save_weight_entry",
            "date" to date,
            "weight" to weight,
            "notes" to notes
        ))
        sendWsMessage?.invoke(msg) ?: AppLogger.w(TAG, "WS not ready")
        // Optimistic local update
        val current = _entries.value.toMutableList()
        current.removeAll { it.date == date }
        current.add(WeightEntry(date = date, weight = weight, notes = notes.ifEmpty { null }))
        _entries.value = current.sortedBy { it.date }
    }
}
