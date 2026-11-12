package com.auroraar.skyscore

import com.auroraar.skyscore.estimators.BrightnessEstimator
import com.auroraar.skyscore.estimators.CloudEstimator
import com.auroraar.skyscore.estimators.LightingEstimator
import com.auroraar.skyscore.estimators.PhaseClassifier
import com.auroraar.skyscore.estimators.PhaseHysteresis
import com.auroraar.skyscore.estimators.VisibilityEstimator
import com.auroraar.skyscore.model.AuroraRenderParams
import com.auroraar.skyscore.model.CloudClass
import com.auroraar.skyscore.model.EngineInput
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.Mode
import com.auroraar.skyscore.model.Phase
import com.auroraar.skyscore.model.SkyConditions
import com.auroraar.skyscore.model.SubScores
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.skyscore.policy.IntensityMapper
import com.auroraar.skyscore.policy.MessagePolicy
import com.auroraar.skyscore.policy.SkyScoreFormula
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Orquesta el addendum v4: features -> brightness/phase/cloud/visibility/lighting
 * -> SkyScore -> adaptive render parameters.
 *
 * Pure Kotlin on purpose: the analysis thread feeds it [EngineInput], JVM tests
 * drive it with synthetic inputs, and the UI observes [conditions]/[renderParams].
 */
class SkyScoreEngine(initialSettings: UserSettings = UserSettings()) {

    private val _conditions = MutableStateFlow(initialConditions())
    val conditions: StateFlow<SkyConditions> = _conditions.asStateFlow()

    private val _renderParams = MutableStateFlow(AuroraRenderParams.IDLE)
    val renderParams: StateFlow<AuroraRenderParams> = _renderParams.asStateFlow()

    @Volatile
    private var settings: UserSettings = sanitizeSettings(initialSettings)
    private var hysteresis = PhaseHysteresis(Phase.DAYLIGHT)

    private var smoothedLogLum = 0.5f
    private var smoothedScore = 0f
    private var smoothedParams: AuroraRenderParams? = null
    private var lastUpdateMs = 0L
    private var seeded = false
    private var prevLogExposure: Float? = null
    private var brightnessHoldUntilMs = 0L
    private var heldLogLum: Float? = null
    private val pollutionWindow = ArrayDeque<Float>()

    fun setSettings(update: UserSettings) {
        settings = sanitizeSettings(update)
        // Re-derive render parameters on the next frame; no state reset.
    }

    fun reset() {
        hysteresis = PhaseHysteresis(Phase.DAYLIGHT)
        smoothedLogLum = 0.5f
        smoothedScore = 0f
        smoothedParams = null
        lastUpdateMs = 0L
        seeded = false
        prevLogExposure = null
        heldLogLum = null
        pollutionWindow.clear()
        _conditions.value = initialConditions()
        _renderParams.value = AuroraRenderParams.IDLE
    }

    /** Called from the analysis thread. Publishes [conditions] and returns the same snapshot. */
    fun onInput(input: EngineInput): SkyConditions {
        val now = input.nowMs
        val f = input.features
        val isFirst = lastUpdateMs == 0L
        val elapsedSec = if (isFirst || now < lastUpdateMs) 0.2f else (now - lastUpdateMs) / 1000f
        val dt = if (elapsedSec.isFinite()) elapsedSec.coerceIn(0.02f, 2f) else 0.2f
        lastUpdateMs = now

        // --- AE awareness: hold the photometric estimate through exposure swings.
        val logExposure = input.exposure?.logExposure?.takeIf { it.isFinite() }
        if (logExposure != null && prevLogExposure != null && abs(logExposure - prevLogExposure!!) > 0.3f) {
            brightnessHoldUntilMs = now + 400L
        }
        prevLogExposure = logExposure

        val estimate = BrightnessEstimator.estimate(f, input.exposure)
        var logLum = estimate.logLum.takeIf { it.isFinite() }?.coerceIn(-4f, 5f) ?: -1.8f
        var lumConf = estimate.confidence.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val held = heldLogLum?.takeIf { it.isFinite() }
        if (now < brightnessHoldUntilMs && held != null) {
            logLum = held
            lumConf *= 0.5f
        }
        heldLogLum = logLum

        val tauLum = if (abs(logLum - smoothedLogLum) > 1.5f) 0.25f else 0.6f
        val lumStep = (logLum - smoothedLogLum) * (1f - exp(-dt / tauLum))
        if (lumStep.isFinite()) smoothedLogLum = (smoothedLogLum + lumStep).coerceIn(-5f, 6f)

        // --- Phase fusion + hysteresis.
        val prob = PhaseClassifier.fuse(
            logLum = smoothedLogLum,
            lumValid = estimate.photometric || lumConf >= 0.3f,
            lumConfidence = lumConf,
            features = f,
            exposure = input.exposure,
            lux = input.lux?.takeIf { it.isFinite() && it >= 0f },
            astro = input.astro,
        )
        val probList = prob.toList()
        if (!seeded) {
            hysteresis.reset(Phase.fromIndex(prob.indices.maxByOrNull { prob[it] } ?: 3))
            seeded = true
        }
        val phase = hysteresis.update(probList, now)
        val darkness = SkyScoreFormula.darkness(probList)

        // --- Cloud (day + night models, weighted by darkness), visibility, lighting.
        val dayCloud = CloudEstimator.daytime(f)
        val nightCloud = CloudEstimator.night(f)
        val cloud = CloudEstimator.blend(dayCloud, nightCloud, probList)
        val effectiveCloud = SkyScoreFormula.cloudEffective(cloud.cover, cloud.confidence)

        val elevation = if (input.cameraElevationDeg.isFinite()) input.cameraElevationDeg else 0f
        val visibility = VisibilityEstimator.estimate(f, elevation)

        val lux = input.lux?.takeIf { it.isFinite() && it >= 0f }
        val lightingSample = LightingEstimator.estimate(f, probList, lux)
        if (lightingSample.isFinite()) pollutionWindow.addLast(lightingSample.coerceIn(0f, 1f))
        while (pollutionWindow.size > 7) pollutionWindow.removeFirst()
        val pollution = median(pollutionWindow)

        val subScores = SubScores(
            brightness = darkness,
            cloud = SkyScoreFormula.cloudScore(cloud.cover, cloud.confidence, probList),
            visibility = visibility,
            lighting = SkyScoreFormula.lightingScore(pollution),
        )

        // --- Gates.
        val cameraBlocked = SkyScoreFormula.cameraBlocked(
            meanLum = f.meanLum,
            textureEnergy = f.textureEnergy,
            noiseSigma = f.noiseSigma,
            starCount = f.starCount,
            lux = lux,
        )
        val creative = settings.mode == Mode.CREATIVE
        val gate = SkyScoreFormula.gate(darkness, visibility, cameraBlocked, creative)
        val indoors = gate == Gate.NO_SKY && f.meanLum > 0.08f
        val gatedScore = if (gate == Gate.OPEN) SkyScoreFormula.score(subScores) else 0

        val tauScore = if (gatedScore < smoothedScore) 0.8f else 2.5f
        val scoreStep = (gatedScore - smoothedScore) * (1f - exp(-dt / tauScore))
        if (scoreStep.isFinite()) smoothedScore = (smoothedScore + scoreStep).coerceIn(0f, 100f)
        // A closed gate always reports 0, even while the internal smoother is
        // still decaying. Otherwise a daylight/covered frame briefly carries the
        // previous night's score into the UI and the calibration JSON.
        val skyScore = if (gate == Gate.OPEN) smoothedScore.roundToInt().coerceIn(0, 100) else 0

        val skyLumNorm = ((smoothedLogLum + 2f) / 4.5f).coerceIn(0f, 1f)

        // --- Adaptive rendering. Creative may bypass darkness, never "no sky".
        val index = when (settings.mode) {
            Mode.OFF -> 0f
            Mode.AUTO -> if (gate == Gate.OPEN) skyScore / 100f else 0f
            Mode.CREATIVE -> when {
                gate != Gate.OPEN -> 0f
                settings.lockIntensity -> 1f
                else -> max(skyScore / 100f, 0.60f)
            }
        }
        val target = IntensityMapper.map(
            intensityIndex = index,
            darkness = darkness,
            cloudEffective = if (creative && !settings.respectClouds) 0f else effectiveCloud,
            settings = settings,
            creative = creative,
            skyLumNorm = skyLumNorm,
        )
        val renderParams = smoothRenderParams(target, dt)

        val conditions = SkyConditions(
            phase = phase,
            phaseProb = probList,
            skyLogLum = smoothedLogLum,
            brightnessConf = lumConf.coerceIn(0f, 1f),
            cloudCover = cloud.cover,
            cloudClass = cloud.cloudClass,
            cloudConf = cloud.confidence,
            skyVisibility = visibility,
            lightPollution = pollution,
            subScores = subScores,
            skyScore = skyScore,
            gate = gate,
            message = MessagePolicy.message(
                gate,
                phase,
                cloud.cloudClass,
                darkness,
                indoors,
                creative,
                auroraShown = renderParams.enabled,
            ),
            timestampMs = now,
            skyLumNorm = skyLumNorm,
            creative = creative,
        )

        _conditions.value = conditions
        _renderParams.value = if (settings.mode == Mode.OFF) AuroraRenderParams.IDLE else renderParams
        return conditions
    }

    private fun smoothRenderParams(target: AuroraRenderParams, dt: Float): AuroraRenderParams {
        val current = smoothedParams
        if (current == null) {
            smoothedParams = target
            return target
        }
        if (!target.enabled) {
            smoothedParams = target
            return target
        }
        val k = 1f - exp(-dt / 1.2f)
        fun approach(a: Float, b: Float): Float {
            val v = a + (b - a) * k
            return if (v.isFinite()) v else b
        }
        val next = target.copy(
            intensity = approach(current.intensity, target.intensity),
            saturation = approach(current.saturation, target.saturation),
            density = approach(current.density, target.density),
            glow = approach(current.glow, target.glow),
            rayContrast = approach(current.rayContrast, target.rayContrast),
            speed = approach(current.speed, target.speed),
            sharpness = approach(current.sharpness, target.sharpness),
            stars = approach(current.stars, target.stars),
            nightGrade = approach(current.nightGrade, target.nightGrade),
            cloud = approach(current.cloud, target.cloud),
            skyLumNorm = approach(current.skyLumNorm, target.skyLumNorm),
        )
        smoothedParams = next
        return next
    }

    private fun median(values: ArrayDeque<Float>): Float {
        if (values.isEmpty()) return 0.3f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    companion object {
        fun initialConditions() = SkyConditions(
            phase = Phase.DAYLIGHT,
            skyScore = 0,
            gate = Gate.NO_SKY,
            message = null,
        )

        fun cloudClassFor(cover: Float): CloudClass = CloudEstimator.classify(cover)

        /** Rejects NaN/Infinity and out-of-range slider values from prefs or tests. */
        fun sanitizeSettings(s: UserSettings): UserSettings = s.copy(
            intensity = s.intensity.finiteOr(0.85f).coerceIn(0f, 1f),
            saturation = s.saturation.finiteOr(1f).coerceIn(0f, 2f),
            maxIntensity = s.maxIntensity.finiteOr(1f).coerceIn(0f, 1f),
            styleId = com.auroraar.premium.styles.AuroraStyleRegistry.sanitizeId(s.styleId),
            layers = s.layers.coerceIn(1, 4),
            coverage = s.coverage.finiteOr(0.75f).coerceIn(0f, 1f),
            waveAmp = s.waveAmp.finiteOr(0.5f).coerceIn(0f, 1f),
            waveFreq = s.waveFreq.finiteOr(0.5f).coerceIn(0f, 1f),
            advancedSpeed = s.advancedSpeed.finiteOr(1f).coerceIn(0.4f, 2f),
            raySharpness = s.raySharpness.finiteOr(1f).coerceIn(0.3f, 1.5f),
            burst = s.burst.finiteOr(0f).coerceIn(0f, 1f),
            direction = if (s.direction < 0f) -1f else 1f,
            automationParam = s.automationParam.finiteOr(0.5f).coerceIn(0f, 1f),
        )

        private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
    }
}
