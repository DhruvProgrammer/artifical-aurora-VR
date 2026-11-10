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
import com.auroraar.skyscore.model.EngineInput
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Binds CameraX Preview + ImageAnalysis and feeds the SkyScore engine at <=10 Hz.
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

    var recorder: CalibrationRecorder? = null
    var useLocation: Boolean = false
        set(value) {
            field = value
            extractor.resetTemporal()
        }

    private var provider: ProcessCameraProvider? = null
    private var running = false
    private var lastAcceptedMs = 0L

    val cameraElevationDeg: Float
        get() = poseReader.elevationDeg

    fun start(previewView: PreviewView) {
        if (running) return
        running = true
        lightReader.start()
        poseReader.start()

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
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
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to bind camera", t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { provider?.unbindAll() }
        lightReader.stop()
        poseReader.stop()
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAcceptedMs < ANALYSIS_INTERVAL_MS) return
            lastAcceptedMs = now

            val features = extractor.analyze(proxy, poseReader.elevationDeg)

            val latLon = if (useLocation && hasLocationPermission()) locationProvider.lastKnown() else null
            val astro = AstronomyPrior.forTime(
                epochMs = System.currentTimeMillis(),
                latitude = latLon?.first,
                longitude = latLon?.second,
            )

            val input = EngineInput(
                features = features,
                exposure = exposureReader.latest,
                lux = lightReader.lux,
                astro = astro,
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

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CameraSession"
        private const val ANALYSIS_INTERVAL_MS = 100L
    }
}
