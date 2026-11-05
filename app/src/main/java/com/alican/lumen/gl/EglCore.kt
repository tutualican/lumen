package com.alican.lumen.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.view.Surface

/**
 * Minimal EGL 3.0 helper used for offscreen rendering (photo export) and for
 * rendering directly into a MediaCodec input surface (video export).
 */
class EglCore {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0
        ) { "No suitable EGL config" }
        config = configs[0]

        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
            0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
    }

    fun makeCurrent(surface: EGLSurface) {
        this.surface = surface
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw IllegalStateException("eglMakeCurrent failed")
        }
    }

    fun createWindowSurface(target: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val result = EGL14.eglCreateWindowSurface(display, config, target, attribs, 0)
        check(result != null && result != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        return result
    }

    /** 1x1 pbuffer; used as the current surface while rendering into FBOs. */
    fun createOffscreenSurface(): EGLSurface {
        val attribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE,
        )
        val result = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        check(result != null && result != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        return result
    }

    fun makeCurrent() = makeCurrent(surface)

    fun swapBuffers(surface: EGLSurface = this.surface): Boolean =
        EGL14.eglSwapBuffers(display, surface)

    fun setPresentationTime(surface: EGLSurface, nanos: Long) {
        EGLExt.presentationTime(display, surface, nanos)
    }

    fun releaseSurface(surface: EGLSurface?) {
        if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, surface)
        }
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
    }

    fun maxTextureSize(): Int {
        val v = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, v, 0)
        return v[0]
    }
}

internal object EGLExt {
    /** EGL_OPENGL_ES3_BIT, not exposed by android.opengl.EGL14. */
    const val EGL_OPENGL_ES3_BIT = 0x0040

    fun presentationTime(display: EGLDisplay, surface: EGLSurface, nanos: Long) {
        android.opengl.EGLExt.eglPresentationTimeANDROID(display, surface, nanos)
    }
}
