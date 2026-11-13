package com.auroraar.capture.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.opengl.GLES20
import java.nio.FloatBuffer

/**
 * Pre-rasterised "Aurora AR · simulated" wordmark drawn into the encoder pass
 * for Free recordings. The bitmap is generated once; every frame only pays for
 * one textured quad. Kept out of the Compose layer on purpose: the file must
 * match the preview and must never contain UI.
 */
class WatermarkRenderer(context: Context) {

    private var program = 0
    private var texture = 0
    private var uTexture = -1
    private var uScale = -1
    private var uOffset = -1
    private var uAlpha = -1
    private var aPos = -1
    private val quad: FloatBuffer = GlUtil.quad()

    init {
        val wordmark = context.getString(com.auroraar.R.string.watermark_wordmark)
        val simulated = context.getString(com.auroraar.R.string.watermark_simulated)
        texture = GlUtil.createTexture(buildBitmap(wordmark, simulated))
    }

    fun init() {
        program = GlUtil.buildProgram(VERTEX, FRAGMENT)
        if (program == 0) error("watermark shader failed")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        uScale = GLES20.glGetUniformLocation(program, "uScale")
        uOffset = GLES20.glGetUniformLocation(program, "uOffset")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
    }

    /**
     * @param frameWidth output width in pixels
     * @param frameHeight output height in pixels
     * @param alpha 0 disables the draw entirely (Premium / not recording)
     */
    fun draw(frameWidth: Int, frameHeight: Int, alpha: Float) {
        if (alpha <= 0.001f || texture == 0 || frameWidth <= 0 || frameHeight <= 0) return

        // ~26% of frame width, capped so it never dominates a 4K frame; the
        // addendum asks for a small mark with a ~14% bottom safe margin.
        val widthPx = (frameWidth * WIDTH_FRACTION).coerceAtMost(MAX_WIDTH_PX)
        val heightPx = widthPx * BITMAP_HEIGHT / BITMAP_WIDTH
        val halfW = widthPx / frameWidth.toFloat()
        val halfH = heightPx / frameHeight.toFloat()
        val centerX = 1f - 2f * MARGIN_FRACTION - halfW
        val centerY = -1f + 2f * BOTTOM_FRACTION + halfH

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glUniform2f(uScale, halfW, halfH)
        GLES20.glUniform2f(uOffset, centerX, centerY)
        GLES20.glUniform1f(uAlpha, alpha)

        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        if (texture != 0) {
            GlUtil.deleteTexture(texture)
            texture = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun buildBitmap(wordmark: String, simulated: String): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Minimal aurora ribbon as the "logo" half of the lock-up.
        val ribbon = Path().apply {
            moveTo(28f, 176f)
            cubicTo(88f, 52f, 138f, 210f, 196f, 86f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 13f
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(7f, 0f, 3f, 0xB0000000.toInt())
        }
        paint.color = 0xFF6EF3B0.toInt()
        canvas.drawPath(ribbon, paint)
        paint.color = 0x993DD9C0.toInt()
        paint.strokeWidth = 7f
        canvas.drawPath(ribbon, paint)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            letterSpacing = 0.14f
            setShadowLayer(9f, 0f, 3f, 0xC0000000.toInt())
        }
        text.textSize = fitTextSize(text, wordmark, TEXT_LEFT, 104f)
        canvas.drawText(wordmark, TEXT_LEFT, 156f, text)

        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        text.letterSpacing = 0.42f
        text.textSize = fitTextSize(text, simulated, SUBTEXT_LEFT, 38f)
        text.color = 0xE6FFFFFF.toInt()
        canvas.drawText(simulated, SUBTEXT_LEFT, 220f, text)
        return bitmap
    }

    /**
     * Android does not wrap or ellipsise [Canvas.drawText], so a wordmark wider
     * than the texture is silently cut at the right edge. Scale the requested
     * size down until the measured line fits the bitmap with padding.
     */
    private fun fitTextSize(paint: Paint, text: String, left: Float, preferred: Float): Float {
        paint.textSize = preferred
        val available = BITMAP_WIDTH - left - TEXT_RIGHT_PADDING
        val measured = paint.measureText(text)
        if (measured <= available || measured <= 0f) return preferred
        return preferred * available / measured
    }

    private companion object {
        const val BITMAP_WIDTH = 1024
        const val BITMAP_HEIGHT = 288
        const val TEXT_LEFT = 226f
        const val SUBTEXT_LEFT = 230f
        const val TEXT_RIGHT_PADDING = 28f
        const val WIDTH_FRACTION = 0.26f
        const val MAX_WIDTH_PX = 420f
        const val MARGIN_FRACTION = 0.035f
        const val BOTTOM_FRACTION = 0.14f

        const val VERTEX = """
            attribute vec2 aPos;
            uniform vec2 uScale;
            uniform vec2 uOffset;
            varying vec2 vUv;
            void main() {
                vec2 uv = aPos * 0.5 + 0.5;
                vUv = vec2(uv.x, 1.0 - uv.y);
                gl_Position = vec4(aPos * uScale + uOffset, 0.0, 1.0);
            }
        """

        const val FRAGMENT = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTexture;
            uniform float uAlpha;
            void main() {
                vec4 c = texture2D(uTexture, vUv);
                gl_FragColor = vec4(c.rgb, c.a * uAlpha);
            }
        """
    }
}
