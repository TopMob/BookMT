package com.TopMob.bookmt.ui.theme

import com.TopMob.bookmt.domain.model.MotionMode
import org.junit.Assert.*
import org.junit.Test

class ComfortFoundationTest {
    @Test fun `system zero wins and reduced motion removes travel without double scaling`() {
        assertEquals(0, ComfortMotion(MotionMode.SYSTEM, systemEnabled = false).duration(160))
        assertFalse(ComfortMotion(MotionMode.SYSTEM, systemEnabled = false).spatial)
        assertEquals(80, ComfortMotion(MotionMode.MINIMAL).duration(160))
        assertEquals(60, ComfortMotion(MotionMode.MINIMAL).duration(60))
        assertFalse(ComfortMotion(MotionMode.MINIMAL).spatial)
        assertEquals(0, ComfortMotion(MotionMode.OFF).duration(200))
        assertFalse(ComfortMotion(MotionMode.OFF).spatial)
        assertEquals(160, ComfortMotion().duration(160))
    }

    @Test fun `old annotation colors remain readable in all new themes without modifying stored argb`() {
        val legacy = listOf(0xFFFFD54F.toInt(), 0xFF81C784.toInt(), 0xFF64B5F6.toInt(),
            0xFFF48FB1.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF282828.toInt())
        for (palette in listOf(ComfortPalettes.Day, ComfortPalettes.Sepia, ComfortPalettes.Night, ComfortPalettes.Black)) {
            for (stored in legacy) assertTrue(contrastRatio(palette.readerText, palette.annotationColor(stored)) >= 4.5f)
        }
    }
}
