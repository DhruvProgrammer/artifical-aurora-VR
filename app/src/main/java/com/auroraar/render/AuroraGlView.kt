package com.auroraar.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.auroraar.skyscore.model.AuroraRenderParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp
import kotlin.math.min

/**
 * Procedural aurora composited over the camera preview: two translucent GL
 * surfaces, this one premultiplied and translucent. The addendum's v2/v3
 * renderer is not available in this repo, so this is a compact shader version
 * that consumes the same [AuroraRenderParams] contract (intensity, saturation,
 * density, glow, ray contrast, sharpness, stars, cloud, sky-luminance gate,
 * night grade for Creative mode).
 */
class AuroraGlView(context: Context) : GLSurfaceView(context) {

    private val auroraRenderer = AuroraRenderer()

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause = true
        setRenderer(auroraRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setParams(params: AuroraRenderParams) {
        auroraRenderer.target = params
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Aurora is soft: render at ~2/3 resolution and let the compositor upscale.
        holder.setFixedSize((w * 0.66f).toInt().coerceAtLeast(2), (h * 0.66f).toInt().coerceAtLeast(2))
    }
}

class AuroraRenderer : GLSurfaceView.Renderer {

    @Volatile
    var target: AuroraRenderParams = AuroraRenderParams.IDLE

    private var current = AuroraRenderParams.IDLE
    private var program = 0
    private val uniforms = HashMap<String, Int>()
    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD)
            position(0)
        }

    private var lastNanos = 0L
    private val startNanos = System.nanoTime()
    private var viewportW = 1
    private var viewportH = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program != 0) {
            uniforms.clear()
            UNIFORM_NAMES.forEach { uniforms[it] = GLES20.glGetUniformLocation(program, it) }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportW, viewportH)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1_000_000_000f, 0.1f)
        lastNanos = now

        val tgt = target
        current = if (current.enabled != tgt.enabled) tgt else smooth(current, tgt, dt)
        if (!current.enabled || current.intensity <= 0.001f) return

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(u("uRes"), viewportW.toFloat(), viewportH.toFloat())
        GLES20.glUniform1f(u("uTime"), (now - startNanos) / 1_000_000_000f)
        GLES20.glUniform1f(u("uIntensity"), current.intensity)
        GLES20.glUniform1f(u("uSaturation"), current.saturation)
        GLES20.glUniform1f(u("uDensity"), current.density)
        GLES20.glUniform1f(u("uGlow"), current.glow)
        GLES20.glUniform1f(u("uRay"), current.rayContrast)
        GLES20.glUniform1f(u("uSpeed"), current.speed)
        GLES20.glUniform1f(u("uSharp"), current.sharpness)
        GLES20.glUniform1f(u("uStars"), current.stars)
        GLES20.glUniform1f(u("uCloud"), current.cloud)
        GLES20.glUniform1f(u("uSkyLum"), current.skyLumNorm)
        GLES20.glUniform1f(u("uCreative"), if (current.creative) 1f else 0f)
        GLES20.glUniform1f(u("uNightGrade"), current.nightGrade)

        val pos = GLES20.glGetAttribLocation(program, "aPos")
        GLES20.glEnableVertexAttribArray(pos)
        quad.position(0)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
    }

    private fun u(name: String): Int = uniforms[name] ?: -1

    private fun smooth(a: AuroraRenderParams, b: AuroraRenderParams, dt: Float): AuroraRenderParams {
        if (dt <= 0f) return b
        val k = 1f - exp(-dt / 0.35f)
        fun l(x: Float, y: Float) = x + (y - x) * k
        return b.copy(
            intensity = l(a.intensity, b.intensity),
            saturation = l(a.saturation, b.saturation),
            density = l(a.density, b.density),
            glow = l(a.glow, b.glow),
            rayContrast = l(a.rayContrast, b.rayContrast),
            speed = l(a.speed, b.speed),
            sharpness = l(a.sharpness, b.sharpness),
            stars = l(a.stars, b.stars),
            nightGrade = l(a.nightGrade, b.nightGrade),
            cloud = l(a.cloud, b.cloud),
            skyLumNorm = l(a.skyLumNorm, b.skyLumNorm),
        )
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "link failed: ${GLES20.glGetProgramInfoLog(id)}")
            GLES20.glDeleteProgram(id)
            return 0
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return id
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val TAG = "AuroraRenderer"

        private val QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

        private val UNIFORM_NAMES = listOf(
            "uRes", "uTime", "uIntensity", "uSaturation", "uDensity", "uGlow",
            "uRay", "uSpeed", "uSharp", "uStars", "uCloud", "uSkyLum",
            "uCreative", "uNightGrade",
        )

        private const val VERTEX_SHADER = """
            attribute vec2 aPos;
            varying vec2 vUv;
            void main() {
                vUv = aPos * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vUv;
            uniform vec2 uRes;
            uniform float uTime;
            uniform float uIntensity;
            uniform float uSaturation;
            uniform float uDensity;
            uniform float uGlow;
            uniform float uRay;
            uniform float uSpeed;
            uniform float uSharp;
            uniform float uStars;
            uniform float uCloud;
            uniform float uSkyLum;
            uniform float uCreative;
            uniform float uNightGrade;

            float hash21(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float vnoise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 w = f * f * (3.0 - 2.0 * f);
                float a = hash21(i);
                float b = hash21(i + vec2(1.0, 0.0));
                float c = hash21(i + vec2(0.0, 1.0));
                float d = hash21(i + vec2(1.0, 1.0));
                return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
            }

            float fbm(vec2 p) {
                float v = 0.0;
                float amp = 0.5;
                for (int i = 0; i < 4; i++) {
                    v += amp * vnoise(p);
                    p = p * 2.03 + vec2(11.3, 7.7);
                    amp *= 0.5;
                }
                return v;
            }

            void main() {
                vec2 uv = vUv;
                float aspect = uRes.x / max(uRes.y, 1.0);
                vec2 p = vec2(uv.x * aspect, uv.y);
                float t = uTime * uSpeed;

                float edge = 0.28 + 0.22 * fbm(vec2(p.x * 1.7 + t * 0.05, 3.0));
                float top = edge + 0.52 + 0.22 * fbm(vec2(p.x * 2.3 - t * 0.03, 7.0));

                float band = smoothstep(edge, edge + 0.07, uv.y) * (1.0 - smoothstep(top, top + 0.30, uv.y));

                float rayNoise = fbm(vec2(p.x * 16.0 + t * 0.20, uv.y * 2.5 - t * 0.35));
                float rays = mix(1.0, smoothstep(0.22, 0.85, rayNoise), uRay);

                float folds = fbm(vec2(p.x * 3.0 - t * 0.12, uv.y * 3.0 + t * 0.08));
                float detail = mix(0.55, 1.15, folds);

                float sharp = mix(1.0, 0.35 + 0.65 * smoothstep(0.30, 0.90, rayNoise), uSharp);

                float a = band * rays * detail * sharp * uDensity;

                float skyHere = uSkyLum * mix(1.0, 0.55, uv.y);
                float gate = 1.0 - smoothstep(0.10, 0.45, skyHere);
                a *= mix(gate, 1.0, uCreative);

                a *= smoothstep(0.0, 0.10, uv.y);
                a *= 1.0 - smoothstep(0.92, 1.0, uv.y);

                vec3 green = vec3(0.32, 1.00, 0.62);
                vec3 teal  = vec3(0.18, 0.92, 0.85);
                vec3 violet = vec3(0.62, 0.35, 1.00);
                vec3 col = mix(green, teal, smoothstep(0.10, 0.55, uv.y));
                col = mix(col, violet, smoothstep(0.50, 0.95, uv.y));
                col = mix(vec3(dot(col, vec3(0.299, 0.587, 0.114))), col, uSaturation);

                float halo = exp(-abs(uv.y - (edge + 0.10)) * 7.0)
                    * (0.25 + 0.75 * fbm(vec2(p.x * 4.0 + t * 0.06, uv.y * 3.0)));
                col += vec3(0.20, 0.55, 0.45) * halo * uGlow * 0.25;

                a *= mix(1.0, 1.0 - 0.75 * uCloud, 0.85);
                col = mix(col, vec3(0.65, 0.75, 0.85), 0.25 * uCloud);
                a = clamp(a, 0.0, 1.0);

                if (uStars > 0.001) {
                    vec2 sp = uv * vec2(uRes.x / 2.5, uRes.y / 2.5);
                    vec2 cell = floor(sp);
                    vec2 f = fract(sp) - 0.5;
                    float h = hash21(cell);
                    float star = step(0.9955, h) * (1.0 - smoothstep(0.0, 0.35, length(f)));
                    float twinkle = 0.6 + 0.4 * sin(uTime * 3.0 + h * 40.0);
                    float starA = star * twinkle * uStars * smoothstep(0.15, 0.55, uv.y);
                    col += vec3(0.9, 0.95, 1.0) * starA;
                    a = max(a, starA * 0.9);
                }

                float scrim = uCreative * uNightGrade * 0.55 * smoothstep(0.0, 0.35, uv.y);
                vec3 dusk = vec3(0.05, 0.07, 0.16);

                vec3 premul = col * a + dusk * scrim;
                float alpha = 1.0 - (1.0 - a) * (1.0 - scrim);

                premul *= uIntensity;
                alpha *= uIntensity;

                gl_FragColor = vec4(premul, alpha);
            }
        """
    }
}
