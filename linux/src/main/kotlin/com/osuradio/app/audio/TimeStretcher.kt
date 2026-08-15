package com.osuradio.app.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pitch-preserving time-stretcher based on WSOLA
 * (Waveform Similarity Overlap-Add). Used so mods like Double Time, Half Time
 * and Bass Boost can change tempo without the pitch changing — the same
 * behaviour ExoPlayer provides on Android via its sonic time-stretcher.
 */
object TimeStretcher {

    /**
     * Stretches [samples] (interleaved [channels]) so its tempo is multiplied
     * by [tempo] (>1 = faster/shorter, <1 = slower/longer). Pitch is unchanged.
     */
    fun stretch(samples: FloatArray, channels: Int, sampleRate: Float, tempo: Float): FloatArray {
        if (abs(tempo - 1f) < 0.001f) return samples
        val t = tempo.coerceIn(0.25f, 3.5f)
        val framesIn = samples.size / channels
        if (framesIn < 1024) return samples

        val window = (0.06f * sampleRate).toInt().coerceAtLeast(128)          // 60ms analysis window
        val synthHop = (window * 0.75f).toInt().coerceAtLeast(16)             // synthesis hop
        val analysisHop = (synthHop / t).toInt().coerceAtLeast(16)            // input hop
        val overlap = window - synthHop
        if (overlap <= 0) return samples

        val searchRange = (analysisHop * 0.5).toInt().coerceAtLeast(4)
        val probeStride = max(1, searchRange / 16)
        val corrStride = max(1, overlap / 16)
        val framesOut = ((framesIn - window).toDouble() / analysisHop * synthHop).toInt() + window
        val out = FloatArray(framesOut * channels)

        // Mono down-mix used only to pick the most similar overlap position.
        val mono = FloatArray(framesIn) { i ->
            val j = i * channels
            var s = 0f
            for (c in 0 until channels) s += samples[j + c]
            s / channels
        }
        val outMono = FloatArray(framesOut)

        // First window copied verbatim.
        System.arraycopy(samples, 0, out, 0, window * channels)
        for (i in 0 until window) {
            var s = 0f
            for (c in 0 until channels) s += out[i * channels + c]
            outMono[i] = s / channels
        }

        var inPos = analysisHop
        var outPos = synthHop

        while (inPos + window < framesIn && outPos + window < framesOut) {
            // Search for the most similar overlap region.
            val lo = max(0, inPos - searchRange)
            val hi = min(framesIn - window, inPos + searchRange)
            var best = inPos
            var bestCorr = Double.NEGATIVE_INFINITY
            for (probe in lo..hi step probeStride) {
                var corr = 0.0
                var i = 0
                while (i < overlap) {
                    corr += mono[probe + i].toDouble() * outMono[outPos + i]
                    i += corrStride
                }
                if (corr > bestCorr) {
                    bestCorr = corr
                    best = probe
                }
            }

            // Crossfade the overlap region.
            for (i in 0 until overlap) {
                val f = i.toFloat() / overlap
                val srcBase = (best + i) * channels
                val dstBase = (outPos + i) * channels
                var m = 0f
                for (c in 0 until channels) {
                    val v = out[dstBase + c] * (1f - f) + samples[srcBase + c] * f
                    out[dstBase + c] = v
                    m += v
                }
                outMono[outPos + i] = m / channels
            }
            // Copy the non-overlapping tail.
            for (i in overlap until window) {
                val srcBase = (best + i) * channels
                val dstBase = (outPos + i) * channels
                var m = 0f
                for (c in 0 until channels) {
                    val v = samples[srcBase + c]
                    out[dstBase + c] = v
                    m += v
                }
                outMono[outPos + i] = m / channels
            }

            outPos += synthHop
            inPos = best + analysisHop
        }

        // Trim trailing silence produced by the final partial window.
        var lastNonZero = out.size / channels - 1
        while (lastNonZero > window) {
            val j = lastNonZero * channels
            var silence = true
            for (c in 0 until channels) {
                if (abs(out[j + c]) > 1e-6f) { silence = false; break }
            }
            if (!silence) break
            lastNonZero--
        }
        return if (lastNonZero + 1 < out.size / channels) {
            out.copyOf((lastNonZero + 1) * channels)
        } else out
    }
}
