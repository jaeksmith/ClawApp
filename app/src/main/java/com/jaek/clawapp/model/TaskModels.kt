package com.jaek.clawapp.model

data class ClawTask(
    val id: String,
    val label: String,
    val type: String,           // acp | subagent | cron
    val sessionKey: String?,
    val spawnedAt: Long,        // Unix seconds
    val timeoutAt: Long,        // Unix seconds
    val lastCheckAt: Long?,     // Unix seconds
    val status: String,         // running | stalled | complete | failed
    val channel: String?,
    val description: String?,
    val notes: String?,
    val priorFailures: Int = 0
) {
    val isActive: Boolean get() = status == "running" || status == "stalled"
    val isStalled: Boolean get() = status == "running" && System.currentTimeMillis() / 1000 > timeoutAt
    // Normalise "success" → "complete" (legacy status name)
    val effectiveStatus: String get() = when {
        isStalled -> "stalled"
        status == "success" -> "complete"
        else -> status
    }
}
