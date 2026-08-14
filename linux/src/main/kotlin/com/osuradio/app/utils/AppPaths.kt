package com.osuradio.app.utils

import java.io.File

/**
 * Filesystem locations for the desktop build.
 *
 * The library keeps the exact same on-disk layout as the Android app
 * (`~/osu!radio/Songs/...`) so playlists, settings and songs stay compatible
 * with the phone version.
 */
object AppPaths {
    fun osuRadioDir(): File =
        File(System.getProperty("user.home"), "osu!radio").apply { mkdirs() }

    fun songsDir(): File =
        File(osuRadioDir(), "Songs").apply { mkdirs() }

    fun osuDroidSongsDir(): File =
        File(System.getProperty("user.home"), "osu!droid/Songs")

    fun cacheDir(): File =
        File(System.getProperty("java.io.tmpdir"), "osu-radio").apply { mkdirs() }

    fun prefsFile(): File = File(osuRadioDir(), "desktop_prefs.properties")
}
