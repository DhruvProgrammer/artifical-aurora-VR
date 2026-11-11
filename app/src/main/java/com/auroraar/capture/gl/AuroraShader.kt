package com.auroraar.capture.gl

import android.opengl.GLES20
import com.auroraar.skyscore.model.AuroraRenderParams
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.min

/**
 * Procedural aurora shader used by both the live preview and the recording
 * composite. Styles change geometry, palette, layer count, coverage, wave
 * motion and bursts; they are real shader variants, not recolours.
 *
 * Output is premultiplied alpha.
 */
class AuroraShader {

    private var program = 0
    private val uniforms = HashMap<String, Int>()
    private val quad: FloatBuffer = GlUtil.quad()

    private var current = AuroraRenderParams.IDLE
    private var lastTime = -1f

    fun init() {
        program = GlUtil.buildProgram(VERTEX, FRAGMENT)
        if (program == 0) error("aurora shader failed")
        UNIFORM_NAMES.forEach { uniforms[it] = GLES20.glGetUniformLocation(program, it) }
    }

    fun draw(width: Int, height: Int, timeSeconds: Float, target: AuroraRenderParams) {
        val dt = if (lastTime < 0f || timeSeconds < lastTime) {
            0f
        } else {
            min(timeSeconds - lastTime, 0.1f)
        }
        lastTime = timeSeconds
        current = if (current.enabled != target.enabled) target else smooth(current, target, dt)
        if (!current.enabled || current.intensity <= 0.001f) return

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)

        GLES20.glUniform2f(u("uRes"), width.toFloat(), height.toFloat())
        GLES20.glUniform1f(u("uTime"), timeSeconds)
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
        GLES20.glUniform3f(u("uLow"), red(current.lowColor), green(current.lowColor), blue(current.lowColor))
        GLES20.glUniform3f(u("uMid"), red(current.midColor), green(current.midColor), blue(current.midColor))
        GLES20.glUniform3f(u("uHigh"), red(current.highColor), green(current.highColor), blue(current.highColor))
        GLES20.glUniform1f(u("uGeometry"), current.geometry.toFloat())
        GLES20.glUniform1f(u("uLayers"), current.layers)
        GLES20.glUniform1f(u("uCoverage"), current.coverage)
        GLES20.glUniform1f(u("uWaveAmp"), current.waveAmp)
        GLES20.glUniform1f(u("uWaveFreq"), current.waveFreq)
        GLES20.glUniform1f(u("uDirection"), current.direction)
        GLES20.glUniform1f(u("uBurst"), current.burst)
        GLES20.glUniform1f(u("uAutomation"), current.automation.toFloat())
        GLES20.glUniform1f(u("uAutoParam"), current.automationParam)

        val pos = GLES20.glGetAttribLocation(program, "aPos")
        GLES20.glEnableVertexAttribArray(pos)
        quad.position(0)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
    }

    fun release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun u(name: String): Int = uniforms[name] ?: -1

    private fun red(color: Int): Float = ((color shr 16) and 0xFF) / 255f

    private fun green(color: Int): Float = ((color shr 8) and 0xFF) / 255f

    private fun blue(color: Int): Float = (color and 0xFF) / 255f

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

    private companion object {
        val UNIFORM_NAMES = listOf(
            "uRes", "uTime", "uIntensity", "uSaturation", "uDensity", "uGlow",
            "uRay", "uSpeed", "uSharp", "uStars", "uCloud", "uSkyLum",
            "uCreative", "uNightGrade", "uLow", "uMid", "uHigh", "uGeometry",
            "uLayers", "uCoverage", "uWaveAmp", "uWaveFreq", "uDirection",
            "uBurst", "uAutomation", "uAutoParam",
        )

        const val VERTEX = """
            attribute vec2 aPos;
            varying vec2 vUv;
            void main() {
                vUv = aPos * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        const val FRAGMENT = """
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
            uniform vec3 uLow;
            uniform vec3 uMid;
            uniform vec3 uHigh;
            uniform float uGeometry;
            uniform float uLayers;
            uniform float uCoverage;
            uniform float uWaveAmp;
            uniform float uWaveFreq;
            uniform float uDirection;
            uniform float uBurst;
            uniform float uAutomation;
            uniform float uAutoParam;

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

            vec3 palette(float y, float t) {
                float yy = clamp(y, 0.0, 1.0);
                vec3 c = mix(uLow, uMid, smoothstep(0.10, 0.55, yy));
                c = mix(c, uHigh, smoothstep(0.55, 0.95, yy));
                if (uGeometry > 3.5) {
                    float phase = 0.5 + 0.5 * sin(yy * 9.0 + t * 0.8);
                    c = mix(c, mix(uMid, uHigh, phase), 0.55);
                }
                return c;
            }

            float curtainShape(vec2 p, float yy, float t, float seed, float aspect) {
                float wave = uWaveAmp * 0.14 * sin(p.x * (3.0 + 9.0 * uWaveFreq) + t * 0.7 + seed);
                float y = yy + wave;
                float edge = 0.24 + 0.20 * fbm(vec2(p.x * 1.7 + t * 0.05 + seed, 3.0));
                float top = edge + 0.50 + 0.22 * fbm(vec2(p.x * 2.3 - t * 0.03 + seed, 7.0));
                float band = smoothstep(edge, edge + 0.07, y) * (1.0 - smoothstep(top, top + 0.30, y));
                float coverNoise = fbm(vec2(p.x * 1.3 - t * 0.03 + seed * 0.3, 5.0));
                band *= mix(1.0, 0.35 + 0.75 * coverNoise, 0.55);
                band *= mix(0.55, 1.0, uCoverage);
                return band;
            }

            float coronaShape(vec2 p, float yy, float t, float seed, float aspect) {
                vec2 c = vec2(0.5 * aspect, 1.05);
                vec2 d = p - c;
                float r = length(d);
                float ang = atan(d.x, -d.y + 0.0001);
                float rays = fbm(vec2(ang * 5.0 + t * 0.12 + seed, r * 3.0 - t * 0.18));
                float crown = smoothstep(0.02, 0.18, r) * (1.0 - smoothstep(0.45, 0.95, r));
                float width = mix(0.55, 1.0, uCoverage);
                return crown * smoothstep(0.20, 0.75, rays) * width;
            }

            float arcShape(vec2 p, float yy, float t, float seed, float aspect) {
                float x = p.x / max(aspect, 0.001) - 0.5;
                float center = 0.70 - 0.22 * (x * 2.0) * (x * 2.0);
                float width = 0.035 + 0.18 * uCoverage;
                float band = 1.0 - smoothstep(width * 0.35, width, abs(yy - center));
                float ripple = 0.80 + 0.20 * sin(p.x * 6.0 + t * 0.5 + seed);
                return band * ripple;
            }

            float pillarsShape(vec2 p, float yy, float t, float seed, float aspect) {
                float colNoise = fbm(vec2(p.x * 10.0 + t * 0.06 + seed, 1.7));
                float mask = smoothstep(0.62 - 0.20 * uCoverage, 0.78, colNoise);
                float vertical = smoothstep(0.06, 0.30, yy) * (1.0 - smoothstep(0.80, 1.0, yy));
                float flicker = 0.75 + 0.25 * sin(p.x * 23.0 + t * 1.2 + seed);
                return mask * vertical * flicker;
            }

            float festivalShape(vec2 p, float yy, float t, float seed, float aspect) {
                float b1 = sin((p.x * 2.2 + yy * 3.4) * 3.0 + t * 0.55 + seed);
                float b2 = sin((p.x * 3.8 - yy * 2.6) * 2.0 - t * 0.40 + seed * 1.7);
                float a1 = smoothstep(0.55, 0.95, b1);
                float a2 = smoothstep(0.70, 0.98, b2) * 0.70;
                float vertical = smoothstep(0.05, 0.25, yy) * (1.0 - smoothstep(0.85, 1.0, yy));
                return max(a1, a2) * vertical * mix(0.55, 1.0, uCoverage);
            }

            float shapeAt(vec2 p, float yy, float t, float seed, float aspect) {
                if (uGeometry < 0.5) return curtainShape(p, yy, t, seed, aspect);
                if (uGeometry < 1.5) return coronaShape(p, yy, t, seed, aspect);
                if (uGeometry < 2.5) return arcShape(p, yy, t, seed, aspect);
                if (uGeometry < 3.5) return pillarsShape(p, yy, t, seed, aspect);
                return festivalShape(p, yy, t, seed, aspect);
            }

            float automationEnvelope(float t) {
                if (uAutomation > 0.5 && uAutomation < 1.5) {
                    return smoothstep(0.0, 3.0, t);
                }
                if (uAutomation > 1.5 && uAutomation < 2.5) {
                    return 0.72 + 0.28 * sin(t * 0.45);
                }
                if (uAutomation > 2.5 && uAutomation < 3.5) {
                    return 0.70 + 0.30 * sin(t * 1.7);
                }
                if (uAutomation > 3.5) {
                    float at = 2.0 + 18.0 * uAutoParam;
                    return smoothstep(at - 0.3, at + 1.8, t);
                }
                return 1.0;
            }

            void main() {
                vec2 uv = vUv;
                float aspect = uRes.x / max(uRes.y, 1.0);
                float dir = uDirection < 0.0 ? -1.0 : 1.0;
                float t = uTime * uSpeed * dir;
                vec2 p = vec2(uv.x * aspect * dir, uv.y);

                float a = 0.0;
                for (int i = 0; i < 4; i++) {
                    float fi = float(i);
                    if (fi < uLayers) {
                        float off = fi * 0.055;
                        vec2 q = vec2(p.x * (1.0 + fi * 0.11) + off, p.y);
                        float yy = uv.y - off * 0.35;
                        float s = shapeAt(q, yy, t * (1.0 - fi * 0.10), fi * 7.31, aspect);
                        a = max(a, s * (1.0 - fi * 0.16));
                    }
                }

                float rayNoise = fbm(vec2(p.x * 14.0 + t * 0.20, uv.y * 2.5 - t * 0.35));
                float rays = mix(1.0, smoothstep(0.22, 0.85, rayNoise), uRay);
                float folds = fbm(vec2(p.x * 3.0 - t * 0.12, uv.y * 3.0 + t * 0.08));
                float detail = mix(0.55, 1.15, folds);
                float sharp = mix(1.0, 0.35 + 0.65 * smoothstep(0.30, 0.90, rayNoise), uSharp);

                a *= rays * detail * sharp * uDensity;

                float skyHere = uSkyLum * mix(1.0, 0.55, uv.y);
                float gate = 1.0 - smoothstep(0.10, 0.45, skyHere);
                a *= mix(gate, 1.0, uCreative);

                a *= smoothstep(0.0, 0.10, uv.y);
                a *= 1.0 - smoothstep(0.92, 1.0, uv.y);

                vec3 col = palette(uv.y, t);
                col = mix(vec3(dot(col, vec3(0.299, 0.587, 0.114))), col, uSaturation);

                float haloY = uGeometry < 1.5
                    ? 0.30 + 0.10 * sin(p.x * 2.0 + t * 0.05)
                    : 0.55 + 0.16 * sin(p.x * 1.6 - t * 0.04);
                float halo = exp(-abs(uv.y - haloY) * 7.0)
                    * (0.25 + 0.75 * fbm(vec2(p.x * 4.0 + t * 0.06, uv.y * 3.0)));
                col += uMid * halo * uGlow * 0.25;

                if (uBurst > 0.001) {
                    float b = 0.5 + 0.5 * sin(t * (2.0 + 7.0 * uBurst) + 4.0 * fbm(vec2(t * 0.15, 2.0)));
                    a *= mix(1.0, 0.55 + 0.85 * b, uBurst);
                }

                a *= automationEnvelope(t);

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
