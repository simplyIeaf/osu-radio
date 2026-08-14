package com.osuradio.app.utils

import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private var logsDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun init(osuRadioDir: File) {
        logsDir = File(osuRadioDir, "Logs").also { it.mkdirs() }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = timeFormat.format(Date())
        val logContent = buildString {
            appendLine("=== ERROR LOG ===")
            appendLine("Timestamp: $timestamp")
            appendLine("Tag: $tag")
            appendLine("Message: $message")
            if (throwable != null) {
                appendLine("Exception: ${throwable::class.java.simpleName}")
                appendLine("Exception Message: ${throwable.message}")
                appendLine("Stack Trace:")
                appendLine(throwable.stackTraceToString())
            }
            appendLine("--- System Info ---")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("==================")
        }
        writeLog("error", logContent)
        android.util.Log.e(tag, message, throwable)
    }

    fun info(tag: String, message: String) {
        android.util.Log.i(tag, message)
    }

    fun warn(tag: String, message: String) {
        android.util.Log.w(tag, message)
    }

    private fun writeLog(level: String, content: String) {
        try {
            val dir = logsDir ?: return
            val date = dateFormat.format(Date())
            val logFile = File(dir, "${level}_$date.txt")
            logFile.appendText(content + "\n")
        } catch (e: Exception) {
            android.util.Log.e("Logger", "Failed to write log file", e)
        }
    }
}
