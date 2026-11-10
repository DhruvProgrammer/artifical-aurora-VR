package com.auroraar.skyscore.model

/** Sky phase classes, ordered to match [SkyScoreFormula] anchors and probability arrays. */
enum class Phase {
    DAYLIGHT,
    SUNSET,
    TWILIGHT,
    NIGHT;

    companion object {
        fun fromIndex(index: Int): Phase = entries[index.coerceIn(0, entries.size - 1)]
    }
}

enum class CloudClass { CLEAR, PARTLY, OVERCAST, HEAVY }

/** Why the experience is (not) available right now. */
enum class Gate { OPEN, BLOCKED_DAYLIGHT, NO_SKY, CAMERA_BLOCKED }

/** Reason IDs; the UI layer owns the localized copy (EN / HI). */
enum class MessageId {
    DAYLIGHT,
    SUNSET,
    TWILIGHT,
    CLOUDY,
    PARTLY_CLEAR,
    CLEAR_DARK,
    LOW_SKY,
    INDOORS,
    CAMERA_COVERED,
}

enum class RatingBand { EXCELLENT, GREAT, GOOD, FAIR, LIMITED, UNAVAILABLE }

enum class Mode { AUTO, CREATIVE, OFF }

data class SubScores(
    val brightness: Float,
    val cloud: Float,
    val visibility: Float,
    val lighting: Float,
) {
    fun product(): Float = brightness * cloud * visibility * lighting
}

/**
 * Output of the SkyScore engine. Published as a StateFlow and mirrored into a
 * GL-thread-safe snapshot for the renderer.
 */
data class SkyConditions(
    val phase: Phase,
    val phaseProb: List<Float> = listOf(0f, 0f, 0f, 0f),
    val skyLogLum: Float = -1.8f,
    val brightnessConf: Float = 0f,
    val cloudCover: Float = 0.25f,
    val cloudClass: CloudClass = CloudClass.PARTLY,
    val cloudConf: Float = 0f,
    val skyVisibility: Float = 0f,
    val lightPollution: Float = 0.3f,
    val subScores: SubScores = SubScores(0f, 0f, 0f, 0f),
    val skyScore: Int = 0,
    val gate: Gate = Gate.NO_SKY,
    val message: MessageId? = null,
    val timestampMs: Long = 0L,
    /** 0..1, used by the renderer for the local sky-luminance gate. */
    val skyLumNorm: Float = 1f,
    val creative: Boolean = false,
) {
    val isNight: Boolean get() = phase == Phase.NIGHT
}

/** Everything the GL renderer needs. All values are already user-scaled. */
data class AuroraRenderParams(
    val enabled: Boolean = false,
    val intensity: Float = 0f,
    val saturation: Float = 1f,
    val density: Float = 1f,
    val glow: Float = 1f,
    val rayContrast: Float = 1f,
    val speed: Float = 1f,
    val sharpness: Float = 1f,
    val stars: Float = 0f,
    val nightGrade: Float = 0f,
    val cloud: Float = 0.25f,
    val skyLumNorm: Float = 1f,
    val creative: Boolean = false,
) {
    companion object {
        val IDLE = AuroraRenderParams()
    }
}

/** User-controlled knobs. User values are multipliers on top of the adaptive values. */
data class UserSettings(
    val mode: Mode = Mode.AUTO,
    val intensity: Float = 0.85f,
    val saturation: Float = 1f,
    val maxIntensity: Float = 1f,
    val respectClouds: Boolean = true,
    val lockIntensity: Boolean = false,
    val hudEnabled: Boolean = true,
    val showSubScores: Boolean = true,
)

/**
 * Coarse astronomy output. [prob] is a prior over [Phase] computed offline from
 * date/time (and optional coarse location) - never a live measurement.
 */
data class AstroPrior(
    val sunElevationDeg: Float,
    val prob: List<Float>,
    val confidence: Float,
    val hasLocation: Boolean,
)

/** Semantically same as v3's sky mask result, reduced to what SkyScore needs. */
data class SkyMaskStats(
    val fSky: Float,
    val meanConfidence: Float,
    val skyLumBottom: Float,
    val skyLumTop: Float,
    val skyLumBands: List<Float>,
)

/**
 * Per-frame features extracted on the analysis thread. Everything is normalized
 * into 0..1 unless noted.
 */
data class FrameFeatures(
    val meanLum: Float = 0f,
    val skyMeanLum: Float = 0f,
    val skyMeanLinear: Float = 0f,
    val blueDominance: Float = 0f,
    val warmth: Float = 1f,
    val saturation: Float = 0f,
    val noiseSigma: Float = 0f,
    val starCount: Int = 0,
    val clippedFraction: Float = 0f,
    val textureEnergy: Float = 0f,
    val gradientStrength: Float = 0f,
    val fSky: Float = 0f,
    val maskConfidence: Float = 0f,
    val foregroundLum: Float = 0f,
    val brightBlobCount: Int = 0,
    val brightBlobFraction: Float = 0f,
    val skyLumBands: List<Float> = emptyList(),
)

/** Camera exposure triplet plus AE state. Null fields mean "not exposed by this device". */
data class ExposureInfo(
    val exposureSec: Float?,
    val iso: Float?,
    val fNumber: Float?,
    val aeConverged: Boolean,
    val aeCompensationEv: Float,
    val timestampNs: Long,
) {
    val logExposure: Float?
        get() {
            val t = exposureSec ?: return null
            val s = iso ?: return null
            if (t <= 0f || s <= 0f) return null
            return kotlin.math.log2(t * s)
        }
}

/** One update tick for the engine. */
data class EngineInput(
    val features: FrameFeatures,
    val exposure: ExposureInfo?,
    val lux: Float?,
    val astro: AstroPrior,
    val cameraElevationDeg: Float,
    val nowMs: Long,
)
