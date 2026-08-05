package com.osuradio.app.data

enum class AppTheme { PINK }

enum class AnimationStyle { SLIDE, FADE, SCALE, NONE }

enum class AudioTransition { NONE, FADE_IN_OUT, CROSSFADE, SWOOSH }

enum class RepeatMode { NONE, ONE, ALL }

enum class EqPreset(val label: String, val bandLevels: List<Int>) {
    FLAT         ("Flat",          listOf(    0,    0,    0,    0,    0)),
    BASS_BOOST   ("Bass Boost",    listOf(  800,  400,    0,    0,    0)),
    TREBLE_BOOST ("Treble Boost",  listOf(    0,    0,    0,  400,  800)),
    VOCAL        ("Vocal",         listOf( -300,    0,  500,  400, -100)),
    ELECTRONIC   ("Electronic",    listOf(  400,  300,    0,  300,  400)),
    ROCK         ("Rock",          listOf(  600,  300, -100,  400,  600)),
}

data class EqualizerSettings(
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0)
)

data class ThemeColors(
    val primary: Long? = null,
    val onPrimary: Long? = null,
    val secondary: Long? = null,
    val onSecondary: Long? = null,
    val tertiary: Long? = null,
    val background: Long? = null,
    val onBackground: Long? = null,
    val surface: Long? = null,
    val onSurface: Long? = null,
    val surfaceVariant: Long? = null,
    val onSurfaceVariant: Long? = null
)

data class AppSettings(
    val theme: AppTheme = AppTheme.PINK,
    val themeColors: ThemeColors = ThemeColors(),
    val animationStyle: AnimationStyle = AnimationStyle.SLIDE,
    val audioTransition: AudioTransition = AudioTransition.FADE_IN_OUT,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.NONE,
    val volume: Float = 1.0f,
    val autoCheckUpdates: Boolean = true,
    val lastDismissedVersion: String = "",
    val syncWithOsuDroid: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val loudnessNormalization: Boolean = false,
    val loudnessGainDb: Int = 3
)
