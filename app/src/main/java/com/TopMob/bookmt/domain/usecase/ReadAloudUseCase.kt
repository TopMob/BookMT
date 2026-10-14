package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.TextSegment
import com.TopMob.bookmt.domain.model.VoiceAssignment
import com.TopMob.bookmt.domain.model.SpeakerRole
import com.TopMob.bookmt.domain.tts.TtsEngine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Lazily feeds bounded passages: never copies or analyzes the entire book before speaking. */
class ReadAloudUseCase @Inject constructor(private val engine: TtsEngine) {
    val state get() = engine.state
    suspend fun prepare(assignment: VoiceAssignment) = engine.prepare(assignment)
    suspend fun start(content: BookContent, fromOffset: Int) = engine.speak(flow {
        for (chapter in content.chapters) {
            var cursor = (fromOffset - chapter.startOffset).coerceIn(0, chapter.text.length)
            while (cursor < chapter.text.length) {
                val end = speechBoundary(chapter.text, cursor)
                val batch = chapter.text.substring(cursor, end)
                // Keep dialogue and attribution together for natural prosody. Automatic role
                // assignment is deliberately deferred until character analysis is implemented.
                emit(TextSegment(batch, SpeakerRole.NARRATOR, chapter.startOffset + cursor))
                cursor = end
            }
        }
    })
    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun stop() = engine.stop()
    fun release() = engine.release()
}

/** Bounded even for malformed books without whitespace; preserves original character offsets. */
internal fun speechBoundary(text: String, start: Int, limit: Int = 240): Int {
    var end = minOf(start + limit, text.length)
    if (end == text.length) return end
    val sentence = (end - 1 downTo start + 40).firstOrNull {
        text[it] in ".!?…\n" && (it + 1 == text.length || text[it + 1].isWhitespace())
    }
    if (sentence != null) return sentence + 1
    val space = (end - 1 downTo start + 1).firstOrNull { text[it].isWhitespace() }
    if (space != null) return space + 1
    if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
    return end
}
