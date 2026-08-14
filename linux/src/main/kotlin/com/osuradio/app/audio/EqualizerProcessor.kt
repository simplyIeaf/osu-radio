package com.osuradio.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 5-band peaking equalizer (RBJ cookbook biquads) applied live to interleaved
 * float PCM. Mirrors the Android equalizer's 5 bands / presets.
 */
class EqualizerProcessor(
    private val sampleRate: Float,
    private val channels: Int
) {
    var enabled: Boolean = false
        private set
    var bandLevels: List<Int> = List(5) { 0 }
        private set

    private val freqHz = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
    private var chain: Array<Array<PeakingFilter>> = buildChain()

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) reset()
    }

    fun setBandLevels(levels: List<Int>) {
        bandLevels = levels
        chain = buildChain()
    }

    private fun buildChain(): Array<Array<PeakingFilter>> =
        Array(channels) { ch ->
            Array(freqHz.size) { band ->
                PeakingFilter(freqHz[band], bandGainDb(band), sampleRate)
            }
        }

    private fun bandGainDb(band: Int): Float = bandLevels.getOrElse(band) { 0 } / 100f

    private fun reset() {
        chain.forEach { filters -> filters.forEach { it.reset() } }
    }

    /** Process [frameCount] frames of interleaved audio at [offset] within [samples]. */
    fun process(samples: FloatArray, offset: Int, frameCount: Int) {
        if (!enabled) return
        var i = offset
        for (frame in 0 until frameCount) {
            for (c in 0 until channels) {
                var v = samples[i + c]
                for (band in 0 until freqHz.size) {
                    v = chain[c][band].process(v)
                }
                samples[i + c] = v
            }
            i += channels
        }
    }

    /** Simple peaking EQ biquad (RBJ). */
    private class PeakingFilter(private val freq: Float, private val gainDb: Float, fs: Float) {
        private val q = 1.0
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        init {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freq / fs
            val alpha = sin(w0) / (2.0 * q)
            val cosw = cos(w0)
            val a0 = 1.0 + alpha / a
            b0 = (1.0 + alpha * a) / a0
            b1 = (-2.0 * cosw) / a0
            b2 = (1.0 - alpha * a) / a0
            a1 = (-2.0 * cosw) / a0
            a2 = (1.0 - alpha / a) / a0
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x.toDouble()
            y2 = y1; y1 = y
            return y.toFloat()
        }

        fun reset() {
            x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
        }
    }
}
