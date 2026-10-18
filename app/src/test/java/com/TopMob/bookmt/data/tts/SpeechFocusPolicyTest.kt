package com.TopMob.bookmt.data.tts

import com.TopMob.bookmt.domain.tts.TtsStatus
import org.junit.Assert.*
import org.junit.Test

class SpeechFocusPolicyTest {
    @Test fun preparingAndPlayingSessionsResumeAfterTransientLoss() {
        for (status in listOf(TtsStatus.PREPARING, TtsStatus.PLAYING)) {
            val policy = SpeechFocusPolicy()
            assertTrue(policy.transientLoss(status))
            assertTrue(policy.gain())
            assertFalse(policy.gain())
        }
    }

    @Test fun repeatedLossWhilePausedKeepsTheOriginalResumeRequest() {
        val policy = SpeechFocusPolicy()
        assertTrue(policy.transientLoss(TtsStatus.PREPARING))
        assertFalse(policy.transientLoss(TtsStatus.PAUSED))
        assertTrue(policy.gain())
    }

    @Test fun manualPauseHeadsetDisconnectOrStopPreventsAutomaticResume() {
        val policy = SpeechFocusPolicy()
        policy.transientLoss(TtsStatus.PLAYING)
        policy.cancelResume()
        assertFalse(policy.gain())
        assertFalse(policy.transientLoss(TtsStatus.PAUSED))
        assertFalse(policy.gain())
    }
}
