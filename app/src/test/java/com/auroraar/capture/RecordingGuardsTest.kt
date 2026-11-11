package com.auroraar.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingGuardsTest {

    private val plentyFree = 8L * 1024 * 1024 * 1024
    private val healthyBattery = 80

    @Test
    fun `healthy device can record`() {
        val result = RecordingGuards.evaluate(plentyFree, healthyBattery, false, 0)
        assertTrue(result.canRecord)
        assertNull(result.refusal)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `under one hundred megabytes refuses`() {
        val result = RecordingGuards.evaluate(60L * 1024 * 1024, healthyBattery, false, 0)
        assertFalse(result.canRecord)
        assertEquals(GuardRefusal.STORAGE, result.refusal)
    }

    @Test
    fun `under five hundred megabytes warns`() {
        val result = RecordingGuards.evaluate(200L * 1024 * 1024, healthyBattery, false, 0)
        assertTrue(result.canRecord)
        assertTrue(GuardWarning.LOW_STORAGE in result.warnings)
    }

    @Test
    fun `critical battery refuses only when unplugged`() {
        val unplugged = RecordingGuards.evaluate(plentyFree, 4, false, 0)
        assertFalse(unplugged.canRecord)
        assertEquals(GuardRefusal.BATTERY, unplugged.refusal)

        val charging = RecordingGuards.evaluate(plentyFree, 4, true, 0)
        assertTrue(charging.canRecord)
        assertTrue(charging.warnings.isEmpty())
    }

    @Test
    fun `low battery warns when unplugged`() {
        val result = RecordingGuards.evaluate(plentyFree, 10, false, 0)
        assertTrue(result.canRecord)
        assertTrue(GuardWarning.LOW_BATTERY in result.warnings)
    }

    @Test
    fun `unknown battery reading never blocks`() {
        val result = RecordingGuards.evaluate(plentyFree, -1, false, 0)
        assertTrue(result.canRecord)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `severe thermal refuses and moderate warns`() {
        val severe = RecordingGuards.evaluate(plentyFree, healthyBattery, false, RecordingGuards.THERMAL_REFUSE)
        assertFalse(severe.canRecord)
        assertEquals(GuardRefusal.THERMAL, severe.refusal)

        val moderate = RecordingGuards.evaluate(plentyFree, healthyBattery, false, RecordingGuards.THERMAL_WARN)
        assertTrue(moderate.canRecord)
        assertTrue(GuardWarning.THERMAL in moderate.warnings)
    }

    @Test
    fun `storage refusal is reported before battery and thermal`() {
        val result = RecordingGuards.evaluate(
            freeBytes = 1L * 1024 * 1024,
            batteryPercent = 1,
            charging = false,
            thermalStatus = RecordingGuards.THERMAL_REFUSE,
        )
        assertEquals(GuardRefusal.STORAGE, result.refusal)
    }

    @Test
    fun `exact thresholds are inclusive`() {
        val storage = RecordingGuards.evaluate(RecordingGuards.MIN_FREE_BYTES, healthyBattery, false, 0)
        assertTrue(storage.canRecord)
        assertTrue(GuardWarning.LOW_STORAGE in storage.warnings)

        val battery = RecordingGuards.evaluate(plentyFree, RecordingGuards.MIN_BATTERY_PERCENT, false, 0)
        assertTrue(battery.canRecord)
        assertTrue(GuardWarning.LOW_BATTERY in battery.warnings)
    }
}
