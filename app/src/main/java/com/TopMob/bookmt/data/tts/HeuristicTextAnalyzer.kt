package com.TopMob.bookmt.data.tts

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.domain.model.SpeakerRole
import com.TopMob.bookmt.domain.model.TextSegment
import com.TopMob.bookmt.domain.tts.TextAnalyzer
import kotlinx.coroutines.withContext

/**
 * Fast, fully on-device [TextAnalyzer]. It separates narration from dialogue by quote detection and
 * infers a speaker's gender from nearby attribution cues (speech-verb patterns, gendered pronouns
 * and honorifics). It is intentionally dependency-free so multi-voice playback works offline; the
 * interface lets it be swapped for an ML model later without touching the TTS engine.
 *
 * Guarantees: the concatenation of returned [TextSegment.text] exactly reconstructs the input, and
 * offsets are absolute (relative to [baseOffset]).
 */
class HeuristicTextAnalyzer(
    private val dispatchers: DispatcherProvider,
) : TextAnalyzer {

    override suspend fun analyze(text: String, baseOffset: Int): List<TextSegment> =
        withContext(dispatchers.default) {
            if (text.isEmpty()) return@withContext emptyList()

            val segments = mutableListOf<TextSegment>()
            var i = 0
            var narrationStart = 0
            val n = text.length

            fun flushNarration(end: Int) {
                if (end > narrationStart) {
                    segments += TextSegment(
                        text = text.substring(narrationStart, end),
                        role = SpeakerRole.NARRATOR,
                        startOffset = baseOffset + narrationStart,
                    )
                }
            }

            while (i < n) {
                if (text[i] in OPEN_QUOTES) {
                    flushNarration(i)
                    val close = findClosingQuote(text, i + 1)
                    val end = if (close == -1) n else close + 1
                    val dialogue = text.substring(i, end)
                    val (role, name) = inferSpeaker(text, dialogueStart = i, dialogueEnd = end)
                    segments += TextSegment(
                        text = dialogue,
                        role = role,
                        startOffset = baseOffset + i,
                        speakerName = name,
                    )
                    i = end
                    narrationStart = end
                } else {
                    i++
                }
            }
            flushNarration(n)
            segments
        }

    private fun findClosingQuote(text: String, from: Int): Int {
        for (k in from until text.length) {
            if (text[k] in CLOSE_QUOTES) return k
        }
        return -1
    }

    /**
     * Infers (role, name) by scanning a small window of narration around the dialogue for speech
     * attributions ("said Alice", "he whispered") and gender cues.
     */
    private fun inferSpeaker(text: String, dialogueStart: Int, dialogueEnd: Int): Pair<SpeakerRole, String?> {
        val before = text.substring((dialogueStart - WINDOW).coerceAtLeast(0), dialogueStart)
        val after = text.substring(dialogueEnd, (dialogueEnd + WINDOW).coerceAtMost(text.length))
        val context = (before + " " + after)

        // The regex captures the name in group 1 ("Name said") or group 2 ("said Name").
        val match = ATTRIBUTION.find(context)
        val name = match?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: match?.groupValues?.get(2)?.takeIf { it.isNotBlank() }

        val lower = context.lowercase()
        val role = when {
            FEMALE_CUES.any { it in lower } -> SpeakerRole.FEMALE_CHARACTER
            MALE_CUES.any { it in lower } -> SpeakerRole.MALE_CHARACTER
            else -> SpeakerRole.UNKNOWN_CHARACTER
        }
        return role to name?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val WINDOW = 48
        val OPEN_QUOTES = charArrayOf('"', '“', '«').toSet() // " “ «
        val CLOSE_QUOTES = charArrayOf('"', '”', '»').toSet() // " ” »

        // "<verb> <Name>" or "<Name> <verb>"
        val ATTRIBUTION = Regex("\\b([A-Z][a-z]+)\\b\\s+(?:said|asked|replied)|(?:said|asked|replied)\\s+\\b([A-Z][a-z]+)\\b")

        val FEMALE_CUES = setOf(" she ", " her ", " hers", " woman", " girl", " lady", " mrs", " miss", " mother", " queen")
        val MALE_CUES = setOf(" he ", " him ", " his ", " man", " boy", " mr ", " sir", " father", " king")
    }
}
