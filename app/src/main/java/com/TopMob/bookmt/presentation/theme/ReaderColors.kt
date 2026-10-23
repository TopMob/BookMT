package com.TopMob.bookmt.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.TopMob.bookmt.domain.model.ReaderTheme

@Immutable
data class ReaderColors(
    val background: Color, val text: Color, val secondaryText: Color,
    val highlightDefault: Color, val controlScrim: Color, val ttsHighlight: Color,
) {
    companion object {
        private fun from(p: ComfortPalette) = ReaderColors(p.readerBackground, p.readerText,
            p.secondaryText, p.markYellow, p.surface, p.tts)
        val Day = from(ComfortPalettes.Day)
        val Sepia = from(ComfortPalettes.Sepia)
        val Night = from(ComfortPalettes.Night)
        val AmoledBlack = from(ComfortPalettes.Black)
        fun forTheme(theme: ReaderTheme, custom: ReaderColors? = null): ReaderColors = when (theme) {
            ReaderTheme.DAY -> Day
            ReaderTheme.SEPIA -> Sepia
            ReaderTheme.NIGHT -> Night
            ReaderTheme.AMOLED_BLACK -> AmoledBlack
            ReaderTheme.CUSTOM -> custom ?: Day
        }
    }
}
