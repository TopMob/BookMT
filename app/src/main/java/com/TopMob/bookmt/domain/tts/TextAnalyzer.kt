package com.TopMob.bookmt.domain.tts

import com.TopMob.bookmt.domain.model.TextSegment

/**
 * Splits a block of prose into [TextSegment]s annotated with the speaker role that should voice
 * each span (NARRATOR / MALE_CHARACTER / FEMALE_CHARACTER / ...).
 *
 * This is the "AI" seam of the multi-voice audiobook feature. The initial implementation is a
 * fast on-device heuristic (dialogue detection + gender inference from speech verbs/pronouns), but
 * the interface is deliberately implementation-agnostic so it can be swapped for an on-device LLM
 * or a remote model without touching the TTS engine that consumes the output.
 */
interface TextAnalyzer {
    /**
     * Analyzes [text] and returns role-tagged segments in reading order. The concatenation of all
     * returned [TextSegment.text] values equals [text], and offsets are relative to [baseOffset]
     * (the absolute position of [text] within the flattened book stream) so results can be mapped
     * back onto the book.
     */
    suspend fun analyze(text: String, baseOffset: Int = 0): List<TextSegment>
}
