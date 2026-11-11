package com.auroraar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.auroraar.R
import com.auroraar.skyscore.model.CloudClass
import com.auroraar.skyscore.model.MessageId
import com.auroraar.skyscore.model.Phase
import com.auroraar.skyscore.model.RatingBand

@Composable
fun ratingText(band: RatingBand): String = stringResource(
    when (band) {
        RatingBand.EXCELLENT -> R.string.rating_excellent
        RatingBand.GREAT -> R.string.rating_great
        RatingBand.GOOD -> R.string.rating_good
        RatingBand.FAIR -> R.string.rating_fair
        RatingBand.LIMITED -> R.string.rating_limited
        RatingBand.UNAVAILABLE -> R.string.rating_unavailable
    },
)

@Composable
fun phaseText(phase: Phase): String = stringResource(
    when (phase) {
        Phase.DAYLIGHT -> R.string.phase_day
        Phase.SUNSET -> R.string.phase_sunset
        Phase.TWILIGHT -> R.string.phase_twilight
        Phase.NIGHT -> R.string.phase_night
    },
)

@Composable
fun cloudSkyText(cloudClass: CloudClass): String = stringResource(
    when (cloudClass) {
        CloudClass.CLEAR -> R.string.cloud_clear_sky
        CloudClass.PARTLY -> R.string.cloud_partly_sky
        CloudClass.OVERCAST -> R.string.cloud_overcast_sky
        CloudClass.HEAVY -> R.string.cloud_heavy_sky
    },
)

@Composable
fun messageText(id: MessageId): String = stringResource(
    when (id) {
        MessageId.DAYLIGHT -> R.string.msg_daylight
        MessageId.SUNSET -> R.string.msg_sunset
        MessageId.TWILIGHT -> R.string.msg_twilight
        MessageId.CLOUDY -> R.string.msg_cloudy
        MessageId.PARTLY_CLEAR -> R.string.msg_partly_clear
        MessageId.CLEAR_DARK -> R.string.msg_clear_dark
        MessageId.LOW_SKY -> R.string.msg_low_sky
        MessageId.INDOORS -> R.string.msg_indoors
        MessageId.CAMERA_COVERED -> R.string.msg_camera_covered
    },
)

@Composable
fun darknessStateText(darkness: Float): String = stringResource(
    when {
        darkness >= 0.85f -> R.string.sub_darkness_very_dark
        darkness >= 0.60f -> R.string.sub_darkness_dark
        darkness >= 0.35f -> R.string.sub_darkness_moderate
        darkness >= 0.15f -> R.string.sub_darkness_bright
        else -> R.string.sub_darkness_very_bright
    },
)

@Composable
fun cloudStateText(cover: Float): String = stringResource(
    when {
        cover < 0.20f -> R.string.sub_cloud_minimal
        cover < 0.45f -> R.string.sub_cloud_light
        cover < 0.65f -> R.string.sub_cloud_partial
        cover < 0.85f -> R.string.sub_cloud_heavy
        else -> R.string.sub_cloud_overcast
    },
)

@Composable
fun visibilityStateText(visibility: Float): String = stringResource(
    when {
        visibility >= 0.85f -> R.string.sub_vis_clear
        visibility >= 0.60f -> R.string.sub_vis_good
        visibility >= 0.30f -> R.string.sub_vis_partial
        visibility >= 0.10f -> R.string.sub_vis_limited
        else -> R.string.sub_vis_blocked
    },
)

@Composable
fun lightingStateText(pollution: Float): String = stringResource(
    when {
        pollution < 0.25f -> R.string.sub_light_low
        pollution < 0.50f -> R.string.sub_light_moderate
        pollution < 0.75f -> R.string.sub_light_high
        else -> R.string.sub_light_very_high
    },
)
