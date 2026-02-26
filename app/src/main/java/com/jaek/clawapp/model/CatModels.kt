package com.jaek.clawapp.model

import com.google.gson.annotations.SerializedName

enum class CatLocation(val displayName: String, val emoji: String) {
    INSIDE("Inside", "🏠"),
    OUTSIDE("Outside", "🌿"),
    UNKNOWN("Unknown", "❓");

    companion object {
        fun fromString(s: String?) = when (s?.lowercase()) {
            "inside" -> INSIDE
            "outside" -> OUTSIDE
            else -> UNKNOWN
        }
        fun toServerString(loc: CatLocation) = loc.name.lowercase()
    }
}

data class CatState(
    val name: String,
    val state: CatLocation,
    val stateSetAt: Long?,
    val outdoorOnly: Boolean,
    val image: String?
)

data class CatNotification(
    val id: String,
    val type: String,               // "relative" | "absolute"
    val offsetMinutes: Int?,        // for relative
    val absoluteTime: String?,      // for absolute, "HH:MM"
    val message: String,
    val notificationType: String    // "vibration" | "noise" | "noise_tts"
)

// Server response shapes
data class CatsResponse(
    val cats: Map<String, CatServerState>,
    val notifications: List<CatNotification>,
    val lastCatOutAt: Long?,
    val outdoorCats: List<String>
)

data class CatServerState(
    val state: String,
    val stateSetAt: Long?,
    val outdoorOnly: Boolean,
    val image: String?
)

// WebSocket push payloads
data class CatStateChangedPayload(
    val catName: String,
    val state: String,
    val stateSetAt: Long?,
    val source: String
)
