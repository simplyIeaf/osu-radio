package com.osuradio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.osuradio.app.data.AppTheme
import com.osuradio.app.data.ThemeColors

fun getColorScheme(
    theme: AppTheme,
    colors: ThemeColors = ThemeColors()
) = darkColorScheme(
    primary = Color(colors.primary ?: OsuPink.toArgb().toLong()),
    onPrimary = Color(colors.onPrimary ?: White.toArgb().toLong()),
    secondary = Color(colors.secondary ?: OsuPinkLight.toArgb().toLong()),
    onSecondary = Color(colors.onSecondary ?: Black.toArgb().toLong()),
    background = Color(colors.background ?: DarkBackground.toArgb().toLong()),
    onBackground = Color(colors.onBackground ?: TextPrimary.toArgb().toLong()),
    surface = Color(colors.surface ?: DarkSurface.toArgb().toLong()),
    onSurface = Color(colors.onSurface ?: TextPrimary.toArgb().toLong()),
    surfaceVariant = Color(colors.surfaceVariant ?: DarkCard.toArgb().toLong()),
    onSurfaceVariant = Color(colors.onSurfaceVariant ?: TextSecondary.toArgb().toLong()),
    surfaceContainer = Color(colors.surfaceVariant ?: DarkCard.toArgb().toLong()),
    surfaceContainerLow = Color(colors.surfaceVariant ?: DarkCard.toArgb().toLong()),
    surfaceContainerHigh = Color(colors.surfaceVariant ?: DarkCard.toArgb().toLong()),
    surfaceContainerHighest = Color(colors.surfaceVariant ?: DarkCard.toArgb().toLong()),
    tertiary = Color(colors.tertiary ?: OsuPinkDark.toArgb().toLong()),
    error = Color(0xFFCF6679)
)

@Composable
fun OsuRadioTheme(
    theme: AppTheme = AppTheme.PINK,
    colors: ThemeColors = ThemeColors(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = getColorScheme(theme, colors),
        typography = OsuRadioTypography,
        content = content
    )
}
