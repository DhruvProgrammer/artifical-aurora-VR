package com.auroraar.capture.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * Small EGL14 wrapper for the recording compositor. One context, any number of
 * window surfaces (preview + encoder targets supplied by CameraX).
 */
class EglCore {

    var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private set
    var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private set
    private var config: EGLConfig? = null

    fun init() {
        check(display === EGL14.EGL_NO_DISPLAY) { "EGL already initialised" }
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display === EGL14.EGL_NO_DISPLAY) error("eglGetDisplay failed")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            display = EGL14.EGL_NO_DISPLAY
            error("eglInitialize failed")
        }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            error("eglChooseConfig failed")
        }
        config = configs[0] ?: error("no EGL config")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE,
        )
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context === EGL14.EGL_NO_CONTEXT) error("eglCreateContext failed")
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
        if (eglSurface == null || eglSurface === EGL14.EGL_NO_SURFACE) {
            error("eglCreateWindowSurface failed")
        }
        return eglSurface
    }

    /** Tiny off-screen surface so GL calls are legal before an encoder target exists. */
    fun createPbufferSurface(width: Int = 1, height: Int = 1): EGLSurface {
        val attribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE,
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        if (eglSurface == null || eglSurface === EGL14.EGL_NO_SURFACE) {
            error("eglCreatePbufferSurface failed")
        }
        return eglSurface
    }

    fun makeCurrent(surface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            error("eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
        }
    }

    fun setPresentationTime(surface: EGLSurface, timestampNs: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, timestampNs)
    }

    fun swapBuffers(surface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, surface)

    fun destroySurface(surface: EGLSurface) {
        runCatching { EGL14.eglDestroySurface(display, surface) }
    }

    fun release() {
        if (display !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (context !== EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        context = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
        config = null
    }
}
