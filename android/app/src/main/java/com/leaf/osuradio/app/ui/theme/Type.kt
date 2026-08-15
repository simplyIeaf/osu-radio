package com.leaf.osuradio.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.leaf.osuradio.data.AppFont

fun AppFont.toFontFamily(assetManager: AssetManager): FontFamily {
    if (this == AppFont.DEFAULT) return FontFamily.Default
    val regular = Font("fonts/$regular", assetManager, FontWeight.Normal)
    val bold = bold?.let { Font("fonts/$it", assetManager, FontWeight.Bold) } ?: regular
    return FontFamily(regular, bold)
}

fun appTypography(fontFamily: FontFamily, fontScale: Float): Typography {
    fun scale(sp: TextUnit) = sp * fontScale
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = scale(28.sp),
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale(22.sp)
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = scale(18.sp)
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale(15.sp)
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = scale(15.sp)
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = scale(13.sp)
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = scale(11.sp),
            letterSpacing = 0.5.sp
        )
    )
}
