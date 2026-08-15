package com.osuradio.app.utils

import com.osuradio.app.audio.AudioConverter
import com.osuradio.app.audio.AudioDecoder
import com.osuradio.app.data.Song
import java.io.File
import java.util.UUID

object SongScanner {
    private const val TAG = "SongScanner"
    private val EXCLUDED_PREFIXES = listOf("soft-", "normal-", "drum-")
    private const val SONGS_FOLDER = "Songs"

    fun getOsuDroidSongsDir(): File? {
        val dir = AppPaths.osuDroidSongsDir()
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    fun getOsuRadioDir(): File = AppPaths.osuRadioDir()

    fun getOrCreateOutputSongsDir(osuRadioDir: File): File = AppPaths.songsDir()

    fun scanAndCopySongs(onProgress: (String) -> Unit = {}): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val osuDroidSongsDir = getOsuDroidSongsDir()
            if (osuDroidSongsDir == null) {
                Logger.warn(TAG, "osu!droid Songs directory not found at ${AppPaths.osuDroidSongsDir()}")
                return songs
            }
            val outputSongsDir = getOrCreateOutputSongsDir(getOsuRadioDir())

            val beatmapFolders = osuDroidSongsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (folder in beatmapFolders) {
                try {
                    val song = processBeatmapFolder(folder, outputSongsDir)
                    if (song != null) {
                        onProgress("Loaded: ${song.title}")
                        songs.add(song)
                    }
                } catch (e: Exception) {
                    Logger.error(TAG, "Error processing folder: ${folder.name}", e)
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Error scanning songs", e)
        }
        return songs
    }

    private fun processBeatmapFolder(folder: File, outputSongsDir: File): Song? {
        val (artist, title) = parseFolderName(folder.name)

        val audioFiles = folder.listFiles()?.filter { file ->
            AudioConverter.isConvertible(file) &&
                    !EXCLUDED_PREFIXES.any { file.name.lowercase().startsWith(it) }
        } ?: emptyList()

        // Pick the largest audio file if multiple exist (most likely the main track)
        val audioFile = when {
            audioFiles.isEmpty() -> return null
            audioFiles.size == 1 -> audioFiles[0]
            else -> audioFiles.maxByOrNull { it.length() } ?: audioFiles[0]
        }

        val imageFiles = folder.listFiles()?.filter {
            it.extension.lowercase() == "jpg" || it.extension.lowercase() == "jpeg" ||
                    it.extension.lowercase() == "png"
        } ?: emptyList()
        val selectedImage = if (imageFiles.isNotEmpty()) imageFiles.maxByOrNull { it.length() } else null

        val outputFolder = File(outputSongsDir, folder.name)
        outputFolder.mkdirs()

        val destAudio = File(outputFolder, audioFile.name)
        if (!destAudio.exists()) audioFile.copyTo(destAudio, overwrite = true)

        val finalAudio = AudioConverter.ensureMp3(destAudio)
        if (!AudioConverter.isDecodable(finalAudio)) return null

        var destImagePath: String? = null
        if (selectedImage != null) {
            val destImage = File(outputFolder, selectedImage.name)
            if (!destImage.exists()) selectedImage.copyTo(destImage, overwrite = true)
            destImagePath = destImage.absolutePath
        }

        val duration = getAudioDuration(finalAudio)

        return Song(
            id = UUID.nameUUIDFromBytes(folder.name.toByteArray()).toString(),
            title = title,
            artist = artist,
            audioPath = finalAudio.absolutePath,
            imagePath = destImagePath,
            folderPath = outputFolder.absolutePath,
            duration = duration
        )
    }

    fun parseFolderName(folderName: String): Pair<String, String> {
        val withoutId = folderName.replace(Regex("^\\d+\\s*"), "")
        val separatorIdx = withoutId.indexOf(" - ")
        return if (separatorIdx >= 0) {
            val artist = withoutId.substring(0, separatorIdx).trim()
            val title = withoutId.substring(separatorIdx + 3).trim()
            Pair(artist, title)
        } else {
            Pair("Unknown Artist", withoutId.trim().ifEmpty { folderName })
        }
    }

    private fun getAudioDuration(file: File): Long = AudioDecoder.durationOf(file)

    fun loadAlreadyScannedSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val songsDir = AppPaths.songsDir()
            if (!songsDir.exists()) return songs
            val folders = songsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (folder in folders) {
                try {
                    val song = loadSongFromOutputFolder(folder)
                    if (song != null) songs.add(song)
                } catch (e: Exception) {
                    Logger.error(TAG, "Error loading song from ${folder.name}", e)
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Error loading scanned songs", e)
        }
        return songs
    }

    private fun loadSongFromOutputFolder(folder: File): Song? {
        val audioFile = folder.listFiles()?.filter { file ->
            AudioConverter.isDecodable(file) &&
                    !EXCLUDED_PREFIXES.any { file.name.lowercase().startsWith(it) }
        }?.maxByOrNull { it.length() } ?: return null

        val imageFile = folder.listFiles()?.filter {
            it.extension.lowercase() == "jpg" || it.extension.lowercase() == "jpeg" ||
                    it.extension.lowercase() == "png"
        }?.maxByOrNull { it.length() }

        val (artist, title) = parseFolderName(folder.name)
        val duration = getAudioDuration(audioFile)

        return Song(
            id = UUID.nameUUIDFromBytes(folder.name.toByteArray()).toString(),
            title = title,
            artist = artist,
            audioPath = audioFile.absolutePath,
            imagePath = imageFile?.absolutePath,
            folderPath = folder.absolutePath,
            duration = duration
        )
    }
}
