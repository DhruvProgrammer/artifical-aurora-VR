package com.auroraar.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import com.auroraar.skyscore.model.ExposureInfo

/**
 * Reads the exposure triplet from Camera2 CaptureResult via the CameraX interop
 * layer (addendum §2). Devices that don't expose these keys simply report null,
 * and the engine falls back to appearance cues.
 */
class ExposureReader {

    @Volatile
    var latest: ExposureInfo? = null
        private set

    @OptIn(ExperimentalCamera2Interop::class)
    fun attach(builder: ImageAnalysis.Builder) {
        Camera2Interop.Extender(builder).setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: android.hardware.camera2.CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    read(result)
                }
            },
        )
    }

    private fun read(result: TotalCaptureResult) {
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val aperture = result.get(CaptureResult.LENS_APERTURE)
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val aeComp = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION) ?: 0

        latest = ExposureInfo(
            exposureSec = exposureNs?.let { it / 1_000_000_000f },
            iso = iso?.toFloat(),
            fNumber = aperture,
            aeConverged = aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED,
            aeCompensationEv = aeComp.toFloat(),
            timestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0L,
        )
    }
}
