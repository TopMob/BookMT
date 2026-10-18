package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.tts.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReadAloudSegmentsTest {
    @Test fun laterChaptersAndStorageBlocksKeepAbsoluteSourceRanges() = runTest {
        val first = "Первое предложение.\n"
        val second = "Второе\u00AD предложение!\nТретий абзац."
        val chapters = listOf(Chapter(0, "", first, 0),
            Chapter(1, "", second, first.length, sourceId = "chapter:0", sourceOffset = first.length))
        val content = BookContent("Book", null, chapters, TableOfContents.EMPTY)
        val engine = CaptureEngine()
        ReadAloudUseCase(engine).start(content, first.length)
        val text = first + second
        assertTrue(engine.segments.size >= 2)
        engine.segments.forEach {
            assertTrue(it.endOffset > it.startOffset)
            assertEquals(it.text, text.substring(it.startOffset, it.endOffset).replace("\u00AD", ""))
        }
        assertEquals(first.length, engine.segments.first().startOffset)
        assertEquals(text.length, engine.segments.last().endOffset)
    }

    @Test fun phrasesKeepOriginalOffsetsAndDistinctParagraphPauses() = runTest {
        val text = "Первое предложение. Второе предложение!\nСледующий абзац."
        val engine = CaptureEngine()
        val content = BookContent("Book", null, listOf(Chapter(0, "", text, 0)), TableOfContents.EMPTY)
        ReadAloudUseCase(engine).start(content, 0, 200, 600)
        assertEquals(listOf("Первое предложение.", "Второе предложение!", "Следующий абзац."), engine.segments.map { it.text })
        assertEquals(listOf(200, 600, 600), engine.segments.map { it.pauseAfterMs })
        engine.segments.forEach { assertEquals(it.text, text.substring(it.startOffset, it.endOffset)) }
    }
    @Test fun decimalsAndInitialsStayInTheSamePhrase() {
        val text = "А. С. Пушкин жил в г. Москве. Значение равно 3.14. Конец."
        assertEquals(text.indexOf(" Значение"), speechBoundary(text, 0))
        val start = text.indexOf("Значение")
        assertEquals(text.indexOf(" Конец"), speechBoundary(text, start))
    }
    private class CaptureEngine : TtsEngine {
        override val state = MutableStateFlow(TtsPlaybackState())
        val segments = mutableListOf<TextSegment>()
        override suspend fun prepare(assignment: VoiceAssignment, bookId: Long?) { }
        override suspend fun availableVoices() = emptyList<VoiceProfile>()
        override suspend fun speak(segments: List<TextSegment>) { this.segments += segments }
        override suspend fun speak(segments: Flow<TextSegment>) { segments.collect { this.segments += it } }
        override fun pause() { }; override fun resume() { }; override fun stop() { }; override fun release() { }
    }
}
