package com.jaek.clawapp.repository

import com.jaek.clawapp.AppLogger
import com.jaek.clawapp.model.BloodPressureEntry
import com.jaek.clawapp.model.HeartRateEntry
import com.jaek.clawapp.model.WeightEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WeightRepository {
    companion object { const val TAG = "WeightRepository" }

    private val _entries = MutableStateFlow<List<WeightEntry>>(emptyList())
    val entries: StateFlow<List<WeightEntry>> = _entries.asStateFlow()

    private val _heartRate = MutableStateFlow<List<HeartRateEntry>>(emptyList())
    val heartRate: StateFlow<List<HeartRateEntry>> = _heartRate.asStateFlow()

    private val _bloodPressure = MutableStateFlow<List<BloodPressureEntry>>(emptyList())
    val bloodPressure: StateFlow<List<BloodPressureEntry>> = _bloodPressure.asStateFlow()

    var sendWsMessage: ((String) -> Unit)? = null

    fun applyHealthData(hrRaw: List<Any?>, bpRaw: List<Any?>) {
        _heartRate.value = hrRaw.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            val date = m["date"] as? String ?: return@mapNotNull null
            val bpm = (m["bpm"] as? Number)?.toInt() ?: return@mapNotNull null
            HeartRateEntry(date, bpm)
        }.sortedBy { it.date }
        _bloodPressure.value = bpRaw.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            val date = m["date"] as? String ?: return@mapNotNull null
            val sys = (m["systolic"] as? Number)?.toInt() ?: return@mapNotNull null
            val dia = (m["diastolic"] as? Number)?.toInt() ?: return@mapNotNull null
            BloodPressureEntry(date, sys, dia)
        }.sortedBy { it.date }
        AppLogger.i(TAG, "Health data updated: ${_heartRate.value.size} HR, ${_bloodPressure.value.size} BP")
    }

    fun saveHeartRate(date: String, bpm: Int) {
        val msg = com.google.gson.Gson().toJson(mapOf("type" to "save_heart_rate", "date" to date, "bpm" to bpm))
        sendWsMessage?.invoke(msg) ?: AppLogger.w(TAG, "WS not ready")
        val current = _heartRate.value.toMutableList()
        current.removeAll { it.date == date }
        current.add(HeartRateEntry(date, bpm))
        _heartRate.value = current.sortedBy { it.date }
    }

    fun saveBloodPressure(date: String, systolic: Int, diastolic: Int) {
        val msg = com.google.gson.Gson().toJson(mapOf("type" to "save_blood_pressure", "date" to date, "systolic" to systolic, "diastolic" to diastolic))
        sendWsMessage?.invoke(msg) ?: AppLogger.w(TAG, "WS not ready")
        val current = _bloodPressure.value.toMutableList()
        current.removeAll { it.date == date }
        current.add(BloodPressureEntry(date, systolic, diastolic))
        _bloodPressure.value = current.sortedBy { it.date }
    }

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
