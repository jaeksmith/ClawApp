package com.jaek.clawapp.repository

import com.jaek.clawapp.AppLogger
import com.jaek.clawapp.model.ClawTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class TaskRepository {
    companion object { const val TAG = "TaskRepository" }

    private val _activeTasks = MutableStateFlow<List<ClawTask>>(emptyList())
    val activeTasks: StateFlow<List<ClawTask>> = _activeTasks.asStateFlow()

    private val _recentCompleted = MutableStateFlow<List<ClawTask>>(emptyList())
    val recentCompleted: StateFlow<List<ClawTask>> = _recentCompleted.asStateFlow()

    private val _lastUpdateMs = MutableStateFlow<Long?>(null)
    val lastUpdateMs: StateFlow<Long?> = _lastUpdateMs.asStateFlow()

    /** Called by ClawService when a task_update command arrives via WS */
    fun applyUpdate(activeRaw: List<Any?>, completedRaw: List<Any?>) {
        _activeTasks.value = parseTasks(activeRaw)
        _recentCompleted.value = parseTasks(completedRaw)
            .sortedByDescending { it.spawnedAt }
            .take(20)
        _lastUpdateMs.value = System.currentTimeMillis()
        AppLogger.i(TAG, "Tasks updated: ${_activeTasks.value.size} active, ${_recentCompleted.value.size} completed")
    }

    private fun parseTasks(raw: List<Any?>): List<ClawTask> =
        raw.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            ClawTask(
                id          = m["id"] as? String ?: return@mapNotNull null,
                label       = m["label"] as? String ?: "Unnamed task",
                type        = m["type"] as? String ?: "unknown",
                sessionKey  = m["sessionKey"] as? String,
                spawnedAt   = (m["spawnedAt"] as? Number)?.toLong() ?: 0L,
                timeoutAt   = (m["timeoutAt"] as? Number)?.toLong() ?: 0L,
                lastCheckAt = (m["lastCheckAt"] as? Number)?.toLong(),
                status      = m["status"] as? String ?: "running",
                channel     = m["channel"] as? String,
                description = m["description"] as? String,
                notes       = m["notes"] as? String,
                priorFailures = (m["priorFailures"] as? Number)?.toInt() ?: 0
            )
        }
}
