package com.TopMob.bookmt.domain.model

/**
 * A span of book text tagged with the speaker role that should voice it. Produced by
 * [com.TopMob.bookmt.domain.tts.TextAnalyzer] and consumed by the TTS engine, which switches voice
 * models on the fly as the role changes between consecutive segments.
 *
 * @property text The exact text to synthesize.
 * @property role Who is "speaking" this span.
 * @property startOffset Absolute character offset of this span in the flattened book stream — lets
 *           the reader highlight the currently-spoken text and resume mid-book.
 * @property speakerName Optional resolved character name (e.g. "Alice"), used to map to a stable
 *           per-character voice when available.
 */
data class TextSegment(
    val text: String,
    val role: SpeakerRole,
    val startOffset: Int,
    val speakerName: String? = null,
) {
    val endOffset: Int get() = startOffset + text.length
}

/**
 * The role a piece of text is read in. The TTS layer maps each role (and optionally a resolved
 * [TextSegment.speakerName]) to a concrete [VoiceProfile].
 */
enum class SpeakerRole {
    NARRATOR,
    MALE_CHARACTER,
    FEMALE_CHARACTER,
    /** Dialogue whose speaker gender could not be inferred. */
    UNKNOWN_CHARACTER,
}
