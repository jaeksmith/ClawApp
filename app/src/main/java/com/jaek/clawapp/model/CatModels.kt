package com.jaek.clawapp.model

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

/**
 * Combo delivery options for a notification.
 * Any combination of options can be enabled simultaneously.
 */
data class DeliveryOptions(
    val vibration: Boolean = false,
    val meow: Boolean = false,
    val phoneSound: Boolean = false,
    val phoneSoundUri: String? = null,
    val tts: Boolean = false,
    /** If false, app respects the phone's ringer/silent mode for sound. True = always play (alarm stream). */
    val bypassSilent: Boolean = true
)

/**
 * A notification rule.
 *
 * type = "repeating": fires initialDelayMinutes after lastCatOutAt,
 *   then doubles the interval up to maxDelayMinutes, repeating at max.
 *
 * type = "absolute": fires at absoluteTime (HH:MM) each day if ≥1 cat outside.
 */
data class CatNotification(
    val id: String,
    val type: String,               // "repeating" | "absolute"
    val initialDelayMinutes: Int = 60,   // repeating: delay before first fire
    val maxDelayMinutes: Int = 240,      // repeating: cap on doubling
    val absoluteTime: String? = null,    // absolute: "HH:MM"
    val message: String = "{cats} have been outside.",
    val delivery: DeliveryOptions = DeliveryOptions(tts = true, phoneSound = true)
)

data class MuteState(
    val until: Long? = null
) {
    val isMuted: Boolean get() = until != null && System.currentTimeMillis() < until
}

// Server response shapes
data class CatsResponse(
    val cats: Map<String, CatServerState>,
    val notifications: List<CatNotification>,
    val lastCatOutAt: Long?,
    val outdoorCats: List<String>,
    val mute: MuteState?
)

data class CatServerState(
    val state: String,
    val stateSetAt: Long?,
    val outdoorOnly: Boolean,
    val image: String?
)

data class CatStateChangedPayload(
    val catName: String,
    val state: String,
    val stateSetAt: Long?,
    val source: String
)
