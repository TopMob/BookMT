package com.TopMob.bookmt.domain.model

/**
 * Describes a single ONNX/Piper voice model and how it should be rendered. The TTS engine keeps a
 * pool of these and selects one per [TextSegment] based on its [SpeakerRole].
 *
 * @property id Stable identifier (e.g. "en_US-amy-medium").
 * @property displayName Human-readable name for settings UI.
 * @property modelAssetPath Path to the `.onnx` acoustic model (asset- or file-relative).
 * @property tokensAssetPath Path to the model's `tokens.txt` / phoneme table.
 * @property sampleRate Output sample rate in Hz the model was trained at.
 * @property speakerId Multi-speaker model speaker index; 0 for single-speaker models.
 * @property suitableRoles Roles this voice is appropriate for, used for automatic assignment.
 */
data class VoiceProfile(
    val id: String,
    val displayName: String,
    val modelAssetPath: String,
    val tokensAssetPath: String,
    val sampleRate: Int = 22_050,
    val speakerId: Int = 0,
    val speed: Float = 1.0f,
    val suitableRoles: Set<SpeakerRole> = emptySet(),
)

/**
 * The user/engine mapping from a [SpeakerRole] to the [VoiceProfile] that should voice it. A null
 * value means "fall back to the narrator voice".
 */
data class VoiceAssignment(
    val narrator: VoiceProfile,
    val maleCharacter: VoiceProfile? = null,
    val femaleCharacter: VoiceProfile? = null,
    val unknownCharacter: VoiceProfile? = null,
) {
    fun voiceFor(role: SpeakerRole): VoiceProfile = when (role) {
        SpeakerRole.NARRATOR -> narrator
        SpeakerRole.MALE_CHARACTER -> maleCharacter ?: narrator
        SpeakerRole.FEMALE_CHARACTER -> femaleCharacter ?: narrator
        SpeakerRole.UNKNOWN_CHARACTER -> unknownCharacter ?: narrator
    }
}
