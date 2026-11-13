package com.auroraar.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Surface
import androidx.camera.core.ProcessingException
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.auroraar.capture.gl.AuroraShader
import com.auroraar.capture.gl.CameraShader
import com.auroraar.capture.gl.EglCore
import com.auroraar.capture.gl.GlUtil
import com.auroraar.capture.gl.OverlayBlit
import com.auroraar.capture.gl.WatermarkRenderer
import com.auroraar.skyscore.model.AuroraRenderParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor

/**
 * CameraX SurfaceProcessor that draws the final composite:
 *
 *   camera OES texture -> aurora shader -> Free watermark -> effect output
 *
 * CameraX feeds the result to BOTH Preview and VideoCapture, so the file is the
 * same GL output the user sees (minus Compose chrome, which is never in this
 * pipeline). Everything runs on one HandlerThread; the encoder is owned by
 * CameraX's VideoCapture, not by this class.
 */
class CompositeProcessor(
    private val context: Context,
    private val params: () -> AuroraRenderParams,
    private val watermarkAlpha: () -> Float,
    /** Clockwise rotation CameraX will apply to the effect output (0/90/180/270). */
    private val rotation: () -> Int,
    private val clock: RecordingClock,
    private val onError: (Throwable) -> Unit,
) : SurfaceProcessor, SurfaceTexture.OnFrameAvailableListener {

    private val thread = HandlerThread("AuroraComposite", Process.THREAD_PRIORITY_DISPLAY)
    private val handler: Handler
    val executor: Executor

    private val egl = EglCore()
    private var eglReady = false
    private var pbuffer: EGLSurface? = null

    private var cameraShader: CameraShader? = null
    private var auroraShader: AuroraShader? = null
    private var watermark: WatermarkRenderer? = null
    private var overlayBlit: OverlayBlit? = null

    // Overlays are rendered in display orientation, then rotated into the
    // effect surface. Recreated when the output size or orientation changes.
    private var overlayTexture = 0
    private var overlayFbo = 0
    private var overlayWidth = 0
    private var overlayHeight = 0

    private var input: Input? = null
    private var output: SurfaceOutput? = null
    private var outputEglSurface: EGLSurface? = null
    private var outputWidth = 0
    private var outputHeight = 0
    private var loggedGeometry = false

    private val texMatrix = FloatArray(16)
    private val finalMatrix = FloatArray(16)
    private var failed = false
    private var released = false

    /** Set from the UI thread, consumed on the GL thread at the next frame. */
    private var pendingPhoto: ((Bitmap) -> Unit)? = null

    private class Input(
        val textureId: Int,
        val surfaceTexture: SurfaceTexture,
        val surface: Surface,
    )

    init {
        thread.start()
        handler = Handler(thread.looper)
        executor = Executor { command -> handler.post(command) }
    }

    override fun onInputSurface(request: SurfaceRequest) {
        try {
            ensureGl()
            val textureId = GlUtil.createExternalTexture()
            val surfaceTexture = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(request.resolution.width, request.resolution.height)
                setOnFrameAvailableListener(this@CompositeProcessor, handler)
            }
            val surface = Surface(surfaceTexture)
            val newInput = Input(textureId, surfaceTexture, surface)
            input = newInput
            Log.i(TAG, "input surface ${request.resolution.width}x${request.resolution.height}")

            request.provideSurface(surface, executor) {
                handler.post {
                    if (released) return@post
                    newInput.surfaceTexture.setOnFrameAvailableListener(null)
                    runCatching { newInput.surfaceTexture.release() }
                    runCatching { newInput.surface.release() }
                    if (input === newInput) input = null
                    if (!released) {
                        // Texture deletion needs the EGL context that created it.
                        runCatching {
                            ensureGl()
                            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            fail(t, notifyCameraX = true)
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        try {
            ensureGl()
            releaseOutput()
            val surface = surfaceOutput.getSurface(executor) { event ->
                if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) {
                    handler.post {
                        if (output === surfaceOutput) releaseOutput()
                    }
                }
            }
            output = surfaceOutput
            outputWidth = surfaceOutput.size.width
            outputHeight = surfaceOutput.size.height
            loggedGeometry = false
            outputEglSurface = egl.createWindowSurface(surface)
            egl.makeCurrent(outputEglSurface!!)
            GLES20.glViewport(0, 0, outputWidth, outputHeight)
        } catch (t: Throwable) {
            fail(t, notifyCameraX = true)
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (released || failed) return
        val current = input
        if (current == null || current.surfaceTexture !== surfaceTexture) return
        val eglSurface = outputEglSurface ?: return
        try {
            egl.makeCurrent(eglSurface)
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(texMatrix)
            output?.updateTransformMatrix(finalMatrix, texMatrix)
            drawFrame(surfaceTexture.timestamp)
        } catch (t: Throwable) {
            fail(t)
        }
    }

    private fun drawFrame(timestampNs: Long) {
        if (outputWidth <= 0 || outputHeight <= 0) return
        val eglSurface = outputEglSurface ?: return
        val textureId = input?.textureId ?: return
        val currentParams = params()

        // CameraX gives the effect a surface in sensor orientation (landscape
        // on a phone held upright) and rotates the whole surface afterwards.
        // Overlays therefore have to be rendered in display orientation and
        // pre-rotated into this surface, or they end up sideways in portrait.
        val clockwise = normalizeRotation(rotation())
        val overlayW = if (clockwise % 180 == 0) outputWidth else outputHeight
        val overlayH = if (clockwise % 180 == 0) outputHeight else outputWidth
        if (!loggedGeometry) {
            loggedGeometry = true
            Log.i(
                TAG,
                "geometry output=${outputWidth}x$outputHeight overlay=${overlayW}x$overlayH " +
                    "rotation=$clockwise",
            )
        }
        ensureOverlayTarget(overlayW, overlayH)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        cameraShader?.draw(textureId, finalMatrix, currentParams.look, currentParams.grain)
        GLES20.glDisable(GLES20.GL_BLEND)

        // Camera timestamps keep preview and file locked while idle. During a
        // recording the controller's recorded clock (which excludes pauses) is
        // used so a pause/resume in the file does not jump the aurora animation.
        val recordedMs = clock.recordedElapsedMs()
        val timeSeconds = recordedMs?.div(1000f) ?: (timestampNs / 1_000_000_000f)
        drawOverlay(overlayW, overlayH) {
            auroraShader?.draw(overlayW, overlayH, timeSeconds, currentParams)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
        overlayBlit?.draw(overlayTexture, clockwise, premultiplied = true)

        // Photos are read back before the Free watermark is drawn: v5 §6.4
        // keeps stills unwatermarked on every plan. The preview/file path
        // continues with the watermark below. The readback is in effect-surface
        // orientation, so rotate it to match what the user sees.
        capturePhotoIfRequested(clockwise)

        drawOverlay(overlayW, overlayH) {
            watermark?.draw(overlayW, overlayH, watermarkAlpha())
        }
        // Blending the straight-alpha wordmark into the transparent target
        // already premultiplies it, so the blit must not multiply alpha again.
        overlayBlit?.draw(overlayTexture, clockwise, premultiplied = true)

        egl.setPresentationTime(eglSurface, timestampNs)
        if (!egl.swapBuffers(eglSurface)) {
            Log.w(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
        }
    }

    /** Renders one overlay pass into the transparent display-oriented target. */
    private inline fun drawOverlay(width: Int, height: Int, draw: () -> Unit) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, overlayFbo)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        draw()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
    }

    private fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360

    private fun ensureOverlayTarget(width: Int, height: Int) {
        if (overlayTexture != 0 && overlayWidth == width && overlayHeight == height) return
        releaseOverlayTarget()

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        overlayTexture = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        val framebuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, framebuffers, 0)
        overlayFbo = framebuffers[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, overlayFbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            overlayTexture,
            0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            releaseOverlayTarget()
            error("overlay framebuffer incomplete: 0x${Integer.toHexString(status)}")
        }

        overlayWidth = width
        overlayHeight = height
    }

    private fun releaseOverlayTarget() {
        if (overlayFbo != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(overlayFbo), 0)
            overlayFbo = 0
        }
        if (overlayTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(overlayTexture), 0)
            overlayTexture = 0
        }
        overlayWidth = 0
        overlayHeight = 0
    }

    /**
     * Grabs the next fully composited frame. The readback happens before
     * eglSwapBuffers while the default framebuffer still holds the frame. It is
     * only used for still photos, never while a video recording is running.
     */
    fun requestPhoto(onCaptured: (Bitmap) -> Unit) {
        handler.post {
            if (released || failed) return@post
            pendingPhoto = onCaptured
        }
    }

    private fun capturePhotoIfRequested(rotationDegrees: Int) {
        val callback = pendingPhoto ?: return
        pendingPhoto = null
        val w = outputWidth
        val h = outputHeight
        if (w <= 0 || h <= 0) return
        val buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.w(TAG, "Photo readback failed: 0x${Integer.toHexString(error)}")
            return
        }
        buffer.rewind()
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val targetRow = (h - 1 - y) * w
            for (x in 0 until w) {
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                pixels[targetRow + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val clockwise = normalizeRotation(rotationDegrees)
        if (clockwise == 0) {
            callback(bitmap)
            return
        }
        val matrix = Matrix().apply { postRotate(clockwise.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        callback(rotated)
    }

    private fun ensureGl() {
        if (released) error("composite already released")
        if (eglReady) return
        egl.init()
        pbuffer = egl.createPbufferSurface()
        egl.makeCurrent(pbuffer!!)
        cameraShader = CameraShader().also { it.init() }
        auroraShader = AuroraShader().also { it.init() }
        watermark = WatermarkRenderer(context).also { it.init() }
        overlayBlit = OverlayBlit().also { it.init() }
        eglReady = true
    }

    private fun releaseOutput() {
        val eglSurface = outputEglSurface
        if (eglSurface != null) {
            egl.destroySurface(eglSurface)
            outputEglSurface = null
        }
        val surfaceOutput = output
        output = null
        outputWidth = 0
        outputHeight = 0
        if (surfaceOutput != null) {
            runCatching { surfaceOutput.close() }
        }
    }

    private fun fail(t: Throwable, notifyCameraX: Boolean = false) {
        if (failed) return
        failed = true
        Log.e(TAG, "Composite failed", t)
        onError(t)
        if (notifyCameraX) {
            // 1.4.1 exposes only a no-arg ProcessingException; keep the cause attached.
            val processing = ProcessingException()
            processing.initCause(t)
            throw processing
        }
    }

    /** Frees the GL thread. Call only after CameraX has unbound the effect. */
    fun release() {
        if (released) return
        released = true
        handler.post {
            pendingPhoto = null
            releaseOutput()
            input?.let {
                it.surfaceTexture.setOnFrameAvailableListener(null)
                runCatching { it.surfaceTexture.release() }
                runCatching { it.surface.release() }
                input = null
            }
            if (eglReady) {
                cameraShader?.release()
                auroraShader?.release()
                watermark?.release()
                overlayBlit?.release()
                releaseOverlayTarget()
                pbuffer?.let { egl.destroySurface(it) }
                pbuffer = null
                egl.release()
                eglReady = false
            }
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "AuroraComposite"
    }
}
