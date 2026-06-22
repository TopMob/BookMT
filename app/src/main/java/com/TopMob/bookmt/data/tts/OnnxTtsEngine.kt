package com.TopMob.bookmt.data.tts

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.domain.model.TextSegment
import com.TopMob.bookmt.domain.model.VoiceAssignment
import com.TopMob.bookmt.domain.model.VoiceProfile
import com.TopMob.bookmt.domain.tts.TtsEngine
import com.TopMob.bookmt.domain.tts.TtsPlaybackState
import com.TopMob.bookmt.domain.tts.TtsStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * ONNX-runtime-backed [TtsEngine] (designed for sherpa-onnx / Piper `.onnx` voices).
 *
 * This baseline wires up the full control flow — voice-pool management, on-the-fly voice switching
 * as [TextSegment.role] changes, playback state, pause/resume/stop — with the heavy native pieces
 * stubbed at clearly marked seams ([synthesize], [playPcm], voice-session loading). Dropping in
 * sherpa-onnx means filling those three seams; nothing upstream changes.
 *
 * Integration notes for the synthesis seam:
 *  - Create one `ai.onnxruntime.OrtEnvironment` for the process.
 *  - Lazily build an `OrtSession` per [VoiceProfile.modelAssetPath]; cache in [voiceSessions].
 *  - For each segment: phonemize text -> token ids (tokens.txt), run the session, receive a float
 *    PCM array, and stream it to an `AudioTrack` configured at [VoiceProfile.sampleRate].
 */
class OnnxTtsEngine(
    private val dispatchers: DispatcherProvider,
) : TtsEngine {

    private val _state = MutableStateFlow(TtsPlaybackState())
    override val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    private var assignment: VoiceAssignment? = null

    /** Cache of loaded native voice sessions keyed by [VoiceProfile.id] (opaque handles for now). */
    private val voiceSessions = mutableMapOf<String, Any>()

    // Cooperative playback control flags, checked between/within segments.
    @Volatile private var paused = false
    @Volatile private var stopped = false

    override suspend fun prepare(assignment: VoiceAssignment) {
        _state.value = TtsPlaybackState(status = TtsStatus.PREPARING)
        this.assignment = assignment
        // Pre-load the distinct voices referenced by the assignment so the first switch is instant.
        listOfNotNull(
            assignment.narrator,
            assignment.maleCharacter,
            assignment.femaleCharacter,
            assignment.unknownCharacter,
        ).distinctBy { it.id }.forEach { loadVoice(it) }
        _state.value = TtsPlaybackState(status = TtsStatus.IDLE)
    }

    override suspend fun availableVoices(): List<VoiceProfile> =
        assignment?.let {
            listOfNotNull(it.narrator, it.maleCharacter, it.femaleCharacter, it.unknownCharacter)
                .distinctBy { v -> v.id }
        } ?: emptyList()

    override suspend fun speak(segments: List<TextSegment>) {
        val assignment = this.assignment ?: run {
            _state.value = TtsPlaybackState(status = TtsStatus.ERROR, errorMessage = "Engine not prepared")
            return
        }
        stopped = false
        paused = false

        withContext(dispatchers.default) {
            var activeVoiceId: String? = null
            for (segment in segments) {
                if (stopped) break
                awaitWhilePaused()
                if (stopped) break

                // ---- On-the-fly voice switching ----
                val voice = assignment.voiceFor(segment.role)
                if (voice.id != activeVoiceId) {
                    loadVoice(voice)
                    activeVoiceId = voice.id
                }

                _state.value = _state.value.copy(
                    status = TtsStatus.PLAYING,
                    activeVoiceId = voice.id,
                    currentSegment = segment,
                    currentOffset = segment.startOffset,
                    errorMessage = null,
                )

                val pcm = synthesize(segment, voice)
                playPcm(pcm, voice.sampleRate)
            }

            if (!stopped) {
                _state.value = _state.value.copy(status = TtsStatus.IDLE, currentSegment = null)
            }
        }
    }

    override fun pause() {
        paused = true
        _state.value = _state.value.copy(status = TtsStatus.PAUSED)
    }

    override fun resume() {
        paused = false
        _state.value = _state.value.copy(status = TtsStatus.PLAYING)
    }

    override fun stop() {
        stopped = true
        paused = false
        _state.value = _state.value.copy(status = TtsStatus.STOPPED, currentSegment = null)
    }

    override fun release() {
        stop()
        voiceSessions.clear()
        // TODO(onnx): close OrtSession handles and the shared OrtEnvironment here.
    }

    private suspend fun awaitWhilePaused() {
        while (paused && !stopped) delay(POLL_INTERVAL_MS)
    }

    /**
     * Loads (or returns cached) native session for [voice].
     *
     * SEAM: replace the placeholder handle with an actual `OrtSession` created from the model bytes.
     */
    private fun loadVoice(voice: VoiceProfile) {
        voiceSessions.getOrPut(voice.id) { VoiceSessionHandle(voice.id) }
    }

    /**
     * SEAM: run ONNX inference for [segment] using [voice]'s session and return PCM samples.
     * The stub returns an empty buffer and reports timing so the rest of the pipeline is exercised.
     */
    private fun synthesize(segment: TextSegment, voice: VoiceProfile): FloatArray {
        // Real impl: tokenize -> OrtSession.run(...) -> read output tensor -> FloatArray PCM.
        return FloatArray(0)
    }

    /**
     * SEAM: write PCM to an `AudioTrack`. The stub approximates real playback duration from the
     * text length so playback state, highlighting, and pause/stop can be observed end-to-end.
     */
    private suspend fun playPcm(pcm: FloatArray, sampleRate: Int) {
        val approxMs = (synthesizedDurationMs())
        var elapsed = 0L
        while (elapsed < approxMs && !stopped) {
            awaitWhilePaused()
            delay(POLL_INTERVAL_MS)
            elapsed += POLL_INTERVAL_MS
        }
    }

    private fun synthesizedDurationMs(): Long {
        val segment = _state.value.currentSegment ?: return 0L
        // ~15 characters per second of speech as a rough placeholder pacing.
        return (segment.text.length * 1000L / CHARS_PER_SECOND).coerceAtLeast(POLL_INTERVAL_MS)
    }

    /** Opaque placeholder standing in for a native `OrtSession`. */
    private data class VoiceSessionHandle(val voiceId: String)

    private companion object {
        const val POLL_INTERVAL_MS = 50L
        const val CHARS_PER_SECOND = 15
    }
}
