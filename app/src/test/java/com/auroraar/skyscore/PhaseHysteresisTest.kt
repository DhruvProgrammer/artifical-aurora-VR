package com.auroraar.skyscore

import com.auroraar.skyscore.estimators.PhaseHysteresis
import com.auroraar.skyscore.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseHysteresisTest {

    private val night = listOf(0.02f, 0.03f, 0.05f, 0.90f)
    private val day = listOf(0.90f, 0.03f, 0.04f, 0.03f)

    @Test
    fun `does not switch before the lead has held long enough`() {
        val hysteresis = PhaseHysteresis(initial = Phase.DAYLIGHT)
        assertEquals(Phase.DAYLIGHT, hysteresis.update(night, 1_000L))
        assertEquals(Phase.DAYLIGHT, hysteresis.update(night, 3_500L))
        assertEquals(Phase.NIGHT, hysteresis.update(night, 6_100L))
    }

    @Test
    fun `day transitions use the slower hold`() {
        val hysteresis = PhaseHysteresis(initial = Phase.NIGHT)
        assertEquals(Phase.NIGHT, hysteresis.update(day, 1_000L))
        assertEquals(Phase.NIGHT, hysteresis.update(day, 5_500L))
        assertEquals(Phase.DAYLIGHT, hysteresis.update(day, 6_100L))
    }

    @Test
    fun `small leads do not switch at all`() {
        val hysteresis = PhaseHysteresis(initial = Phase.TWILIGHT)
        val nearlyFlat = listOf(0.20f, 0.20f, 0.32f, 0.28f)
        repeat(20) { step ->
            assertEquals(Phase.TWILIGHT, hysteresis.update(nearlyFlat, 1_000L + step * 1_000L))
        }
    }

    @Test
    fun `noisy signal does not oscillate`() {
        val hysteresis = PhaseHysteresis(initial = Phase.NIGHT)
        val alternating = listOf(
            listOf(0.02f, 0.03f, 0.45f, 0.50f),
            listOf(0.02f, 0.03f, 0.55f, 0.40f),
        )
        var switches = 0
        var previous = Phase.NIGHT
        repeat(30) { step ->
            val phase = hysteresis.update(alternating[step % 2], 1_000L + step * 500L)
            if (phase != previous) switches++
            previous = phase
        }
        assertEquals(0, switches)
    }
}
