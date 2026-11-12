package com.auroraar.skyscore.policy

import com.auroraar.skyscore.model.CloudClass
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.MessageId
import com.auroraar.skyscore.model.Phase

/** Addendum §12.3. */
object MessagePolicy {

    /**
     * @param auroraShown whether the renderer actually enabled the aurora for
     *   this frame. [Gate.OPEN] alone does not imply that: the score can be low
     *   enough that [IntensityMapper] disables rendering, and copy that promises
     *   an aurora is then contradicted by an empty screen.
     * @param auroraRequested whether the user asked for the aurora at all. In
     *   [Mode.OFF] nothing renders by design, so a blank preview must not be
     *   blamed on the sky.
     */
    fun message(
        gate: Gate,
        phase: Phase,
        cloudClass: CloudClass,
        darkness: Float,
        indoors: Boolean,
        creative: Boolean = false,
        auroraShown: Boolean = true,
        auroraRequested: Boolean = true,
    ): MessageId? = when (gate) {
        Gate.CAMERA_BLOCKED -> MessageId.CAMERA_COVERED
        Gate.NO_SKY -> if (indoors) MessageId.INDOORS else MessageId.LOW_SKY
        Gate.BLOCKED_DAYLIGHT -> MessageId.DAYLIGHT
        Gate.OPEN -> when {
            // Creative mode can open the gate in daylight; never claim it is dark.
            creative && phase == Phase.DAYLIGHT -> null
            // The gate can be open while the score is still too low to render:
            // with twilight darkness 0.279 the score reaches 1, and
            // IntensityMapper disables the renderer at <= 0.01. The twilight
            // copy promises "a subtle simulated aurora for now", which an empty
            // screen then contradicts. Say why instead.
            phase == Phase.TWILIGHT && auroraRequested && !auroraShown -> MessageId.SKY_TOO_BRIGHT
            phase == Phase.TWILIGHT -> MessageId.TWILIGHT
            phase == Phase.SUNSET -> MessageId.SUNSET
            cloudClass == CloudClass.HEAVY || cloudClass == CloudClass.OVERCAST -> MessageId.CLOUDY
            darkness >= 0.80f && cloudClass == CloudClass.CLEAR -> MessageId.CLEAR_DARK
            else -> MessageId.PARTLY_CLEAR
        }
    }
}
