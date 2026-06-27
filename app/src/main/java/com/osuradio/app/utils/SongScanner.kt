package com.osuradio.app.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import com.osuradio.app.data.Song
import java.io.File
import java.util.UUID

object SongScanner {
    private const val TAG = "SongScanner"
    private val EXCLUDED_PREFIXES = listOf("soft-", "normal-", "drum-")
    private const val OSU_DROID_FOLDER = "osu!droid"
    private const val OSU_RADIO_FOLDER = "osu!radio"
    private const val SONGS_FOLDER = "Songs"

    fun getOsuDroidDir(): File? {
        val base = File("/storage/emulated/0")
        val dir = File(base, OSU_DROID_FOLDER)
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    fun getOsuRadioDir(): File {
        val base = File("/storage/emulated/0")
        val dir = File(base, OSU_RADIO_FOLDER)
        dir.mkdirs()
        return dir
    }

    fun getOsuDroidSongsDir(): File? {
        val osuDir = getOsuDroidDir() ?: return null
        val songsDir = File(osuDir, SONGS_FOLDER)
        return if (songsDir.exists() && songsDir.isDirectory) songsDir else null
    }

    fun getOrCreateOutputSongsDir(osuRadioDir: File): File {
        val songsDir = File(osuRadioDir, SONGS_FOLDER)
        songsDir.mkdirs()
        return songsDir
    }

    fun scanAndCopySongs(context: Context, onProgress: (String) -> Unit = {}): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val osuDroidSongsDir = getOsuDroidSongsDir()
            if (osuDroidSongsDir == null) {
                Logger.warn(TAG, "osu!droid Songs directory not found")
                return songs
            }
            val osuRadioDir = getOsuRadioDir()
            val outputSongsDir = getOrCreateOutputSongsDir(osuRadioDir)

            val beatmapFolders = osuDroidSongsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (folder in beatmapFolders) {
                try {
                    val song = processBeatmapFolder(folder, outputSongsDir, context)
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

    private fun processBeatmapFolder(folder: File, outputSongsDir: File, context: Context): Song? {
        val (artist, title) = parseFolderName(folder.name)

        val audioFiles = folder.listFiles()?.filter { file ->
            (file.extension.lowercase() == "mp3" || file.extension.lowercase() == "ogg") &&
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

        var destImagePath: String? = null
        if (selectedImage != null) {
            val destImage = File(outputFolder, selectedImage.name)
            if (!destImage.exists()) selectedImage.copyTo(destImage, overwrite = true)
            destImagePath = destImage.absolutePath
        }

        val duration = getAudioDuration(destAudio)

        return Song(
            id = UUID.nameUUIDFromBytes(folder.name.toByteArray()).toString(),
            title = title,
            artist = artist,
            audioPath = destAudio.absolutePath,
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

    private fun getAudioDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        } catch (e: Exception) {
            0L
        }
    }

    fun loadAlreadyScannedSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val osuRadioDir = getOsuRadioDir()
            val songsDir = File(osuRadioDir, SONGS_FOLDER)
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
            (file.extension.lowercase() == "mp3" || file.extension.lowercase() == "ogg") &&
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
