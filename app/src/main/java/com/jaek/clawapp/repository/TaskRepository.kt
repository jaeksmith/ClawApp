package com.jaek.clawapp.repository

import android.util.Log
import com.jaek.clawapp.AppLogger
import com.jaek.clawapp.model.ClawTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TaskRepository(
    private val baseUrl: String,   // e.g. http://100.126.78.128:18791
    private val token: String
) {
    companion object {
        const val TAG = "TaskRepository"
        const val POLL_INTERVAL_MS = 30_000L
    }

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeTasks = MutableStateFlow<List<ClawTask>>(emptyList())
    val activeTasks: StateFlow<List<ClawTask>> = _activeTasks.asStateFlow()

    private val _recentCompleted = MutableStateFlow<List<ClawTask>>(emptyList())
    val recentCompleted: StateFlow<List<ClawTask>> = _recentCompleted.asStateFlow()

    private val _lastFetchMs = MutableStateFlow<Long?>(null)
    val lastFetchMs: StateFlow<Long?> = _lastFetchMs.asStateFlow()

    private var pollJob: Job? = null

    fun start() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                fetchAll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() { pollJob?.cancel() }

    fun refreshNow() {
        scope.launch { fetchAll() }
    }

    private suspend fun fetchAll() {
        try {
            val active = fetchTaskList("/tasks")
            val completed = fetchTaskList("/tasks/completed")
            _activeTasks.value = active
            // Show last 10 completed, most recent first
            _recentCompleted.value = completed
                .filter { it.status == "complete" || it.status == "failed" }
                .sortedByDescending { it.spawnedAt }
                .take(10)
            _lastFetchMs.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Task fetch failed: ${e.message}")
        }
    }

    private suspend fun fetchTaskList(path: String): List<ClawTask> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        parseTasks(body)
    }

    private fun parseTasks(json: String): List<ClawTask> {
        return try {
            val arr = JSONObject(json).getJSONArray("tasks")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                ClawTask(
                    id = o.optString("id"),
                    label = o.optString("label", "Unnamed task"),
                    type = o.optString("type", "unknown"),
                    sessionKey = o.optString("sessionKey").ifEmpty { null },
                    spawnedAt = o.optLong("spawnedAt", 0),
                    timeoutAt = o.optLong("timeoutAt", 0),
                    lastCheckAt = if (o.has("lastCheckAt")) o.optLong("lastCheckAt") else null,
                    status = o.optString("status", "running"),
                    channel = o.optString("channel").ifEmpty { null },
                    description = o.optString("description").ifEmpty { null },
                    notes = o.optString("notes").ifEmpty { null }
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Task parse error: ${e.message}")
            emptyList()
        }
    }
}
