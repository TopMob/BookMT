package com.TopMob.bookmt.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadingSpeedEstimatorTest {

    private lateinit var estimator: ReadingSpeedEstimator

    @Before
    fun setUp() {
        estimator = ReadingSpeedEstimator()
    }

    @Test
    fun defaultWpm_isReturnedWhenNoSamples() {
        assertEquals(ReadingSpeedEstimator.DEFAULT_WPM, estimator.calculateWpm())
        assertEquals(1, estimator.estimateMinutesRemaining(220))
    }

    @Test
    fun outlierUnder1Second_isRejected() {
        // Fast skimming (500ms) should be rejected
        val accepted = estimator.recordSample(wordCount = 200, durationMs = 500L)
        assertFalse(accepted)
        assertEquals(ReadingSpeedEstimator.DEFAULT_WPM, estimator.calculateWpm())
    }

    @Test
    fun outlierOver2Minutes_isRejected() {
        // User walked away for 3 minutes (180_000ms) should be rejected
        val accepted = estimator.recordSample(wordCount = 200, durationMs = 180_000L)
        assertFalse(accepted)
        assertEquals(ReadingSpeedEstimator.DEFAULT_WPM, estimator.calculateWpm())
    }

    @Test
    fun validSamples_calculateMovingMedianCorrectly() {
        // 100 words in 30 seconds -> 200 WPM
        assertTrue(estimator.recordSample(wordCount = 100, durationMs = 30_000L))
        // 150 words in 30 seconds -> 300 WPM
        assertTrue(estimator.recordSample(wordCount = 150, durationMs = 30_000L))
        // 120 words in 30 seconds -> 240 WPM
        assertTrue(estimator.recordSample(wordCount = 120, durationMs = 30_000L))

        // Median of [200, 240, 300] is 240
        assertEquals(240, estimator.calculateWpm())

        // 480 words remaining at 240 WPM -> 2 minutes
        assertEquals(2, estimator.estimateMinutesRemaining(480))
    }

    @Test
    fun estimateMinutesRemainingForChars_calculatesReasonableTime() {
        // 2200 chars / 5.5 = 400 words. At default 220 WPM: ~1.81 mins -> 1 min
        val mins = estimator.estimateMinutesRemainingForChars(2200)
        assertTrue(mins in 1..2)
    }

    @Test
    fun estimateZeroOrNegative_returnsZero() {
        assertEquals(0, estimator.estimateMinutesRemaining(0))
        assertEquals(0, estimator.estimateMinutesRemaining(-100))
        assertEquals(0, estimator.estimateMinutesRemainingForChars(0))
    }
}
