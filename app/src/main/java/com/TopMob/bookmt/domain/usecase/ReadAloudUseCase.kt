package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.TextSegment
import com.TopMob.bookmt.domain.model.VoiceAssignment
import com.TopMob.bookmt.domain.tts.TextAnalyzer
import com.TopMob.bookmt.domain.tts.TtsEngine
import com.TopMob.bookmt.domain.tts.TtsPlaybackState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Orchestrates the multi-voice "read aloud" feature: takes the book content from a starting offset,
 * runs it through the [TextAnalyzer] to tag speaker roles, then hands the role-tagged stream to the
 * [TtsEngine], which switches voices on the fly.
 *
 * This is the single domain entry point the reader VM uses; it keeps the analyzer→engine wiring out
 * of the presentation layer.
 */
class ReadAloudUseCase @Inject constructor(
    private val analyzer: TextAnalyzer,
    private val engine: TtsEngine,
) {
    val state: StateFlow<TtsPlaybackState> get() = engine.state

    /** Loads/warms the voices that will be used before playback begins. */
    suspend fun prepare(assignment: VoiceAssignment) = engine.prepare(assignment)

    /** Analyzes [content] starting at [fromOffset] and speaks it with role-appropriate voices. */
    suspend fun start(content: BookContent, fromOffset: Int) {
        val segments = buildSegments(content, fromOffset)
        engine.speak(segments)
    }

    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun stop() = engine.stop()

    /**
     * Flattens the book from [fromOffset] onward and analyzes it in bounded batches so synthesis can
     * begin without waiting for the entire (possibly huge) book to be analyzed. Batches are aligned
     * to sentence boundaries to avoid cutting dialogue mid-utterance.
     */
    private suspend fun buildSegments(content: BookContent, fromOffset: Int): List<TextSegment> {
        val full = buildString { content.chapters.forEach { append(it.text) } }
        if (fromOffset >= full.length) return emptyList()

        val segments = mutableListOf<TextSegment>()
        var cursor = fromOffset.coerceIn(0, full.length)
        while (cursor < full.length) {
            val end = nextBatchBoundary(full, cursor)
            val batch = full.substring(cursor, end)
            segments += analyzer.analyze(batch, baseOffset = cursor)
            cursor = end
        }
        return segments
    }

    private fun nextBatchBoundary(text: String, start: Int): Int {
        val hardEnd = (start + Constants.ANALYZER_BATCH_CHAR_BUDGET).coerceAtMost(text.length)
        if (hardEnd >= text.length) return text.length
        // Extend to the next sentence terminator so a batch never splits a sentence.
        val sentenceEnd = text.indexOfAny(charArrayOf('.', '!', '?', '\n'), startIndex = hardEnd)
        return if (sentenceEnd == -1) text.length else (sentenceEnd + 1).coerceAtMost(text.length)
    }
}
