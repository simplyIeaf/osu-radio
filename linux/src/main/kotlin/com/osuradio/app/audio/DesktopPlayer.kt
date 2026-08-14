package com.osuradio.app.audio

import com.osuradio.app.data.ModSettings
import com.osuradio.app.data.Song
import com.osuradio.app.data.SongMod
import com.osuradio.app.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs

/**
 * Full desktop audio engine. Decodes the whole song into memory once, then a
 * playback thread reads it with linear interpolation so pitch and tempo can be
 * changed independently (mods), with a live 5-band equalizer, loudness gain,
 * volume fades (audio transitions) and sleep-timer friendly playback.
 */
class DesktopPlayer(
    private val scope: CoroutineScope
) {
    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onSongEnded()
    }

    private val TAG = "DesktopPlayer"
    private val lock = Object()

    private var listener: Listener? = null

    private var buffer: FloatArray? = null
    private var channels = 2
    private var sampleRate = 44100f
    private var bufferIndex = 0L               // current read position in frames
    private var advance = 1.0                  // read step per output frame (pitch)
    private var rampStart = 1.0
    private var rampEnd = 1.0
    private var rampActive = false
    private var currentSongId: String? = null

    private var playing = false
    @Volatile private var stopped = false
    private var thread: Thread? = null

    private var baseVolume = 1f
    private var currentVolume = 1f
    private var volumeGoal = 1f
    private var fadeActive = false
    private var fadeSteps = 0
    private var fadeCounter = 0
    private var fadeStartVolume = 1f
    private var pauseToken = 0

    private var loudnessEnabled = false
    private var loudnessGainDb = 3
    private var loudnessGain = 1f

    private var eq: EqualizerProcessor? = null
    private var eqEnabled = false
    private var eqLevels: List<Int> = List(5) { 0 }

    private var line: SourceDataLine? = null
    private var lineRate = 0f

    private var rampJob: Job? = null
    private var endedNotified = false

    fun setListener(listener: Listener?) {
        synchronized(lock) { this.listener = listener }
    }

    fun isLoaded(): Boolean = synchronized(lock) { buffer != null }

    // ── Public control API ────────────────────────────────────────────────────

    /** Decodes + stretches a song and starts playing it from [startPositionMs]. */
    fun play(song: Song, modSettings: ModSettings, startPositionMs: Long = 0L) {
        synchronized(lock) {
            currentSongId = song.id
            stopped = false
            rampJob?.cancel()
        }
        scope.launch(Dispatchers.IO) {
            try {
                val decoded = AudioDecoder.decode(File(song.audioPath))
                val (tempo, pitch) = resolveModParams(modSettings)
                val rampActive = modSettings.activeMod == SongMod.WIND_UP ||
                        modSettings.activeMod == SongMod.WIND_DOWN
                // Ramps interpolate live from 1.0 → target, so the buffer must be untouched.
                val stretched: FloatArray = if (!rampActive && abs(tempo / pitch - 1f) > 0.002f) {
                    TimeStretcher.stretch(decoded.samples, decoded.channels, decoded.sampleRate, tempo / pitch)
                } else {
                    decoded.samples
                }

                val startFrame = if (startPositionMs > 0) {
                    (startPositionMs * decoded.sampleRate / 1000.0).toLong()
                        .coerceIn(0L, (stretched.size / decoded.channels).toLong() - 1L)
                } else 0L

                synchronized(lock) {
                    buffer = stretched
                    channels = decoded.channels
                    sampleRate = decoded.sampleRate
                    bufferIndex = startFrame.coerceAtLeast(0L)
                    rampActive = modSettings.activeMod == SongMod.WIND_UP || modSettings.activeMod == SongMod.WIND_DOWN
                    rampStart = 1.0
                    rampEnd = when (modSettings.activeMod) {
                        SongMod.WIND_UP -> 1.8
                        SongMod.WIND_DOWN -> 0.6
                        else -> tempo
                    }
                    advance = if (rampActive) rampStart else pitch.toDouble()
                    if (rampActive) startRampLocked()
                    eq = EqualizerProcessor(sampleRate, channels)
                    eq?.setEnabled(eqEnabled)
                    eq?.setBandLevels(eqLevels)
                    loudnessGain = if (loudnessEnabled) Math.pow(10.0, loudnessGainDb / 20.0).toFloat() else 1f
                    playing = true
                    endedNotified = false
                    // Fade in from silence.
                    volumeGoal = baseVolume
                    fadeStartVolume = 0f
                    currentVolume = 0f
                    fadeActive = true
                    fadeSteps = 12
                    fadeCounter = 0
                    lock.notifyAll()
                }
                ensureThread()
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to prepare ${song.title}", e)
                synchronized(lock) {
                    playing = false
                    lock.notifyAll()
                }
            }
        }
    }

    /** Fades out over [fadeMs] and then starts [song] (used for audio transitions). */
    fun fadeOutThenPlay(song: Song, modSettings: ModSettings, fadeMs: Long, startPositionMs: Long = 0L) {
        synchronized(lock) {
            if (playing && buffer != null) {
                volumeGoal = 0f
                fadeStartVolume = currentVolume
                fadeActive = true
                fadeSteps = (fadeMs / 16).toInt().coerceAtLeast(4)
                fadeCounter = 0
            }
        }
        scope.launch {
            delay(fadeMs)
            play(song, modSettings, startPositionMs)
        }
    }

    fun pause() {
        val token: Int
        synchronized(lock) {
            if (!playing) return
            token = ++pauseToken
            volumeGoal = 0f
            fadeStartVolume = currentVolume
            fadeActive = true
            fadeSteps = 12
            fadeCounter = 0
        }
        scope.launch {
            delay(200)
            val shouldPause = synchronized(lock) {
                if (pauseToken != token) {
                    false
                } else {
                    playing = false
                    fadeActive = false
                    currentVolume = 0f
                    volumeGoal = 0f
                    lock.notifyAll()
                    true
                }
            }
            if (shouldPause) listener?.onIsPlayingChanged(false)
        }
    }

    fun resume() {
        synchronized(lock) {
            if (playing || buffer == null) return
            ++pauseToken
            playing = true
            if (currentVolume < baseVolume - 0.02f) {
                volumeGoal = baseVolume
                fadeStartVolume = currentVolume
                fadeActive = true
                fadeSteps = 12
                fadeCounter = 0
            } else {
                currentVolume = baseVolume
                volumeGoal = baseVolume
            }
            lock.notifyAll()
        }
        scope.launch { listener?.onIsPlayingChanged(true) }
    }

    fun pauseResume() {
        synchronized(lock) { if (playing) pause() else resume() }
    }

    fun isPlaying(): Boolean = synchronized(lock) { playing }

    fun currentPositionMs(): Long = synchronized(lock) {
        if (sampleRate <= 0f) 0L
        else (bufferIndex * 1000 / sampleRate).toLong()
    }

    fun seekTo(positionMs: Long) {
        synchronized(lock) {
            val buf = buffer ?: return
            val frames = buf.size / channels
            bufferIndex = (positionMs * sampleRate / 1000.0).toLong().coerceIn(0L, (frames - 1).toLong())
            lock.notifyAll()
        }
    }

    fun setVolume(volume: Float) {
        synchronized(lock) {
            baseVolume = volume.coerceIn(0f, 1f)
            volumeGoal = baseVolume
        }
    }

    /**
     * Applies a new mod live. Ramps and pure (coupled) speed changes are cheap
     * and handled in-place; decoupled mods (tempo != pitch) need a new stretch,
     * so the caller (ViewModel) re-calls [play] with the same position instead.
     *
     * @return true if applied in-place, false if a re-stretch is required.
     */
    fun applyMod(modSettings: ModSettings): Boolean {
        synchronized(lock) {
            rampJob?.cancel()
            val (tempo, pitch) = resolveModParams(modSettings)
            val needsRestretch = abs(tempo / pitch - 1f) > 0.002f
            rampActive = modSettings.activeMod == SongMod.WIND_UP || modSettings.activeMod == SongMod.WIND_DOWN
            if (rampActive) {
                rampStart = 1.0
                rampEnd = when (modSettings.activeMod) {
                    SongMod.WIND_UP -> 1.8
                    SongMod.WIND_DOWN -> 0.6
                    else -> tempo
                }
                advance = rampStart
                startRampLocked()
                return true
            } else if (!needsRestretch) {
                advance = pitch.toDouble()
                return true
            }
            return false
        }
    }

    fun setEqualizer(enabled: Boolean, levels: List<Int>) {
        synchronized(lock) {
            eqEnabled = enabled
            eqLevels = levels
            eq?.setEnabled(enabled)
            eq?.setBandLevels(levels)
        }
    }

    fun setLoudness(enabled: Boolean, gainDb: Int) {
        synchronized(lock) {
            loudnessEnabled = enabled
            loudnessGainDb = gainDb
            loudnessGain = if (enabled) Math.pow(10.0, gainDb / 20.0).toFloat() else 1f
        }
    }

    /** Immediate stop (used on shutdown / queue rebuild). */
    fun stop() {
        synchronized(lock) {
            stopped = true
            playing = false
            buffer = null
            currentSongId = null
            rampJob?.cancel()
            lock.notifyAll()
        }
        stopLine()
    }

    fun release() {
        stop()
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun startRampLocked() {
        rampJob?.cancel()
        rampJob = scope.launch {
            repeat(60) { i ->
                delay(45_000L / 60)
                val fraction = (i + 1) / 60f
                synchronized(lock) {
                    advance = rampStart + (rampEnd - rampStart) * fraction
                }
            }
        }
    }

    private fun resolveModParams(modSettings: ModSettings): Pair<Float, Float> = when (modSettings.activeMod) {
        SongMod.NONE -> 1.0f to 1.0f
        SongMod.DAYCORE -> 0.75f to 0.75f
        SongMod.NIGHTCORE -> 1.5f to 1.5f
        SongMod.DOUBLE_TIME -> 1.5f to 1.0f
        SongMod.HALF_TIME -> 0.75f to 1.0f
        SongMod.WIND_UP -> 1.3f to 1.1f
        SongMod.WIND_DOWN -> 0.8f to 0.9f
        SongMod.BASS_BOOST -> 1.0f to 0.85f
        SongMod.VAPORWAVE -> 0.7f to 0.7f
        SongMod.CUSTOM_SPEED -> modSettings.customSpeed to modSettings.customSpeed
    }

    private fun ensureThread() {
        synchronized(lock) {
            if (thread == null || thread?.isAlive != true) {
                stopped = false
                thread = Thread { runLoop() }.apply {
                    isDaemon = true
                    name = "osu-radio-audio"
                    start()
                }
            }
        }
    }

    private fun runLoop() {
        while (true) {
            val framesToWrite: Int
            val chunk: FloatArray
            synchronized(lock) {
                while (true) {
                    if (stopped) return
                    val buf = buffer
                    if (buf == null || !playing) {
                        lock.wait()
                        continue
                    }
                    break
                }
                chunk = FloatArray(1024 * channels)
                framesToWrite = fillChunk(chunk)
                updateFadeLocked()
            }

            if (framesToWrite <= 0) {
                val notifyEnded = synchronized(lock) {
                    if (endedNotified) {
                        false
                    } else {
                        endedNotified = true
                        playing = false
                        true
                    }
                }
                if (notifyEnded) {
                    scope.launch { listener?.onSongEnded() }
                    // Wait for the next play() to feed us a new buffer.
                    synchronized(lock) {
                        try { lock.wait() } catch (_: InterruptedException) { return }
                    }
                }
                continue
            }

            writeToLine(chunk, framesToWrite)
        }
    }

    /** Reads interleaved frames into [chunk] at the current [advance]/pitch. Must hold [lock]. */
    private fun fillChunk(chunk: FloatArray): Int {
        val buf = buffer ?: return 0
        val frames = buf.size / channels
        val eqProcessor = eq
        val gain = loudnessGain
        var out = 0
        var idx = bufferIndex
        var written = 0
        val maxFrames = chunk.size / channels

        for (f in 0 until maxFrames) {
            val i0 = idx.toInt()
            if (i0 >= frames - 1) break
            val frac = (idx - i0).toFloat()
            val i1 = i0 + 1
            for (c in 0 until channels) {
                val a = buf[i0 * channels + c]
                val b = buf[i1 * channels + c]
                chunk[out++] = (a + (b - a) * frac) * gain
            }
            idx += advance
            written++
        }
        eqProcessor?.process(chunk, 0, written)

        val v = currentVolume
        if (v != 1f) {
            for (i in 0 until written * channels) chunk[i] *= v
        }
        bufferIndex = idx
        return written
    }

    private fun updateFadeLocked() {
        if (!fadeActive) return
        fadeCounter++
        val t = (fadeCounter.toFloat() / fadeSteps).coerceAtMost(1f)
        currentVolume = fadeStartVolume + (volumeGoal - fadeStartVolume) * t
        if (fadeCounter >= fadeSteps) {
            fadeActive = false
            currentVolume = volumeGoal
        }
    }

    private fun writeToLine(chunk: FloatArray, frames: Int) {
        try {
            var l = line
            if (l == null || lineRate != sampleRate) {
                stopLine()
                val format = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false
                )
                l = AudioSystem.getSourceDataLine(format)
                l.open(format, 4096)
                l.start()
                synchronized(lock) {
                    line = l
                    lineRate = sampleRate
                }
            }
            val bytes = ByteArray(frames * channels * 2)
            var b = 0
            var i = 0
            val n = frames * channels
            while (i < n) {
                val s = (chunk[i] * 32767f).toInt().coerceIn(-32768, 32767)
                bytes[b++] = (s and 0xFF).toByte()
                bytes[b++] = ((s shr 8) and 0xFF).toByte()
                i++
            }
            l.write(bytes, 0, bytes.size)
        } catch (e: Exception) {
            // No audio device or audio error — silently keep the timeline moving.
            Logger.warn(TAG, "Audio output unavailable: ${e.message}")
        }
    }

    private fun stopLine() {
        try {
            synchronized(lock) {
                line?.drain()
                line?.stop()
                line?.close()
                line = null
                lineRate = 0f
            }
        } catch (_: Exception) {
        }
    }
}
