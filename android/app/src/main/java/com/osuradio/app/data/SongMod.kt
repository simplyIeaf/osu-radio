package com.osuradio.app.data

enum class SongMod {
    NONE,
    DAYCORE,
    NIGHTCORE,
    DOUBLE_TIME,
    HALF_TIME,
    WIND_UP,
    WIND_DOWN,
    BASS_BOOST,
    VAPORWAVE,
    CUSTOM_SPEED
}

data class ModSettings(
    val activeMod: SongMod = SongMod.NONE,
    val customSpeed: Float = 1.0f
)
