package com.TopMob.bookmt.data.tts

import com.TopMob.bookmt.domain.model.SpeakerRole
import com.TopMob.bookmt.util.TestDispatcherProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicTextAnalyzerTest {

    private val analyzer = HeuristicTextAnalyzer(TestDispatcherProvider())

    @Test
    fun `segments reconstruct the original text exactly`() = runTest {
        val text = "The room was quiet. \"Hello there,\" she said softly. Then silence fell."
        val segments = analyzer.analyze(text)
        assertEquals(text, segments.joinToString("") { it.text })
    }

    @Test
    fun `narration and dialogue are separated`() = runTest {
        val text = "He walked in. \"Get out!\" he shouted."
        val segments = analyzer.analyze(text)

        assertTrue(segments.any { it.role == SpeakerRole.NARRATOR })
        assertTrue(segments.any { it.role != SpeakerRole.NARRATOR })
    }

    @Test
    fun `female cue infers female character voice`() = runTest {
        val text = "\"I won't go,\" she whispered."
        val segments = analyzer.analyze(text)
        val dialogue = segments.first { it.text.contains("won't go") }
        assertEquals(SpeakerRole.FEMALE_CHARACTER, dialogue.role)
    }

    @Test
    fun `offsets are absolute to the base offset`() = runTest {
        val text = "abc \"hi\" xyz"
        val base = 100
        val segments = analyzer.analyze(text, baseOffset = base)
        assertEquals(base, segments.first().startOffset)
    }
}
