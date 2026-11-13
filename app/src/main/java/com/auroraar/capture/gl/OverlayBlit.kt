package com.auroraar.capture.gl

import android.opengl.GLES20
import java.nio.FloatBuffer

/**
 * Blits a display-oriented overlay texture into the effect output surface.
 *
 * CameraX hands the compositor a surface in sensor orientation (usually
 * landscape) and rotates the whole surface afterwards to match the display.
 * Overlays rendered straight into that surface therefore come out sideways in
 * portrait. This pass rotates them by the same amount CameraX will apply, so
 * the final preview and file both show them upright.
 */
class OverlayBlit {

    private var program = 0
    private var uTexture = -1
    private var uRotation = -1
    private var aPos = -1
    private val quad: FloatBuffer = GlUtil.quad()

    fun init() {
        program = GlUtil.buildProgram(VERTEX, FRAGMENT)
        if (program == 0) error("overlay blit shader failed")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        uRotation = GLES20.glGetUniformLocation(program, "uRotation")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
    }

    /**
     * @param texture overlay texture in display orientation
     * @param rotationDegrees clockwise rotation CameraX will apply to the
     *   effect surface. The quad is pre-rotated the opposite way in GL space.
     * @param premultiplied true when the texture holds premultiplied alpha
     *   (both overlay passes are blended into a transparent target, so they do)
     */
    fun draw(texture: Int, rotationDegrees: Int, premultiplied: Boolean) {
        if (texture == 0 || program == 0) return

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glUniform1f(
            uRotation,
            Math.toRadians((rotationDegrees % 360 + 360) % 360.toDouble()).toFloat(),
        )
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(
            if (premultiplied) GLES20.GL_ONE else GLES20.GL_SRC_ALPHA,
            GLES20.GL_ONE_MINUS_SRC_ALPHA,
        )

        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aPos;
            uniform float uRotation;
            varying vec2 vUv;
            void main() {
                float c = cos(uRotation);
                float s = sin(uRotation);
                vec2 p = vec2(aPos.x * c - aPos.y * s, aPos.x * s + aPos.y * c);
                vUv = aPos * 0.5 + 0.5;
                gl_Position = vec4(p, 0.0, 1.0);
            }
        """

        const val FRAGMENT = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vUv);
            }
        """
    }
}
