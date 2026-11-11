package com.auroraar.capture.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.FloatBuffer

/**
 * Camera pass: external OES texture -> full-screen quad, with CameraX's
 * transform and the optional Premium cinematic grade. The grade lives here, in
 * the shared composite, so the preview and the encoded file match exactly.
 */
class CameraShader {

    private var program = 0
    private var uMatrix = -1
    private var uTexture = -1
    private var uLook = -1
    private var uGrain = -1
    private var aPos = -1
    private val quad: FloatBuffer = GlUtil.quad()

    fun init() {
        program = GlUtil.buildProgram(VERTEX, FRAGMENT)
        if (program == 0) error("camera shader failed")
        uMatrix = GLES20.glGetUniformLocation(program, "uMatrix")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        uLook = GLES20.glGetUniformLocation(program, "uLook")
        uGrain = GLES20.glGetUniformLocation(program, "uGrain")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
    }

    fun draw(textureId: Int, matrix: FloatArray, look: Int, grain: Float) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glUniformMatrix4fv(uMatrix, 1, false, matrix, 0)
        GLES20.glUniform1f(uLook, look.toFloat())
        GLES20.glUniform1f(uGrain, if (grain.isFinite()) grain.coerceIn(0f, 0.08f) else 0f)

        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
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
            uniform mat4 uMatrix;
            varying vec2 vUv;
            varying vec2 vScreen;
            void main() {
                vec2 uv = aPos * 0.5 + 0.5;
                vScreen = uv;
                vUv = (uMatrix * vec4(uv, 0.0, 1.0)).xy;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vUv;
            varying vec2 vScreen;
            uniform samplerExternalOES uTexture;
            uniform float uLook;
            uniform float uGrain;

            float hash21(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            void main() {
                vec3 c = texture2D(uTexture, vUv).rgb;

                if (uLook > 1.5) {
                    // Dreamy: lifted shadows, softer highlights, heavier vignette.
                    c = mix(c, vec3(1.0), 0.045);
                    c = pow(max(c, 0.0), vec3(0.96));
                    float v = length(vScreen - 0.5) * 1.414;
                    c *= 1.0 - 0.22 * smoothstep(0.20, 0.90, v);
                    c += vec3(0.025, 0.018, 0.045);
                } else if (uLook > 0.5) {
                    // Cinematic: subtle S-curve, teal/violet shadows, light vignette.
                    vec3 s = c * c * (3.0 - 2.0 * c);
                    c = mix(c, s, 0.35);
                    float luma = dot(c, vec3(0.299, 0.587, 0.114));
                    c += mix(
                        vec3(-0.018, 0.012, 0.030),
                        vec3(0.010, 0.0, -0.012),
                        smoothstep(0.20, 0.80, luma)
                    );
                    float v = length(vScreen - 0.5) * 1.414;
                    c *= 1.0 - 0.16 * v * v;
                }

                if (uGrain > 0.0001) {
                    float n = hash21(vScreen * 1024.0) - 0.5;
                    c += n * uGrain;
                }

                gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
            }
        """
    }
}
