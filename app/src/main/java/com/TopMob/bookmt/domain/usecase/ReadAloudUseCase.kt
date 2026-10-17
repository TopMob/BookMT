package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.tts.TtsEngine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Reads lazily from the requested block; phrase offsets always refer to unmodified source text. */
class ReadAloudUseCase @Inject constructor(private val engine: TtsEngine) {
    val state get() = engine.state
    suspend fun prepare(assignment: VoiceAssignment, bookId: Long? = null) = engine.prepare(assignment, bookId)
    suspend fun start(content: BookContent, fromOffset: Int, sentencePauseMs: Int = 320, paragraphPauseMs: Int = 650) = engine.speak(flow {
        val first = content.chapterIndexAt(fromOffset).coerceAtLeast(0)
        for (index in first until content.chapters.size) {
            val chapter = content.chapters[index]
            if (chapter.auxiliary && content.descriptor(first)?.auxiliary != true) continue
            val text = chapter.text
            var cursor = (fromOffset - chapter.startOffset).coerceIn(0, text.length)
            while (cursor < text.length) {
                while (cursor < text.length && (text[cursor].isWhitespace() || text[cursor] == '\uFFFC')) cursor++
                if (cursor == text.length) break
                val end = speechBoundary(text, cursor)
                val raw = text.substring(cursor, end)
                val batch = raw.trimEnd().replace('\uFFFC', ' ').replace("\u00AD", "")
                var next = end
                while (next < text.length && text[next].isWhitespace()) next++
                val contentEnd = cursor + raw.trimEnd().length
                val hasNewline = '\n' in text.substring(contentEnd, next)
                val stripped = raw.trimEnd().trimEnd('"', '\'', '»', '«', '”', '“', '’', '‘', '›', '‹', ')', ']', '}')
                val sentence = stripped.lastOrNull() in setOf('.', '!', '?', '…')
                val clause = stripped.lastOrNull() in setOf(':', ';', '—', '–', ',')
                val paragraph = (hasNewline && (sentence || clause || next >= text.length ||
                    text.substring(contentEnd, next).count { it == '\n' } >= 2 ||
                    (next < text.length && (text[next] == '—' || text[next] == '–' || text[next].isUpperCase())))) ||
                    (end == text.length && (index == content.chapters.lastIndex || content.descriptor(index + 1)?.sourceId != chapter.sourceId))
                val pause = when {
                    paragraph -> paragraphPauseMs
                    sentence -> sentencePauseMs
                    clause -> (sentencePauseMs * 0.65f).toInt().coerceAtLeast(140)
                    else -> 80
                }
                if (batch.any(Char::isLetterOrDigit)) emit(TextSegment(batch, SpeakerRole.NARRATOR,
                    chapter.startOffset + cursor, sourceEndOffset = contentEnd,
                    pauseAfterMs = pause))
                cursor = next
            }
        }
    })
    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun stop() = engine.stop()
    fun release() = engine.release()
}

/** Split on the first real phrase end, excluding decimals, initials and common Russian abbreviations. */
internal fun speechBoundary(text: String, start: Int, limit: Int = 240): Int {
    val hardEnd = minOf(start + limit, text.length)
    var i = start
    while (i < hardEnd) {
        val c = text[i]
        if (c == '\n' && i > start) return i + 1
        if (c in ".!?…") {
            var end = i + 1
            while (end < text.length && text[end] in ".!?…\"'»«”\"’‘›‹)]}") end++
            val boundary = end == text.length || text[end].isWhitespace()
            val word = text.substring(start, i).takeLastWhile { it.isLetter() }.lowercase()
            val abbreviation = c == '.' && ((word.length == 1 && word.firstOrNull()?.isLetter() == true) || word in ABBREVIATIONS)
            if (boundary && !abbreviation && end <= hardEnd) return end
        }
        i++
    }
    if (hardEnd == text.length) return hardEnd
    var end = hardEnd
    val space = (end - 1 downTo start + 1).firstOrNull { text[it].isWhitespace() }
    if (space != null) return space + 1
    if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
    return end
}
private val ABBREVIATIONS = setOf("г", "ул", "д", "стр", "рис", "им", "т", "п", "др", "пр", "руб", "коп", "мин", "сек", "проф", "доц", "акад", "тов", "см", "тел", "fig", "mr", "mrs", "dr", "etc")
