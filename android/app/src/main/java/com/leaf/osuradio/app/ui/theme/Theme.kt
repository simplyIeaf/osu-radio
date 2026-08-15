package com.leaf.osuradio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.leaf.osuradio.data.AppFont
import com.leaf.osuradio.data.AppTheme
import com.leaf.osuradio.data.ThemeColors

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
    font: AppFont = AppFont.DEFAULT,
    fontSize: Float = 1f,
    content: @Composable () -> Unit
) {
    val assets = LocalContext.current.assets
    MaterialTheme(
        colorScheme = getColorScheme(theme, colors),
        typography = appTypography(font.toFontFamily(assets), fontSize),
        content = content
    )
}
