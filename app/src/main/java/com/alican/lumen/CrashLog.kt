package com.alican.lumen

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Persists the last uncaught exception so it can be read back on the next launch.
 *
 * Without this, a crash on a user device is invisible: there is no adb, and the
 * system dialog only says "the app stopped". The home screen shows the stored
 * trace with a copy button instead.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
                val header = buildString {
                    appendLine("thread : ${thread.name}")
                    appendLine("device : ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine()
                }
                File(appContext.filesDir, FILE_NAME).writeText(header + stack)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? = runCatching {
        File(context.applicationContext.filesDir, FILE_NAME)
            .takeIf { it.exists() }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
    }
}
