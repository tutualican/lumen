package com.alican.lumen.gl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import com.alican.lumen.model.FilterParams
import com.alican.lumen.model.Tint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Owns every GL object and renders one filtered frame.
 *
 * The pipeline is resolution independent: callers pass the destination size and
 * source texture, and the renderer writes directly into the bound framebuffer.
 * That lets preview, photo export and video export share the exact same colour
 * math, which is what keeps exported files identical to what the user saw.
 */
class FilterPipeline {

    private var grade2d = 0
    private var gradeOes = 0
    private var bright = 0
    private var blur = 0
    private var composite = 0
    private val flipVertex = Shaders.VERTEX_FLIP

    private var vao = 0

    private var gradeFbo: Framebuffer? = null
    private var bloomA: Framebuffer? = null
    private var bloomB: Framebuffer? = null
    private var halA: Framebuffer? = null
    private var halB: Framebuffer? = null
    private var targetW = 0
    private var targetH = 0

    private var stampTexture = 0
    private var stampDirty = false
    private var pendingStamp: Bitmap? = null

    /** Configures a pre-generated date-stamp bitmap (transparent background). */
    fun setStampBitmap(bitmap: Bitmap?) {
        pendingStamp = bitmap
        stampDirty = true
    }

    /**
     * Forgets every GL name without deleting it. Must be called when the EGL
     * context was lost (surface recreated): the old ids are meaningless in the
     * new context and deleting them could destroy unrelated objects.
     */
    fun invalidateGl() {
        grade2d = 0
        gradeOes = 0
        bright = 0
        blur = 0
        composite = 0
        vao = 0
        gradeFbo = null
        bloomA = null
        bloomB = null
        halA = null
        halB = null
        stampTexture = 0
        stampDirty = true
    }

    fun ensurePrograms() {
        if (grade2d != 0) return
        grade2d = GlUtil.program(Shaders.VERTEX, Shaders.gradeShader(false))
        gradeOes = runCatching {
            GlUtil.program(Shaders.VERTEX, Shaders.gradeShader(true))
        }.getOrDefault(0)
        bright = GlUtil.program(Shaders.VERTEX, Shaders.BRIGHT)
        blur = GlUtil.program(Shaders.VERTEX, Shaders.BLUR)
        composite = GlUtil.program(flipVertex, Shaders.COMPOSITE)

        val buffers = IntArray(1)
        GLES30.glGenBuffers(1, buffers, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
        GlUtil.fullScreenQuad.position(0)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            GlUtil.fullScreenQuad.capacity() * 4,
            GlUtil.fullScreenQuad,
            GLES30.GL_STATIC_DRAW,
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        val arrays = IntArray(1)
        GLES30.glGenVertexArrays(1, arrays, 0)
        vao = arrays[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun uploadStampIfNeeded() {
        if (!stampDirty) return
        val bmp = pendingStamp
        if (bmp == null) {
            if (stampTexture != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(stampTexture), 0)
                stampTexture = 0
            }
            stampDirty = false
            return
        }
        if (stampTexture == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            stampTexture = ids[0]
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stampTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        stampDirty = false
    }

    private fun ensureTargets(width: Int, height: Int) {
        if (gradeFbo?.width == width && gradeFbo?.height == height) return
        releaseTargets()
        gradeFbo = Framebuffer(width, height)
        bloomA = Framebuffer(max(1, width / 4), max(1, height / 4))
        bloomB = Framebuffer(max(1, width / 4), max(1, height / 4))
        halA = Framebuffer(max(1, width / 8), max(1, height / 8))
        halB = Framebuffer(max(1, width / 8), max(1, height / 8))
        targetW = width
        targetH = height
    }

    fun releaseTargets() {
        gradeFbo?.release(); gradeFbo = null
        bloomA?.release(); bloomA = null
        bloomB?.release(); bloomB = null
        halA?.release(); halA = null
        halB?.release(); halB = null
    }

    private fun drawQuad() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    /**
     * Renders [params] over [srcTexture] into [destFramebuffer] (0 = screen).
     *
     * @param flipY true when the destination is a MediaCodec input surface.
     * @param uvScale/uvOffset map local tile UV to full-image UV (photo tiling).
     */
    fun render(
        srcTexture: Int,
        useOes: Boolean,
        texMatrix: FloatArray?,
        params: FilterParams,
        destFramebuffer: Int,
        destWidth: Int,
        destHeight: Int,
        flipY: Boolean,
        fullWidth: Int = destWidth,
        fullHeight: Int = destHeight,
        uvScaleX: Float = 1f,
        uvScaleY: Float = 1f,
        uvOffsetX: Float = 0f,
        uvOffsetY: Float = 0f,
        timeSeconds: Float = 0f,
        viewport: IntArray? = null,
        rotationQuadrant: Int = 0,
        gradeWidth: Int = destWidth,
        gradeHeight: Int = destHeight,
    ) {
        ensurePrograms()
        ensureTargets(gradeWidth, gradeHeight)
        uploadStampIfNeeded()

        val grade = if (useOes && gradeOes != 0) gradeOes else grade2d

        // ---- 1. Grade pass into full-res FBO -------------------------------
        val g = gradeFbo!!
        g.bind()
        GLES30.glUseProgram(grade)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(if (useOes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D, srcTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(grade, "uTex"), 0)
        if (useOes) {
            texMatrix?.let {
                GLES30.glUniformMatrix4fv(
                    GLES30.glGetUniformLocation(grade, "uTexMatrix"), 1, false, it, 0,
                )
            }
        }
        GLES30.glUniform2f(GLES30.glGetUniformLocation(grade, "uTexel"), 1f / gradeWidth, 1f / gradeHeight)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uExposure"), params.exposure)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uTemperature"), params.temperature)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uTintAmt"), params.tint)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uContrast"), params.contrast)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uSaturation"), params.saturation)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uVibrance"), params.vibrance)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uFade"), params.fade)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uGamma"), params.gamma)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uHue"), params.hueShift)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uShadowAmt"), params.shadowAmount)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uHighAmt"), params.highlightAmount)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uMono"), params.mono)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uChromatic"), params.chromatic)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uPixelSize"), params.pixelSize)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(grade, "uSharpen"), params.sharpen)
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(grade, "uShadowTint"),
            params.shadowTint.r, params.shadowTint.g, params.shadowTint.b,
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(grade, "uHighTint"),
            params.highlightTint.r, params.highlightTint.g, params.highlightTint.b,
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(grade, "uChannelMix"),
            params.channelMix.r, params.channelMix.g, params.channelMix.b,
        )
        drawQuad()

        // ---- 2. Bloom / halation chains ------------------------------------
        var bloomTex = 0
        var halTex = 0
        if (params.bloom > 0.0001f) {
            bloomTex = blurChain(g.textureId, bloomA!!, bloomB!!, threshold = 0.62f)
        }
        if (params.halation > 0.0001f) {
            halTex = blurChain(g.textureId, halA!!, halB!!, threshold = 0.45f)
        }

        // ---- 3. Composite to destination -----------------------------------
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, destFramebuffer)
        if (viewport != null && viewport.size == 4) {
            GLES30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        } else {
            GLES30.glViewport(0, 0, destWidth, destHeight)
        }
        GLES30.glUseProgram(composite)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, g.textureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(composite, "uBase"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(composite, "uBloom"), 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, halTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(composite, "uHalation"), 2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stampTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(composite, "uStamp"), 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uFlipY"), if (flipY) -1f else 1f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(composite, "uRotation"), rotationQuadrant)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(composite, "uFullRes"), fullWidth.toFloat(), fullHeight.toFloat())
        GLES30.glUniform2f(GLES30.glGetUniformLocation(composite, "uUvScale"), uvScaleX, uvScaleY)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(composite, "uUvOffset"), uvOffsetX, uvOffsetY)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(composite, "uFullAspect"),
            fullWidth.toFloat() / max(fullHeight.toFloat(), 1f),
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uBloomStr"), params.bloom * 1.35f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uHalStr"), params.halation * 1.6f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uVignette"), params.vignette)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uGrain"), params.grain * 0.55f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uGrainSize"), params.grainSize)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uTime"), timeSeconds)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uLeak"), params.lightLeak)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uLetterbox"), params.letterbox)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(composite, "uScanline"), params.scanline)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(composite, "uStampOn"),
            if (params.dateStamp && stampTexture != 0) 1f else 0f,
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(composite, "uHalTint"),
            params.halationTint.r, params.halationTint.g, params.halationTint.b,
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(composite, "uLeakTint"),
            params.lightLeakTint.r, params.lightLeakTint.g, params.lightLeakTint.b,
        )
        drawQuad()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun blurChain(
        sourceTexture: Int,
        a: Framebuffer,
        b: Framebuffer,
        threshold: Float,
    ): Int {
        // Bright pass -> a
        a.bind()
        GLES30.glUseProgram(bright)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sourceTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(bright, "uTex"), 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(bright, "uThreshold"), threshold)
        drawQuad()

        // Horizontal blur a -> b, vertical blur b -> a
        GLES30.glUseProgram(blur)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(blur, "uTex"), 0)

        b.bind()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, a.textureId)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(blur, "uDir"), 1f / a.width, 0f)
        drawQuad()

        a.bind()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, b.textureId)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(blur, "uDir"), 0f, 1f / b.height)
        drawQuad()

        // Second, wider iteration for a soft glow.
        b.bind()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, a.textureId)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(blur, "uDir"), 2.2f / a.width, 0f)
        drawQuad()

        a.bind()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, b.textureId)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(blur, "uDir"), 0f, 2.2f / b.height)
        drawQuad()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return a.textureId
    }

    fun release() {
        releaseTargets()
        if (stampTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(stampTexture), 0)
            stampTexture = 0
        }
        listOf(grade2d, gradeOes, bright, blur, composite).forEach {
            if (it != 0) GLES30.glDeleteProgram(it)
        }
        grade2d = 0; gradeOes = 0; bright = 0; blur = 0; composite = 0
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
    }

    companion object {
        fun uploadTexture(bitmap: Bitmap, reuse: Int = 0): Int {
            // GLUtils only understands 8888/565 data; HDR or F16 decodes must be
            // converted first or the upload throws.
            val source = if (
                bitmap.config == Bitmap.Config.ARGB_8888 || bitmap.config == Bitmap.Config.RGB_565
            ) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            }
            val ids = IntArray(1)
            if (reuse == 0) {
                GLES30.glGenTextures(1, ids, 0)
            } else {
                ids[0] = reuse
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, source, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            if (source !== bitmap) source.recycle()
            return ids[0]
        }

        fun readPixels(width: Int, height: Int): ByteBuffer {
            val buffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder())
            buffer.position(0)
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            buffer.position(0)
            return buffer
        }
    }
}

internal fun Tint.toArray(): FloatArray = floatArrayOf(r, g, b)
