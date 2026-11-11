package com.auroraar.capture

import androidx.camera.core.CameraEffect
import androidx.core.util.Consumer

/**
 * CameraX effect wrapper. CameraX's 4-arg constructor maps to
 * OUTPUT_OPTION_ONE_FOR_ALL_TARGETS, so one GL draw is fanned out to both the
 * Preview and the VideoCapture encoder. The preview and the file therefore use
 * the same composited frame.
 */
class CompositeEffect(
    targets: Int,
    private val processor: CompositeProcessor,
    onError: (Throwable) -> Unit,
) : CameraEffect(
    targets,
    processor.executor,
    processor,
    Consumer<Throwable> { onError(it) },
) {
    fun release() = processor.release()
}
