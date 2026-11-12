package com.auroraar.ui

import com.auroraar.capture.RecordingPhase

/** What a hold-to-record release should do for the take's current phase. */
enum class PendingHoldStop {
    /** The take has not started yet; remember the release until it does. */
    WAIT,

    /** The take is running; stop it now. */
    STOP,

    /** The take never started (or already ended); forget the release. */
    DISCARD,
}

/**
 * A hold can end while the take is still preparing. The pending release must
 * have a terminal state for every phase, otherwise a failed start leaves it
 * armed and the next take stops the instant it reaches RECORDING.
 */
fun pendingHoldStop(phase: RecordingPhase): PendingHoldStop = when (phase) {
    RecordingPhase.PREPARING -> PendingHoldStop.WAIT
    RecordingPhase.RECORDING, RecordingPhase.PAUSED -> PendingHoldStop.STOP
    else -> PendingHoldStop.DISCARD
}
