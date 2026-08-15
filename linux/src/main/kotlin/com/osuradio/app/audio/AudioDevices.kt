package com.osuradio.app.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine

data class AudioDevice(val id: String, val label: String, val description: String)

object AudioDevices {

    private fun mixerKey(info: Mixer.Info): String =
        "${info.name}|${info.vendor}|${info.description}"

    fun list(): List<AudioDevice> = try {
        AudioSystem.getMixerInfo()
            .filter { info ->
                try {
                    AudioSystem.getMixer(info)
                        .getSourceLineInfo(DataLine.Info(SourceDataLine::class.java, null as AudioFormat?))
                        .isNotEmpty()
                } catch (_: Exception) {
                    false
                }
            }
            .map { info -> AudioDevice(mixerKey(info), info.name, info.description) }
    } catch (_: Exception) {
        emptyList()
    }

    fun byId(id: String): Mixer.Info? = try {
        AudioSystem.getMixerInfo().firstOrNull { mixerKey(it) == id }
    } catch (_: Exception) {
        null
    }

    fun compatibleMixer(): Mixer.Info? = try {
        val infos = AudioSystem.getMixerInfo()
        infos.firstOrNull { it.isLegacyEngine() }
            ?: infos.firstOrNull { it.isSystemDefaultAlsa() }
    } catch (_: Exception) {
        null
    }

    fun modernMixer(): Mixer.Info? = try {
        val infos = AudioSystem.getMixerInfo()
        infos.firstOrNull { it.isSystemDefaultAlsa() && !it.isLegacyEngine() }
            ?: infos.firstOrNull { it.isDirectAlsa() && !it.isLegacyEngine() }
    } catch (_: Exception) {
        null
    }

    private fun Mixer.Info.isLegacyEngine(): Boolean =
        name.contains("Java Sound Audio Engine") || vendor.contains("Java Sound Audio Engine")

    private fun Mixer.Info.isSystemDefaultAlsa(): Boolean =
        name.contains("[default]") || name.contains("alsa_playback") || name.contains("default")

    private fun Mixer.Info.isDirectAlsa(): Boolean =
        name.contains("Direct Audio Device") || description.contains("Direct Audio Device") ||
                name.contains("plughw") || name.contains("ALSA") || name.contains("PulseAudio") ||
                vendor.contains("ALSA")
}
