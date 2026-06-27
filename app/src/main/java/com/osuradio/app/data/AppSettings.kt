package com.osuradio.app.data

enum class AppTheme {
    PINK
}

enum class AnimationStyle {
    SLIDE,
    FADE,
    SCALE,
    NONE
}

enum class AudioTransition {
    NONE,
    FADE_IN_OUT,
    CROSSFADE,
    SWOOSH
}

data class AppSettings(
    val theme: AppTheme = AppTheme.PINK,
    val animationStyle: AnimationStyle = AnimationStyle.SLIDE,
    val audioTransition: AudioTransition = AudioTransition.FADE_IN_OUT,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.NONE,
    val volume: Float = 1.0f
)

enum class RepeatMode {
    NONE,
    ONE,
    ALL
}
