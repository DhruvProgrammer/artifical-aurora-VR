package com.auroraar.capture.caps

import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRecordingCapsTest {

    private val avc = EncoderCapability(
        codecName = "c2.qti.avc.encoder",
        mime = MediaFormat.MIMETYPE_VIDEO_AVC,
        hardware = true,
        maxWidth = 3840,
        maxHeight = 2160,
        maxFps = 60,
        minBitrate = 1_000_000,
        maxBitrate = 80_000_000,
    )

    private val hevc = EncoderCapability(
        codecName = "c2.qti.hevc.encoder",
        mime = MediaFormat.MIMETYPE_VIDEO_HEVC,
        hardware = true,
        maxWidth = 1920,
        maxHeight = 1080,
        maxFps = 30,
        minBitrate = 1_000_000,
        maxBitrate = 40_000_000,
    )

    private val caps = DeviceRecordingCaps(listOf(avc, hevc))

    @Test
    fun `encoder supports checks width height and frame rate`() {
        assertTrue(avc.supports(1920, 1080, 30))
        assertTrue(avc.supports(3840, 2160, 60))
        assertFalse(avc.supports(4096, 2160, 30))
        assertFalse(avc.supports(1920, 1080, 120))
    }

    @Test
    fun `device capabilities expose supported resolutions per codec`() {
        assertTrue(caps.supports720pAvc)
        assertTrue(caps.supports1080pAvc)
        assertTrue(caps.supports4kAvc)
        assertTrue(caps.supports1080pHevc)
        assertFalse(caps.supports4kHevc)
        assertTrue(caps.hasHardwareAvc)
        assertTrue(caps.hasHardwareHevc)
    }

    @Test
    fun `missing codecs report false instead of throwing`() {
        val empty = DeviceRecordingCaps(emptyList())
        assertFalse(empty.supports720pAvc)
        assertFalse(empty.hasHardwareAvc)
        assertEquals("No video encoders reported by this device.", empty.summary())
    }

    @Test
    fun `summary is human readable and lists hardware first`() {
        val summary = caps.summary()
        assertTrue(summary.contains("H.264/AVC"))
        assertTrue(summary.contains("H.265/HEVC"))
        assertTrue(summary.contains("hardware"))
        assertTrue(summary.contains("3840×2160@60"))
        assertTrue(summary.indexOf("H.264/AVC") < summary.indexOf("H.265/HEVC"))
    }

    @Test
    fun `software encoders are labelled`() {
        val software = DeviceRecordingCaps(
            listOf(avc.copy(codecName = "c2.android.avc.encoder", hardware = false)),
        )
        assertFalse(software.hasHardwareAvc)
        assertTrue(software.summary().contains("software"))
    }
}
