package com.auroraar.capture

import android.net.Uri

enum class RecordingPhase { IDLE, PREPARING, RECORDING, PAUSED, FINALIZING, SAVED, ERROR }

enum class RecordingError {
    START_FAILED,
    STORAGE,
    ENCODER_FAILED,
    SOURCE_INACTIVE,
    UNKNOWN,
}

/** Recorded duration excludes paused time. Null outside an active recording. */
fun interface RecordingClock {
    fun recordedElapsedMs(): Long?
}

/** Everything the recording UI and the GL watermark need. */
data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val elapsedMs: Long = 0L,
    val limitMs: Long? = null,
    val uri: Uri? = null,
    val error: RecordingError? = null,
    val tier: RecordingTier = RecordingTier.FREE,
    val hasAudio: Boolean = false,
    /** True when the Free limit stopped the clip (drives the upsell banner). */
    val autoStopped: Boolean = false,
    /** Non-blocking safety warnings collected at start or during recording. */
    val warnings: Set<GuardWarning> = emptySet(),
    /** Set when the safety monitor stopped the clip; the file is still finalised. */
    val stoppedForSafety: GuardRefusal? = null,
) {
    val active: Boolean
        get() = phase == RecordingPhase.PREPARING ||
            phase == RecordingPhase.RECORDING ||
            phase == RecordingPhase.PAUSED ||
            phase == RecordingPhase.FINALIZING

    val watermarkVisible: Boolean
        get() = tier == RecordingTier.FREE && active
}
