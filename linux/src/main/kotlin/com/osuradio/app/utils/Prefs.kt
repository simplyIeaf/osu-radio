package com.osuradio.app.utils

import java.io.File
import java.util.Properties

/**
 * Minimal drop-in replacement for Android's SharedPreferences on the desktop.
 * Backed by a simple `.properties` file inside the osu!radio folder.
 */
class Prefs(private val file: File) {
    private val props = Properties()
    private var dirty = false

    init {
        try {
            if (file.exists()) file.inputStream().use { props.load(it) }
        } catch (e: Exception) {
            Logger.error("Prefs", "Failed to load preferences", e)
        }
    }

    fun getString(key: String, def: String = ""): String = props.getProperty(key) ?: def

    fun getLong(key: String, def: Long = 0L): Long =
        props.getProperty(key)?.toLongOrNull() ?: def

    fun getBoolean(key: String, def: Boolean = false): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: def

    fun getInt(key: String, def: Int = 0): Int =
        props.getProperty(key)?.toIntOrNull() ?: def

    fun putString(key: String, value: String) { props.setProperty(key, value); dirty = true; flush() }

    fun putLong(key: String, value: Long) { props.setProperty(key, value.toString()); dirty = true; flush() }

    fun putBoolean(key: String, value: Boolean) { props.setProperty(key, value.toString()); dirty = true; flush() }

    fun putInt(key: String, value: Int) { props.setProperty(key, value.toString()); dirty = true; flush() }

    fun remove(key: String) { props.remove(key); dirty = true; flush() }

    fun contains(key: String): Boolean = props.containsKey(key)

    fun apply() = flush()

    private fun flush() {
        if (!dirty) return
        dirty = false
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "osu!radio desktop preferences") }
        } catch (e: Exception) {
            Logger.error("Prefs", "Failed to save preferences", e)
        }
    }
}
