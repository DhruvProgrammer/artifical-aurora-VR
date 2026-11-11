package com.auroraar.capture.caps

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log

/** One hardware/software video encoder as reported by MediaCodecList. */
data class EncoderCapability(
    val codecName: String,
    val mime: String,
    val hardware: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFps: Int,
    val minBitrate: Int,
    val maxBitrate: Int,
) {
    /**
     * [maxFps] is 0 when the probe could not read a frame-rate range for this
     * size (some encoders throw from `getSupportedFrameRatesFor`). That is a
     * missing reading, not a refusal, so only an explicit non-zero ceiling can
     * disqualify a size.
     */
    fun supports(width: Int, height: Int, fps: Int = 30): Boolean =
        width <= maxWidth &&
            height <= maxHeight &&
            (maxFps <= 0 || fps <= maxFps)
}

/**
 * Encoder capability probe from v5 §8.2. CameraX still picks the final encoder,
 * but the settings screen can tell the truth about 720p/1080p/4K and HEVC
 * instead of only promising a tier policy.
 */
data class DeviceRecordingCaps(val encoders: List<EncoderCapability>) {

    fun supports(mime: String, width: Int, height: Int, fps: Int = 30): Boolean =
        encoders.any { it.mime == mime && it.supports(width, height, fps) }

    val supports720pAvc: Boolean get() = supports(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720)
    val supports1080pAvc: Boolean get() = supports(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
    val supports4kAvc: Boolean get() = supports(MediaFormat.MIMETYPE_VIDEO_AVC, 3840, 2160)
    val supports1080pHevc: Boolean get() = supports(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080)
    val supports4kHevc: Boolean get() = supports(MediaFormat.MIMETYPE_VIDEO_HEVC, 3840, 2160)
    val hasHardwareAvc: Boolean get() = encoders.any {
        it.mime == MediaFormat.MIMETYPE_VIDEO_AVC && it.hardware
    }
    val hasHardwareHevc: Boolean get() = encoders.any {
        it.mime == MediaFormat.MIMETYPE_VIDEO_HEVC && it.hardware
    }

    /** Human-readable, one line per encoder. Shown in Settings. */
    fun summary(): String {
        if (encoders.isEmpty()) return "No video encoders reported by this device."
        return encoders
            .sortedWith(compareByDescending<EncoderCapability> { it.hardware }.thenByDescending { it.maxWidth })
            .joinToString("\n") { cap ->
                val kind = if (cap.hardware) "hardware" else "software"
                val codec = when (cap.mime) {
                    MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264/AVC"
                    MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265/HEVC"
                    else -> cap.mime
                }
                val rate = if (cap.maxFps > 0) "@${cap.maxFps}" else ""
                "$codec · $kind · ${cap.maxWidth}×${cap.maxHeight}$rate"
            }
    }

    companion object {
        private const val TAG = "DeviceRecordingCaps"

        fun probe(): DeviceRecordingCaps = runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val encoders = list.codecInfos
                .filter { it.isEncoder }
                .flatMap { info ->
                    info.supportedTypes
                        .filter {
                            it == MediaFormat.MIMETYPE_VIDEO_AVC ||
                                it == MediaFormat.MIMETYPE_VIDEO_HEVC
                        }
                        .mapNotNull { mime -> capability(info, mime) }
                }
            DeviceRecordingCaps(encoders)
        }.onFailure { Log.w(TAG, "Capability probe failed", it) }
            .getOrDefault(DeviceRecordingCaps(emptyList()))

        private fun capability(info: MediaCodecInfo, mime: String): EncoderCapability? = runCatching {
            val caps = info.getCapabilitiesForType(mime).videoCapabilities
            val maxWidth = caps.supportedWidths.upper
            val maxHeight = caps.supportedHeights.upper
            val fps = runCatching { caps.getSupportedFrameRatesFor(maxWidth, maxHeight).upper }
                .getOrDefault(0.0)
                .toInt()
            val bitrate = caps.bitrateRange
            EncoderCapability(
                codecName = info.name,
                mime = mime,
                hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                        !info.name.startsWith("c2.android.", ignoreCase = true)
                },
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                maxFps = fps,
                minBitrate = bitrate.lower,
                maxBitrate = bitrate.upper,
            )
        }.onFailure { Log.w(TAG, "Encoder ${info.name} $mime probe failed", it) }.getOrNull()
    }
}
