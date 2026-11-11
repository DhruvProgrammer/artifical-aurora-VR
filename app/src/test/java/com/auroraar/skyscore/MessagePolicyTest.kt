package com.auroraar.skyscore

import com.auroraar.skyscore.model.CloudClass
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.MessageId
import com.auroraar.skyscore.model.Phase
import com.auroraar.skyscore.policy.MessagePolicy
import org.junit.Assert.assertEquals
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
}
