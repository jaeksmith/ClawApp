package com.jaek.clawapp

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.i("App", "ClawApp starting")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = throwable.stackTraceToString()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(filesDir, "crash_$stamp.txt")
                file.writeText("Thread: ${thread.name}\n\n$trace")
                // Also inject into AppLogger so it shows in the log screen if app recovers
                AppLogger.e("CRASH", "Thread: ${thread.name} — ${throwable.message}")
                throwable.stackTrace.take(10).forEach { AppLogger.e("CRASH", "  at $it") }
                Log.e("ClawApp", "CRASH written to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("ClawApp", "Failed to write crash log", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Load any crash files from previous sessions into the log
        loadPreviousCrashes()
    }

    private fun loadPreviousCrashes() {
        try {
            filesDir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(3)
                ?.forEach { f ->
                    AppLogger.e("PREV_CRASH", "=== ${f.name} ===")
                    f.readLines().take(30).forEach { AppLogger.e("PREV_CRASH", it) }
                }
        } catch (e: Exception) {
            Log.w("App", "Could not load previous crashes: ${e.message}")
        }
    }
}
