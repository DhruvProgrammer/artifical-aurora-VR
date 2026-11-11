package com.auroraar.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPolicyTest {

    @Test
    fun `free tier is 720p watermarked and ten seconds`() {
        val policy = RecordingPolicy.FREE
        assertEquals(RecordingTier.FREE, policy.tier)
        assertEquals(10_000L, policy.maxRecordMs)
        assertEquals("0:10", policy.maxDurationLabel)
        assertTrue(policy.watermarkRequired)
        assertEquals(720, policy.shortSide)
        assertFalse(policy.allows1080p)
        assertFalse(policy.advancedControls)
        assertFalse(policy.cinematicLooks)
        assertFalse(policy.allowsHevc)
        assertFalse(policy.allows60fps)
        assertFalse(policy.allows4k)
        assertFalse(policy.unlimited)
    }

    @Test
    fun `premium tier is 1080p unlimited and unwatermarked`() {
        val policy = RecordingPolicy.PREMIUM
        assertEquals(RecordingTier.PREMIUM, policy.tier)
        assertNull(policy.maxRecordMs)
        assertNull(policy.maxDurationLabel)
        assertFalse(policy.watermarkRequired)
        assertEquals(1080, policy.shortSide)
        assertTrue(policy.allows1080p)
        assertTrue(policy.advancedControls)
        assertTrue(policy.cinematicLooks)
        assertFalse(policy.allowsHevc)
        assertFalse(policy.allows60fps)
        assertFalse(policy.allows4k)
        assertTrue(policy.unlimited)
    }

    @Test
    fun `policy lookup follows the tier`() {
        assertEquals(RecordingPolicy.FREE, RecordingPolicy.forTier(RecordingTier.FREE))
        assertEquals(RecordingPolicy.PREMIUM, RecordingPolicy.forTier(RecordingTier.PREMIUM))
    }

    @Test
    fun `duration formatting is whole seconds with a minute rollover`() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:00", formatDuration(999L))
        assertEquals("0:10", formatDuration(10_000L))
        assertEquals("0:59", formatDuration(59_999L))
        assertEquals("1:00", formatDuration(60_000L))
        assertEquals("1:05", formatDuration(65_500L))
        assertEquals("0:00", formatDuration(-500L))
    }

    @Test
    fun `watermark is only visible for active free recordings`() {
        val activeFree = RecordingUiState(phase = RecordingPhase.RECORDING, tier = RecordingTier.FREE)
        val pausedFree = RecordingUiState(phase = RecordingPhase.PAUSED, tier = RecordingTier.FREE)
        val idleFree = RecordingUiState(phase = RecordingPhase.IDLE, tier = RecordingTier.FREE)
        val savedFree = RecordingUiState(phase = RecordingPhase.SAVED, tier = RecordingTier.FREE)
        val activePremium = RecordingUiState(phase = RecordingPhase.RECORDING, tier = RecordingTier.PREMIUM)

        assertTrue(activeFree.watermarkVisible)
        assertTrue(pausedFree.watermarkVisible)
        assertFalse(idleFree.watermarkVisible)
        assertFalse(savedFree.watermarkVisible)
        assertFalse(activePremium.watermarkVisible)
    }

    @Test
    fun `active covers preparing through finalizing only`() {
        assertTrue(RecordingUiState(phase = RecordingPhase.PREPARING).active)
        assertTrue(RecordingUiState(phase = RecordingPhase.RECORDING).active)
        assertTrue(RecordingUiState(phase = RecordingPhase.PAUSED).active)
        assertTrue(RecordingUiState(phase = RecordingPhase.FINALIZING).active)
        assertFalse(RecordingUiState(phase = RecordingPhase.IDLE).active)
        assertFalse(RecordingUiState(phase = RecordingPhase.SAVED).active)
        assertFalse(RecordingUiState(phase = RecordingPhase.ERROR).active)
    }
}
