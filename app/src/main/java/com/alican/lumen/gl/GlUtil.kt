package com.alican.lumen.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object GlUtil {
    private const val TAG = "GlUtil"

    val fullScreenQuad: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun compile(type: Int, source: String): Int {
        val normalized = source.trimStart()
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, normalized)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $log\n---\n$normalized")
        }
        return shader
    }

    fun program(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw IllegalStateException("Program link failed: $log")
        }
        return program
    }

    fun checkError(where: String) {
        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR) {
            Log.w(TAG, "GL error 0x${Integer.toHexString(err)} at $where")
        }
    }
}
