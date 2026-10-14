package com.TopMob.bookmt.domain.usecase

import org.junit.Assert.*
import org.junit.Test

class SpeechBoundaryTest {
    @Test fun hugeUnbrokenInputIsBoundedAndSurrogatePairsStayTogether() {
        val text = "я".repeat(239) + "😀" + "б".repeat(10_000)
        var cursor = 0
        val restored = StringBuilder()
        while (cursor < text.length) {
            val end = speechBoundary(text, cursor)
            assertTrue(end > cursor && end - cursor <= 240)
            assertFalse(text[end - 1].isHighSurrogate())
            restored.append(text.substring(cursor, end))
            cursor = end
        }
        assertEquals(text, restored.toString())
    }

    @Test fun prefersSentenceBoundaryAndPreservesWhitespace() {
        val sentence = "Это достаточно длинное первое предложение, которое надо дочитать."
        val text = sentence + "  " + "следующее ".repeat(100)
        assertEquals(sentence.length, speechBoundary(text, 0))
        assertEquals(text.length, speechBoundary(text, text.length - 10))
    }
}
