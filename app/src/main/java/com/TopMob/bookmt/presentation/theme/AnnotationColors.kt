package com.TopMob.bookmt.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Display adaptation only: original annotation ARGB and source anchors remain unchanged. */
fun ComfortPalette.annotationColor(argb: Int?): Color = when (argb) {
    null, 0xFFFFD54F.toInt() -> markYellow
    0xFF81C784.toInt() -> markGreen
    0xFF64B5F6.toInt() -> markBlue
    0xFFF48FB1.toInt() -> markPink
    else -> {
        val original = Color(argb).compositeOver(readerBackground)
        (0..100).asSequence().map { lerp(original, readerBackground, it / 100f) }
            .firstOrNull { contrastRatio(readerText, it) >= 4.5f } ?: readerBackground
    }
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val x = a.luminance(); val y = b.luminance()
    return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
}
