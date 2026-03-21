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
    val notes: String?
) {
    val isActive: Boolean get() = status == "running" || status == "stalled"
    val isStalled: Boolean get() = status == "running" && System.currentTimeMillis() / 1000 > timeoutAt
    val effectiveStatus: String get() = if (isStalled) "stalled" else status
}
