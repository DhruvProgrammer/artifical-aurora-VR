package com.auroraar.skyscore.estimators

import com.auroraar.skyscore.model.FrameFeatures
import com.auroraar.skyscore.policy.SkyScoreFormula

/** Addendum §7. Visibility of the open sky in the current view. */
object VisibilityEstimator {

    fun estimate(f: FrameFeatures, cameraElevationDeg: Float): Float {
        if (!f.fSky.isFinite() || f.fSky < 0.03f) return 0f
        // Pointing the phone at the ground should not report a clear sky.
        val elevation = if (cameraElevationDeg.isFinite()) cameraElevationDeg else 0f
        val tiltGate = SkyScoreFormula.smoothstep(-35f, -5f, elevation)
        return (SkyScoreFormula.visibility(f.fSky, f.maskConfidence) * tiltGate).coerceIn(0f, 1f)
    }
}
