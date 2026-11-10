package com.auroraar.skyscore.policy

import com.auroraar.skyscore.model.CloudClass
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.MessageId
import com.auroraar.skyscore.model.Phase

/** Addendum §12.3. */
object MessagePolicy {

    fun message(
        gate: Gate,
        phase: Phase,
        cloudClass: CloudClass,
        darkness: Float,
        indoors: Boolean,
        creative: Boolean = false,
    ): MessageId? = when (gate) {
        Gate.CAMERA_BLOCKED -> MessageId.CAMERA_COVERED
        Gate.NO_SKY -> if (indoors) MessageId.INDOORS else MessageId.LOW_SKY
        Gate.BLOCKED_DAYLIGHT -> MessageId.DAYLIGHT
        Gate.OPEN -> when {
            // Creative mode can open the gate in daylight; never claim it is dark.
            creative && phase == Phase.DAYLIGHT -> null
            phase == Phase.TWILIGHT -> MessageId.TWILIGHT
            phase == Phase.SUNSET -> MessageId.SUNSET
            cloudClass == CloudClass.HEAVY || cloudClass == CloudClass.OVERCAST -> MessageId.CLOUDY
            darkness >= 0.80f && cloudClass == CloudClass.CLEAR -> MessageId.CLEAR_DARK
            else -> MessageId.PARTLY_CLEAR
        }
    }
}
