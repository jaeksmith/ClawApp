package com.jaek.clawapp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object AppLogger {
    private const val MAX_ENTRIES = 300

    data class LogEntry(val timestamp: String, val tag: String, val message: String, val level: String)

    private val _entries = ConcurrentLinkedDeque<LogEntry>()
    private val _flow = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _flow.asStateFlow()

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String, level: String = "I") {
        val entry = LogEntry(fmt.format(Date()), tag, message, level)
        _entries.addLast(entry)
        while (_entries.size > MAX_ENTRIES) _entries.pollFirst()
        _flow.value = _entries.toList()
        // Also forward to Android logcat
        when (level) {
            "E" -> android.util.Log.e(tag, message)
            "W" -> android.util.Log.w(tag, message)
            else -> android.util.Log.i(tag, message)
        }
    }

    fun i(tag: String, message: String) = log(tag, message, "I")
    fun w(tag: String, message: String) = log(tag, message, "W")
    fun e(tag: String, message: String) = log(tag, message, "E")
    fun e(tag: String, message: String, t: Throwable) = log(tag, "$message: ${t.message}", "E")

    fun clear() {
        _entries.clear()
        _flow.value = emptyList()
    }

    fun getAll(): List<LogEntry> = _entries.toList()
}
