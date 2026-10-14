package com.TopMob.bookmt

import android.content.Context
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.TopMob.bookmt.data.tts.DefaultVoices
import com.TopMob.bookmt.data.tts.OnnxTtsEngine
import com.TopMob.bookmt.domain.model.SpeakerRole
import com.TopMob.bookmt.domain.model.TextSegment
import com.TopMob.bookmt.domain.tts.TtsEngine
import com.TopMob.bookmt.domain.tts.TtsStatus
import com.k2fsa.sherpa.onnx.GenerationConfig
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real JNI + packaged models: no mock synthesis, network, or Android system voice. */
class OfflineTtsTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bothBundledRussianVoicesProduceAudio() {
        val runtime = OnnxTtsEngine.createBundledRuntime(context)
        try {
            assertEquals(44100, runtime.sampleRate())
            assertTrue(runtime.numSpeakers() >= 6)
            for (voice in listOf(DefaultVoices.female, DefaultVoices.male)) {
                val start = System.nanoTime()
                val audio = runtime.generateWithConfig("Добрый вечер. За окном тихо шелестели деревья, и начиналась новая история.",
                    GenerationConfig(sid = voice.speakerId, numSteps = 8, extra = mapOf("lang" to "ru")))
                assertEquals(44100, audio.sampleRate)
                assertTrue(audio.samples.size > audio.sampleRate)
                assertTrue(audio.samples.all { it.isFinite() })
                assertTrue(audio.samples.any { kotlin.math.abs(it) > 0.01f })
                assertTrue(audio.save(File(context.cacheDir, "${voice.id}.wav").absolutePath))
                Log.i("OfflineTtsTest", "${voice.id}: ${audio.samples.size / audio.sampleRate.toFloat()}s audio in ${(System.nanoTime() - start) / 1e9}s")
            }
        } finally { runtime.release() }
    }

    @Test fun foregroundPlaybackPausesResumesAndStops() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val engine = EntryPointAccessors.fromApplication(context, DebugTtsEntryPoint::class.java).ttsEngine()
            runBlocking {
                try {
                    engine.prepare(DefaultVoices.assignment)
                    val playback = launch(Dispatchers.Default) {
                        engine.speak(listOf(TextSegment("За окном тихо шелестели деревья, и начиналась новая история.", SpeakerRole.NARRATOR, 137)))
                    }
                    withTimeout(120_000) { engine.state.first { it.status == TtsStatus.PLAYING } }
                    assertEquals(137, engine.state.value.currentOffset)
                    activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    delay(250)
                    assertEquals(TtsStatus.PLAYING, engine.state.value.status)
                    engine.pause()
                    assertEquals(TtsStatus.PAUSED, engine.state.value.status)
                    delay(250)
                    assertEquals(TtsStatus.PAUSED, engine.state.value.status)
                    engine.resume()
                    assertEquals(TtsStatus.PLAYING, engine.state.value.status)
                    engine.stop()
                    withTimeout(10_000) { playback.join() }
                    assertEquals(TtsStatus.STOPPED, engine.state.value.status)
                } finally { engine.release() }
            }
        }
    }
}
