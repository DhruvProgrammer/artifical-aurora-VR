package com.auroraar.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.auroraar.calibration.CalibrationRecorder
import com.auroraar.skyscore.SkyScoreEngine
import com.auroraar.skyscore.features.AstronomyPrior
import com.auroraar.skyscore.model.AstroPrior
import com.auroraar.skyscore.model.EngineInput
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Camera binding state so the UI can offer a retry instead of silently failing. */
enum class CameraStatus { IDLE, STARTING, RUNNING, ERROR }

/**
 * Binds CameraX Preview + ImageAnalysis and feeds the SkyScore engine at <=5 Hz.
 * All per-frame work happens on a single analysis thread; the engine publishes
 * StateFlows to the UI.
 */
class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val engine: SkyScoreEngine,
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val extractor = SkyFeatureExtractor()
    private val exposureReader = ExposureReader()
    private val lightReader = LightSensorReader(context)
    private val poseReader = PoseReader(context)
    private val locationProvider = LocationProvider(context)

    private val _status = MutableStateFlow(CameraStatus.IDLE)
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    @Volatile
    var recorder: CalibrationRecorder? = null

    @Volatile
    var useLocation: Boolean = false
        set(value) {
            field = value
            astroCacheMs = 0L
            locationCacheMs = 0L
        }

    @Volatile
    private var running = false

    @Volatile
    private var closed = false

    private var provider: ProcessCameraProvider? = null
    private var lastAcceptedMs = 0L

    // Location + solar position are refreshed on a slow cadence, not per frame.
    private var astroCache: AstroPrior? = null

    @Volatile
    private var astroCacheMs = 0L
    private var locationCache: Pair<Double, Double>? = null

    @Volatile
    private var locationCacheMs = 0L

    val cameraElevationDeg: Float
        get() = poseReader.elevationDeg

    fun start(previewView: PreviewView) {
        if (closed || running) return
        running = true
        _status.value = CameraStatus.STARTING
        lightReader.start()
        poseReader.start()

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!running) {
                runCatching { future.get().unbindAll() }
                return@addListener
            }
            try {
                val cameraProvider = future.get()
                if (!running) return@addListener
                provider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                @Suppress("DEPRECATION")
                val analysisBuilder = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                exposureReader.attach(analysisBuilder)
                val analysis = analysisBuilder.build().also {
                    it.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                _status.value = CameraStatus.RUNNING
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to bind camera", t)
                runCatching { provider?.unbindAll() }
                provider = null
                running = false
                lightReader.stop()
                poseReader.stop()
                _status.value = CameraStatus.ERROR
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        running = false
        runCatching { provider?.unbindAll() }
        provider = null
        lightReader.stop()
        poseReader.stop()
        if (_status.value != CameraStatus.IDLE) _status.value = CameraStatus.IDLE
    }

    /** Releases the analysis thread. Call once when the screen leaves composition. */
    fun close() {
        closed = true
        stop()
        analysisExecutor.shutdownNow()
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastAcceptedMs < ANALYSIS_INTERVAL_MS) return
            lastAcceptedMs = now

            val features = extractor.analyze(proxy, poseReader.elevationDeg)

            val input = EngineInput(
                features = features,
                exposure = exposureReader.latest,
                lux = lightReader.lux,
                astro = astroForFrame(),
                cameraElevationDeg = poseReader.elevationDeg,
                nowMs = now,
            )
            val conditions = engine.onInput(input)
            recorder?.maybeRecord(features, conditions, input.exposure, input.lux, input.cameraElevationDeg)
        } catch (t: Throwable) {
            Log.w(TAG, "Frame analysis failed", t)
        } finally {
            proxy.close()
        }
    }

    private fun astroForFrame(): AstroPrior {
        val wallMs = System.currentTimeMillis()
        val stale = astroCache == null || wallMs < astroCacheMs || wallMs - astroCacheMs > ASTRO_REFRESH_MS
        if (stale) {
            val latLon = if (useLocation) locationForFrame(wallMs) else null
            astroCache = AstronomyPrior.forTime(wallMs, latLon?.first, latLon?.second)
            astroCacheMs = wallMs
        }
        return astroCache ?: AstronomyPrior.forTime(wallMs, null, null)
    }

    private fun locationForFrame(wallMs: Long): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val stale = locationCache == null ||
            wallMs < locationCacheMs ||
            wallMs - locationCacheMs > LOCATION_REFRESH_MS
        if (stale) {
            locationCache = locationProvider.lastKnown()
            locationCacheMs = wallMs
        }
        return locationCache
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CameraSession"
        private const val ANALYSIS_INTERVAL_MS = 200L
        private const val ASTRO_REFRESH_MS = 30_000L
        private const val LOCATION_REFRESH_MS = 10 * 60_000L
    }
}
