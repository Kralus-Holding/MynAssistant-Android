package com.mynkralus.mynassistant

/**
 * Persisted lifecycle states for the opt-in wake-word foreground service.
 * Keep these values stable: Settings and diagnostic logs read them across process restarts.
 */
internal enum class MynWakeWordPhase(val persistedValue: String) {
    OFF("OFF"),
    WAITING_FOR_PERMISSION("WAITING_FOR_PERMISSION"),
    STARTING("STARTING"),
    DOWNLOADING_MODEL("DOWNLOADING_MODEL"),
    EXTRACTING_MODEL("EXTRACTING_MODEL"),
    VALIDATING_MODEL("VALIDATING_MODEL"),
    LOADING_ENGINE("LOADING_ENGINE"),
    STARTING_MICROPHONE("STARTING_MICROPHONE"),
    LISTENING("LISTENING"),
    RETRYING("RETRYING"),
    ERROR("ERROR");

    companion object {
        fun fromPersisted(value: String?): MynWakeWordPhase =
            entries.firstOrNull { it.persistedValue == value } ?: OFF
    }
}
