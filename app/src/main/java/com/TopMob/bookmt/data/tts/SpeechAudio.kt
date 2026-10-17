package com.TopMob.bookmt.data.tts

import kotlin.math.sqrt

/** Removes model-generated boundary silence; speech inside the phrase is never time-stretched. */
internal object SpeechAudio {
    fun trimAndPad(samples: FloatArray, rate: Int, pauseMs: Int): FloatArray {
        if (samples.isEmpty() || rate <= 0) return samples
        val window = (rate / 100).coerceAtLeast(1)
        val peak = samples.maxOf { kotlin.math.abs(it) }
        val threshold = (peak * .006f).coerceIn(.0005f, .003f)
        fun audible(start: Int): Boolean {
            var sum = 0.0
            val end = minOf(start + window, samples.size)
            for (i in start until end) sum += samples[i].toDouble() * samples[i]
            return sqrt(sum / (end - start).coerceAtLeast(1)) >= threshold
        }
        var from = 0
        while (from + window < samples.size && !audible(from)) from += window
        var to = samples.size
        while (to - window > from && !audible(to - window)) to -= window
        // Retain quiet onsets and endings instead of cutting exactly at the threshold.
        from = maxOf(0, from - rate * 40 / 1000)
        to = minOf(samples.size, to + rate * 70 / 1000)
        if (to <= from || peak < .0005f) {
            val silence = rate.toLong() * pauseMs.coerceIn(0, 3000) / 1000
            return FloatArray(samples.size + silence.toInt()).also { samples.copyInto(it, 0, 0, samples.size) }
        }
        val silence = rate.toLong() * pauseMs.coerceIn(0, 3000) / 1000
        return FloatArray(to - from + silence.toInt()).also { samples.copyInto(it, 0, from, to) }
    }
}
