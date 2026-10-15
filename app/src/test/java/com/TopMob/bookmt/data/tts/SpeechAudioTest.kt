package com.TopMob.bookmt.data.tts

import org.junit.Assert.*
import org.junit.Test

class SpeechAudioTest {
    @Test fun removesBoundarySilenceAndAddsRequestedPauseWithoutClippingSpeech() {
        val samples = FloatArray(1000) + FloatArray(500) { .2f } + FloatArray(2000)
        val output = SpeechAudio.trimAndPad(samples, 1000, 300)
        assertEquals(500, output.count { it == .2f })
        assertTrue(output.size < samples.size)
        assertTrue(output.takeLast(300).all { it == 0f })
        assertEquals(40 + 500 + 70 + 300, output.size)
    }
}
