package com.auroraar.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRecordingCleanupTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `rows older than the grace period are stale`() {
        assertTrue(PendingRecordingCleanup.isStale(now - 61_000L, now))
        assertTrue(PendingRecordingCleanup.isStale(now - 10 * 60_000L, now))
    }

    @Test
    fun `recent rows are left alone`() {
        assertFalse(PendingRecordingCleanup.isStale(now - 1_000L, now))
        assertFalse(PendingRecordingCleanup.isStale(now - 59_000L, now))
        assertFalse(PendingRecordingCleanup.isStale(now, now))
    }

    @Test
    fun `missing dateAdded and future timestamps are not stale`() {
        assertFalse(PendingRecordingCleanup.isStale(0L, now))
        assertFalse(PendingRecordingCleanup.isStale(-1L, now))
        assertFalse(PendingRecordingCleanup.isStale(now + 5_000L, now))
    }

    @Test
    fun `grace period is one minute`() {
        assertTrue(PendingRecordingCleanup.STALE_MS == 60_000L)
    }
}
