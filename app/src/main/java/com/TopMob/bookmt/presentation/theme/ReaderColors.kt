package com.TopMob.bookmt.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.TopMob.bookmt.domain.model.ReaderTheme

/**
 * Concrete color palette for the reading surface, decoupled from Material's app chrome theme so the
 * page background/text can be Sepia or AMOLED-black independently of the system theme.
 */
@Immutable
data class ReaderColors(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val highlightDefault: Color,
    val controlScrim: Color,
) {
    companion object {
        val Day = ReaderColors(
            background = Color(0xFFFAF9F7),
            text = Color(0xFF1A1A1A),
            secondaryText = Color(0xFF6B6B6B),
            highlightDefault = Color(0x66FFEB3B),
            controlScrim = Color(0xCCFFFFFF),
        )

        val Sepia = ReaderColors(
            background = Color(0xFFF4ECD8),
            text = Color(0xFF5B4636),
            secondaryText = Color(0xFF8A7459),
            highlightDefault = Color(0x66D2B48C),
            controlScrim = Color(0xCCF4ECD8),
        )

        val Night = ReaderColors(
            background = Color(0xFF15171A),
            text = Color(0xFFCBD0D6),
            secondaryText = Color(0xFF8A9099),
            highlightDefault = Color(0x553F51B5),
            controlScrim = Color(0xCC15171A),
        )

        val AmoledBlack = ReaderColors(
            background = Color(0xFF000000),
            text = Color(0xFFB8BCC2),
            secondaryText = Color(0xFF6E747C),
            highlightDefault = Color(0x553F51B5),
            controlScrim = Color(0xCC000000),
        )

        /** Resolves a palette for the given [theme]; [custom] is used when theme is CUSTOM. */
        fun forTheme(theme: ReaderTheme, custom: ReaderColors? = null): ReaderColors = when (theme) {
            ReaderTheme.DAY -> Day
            ReaderTheme.SEPIA -> Sepia
            ReaderTheme.NIGHT -> Night
            ReaderTheme.AMOLED_BLACK -> AmoledBlack
            ReaderTheme.CUSTOM -> custom ?: Day
        }
    }
}
