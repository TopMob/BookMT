package com.TopMob.bookmt.domain.tts

import com.TopMob.bookmt.domain.model.TextSegment
import com.TopMob.bookmt.domain.model.VoiceAssignment
import com.TopMob.bookmt.domain.model.VoiceProfile
import kotlinx.coroutines.flow.StateFlow

/**
 * Engine abstraction over an ONNX-backed neural TTS runtime (e.g. sherpa-onnx running Piper `.onnx`
 * voices). Responsible only for *synthesis + playback*; deciding what to say and in which voice is
 * the job of [TextAnalyzer] + [VoiceAssignment] upstream.
 *
 * The engine consumes a pre-segmented stream and dynamically switches the active voice model
 * whenever the role of the next [TextSegment] differs from the current one — this is what produces
 * the "narrator → character" voice changes mid-playback.
 */
interface TtsEngine {

    /** Hot state of the engine for the UI to observe. */
    val state: StateFlow<TtsPlaybackState>

    /** Warms up the runtime and pre-loads the [VoiceProfile]s referenced by [assignment]. */
    suspend fun prepare(assignment: VoiceAssignment)

    /** Lists voices the engine has discovered/loaded (bundled assets + user-imported models). */
    suspend fun availableVoices(): List<VoiceProfile>

    /**
     * Plays the given role-tagged [segments] sequentially, switching voice models as roles change.
     * Suspends until playback finishes, is stopped, or an error occurs. Progress and the currently
     * spoken segment are published via [state].
     */
    suspend fun speak(segments: List<TextSegment>)

    fun pause()
    fun resume()
    fun stop()

    /** Releases native ONNX sessions and audio resources. */
    fun release()
}

/** Observable playback state surfaced to the reader UI. */
data class TtsPlaybackState(
    val status: TtsStatus = TtsStatus.IDLE,
    val activeVoiceId: String? = null,
    /** Absolute character offset currently being spoken, for synchronized text highlighting. */
    val currentOffset: Int = 0,
    val currentSegment: TextSegment? = null,
    val errorMessage: String? = null,
)

enum class TtsStatus { IDLE, PREPARING, PLAYING, PAUSED, STOPPED, ERROR }
