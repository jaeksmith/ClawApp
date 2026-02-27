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

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(filesDir, "crash_$stamp.txt")
                file.writeText("Thread: ${thread.name}\n\n${throwable.stackTraceToString()}")
                Log.e("ClawApp", "CRASH written to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("ClawApp", "Failed to write crash log", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
