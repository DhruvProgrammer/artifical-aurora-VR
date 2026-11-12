package com.auroraar.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingEventGuardTest {

    @Test
    fun `start is only legal while preparing`() {
        assertTrue(RecordingPhase.PREPARING.accepts(RecordingEvent.START))
        assertFalse(RecordingPhase.IDLE.accepts(RecordingEvent.START))
        assertFalse(RecordingPhase.FINALIZING.accepts(RecordingEvent.START))
        assertFalse(RecordingPhase.SAVED.accepts(RecordingEvent.START))
    }

    @Test
    fun `pause is only legal while recording`() {
        assertTrue(RecordingPhase.RECORDING.accepts(RecordingEvent.PAUSE))
        assertFalse(RecordingPhase.PAUSED.accepts(RecordingEvent.PAUSE))
        assertFalse(RecordingPhase.PREPARING.accepts(RecordingEvent.PAUSE))
        assertFalse(RecordingPhase.FINALIZING.accepts(RecordingEvent.PAUSE))
    }

    @Test
    fun `resume is only legal while paused`() {
        assertTrue(RecordingPhase.PAUSED.accepts(RecordingEvent.RESUME))
        assertFalse(RecordingPhase.RECORDING.accepts(RecordingEvent.RESUME))
        assertFalse(RecordingPhase.FINALIZING.accepts(RecordingEvent.RESUME))
    }

    @Test
    fun `late pause cannot resurrect a saved take`() {
        assertFalse(RecordingPhase.SAVED.accepts(RecordingEvent.PAUSE))
        assertFalse(RecordingPhase.ERROR.accepts(RecordingEvent.PAUSE))
    }

    @Test
    fun `finalize is accepted while the take is still open`() {
        assertTrue(RecordingPhase.PREPARING.accepts(RecordingEvent.FINALIZE))
        assertTrue(RecordingPhase.RECORDING.accepts(RecordingEvent.FINALIZE))
        assertTrue(RecordingPhase.PAUSED.accepts(RecordingEvent.FINALIZE))
        assertTrue(RecordingPhase.FINALIZING.accepts(RecordingEvent.FINALIZE))
    }

    @Test
    fun `duplicate finalize is ignored`() {
        assertFalse(RecordingPhase.SAVED.accepts(RecordingEvent.FINALIZE))
        assertFalse(RecordingPhase.ERROR.accepts(RecordingEvent.FINALIZE))
        assertFalse(RecordingPhase.IDLE.accepts(RecordingEvent.FINALIZE))
    }

    @Test
    fun `every event has at least one accepting phase`() {
        val phases = RecordingPhase.entries
        RecordingEvent.entries.forEach { event ->
            assertTrue(
                "no phase accepts $event",
                phases.any { it.accepts(event) },
            )
        }
    }
}
