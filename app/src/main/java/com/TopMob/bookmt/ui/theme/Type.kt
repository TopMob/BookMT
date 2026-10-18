package com.TopMob.bookmt.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun uiText(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp,
)

val Typography = Typography(
    headlineLarge = uiText(28, 36), headlineMedium = uiText(24, 32), headlineSmall = uiText(22, 28),
    titleLarge = uiText(22, 28, FontWeight.Medium), titleMedium = uiText(18, 24, FontWeight.Medium),
    titleSmall = uiText(16, 24, FontWeight.Medium),
    bodyLarge = uiText(16, 24), bodyMedium = uiText(14, 20), bodySmall = uiText(12, 16),
    labelLarge = uiText(14, 20, FontWeight.Medium), labelMedium = uiText(14, 20, FontWeight.Medium), labelSmall = uiText(12, 16),
)
