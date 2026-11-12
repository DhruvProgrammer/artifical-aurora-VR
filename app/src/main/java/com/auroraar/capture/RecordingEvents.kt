package com.auroraar.capture

/** CameraX video events that drive the take lifecycle. */
enum class RecordingEvent { START, PAUSE, RESUME, FINALIZE }

/**
 * The only legal phase for each event. CameraX delivers events on the main
 * executor, but an event queued before a stop can land afterwards. Applying it
 * blindly used to resurrect a finalized take (a late Pause moved SAVED back to
 * PAUSED, where no Finalize would ever arrive again).
 */
fun RecordingPhase.accepts(event: RecordingEvent): Boolean = when (event) {
    RecordingEvent.START -> this == RecordingPhase.PREPARING
    RecordingEvent.PAUSE -> this == RecordingPhase.RECORDING
    RecordingEvent.RESUME -> this == RecordingPhase.PAUSED
    RecordingEvent.FINALIZE ->
        this == RecordingPhase.PREPARING ||
            this == RecordingPhase.RECORDING ||
            this == RecordingPhase.PAUSED ||
            this == RecordingPhase.FINALIZING
}
