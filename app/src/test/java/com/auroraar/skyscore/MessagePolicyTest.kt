package com.auroraar.skyscore

import com.auroraar.skyscore.model.CloudClass
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.MessageId
import com.auroraar.skyscore.model.Phase
import com.auroraar.skyscore.policy.MessagePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagePolicyTest {

    @Test
    fun `creative daylight is never described as dark`() {
        assertNull(
            MessagePolicy.message(
                gate = Gate.OPEN,
                phase = Phase.DAYLIGHT,
                cloudClass = CloudClass.CLEAR,
                darkness = 0f,
                indoors = false,
                creative = true,
            ),
        )
    }

    @Test
    fun `auto daylight shows the daytime message`() {
        assertEquals(
            MessageId.DAYLIGHT,
            MessagePolicy.message(Gate.BLOCKED_DAYLIGHT, Phase.DAYLIGHT, CloudClass.CLEAR, 0f, false),
        )
    }

    @Test
    fun `no sky distinguishes indoors from low sky`() {
        assertEquals(
            MessageId.INDOORS,
            MessagePolicy.message(Gate.NO_SKY, Phase.NIGHT, CloudClass.CLEAR, 1f, indoors = true),
        )
        assertEquals(
            MessageId.LOW_SKY,
            MessagePolicy.message(Gate.NO_SKY, Phase.NIGHT, CloudClass.CLEAR, 1f, indoors = false),
        )
    }

    @Test
    fun `overcast night says cloudy`() {
        assertEquals(
            MessageId.CLOUDY,
            MessagePolicy.message(Gate.OPEN, Phase.NIGHT, CloudClass.OVERCAST, 0.9f, indoors = false),
        )
    }

    @Test
    fun `sunset and twilight get their own messages`() {
        assertEquals(
            MessageId.SUNSET,
            MessagePolicy.message(Gate.OPEN, Phase.SUNSET, CloudClass.CLEAR, 0.2f, indoors = false),
        )
        assertEquals(
            MessageId.TWILIGHT,
            MessagePolicy.message(Gate.OPEN, Phase.TWILIGHT, CloudClass.CLEAR, 0.4f, indoors = false),
        )
    }

    @Test
    fun `heavy cloud says cloudy`() {
        assertEquals(
            MessageId.CLOUDY,
            MessagePolicy.message(Gate.OPEN, Phase.NIGHT, CloudClass.HEAVY, 1f, indoors = false),
        )
    }

    @Test
    fun `a clear dark night gets the clear message`() {
        assertEquals(
            MessageId.CLEAR_DARK,
            MessagePolicy.message(Gate.OPEN, Phase.NIGHT, CloudClass.CLEAR, 1f, indoors = false),
        )
    }

    @Test
    fun `a partly clear night says partly clear`() {
        assertEquals(
            MessageId.PARTLY_CLEAR,
            MessagePolicy.message(Gate.OPEN, Phase.NIGHT, CloudClass.PARTLY, 0.9f, indoors = false),
        )
    }

    @Test
    fun `camera blocked wins over everything`() {
        assertEquals(
            MessageId.CAMERA_COVERED,
            MessagePolicy.message(Gate.CAMERA_BLOCKED, Phase.DAYLIGHT, CloudClass.HEAVY, 0f, indoors = false),
        )
    }

    @Test
    fun `creative mode drawing on a covered lens does not claim the camera is covered`() {
        // Creative mode now draws a simulated aurora even when the camera sees
        // nothing. Saying "camera covered" over a bright curtain is the same
        // contradiction as promising an aurora above an empty screen.
        assertNull(
            MessagePolicy.message(
                Gate.CAMERA_BLOCKED, Phase.NIGHT, CloudClass.CLEAR, 1f, indoors = false,
                creative = true, auroraShown = true, auroraRequested = true,
            ),
        )
        // In AUTO nothing renders on a covered lens, so the warning must stay.
        assertEquals(
            MessageId.CAMERA_COVERED,
            MessagePolicy.message(
                Gate.CAMERA_BLOCKED, Phase.NIGHT, CloudClass.CLEAR, 1f, indoors = false,
                creative = false, auroraShown = false, auroraRequested = true,
            ),
        )
    }

    @Test
    fun `a twilight frame that draws nothing must not promise an aurora`() {
        // The gate can be OPEN while the score is too low to render: darkness
        // 0.279 gives skyScore 1, and IntensityMapper disables the renderer at
        // intensityIndex <= 0.01. The twilight copy promises "a subtle
        // simulated aurora for now", which is then contradicted by an empty
        // screen. The message must follow what is actually drawn.
        val params = com.auroraar.skyscore.policy.IntensityMapper.map(
            intensityIndex = 0.01f,
            darkness = 0.279f,
            cloudEffective = 0.9f,
            settings = com.auroraar.skyscore.model.UserSettings(),
            creative = false,
            skyLumNorm = 0.4f,
        )
        assertFalse("this frame must not render", params.enabled)

        val message = MessagePolicy.message(
            gate = Gate.OPEN,
            phase = Phase.TWILIGHT,
            cloudClass = CloudClass.PARTLY,
            darkness = 0.279f,
            indoors = false,
            auroraShown = params.enabled,
        )
        assertNotEquals(
            "message claimed an aurora while the renderer was disabled",
            MessageId.TWILIGHT,
            message,
        )
    }

    @Test
    fun `an unrendered twilight frame reports the real reason`() {
        assertEquals(
            MessageId.SKY_TOO_BRIGHT,
            MessagePolicy.message(
                gate = Gate.OPEN,
                phase = Phase.TWILIGHT,
                cloudClass = CloudClass.CLEAR,
                darkness = 0.279f,
                indoors = false,
                auroraShown = false,
            ),
        )
    }

    @Test
    fun `a rendered twilight frame keeps the twilight message`() {
        assertEquals(
            MessageId.TWILIGHT,
            MessagePolicy.message(
                gate = Gate.OPEN,
                phase = Phase.TWILIGHT,
                cloudClass = CloudClass.CLEAR,
                darkness = 0.4f,
                indoors = false,
                auroraShown = true,
            ),
        )
    }

    @Test
    fun `not rendering does not override a cloudy sky`() {
        // Cloud is the more useful explanation when it is present; the new
        // branch must not swallow it.
        assertEquals(
            MessageId.CLOUDY,
            MessagePolicy.message(
                gate = Gate.OPEN,
                phase = Phase.NIGHT,
                cloudClass = CloudClass.OVERCAST,
                darkness = 0.9f,
                indoors = false,
                auroraShown = false,
            ),
        )
    }
}
