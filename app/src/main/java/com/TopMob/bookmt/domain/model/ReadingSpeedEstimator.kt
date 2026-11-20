package com.TopMob.bookmt.domain.model

/**
 * Calculates reading speed (Words Per Minute) using a moving median of recent page turn intervals,
 * filtering out outliers (flipping pages fast < 1s, or being idle/away > 120s).
 *
 * Implements benchmark recommendations (Sections 5 & 8) to avoid frozen time-left estimates.
 */
class ReadingSpeedEstimator(
    private val defaultWpm: Int = DEFAULT_WPM,
    private val maxHistorySize: Int = 20,
) {
    companion object {
        const val DEFAULT_WPM = 220
        const val MIN_SAMPLE_DURATION_MS = 1_000L      // < 1s is fast skimming or accidental flip
        const val MAX_SAMPLE_DURATION_MS = 120_000L    // > 2m is pause / distraction / idle screen
        private const val AVG_CHARS_PER_WORD = 5.5
    }

    private val samples = ArrayDeque<ReadingSample>()

    data class ReadingSample(val wordCount: Int, val durationMs: Long)

    /**
     * Records reading activity on a page.
     * Returns true if the sample was valid and recorded; false if rejected as an outlier.
     */
    fun recordSample(wordCount: Int, durationMs: Long): Boolean {
        if (wordCount <= 0) return false
        if (durationMs < MIN_SAMPLE_DURATION_MS || durationMs > MAX_SAMPLE_DURATION_MS) {
            return false
        }
        if (samples.size >= maxHistorySize) {
            samples.removeFirst()
        }
        samples.addLast(ReadingSample(wordCount, durationMs))
        return true
    }

    /** Current effective Words Per Minute based on moving median. */
    fun calculateWpm(): Int {
        if (samples.isEmpty()) return defaultWpm
        val sampleWpms = samples.map { (it.wordCount * 60_000L / it.durationMs).toInt() }.sorted()
        return sampleWpms[sampleWpms.size / 2].coerceIn(50, 1000)
    }

    /**
     * Estimates minutes remaining based on words remaining and current WPM.
     */
    fun estimateMinutesRemaining(wordsRemaining: Int): Int {
        if (wordsRemaining <= 0) return 0
        val wpm = calculateWpm()
        return ((wordsRemaining.toDouble() / wpm).coerceAtLeast(1.0)).toInt()
    }

    /**
     * Estimates minutes remaining based on character count and current WPM.
     */
    fun estimateMinutesRemainingForChars(charsRemaining: Int): Int {
        if (charsRemaining <= 0) return 0
        val estimatedWords = (charsRemaining / AVG_CHARS_PER_WORD).toInt().coerceAtLeast(1)
        return estimateMinutesRemaining(estimatedWords)
    }

    fun clear() {
        samples.clear()
    }
}
