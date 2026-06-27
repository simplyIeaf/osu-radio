package com.osuradio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.osuradio.app.data.AppTheme

fun getColorScheme(theme: AppTheme) = when (theme) {
    AppTheme.PINK -> darkColorScheme(
        primary = OsuPink,
        onPrimary = White,
        secondary = OsuPinkLight,
        onSecondary = Black,
        background = DarkBackground,
        onBackground = TextPrimary,
        surface = DarkSurface,
        onSurface = TextPrimary,
        surfaceVariant = DarkCard,
        onSurfaceVariant = TextSecondary,
        tertiary = OsuPinkDark,
        error = Color(0xFFCF6679)
    )
}

@Composable
fun OsuRadioTheme(theme: AppTheme = AppTheme.PINK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = getColorScheme(theme),
        typography = OsuRadioTypography,
        content = content
    )
}
