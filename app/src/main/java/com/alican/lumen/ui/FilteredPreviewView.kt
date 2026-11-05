package com.alican.lumen.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import com.alican.lumen.gl.FilterPipeline
import com.alican.lumen.model.FilterParams
import kotlin.math.max
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GPU preview surface. Renders either a still bitmap or an OES video frame
 * through the exact same pipeline that produces the exported file.
 */
class FilteredPreviewView(context: Context) : GLSurfaceView(context) {

    private val renderer = PreviewRenderer()
    private val textureCallbacks = mutableListOf<(SurfaceTexture) -> Unit>()
    private var readyTexture: SurfaceTexture? = null

    @Volatile
    var params: FilterParams = FilterParams()

    /** Longest edge of the preview bitmap; export always uses the full original. */
    private val previewMaxEdge = 1440

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setPhoto(bitmap: Bitmap) {
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > previewMaxEdge) {
            val s = previewMaxEdge.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * s).toInt()),
                max(1, (bitmap.height * s).toInt()),
                true,
            )
        } else {
            bitmap
        }
        renderer.mode = Mode.PHOTO
        renderer.sourceAspect = scaled.width.toFloat() / scaled.height
        renderer.pendingBitmap = scaled
        requestRender()
    }

    /**
     * Provides a Surface that a MediaPlayer can render into. The callback may be
     * invoked immediately or as soon as the GL context exists.
     */
    fun withVideoSurface(onReady: (Surface) -> Unit) {
        renderer.mode = Mode.VIDEO
        val existing = readyTexture
        if (existing != null) {
            onReady(Surface(existing))
            return
        }
        textureCallbacks += { texture -> onReady(Surface(texture)) }
    }

    fun setVideoAspect(aspect: Float) {
        renderer.sourceAspect = aspect
        requestRender()
    }

    fun releasePreviewResources() {
        textureCallbacks.clear()
    }

    private inner class PreviewRenderer : Renderer {
        @Volatile var mode: Mode = Mode.PHOTO
        @Volatile var pendingBitmap: Bitmap? = null
        @Volatile var sourceAspect: Float = 1f

        private var photoTexture = 0
        private var oesTexture = 0
        private var surfaceTexture: SurfaceTexture? = null
        private val texMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        private var frameTimeNanos = 0L

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            FilterPipelineHolder.pipeline.ensurePrograms()

            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            oesTexture = ids[0]
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            val texture = SurfaceTexture(oesTexture)
            texture.setDefaultBufferSize(1920, 1080)
            texture.setOnFrameAvailableListener { requestRender() }
            surfaceTexture = texture
            readyTexture = texture
            val pending = textureCallbacks.toList()
            textureCallbacks.clear()
            if (pending.isNotEmpty()) {
                post { pending.forEach { it(texture) } }
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES30.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (mode == Mode.VIDEO) {
                surfaceTexture?.let { st ->
                    try {
                        st.updateTexImage()
                        st.getTransformMatrix(texMatrix)
                        frameTimeNanos = st.timestamp
                    } catch (_: Exception) {
                        // No frame queued yet.
                    }
                }
            }

            pendingBitmap?.let { bmp ->
                photoTexture = FilterPipeline.uploadTexture(bmp, photoTexture)
                pendingBitmap = null
            }

            val usePhoto = mode == Mode.PHOTO
            val src = if (usePhoto) photoTexture else oesTexture
            if (src == 0) return

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glClearColor(0.03f, 0.03f, 0.04f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            val aspect = if (sourceAspect > 0f) sourceAspect else 1f
            val viewW = width
            val viewH = height
            if (viewW <= 0 || viewH <= 0) return

            var drawW = viewW
            var drawH = (viewW / aspect).toInt()
            if (drawH > viewH) {
                drawH = viewH
                drawW = (viewH * aspect).toInt()
            }
            val x = (viewW - drawW) / 2
            val y = (viewH - drawH) / 2

            FilterPipelineHolder.pipeline.render(
                srcTexture = src,
                useOes = !usePhoto,
                texMatrix = texMatrix,
                params = params,
                destFramebuffer = 0,
                destWidth = drawW,
                destHeight = drawH,
                flipY = false,
                timeSeconds = frameTimeNanos / 1_000_000_000f,
                viewport = intArrayOf(x, y, drawW, drawH),
            )
        }
    }
}

/** One GL pipeline shared by the preview surface (created on its GL thread). */
internal object FilterPipelineHolder {
    val pipeline = FilterPipeline()
}

private enum class Mode { PHOTO, VIDEO }
