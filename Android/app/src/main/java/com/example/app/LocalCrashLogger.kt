package com.eyeguardian.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline crash diagnostics. Nothing is uploaded: logs remain in the app's
 * private files directory and are removed by the user's clear-data action.
 */
internal object LocalCrashLogger {
    private const val DIRECTORY_NAME = "crash_logs"
    private const val MAX_LOGS = 8
    private const val MAX_STACK_TRACE_CHARS = 24_000

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                write(appContext, thread, throwable)
                previous?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    fun count(context: Context): Int = crashDirectory(context).listFiles()?.count { it.isFile } ?: 0

    fun clear(context: Context) {
        crashDirectory(context).listFiles()?.forEach { it.delete() }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val directory = crashDirectory(context)
            directory.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val trace = throwable.stackTraceToString().take(MAX_STACK_TRACE_CHARS)
            File(directory, "crash-$stamp.txt").writeText(
                buildString {
                    appendLine("Eye Guardian local crash log")
                    appendLine("time=$stamp")
                    appendLine("thread=${thread.name}")
                    appendLine("exception=${throwable.javaClass.name}")
                    appendLine()
                    append(trace)
                }
            )
            directory.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_LOGS)
                ?.forEach { it.delete() }
        }
    }

    private fun crashDirectory(context: Context): File =
        File(context.applicationContext.filesDir, DIRECTORY_NAME)
}
