package com.TopMob.bookmt.data.tts

import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.domain.model.SpeakerRole
import com.TopMob.bookmt.domain.model.VoiceAssignment
import com.TopMob.bookmt.domain.model.VoiceProfile

/**
 * Default multi-voice mapping used until the user customizes voices in settings. Paths point at
 * Piper `.onnx` models under `assets/voices/`; ship those models (or let the user import them) and the
 * [OnnxTtsEngine] synthesis seam will load them. Until then the engine drives playback state with
 * these as logical voices.
 */
object DefaultVoices {

    private fun voice(id: String, name: String, role: SpeakerRole) = VoiceProfile(
        id = id,
        displayName = name,
        modelAssetPath = "${Constants.VOICE_MODELS_DIR}/$id.onnx",
        tokensAssetPath = "${Constants.VOICE_MODELS_DIR}/$id.tokens.txt",
        suitableRoles = setOf(role),
    )

    val narrator = voice("en_US-narrator-medium", "Narrator", SpeakerRole.NARRATOR)
    val male = voice("en_US-male-medium", "Male character", SpeakerRole.MALE_CHARACTER)
    val female = voice("en_US-female-medium", "Female character", SpeakerRole.FEMALE_CHARACTER)

    val assignment = VoiceAssignment(
        narrator = narrator,
        maleCharacter = male,
        femaleCharacter = female,
        unknownCharacter = narrator,
    )
}
