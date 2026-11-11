package com.auroraar.capture

import java.util.Locale

/** Free vs Premium recording rules. Pure Kotlin so both tiers are unit-tested. */
enum class RecordingTier { FREE, PREMIUM }

data class RecordingPolicy(
    val tier: RecordingTier,
    /** Hard stop for Free; null means unlimited (subject to device safety limits). */
    val maxRecordMs: Long?,
    val watermarkRequired: Boolean,
    /** Short side of the recorded frame: 720 for Free, 1080 for Premium. */
    val shortSide: Int,
    val allows1080p: Boolean,
    val advancedControls: Boolean,
    val cinematicLooks: Boolean,
    /**
     * CameraX 1.4 Recorder owns codec/frame-rate selection, so these stay false
     * in this build. They exist so the settings UI can explain the limitation
     * instead of promising HEVC/60/4K that the pipeline cannot deliver.
     */
    val allowsHevc: Boolean = false,
    val allows60fps: Boolean = false,
    val allows4k: Boolean = false,
) {
    val unlimited: Boolean get() = maxRecordMs == null

    val maxDurationLabel: String? get() = maxRecordMs?.let { formatDuration(it) }

    companion object {
        val FREE = RecordingPolicy(
            tier = RecordingTier.FREE,
            maxRecordMs = 10_000L,
            watermarkRequired = true,
            shortSide = 720,
            allows1080p = false,
            advancedControls = false,
            cinematicLooks = false,
        )

        val PREMIUM = RecordingPolicy(
            tier = RecordingTier.PREMIUM,
            maxRecordMs = null,
            watermarkRequired = false,
            shortSide = 1080,
            allows1080p = true,
            advancedControls = true,
            cinematicLooks = true,
        )

        fun forTier(tier: RecordingTier): RecordingPolicy = when (tier) {
            RecordingTier.FREE -> FREE
            RecordingTier.PREMIUM -> PREMIUM
        }
    }
}

/** "0:07" — whole seconds, used by the timer pill and the Free limit. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
