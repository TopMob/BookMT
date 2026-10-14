package com.TopMob.bookmt.data.tts

import com.TopMob.bookmt.domain.model.*

object DefaultVoices {
    // Upstream generate_voices_bin.py sorts F1..F5,M1..M5: F1=0, M1=5.
    val female = VoiceProfile("supertonic-f1", "Женский · F1", "tts/supertonic-3",
        "tts/supertonic-3/unicode_indexer.bin", sampleRate = 44100, speakerId = 0)
    val male = female.copy(id = "supertonic-m1", displayName = "Мужской · M1", speakerId = 5)
    val narrator = female
    val assignment = VoiceAssignment(narrator)
    fun forSettings(settings: ReaderSettings) = VoiceAssignment(
        (if (settings.ttsMaleVoice) male else female).copy(speed = settings.sanitized().ttsSpeed))
    fun name(id: String) = listOf(female, male).firstOrNull { it.id == id }?.displayName ?: id
}
