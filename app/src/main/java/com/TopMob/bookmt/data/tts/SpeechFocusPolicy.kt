package com.TopMob.bookmt.data.tts

import com.TopMob.bookmt.domain.tts.TtsStatus

/** A manual pause/stop or headset disconnect cancels automatic resumption. */
internal class SpeechFocusPolicy {
    private var resumeAfterInterruption = false

    @Synchronized fun transientLoss(status: TtsStatus): Boolean {
        if (status != TtsStatus.PLAYING && status != TtsStatus.PREPARING) return false
        resumeAfterInterruption = true
        return true
    }

    @Synchronized fun gain(): Boolean = resumeAfterInterruption.also { resumeAfterInterruption = false }
    @Synchronized fun cancelResume() { resumeAfterInterruption = false }
}
