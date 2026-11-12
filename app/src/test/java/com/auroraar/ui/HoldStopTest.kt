package com.auroraar.ui

import com.auroraar.capture.RecordingPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class HoldStopTest {

    @Test
    fun `release before the take starts waits for it`() {
        assertEquals(PendingHoldStop.WAIT, pendingHoldStop(RecordingPhase.PREPARING))
    }

    @Test
    fun `release during a running take stops it`() {
        assertEquals(PendingHoldStop.STOP, pendingHoldStop(RecordingPhase.RECORDING))
        assertEquals(PendingHoldStop.STOP, pendingHoldStop(RecordingPhase.PAUSED))
    }

    @Test
    fun `release after a failed start is discarded`() {
        assertEquals(PendingHoldStop.DISCARD, pendingHoldStop(RecordingPhase.ERROR))
    }

    @Test
    fun `release after the take ended is discarded`() {
        assertEquals(PendingHoldStop.DISCARD, pendingHoldStop(RecordingPhase.SAVED))
        assertEquals(PendingHoldStop.DISCARD, pendingHoldStop(RecordingPhase.FINALIZING))
        assertEquals(PendingHoldStop.DISCARD, pendingHoldStop(RecordingPhase.IDLE))
    }

    @Test
    fun `every phase has a terminal outcome`() {
        RecordingPhase.entries.forEach { phase ->
            // A WAIT is only valid while the take can still produce a Start.
            if (pendingHoldStop(phase) == PendingHoldStop.WAIT) {
                assertEquals(RecordingPhase.PREPARING, phase)
            }
        }
    }
}
