package com.osuradio.app.audio

import java.io.File
import java.util.concurrent.TimeUnit

object AudioConverter {
    private val CONVERTIBLE_EXTENSIONS = setOf(
        "mp3", "ogg", "wav", "flac", "aac", "m4a", "aiff", "aif", "wma", "opus", "mp4"
    )
    private val DECODABLE_EXTENSIONS = setOf("mp3", "ogg", "wav")

    fun isConvertible(file: File): Boolean = file.extension.lowercase() in CONVERTIBLE_EXTENSIONS

    fun isConvertible(extension: String): Boolean = extension.lowercase() in CONVERTIBLE_EXTENSIONS

    fun isDecodable(file: File): Boolean = file.extension.lowercase() in DECODABLE_EXTENSIONS

    /** Converts [file] to mp3 in place, falling back to the original when ffmpeg is missing. */
    fun ensureMp3(file: File): File {
        if (file.extension.lowercase() == "mp3") return file
        val mp3 = toMp3(file)
        return if (mp3 != null) {
            file.delete()
            mp3
        } else file
    }

    private fun toMp3(input: File): File? {
        val mp3 = File(input.parentFile, input.nameWithoutExtension + ".mp3")
        if (mp3.exists()) mp3.delete()
        return try {
            val process = ProcessBuilder(
                "ffmpeg", "-y", "-loglevel", "error",
                "-i", input.absolutePath,
                "-vn", "-acodec", "libmp3lame", "-q:a", "2",
                mp3.absolutePath
            ).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(90, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() == 0 && mp3.exists() && mp3.length() > 0) mp3 else null
        } catch (_: Exception) {
            null
        }
    }
}
