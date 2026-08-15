package com.osuradio.app.audio

import com.osuradio.app.utils.Logger
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Decodes mp3 / ogg / wav beatmap audio into raw float PCM using the
 * `javax.sound.sampled` SPI providers (mp3spi + vorbisspi). Pure JVM, so it
 * works on every desktop platform.
 */
object AudioDecoder {

    data class Decoded(
        val samples: FloatArray,   // interleaved [frame0ch0 frame0ch1 frame1ch0 ...]
        val channels: Int,
        val sampleRate: Float,
        val durationMs: Long
    )

    private val TAG = "AudioDecoder"

    /** Duration without full decode where possible, falls back to decoding. */
    fun durationOf(file: File): Long {
        try {
            val format = AudioSystem.getAudioFileFormat(file)
            val frameLen = format.frameLength
            val frameRate = format.format.frameRate
            if (frameLen > 0 && frameRate > 0f) {
                return (frameLen * 1000.0 / frameRate).toLong()
            }
        } catch (e: Exception) {
            // fall through to full decode
        }
        return try {
            decode(file).durationMs
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to read duration of $file", e)
            0L
        }
    }

    fun decode(file: File): Decoded {
        val base: AudioInputStream = AudioSystem.getAudioInputStream(file)
        val format = base.format
        // Normalise to signed 16-bit little-endian PCM at the source sample rate.
        val target = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            format.sampleRate,
            16,
            format.channels,
            format.channels * 2,
            format.sampleRate,
            false
        )
        val stream = if (format.encoding != AudioFormat.Encoding.PCM_SIGNED ||
            format.sampleSizeInBits != 16 ||
            format.isBigEndian
        ) AudioSystem.getAudioInputStream(target, base) else base

        return stream.use {
            val channels = target.channels
            val sampleRate = target.sampleRate
            val byteBuf = ByteArray(64 * 1024)
            var floats = FloatArray(1 shl 20)
            var size = 0
            while (true) {
                val n = it.read(byteBuf)
                if (n <= 0) break
                var i = 0
                while (i + 1 < n) {
                    val lo = byteBuf[i].toInt() and 0xFF
                    val hi = byteBuf[i + 1].toInt()
                    i += 2
                    if (size == floats.size) floats = floats.copyOf(floats.size shl 1)
                    floats[size++] = (hi shl 8 or lo).toShort().toFloat() / 32768f
                }
            }
            val samples = if (size == floats.size) floats else floats.copyOf(size)
            Decoded(
                samples = samples,
                channels = channels,
                sampleRate = sampleRate,
                durationMs = if (sampleRate > 0f) {
                    (size / channels) * 1000L / sampleRate.toLong()
                } else 0L
            )
        }
    }
}
