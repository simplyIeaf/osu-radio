package com.osuradio.app.utils

import java.io.File

/**
 * Filesystem locations for the desktop build, following the XDG Base Directory
 * specification like most other Linux applications and AppImages:
 *
 *  - config:  `~/.config/osu-radio/`  (config.txt, preferences)
 *  - data:    `~/.local/share/osu-radio/`  (songs, playlist library, logs)
 *  - cache:   `~/.cache/osu-radio/`  (temporary download files)
 */
object AppPaths {
    private fun homeDir(): File = File(System.getProperty("user.home"))

    fun configDir(): File = File(homeDir(), ".config/osu-radio").apply { mkdirs() }

    fun dataDir(): File = File(homeDir(), ".local/share/osu-radio").apply { mkdirs() }

    fun osuRadioDir(): File = dataDir()

    fun songsDir(): File = File(dataDir(), "Songs").apply { mkdirs() }

    fun osuDroidSongsDir(): File = File(homeDir(), "osu!droid/Songs")

    fun cacheDir(): File = File(homeDir(), ".cache/osu-radio").apply { mkdirs() }

    fun prefsFile(): File = File(configDir(), "desktop_prefs.properties")

    fun legacyDataDir(): File = File(homeDir(), "osu!radio")

    fun migrateLegacyData() {
        val legacy = legacyDataDir()
        if (!legacy.exists() || !legacy.isDirectory) return
        val legacySongs = File(legacy, "Songs")
        if (legacySongs.isDirectory) {
            legacySongs.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
                val dest = File(songsDir(), folder.name)
                if (!dest.exists()) {
                    try {
                        if (!folder.renameTo(dest)) {
                            folder.copyRecursively(dest)
                            folder.deleteRecursively()
                        }
                    } catch (e: Exception) {
                        Logger.warn("AppPaths", "Could not migrate ${folder.name} to ${dest.path}")
                    }
                }
            }
        }
        val legacyLogs = File(legacy, "Logs")
        if (legacyLogs.isDirectory && legacyLogs.listFiles()?.isNotEmpty() == true) {
            val destLogs = File(dataDir(), "Logs")
            if (!destLogs.exists()) legacyLogs.renameTo(destLogs)
        }
    }
}
