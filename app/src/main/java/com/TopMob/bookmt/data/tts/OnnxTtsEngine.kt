package com.TopMob.bookmt.data.tts

import android.content.*
import android.media.*
import android.os.Build
import androidx.core.content.ContextCompat
import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.tts.*
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bundled neural synthesis. Never delegates to Android TTS or downloads models. */
class OnnxTtsEngine(private val context: Context, private val dispatchers: DispatcherProvider) : TtsEngine {
    private val mutableState = MutableStateFlow(TtsPlaybackState())
    override val state = mutableState.asStateFlow()
    private val nativeMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var runtime: OfflineTts? = null
    @Volatile private var runtimeGeneration = 0L
    private var assignment: VoiceAssignment? = null
    @Volatile private var stopped = true
    @Volatile private var paused = false
    private val audioLock = Any()
    private var track: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var resumeOnFocusGain = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (state.value.status == TtsStatus.PLAYING) {
                    resumeOnFocusGain = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resume()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause()
            }
        }
    }
    private var focusRequest: AudioFocusRequest? = null
    private var receiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { pause() }
    }

    override suspend fun availableVoices() = listOf(DefaultVoices.female, DefaultVoices.male)

    override suspend fun prepare(assignment: VoiceAssignment, bookId: Long?) = withContext(dispatchers.default) {
        if (state.value.status in setOf(TtsStatus.PLAYING, TtsStatus.PAUSED, TtsStatus.PREPARING)) stop()
        nativeMutex.withLock {
            runtimeGeneration++
            stopped = false
            paused = false
            this@OnnxTtsEngine.assignment = assignment
            mutableState.value = TtsPlaybackState(status = TtsStatus.PREPARING, activeVoiceId = assignment.narrator.id,
                bookId = bookId, sessionId = runtimeGeneration)
            try {
                ContextCompat.startForegroundService(context, Intent(context, TtsPlaybackService::class.java))
                withTimeout(10_000) { TtsPlaybackService.ready.first { it } }
                ensureRunning()
                if (runtime == null) runtime = createBundledRuntime(context)
                ensureRunning()
            } catch (e: Throwable) {
                runtime?.release()
                runtime = null
                fail(e)
                throw e
            }
        }
    }

    override suspend fun speak(segments: List<TextSegment>) = speak(segments.asFlow())

    override suspend fun speak(segments: Flow<TextSegment>) = withContext(dispatchers.default) {
        nativeMutex.withLock {
            try {
                ensureRunning()
                check(requestFocus()) { "Не удалось получить доступ к звуку. Повторите запуск озвучки." }
                ContextCompat.registerReceiver(context, noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
                receiverRegistered = true
                val tts = checkNotNull(runtime) { "Голоса ещё не подготовлены" }
                val voices = checkNotNull(assignment)
                // Synthesize one passage ahead while AudioTrack plays the current one.
                // A rendezvous buffer bounds memory and keeps all native calls on one producer.
                segments.filter { it.text.any(Char::isLetterOrDigit) }.map { segment ->
                    awaitUnpaused()
                    val voice = voices.voiceFor(segment.role)
                    val job = currentCoroutineContext()[Job]!!
                    val audio = tts.generateWithConfigAndCallback(segment.text,
                        GenerationConfig(sid = voice.speakerId, speed = voice.speed,
                            numSteps = 8, extra = mapOf("lang" to "ru"))) {
                        if (stopped || !job.isActive) 0 else 1
                    }
                    ensureRunning()
                    check(audio.samples.isNotEmpty()) { "Модель не смогла озвучить фрагмент" }
                    segment to audio
                }.buffer(2).flowOn(dispatchers.default).collect { (segment, audio) ->
                    awaitUnpaused()
                    mutableState.update { it.copy(activeVoiceId = voices.voiceFor(segment.role).id,
                        currentSegment = segment, currentOffset = segment.startOffset) }
                    play(SpeechAudio.trimAndPad(audio.samples, audio.sampleRate, segment.pauseAfterMs), audio.sampleRate)
                    ensureRunning()
                    mutableState.update { it.copy(status = if (paused) TtsStatus.PAUSED else TtsStatus.PREPARING) }
                }
                mutableState.update { it.copy(status = TtsStatus.IDLE, currentSegment = null) }
            } catch (e: Throwable) { fail(e); throw e }
            finally {
                synchronized(audioLock) { track?.release(); track = null }
                if (receiverRegistered) { context.unregisterReceiver(noisyReceiver); receiverRegistered = false }
                abandonFocus()
                runtime?.release()
                runtime = null
            }
        }
    }

    private fun getOrCreateTrack(sampleRate: Int): AudioTrack {
        synchronized(audioLock) {
            val existing = track
            if (existing != null && existing.sampleRate == sampleRate && existing.state == AudioTrack.STATE_INITIALIZED) {
                return existing
            }
            try { existing?.release() } catch (_: Exception) {}
            val bufferBytes = maxOf(
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
                sampleRate / 5 * 4
            )
            val newTrack = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()
            track = newTrack
            return newTrack
        }
    }

    private suspend fun play(samples: FloatArray, sampleRate: Int) {
        val output = getOrCreateTrack(sampleRate)
        check(output.state == AudioTrack.STATE_INITIALIZED) { "Не удалось открыть аудиовыход" }
        awaitUnpaused()
        synchronized(audioLock) {
            if (!paused && !stopped && output.playState != AudioTrack.PLAYSTATE_PLAYING) {
                output.play()
            }
        }
        mutableState.update { it.copy(status = if (paused) TtsStatus.PAUSED else TtsStatus.PLAYING) }
        val baseHeadPosition = output.playbackHeadPosition.toLong() and 0xffffffffL
        var written = 0
        while (written < samples.size) {
            awaitUnpaused()
            val count = synchronized(audioLock) {
                if (paused || stopped) 0 else output.write(samples, written,
                    minOf(4096, samples.size - written), AudioTrack.WRITE_NON_BLOCKING)
            }
            check(count >= 0) { "Ошибка воспроизведения: $count" }
            written += count
            if (count == 0) delay(10)
        }
        // Drain the final buffer so sentence endings are not clipped.
        val targetHead = baseHeadPosition + written
        while ((output.playbackHeadPosition.toLong() and 0xffffffffL) < targetHead) {
            awaitUnpaused()
            delay(15)
        }
        delay(20)
    }

    private suspend fun ensureRunning() {
        currentCoroutineContext().ensureActive()
        if (stopped) throw CancellationException("Read aloud stopped")
    }
    private suspend fun awaitUnpaused() {
        ensureRunning()
        while (paused) { delay(50); ensureRunning() }
    }
    override fun pause() {
        if (stopped || state.value.status !in setOf(TtsStatus.PLAYING, TtsStatus.PREPARING)) return
        paused = true
        synchronized(audioLock) { track?.pause() }
        mutableState.update { it.copy(status = TtsStatus.PAUSED) }
    }
    override fun resume() {
        if (stopped || !paused || !requestFocus()) return
        paused = false
        synchronized(audioLock) { track?.play() }
        mutableState.update { it.copy(status = if (track == null) TtsStatus.PREPARING else TtsStatus.PLAYING) }
    }
    override fun stop() {
        stopped = true
        paused = false
        resumeOnFocusGain = false
        synchronized(audioLock) {
            try {
                track?.pause()
                track?.flush()
                track?.release()
            } catch (_: Exception) {}
            track = null
        }
        mutableState.update { it.copy(status = TtsStatus.STOPPED, currentSegment = null) }
    }
    override fun release() {
        stop()
        val generation = runtimeGeneration
        cleanupScope.launch {
            nativeMutex.withLock {
                // A new reader may have started before asynchronous cleanup acquires the lock.
                if (generation == runtimeGeneration) { runtime?.release(); runtime = null }
            }
        }
    }
    private fun fail(error: Throwable) {
        stopped = true
        mutableState.update { it.copy(status = if (error is CancellationException) TtsStatus.STOPPED else TtsStatus.ERROR,
            errorMessage = if (error is CancellationException) null else error.message ?: "Ошибка офлайн-озвучки",
            currentSegment = null) }
    }
    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusListener, android.os.Handler(android.os.Looper.getMainLooper()))
                .build().also { focusRequest = it }
            audioManager.requestAudioFocus(request)
        } else audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let(audioManager::abandonAudioFocusRequest)
        else audioManager.abandonAudioFocus(focusListener)
    }
    companion object {
        /** Shared with the native smoke test to exercise the exact packaged configuration. */
        fun createBundledRuntime(context: Context): OfflineTts {
            val base = "tts/supertonic-3"
            return OfflineTts(context.assets, OfflineTtsConfig(model = OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = "$base/duration_predictor.int8.onnx", textEncoder = "$base/text_encoder.int8.onnx",
                    vectorEstimator = "$base/vector_estimator.int8.onnx", vocoder = "$base/vocoder.int8.onnx",
                    ttsJson = "$base/tts.json", unicodeIndexer = "$base/unicode_indexer.bin", voiceStyle = "$base/voice.bin"),
                numThreads = 2, provider = "cpu")))
        }
    }
}
