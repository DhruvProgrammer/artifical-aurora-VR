package com.auroraar.ui

import android.net.Uri

/**
 * The one full-screen surface that is open above the camera, if any.
 *
 * The screen used to keep seven independent booleans, so nothing prevented
 * "settings and gallery are both open" or "the preview dialog is open but its
 * URI is null". A sealed type makes both states unrepresentable.
 */
sealed interface CameraOverlay {
    data object None : CameraOverlay
    data object SkyScore : CameraOverlay
    data object Settings : CameraOverlay
    data object Customize : CameraOverlay
    data object Gallery : CameraOverlay
    data object Calibration : CameraOverlay
    data object Premium : CameraOverlay
    data class Preview(val uri: Uri) : CameraOverlay
}
