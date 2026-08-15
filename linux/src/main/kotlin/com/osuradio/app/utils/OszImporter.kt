package com.osuradio.app.utils

import com.osuradio.app.audio.AudioConverter
import com.osuradio.app.audio.AudioDecoder
import com.osuradio.app.data.Song
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

object OszImporter {
    private const val TAG = "OszImporter"
    private val EXCLUDED_PREFIXES = listOf("soft-", "normal-", "drum-")

    /** Imports a single .osz archive from disk (used by downloads and CLI args). */
    fun importOszFromFile(file: File, fallbackName: String): Song? {
        var outputFolder: File? = null
        var outputFolderExisted = true
        return try {
            val outputSongsDir = AppPaths.songsDir()
            val baseName = sanitizeFolderName(fallbackName.ifBlank { file.nameWithoutExtension })
            outputFolder = File(outputSongsDir, baseName)
            outputFolderExisted = outputFolder.exists()
            outputFolder.mkdirs()

            val audioFiles = mutableListOf<File>()
            val imageFiles = mutableListOf<File>()

            ZipInputStream(file.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = File(entry.name).name
                        val ext = entryName.substringAfterLast('.', "").lowercase()
                        val isExcluded = EXCLUDED_PREFIXES.any { entryName.lowercase().startsWith(it) }

                        when {
                            !isExcluded && AudioConverter.isConvertible(ext) -> {
                                val destFile = File(outputFolder, entryName)
                                destFile.outputStream().use { out -> zip.copyTo(out) }
                                audioFiles.add(destFile)
                            }
                            ext == "jpg" || ext == "jpeg" || ext == "png" -> {
                                val destFile = File(outputFolder, entryName)
                                destFile.outputStream().use { out -> zip.copyTo(out) }
                                imageFiles.add(destFile)
                            }
                            else -> zip.closeEntry()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val audioFile = audioFiles.maxByOrNull { it.length() } ?: run {
                outputFolder.deleteRecursively()
                return null
            }
            val finalAudio = AudioConverter.ensureMp3(audioFile)
            if (!AudioConverter.isDecodable(finalAudio)) {
                outputFolder.deleteRecursively()
                return null
            }
            val selectedImage = imageFiles.maxByOrNull { it.length() }
            val (artist, title) = SongScanner.parseFolderName(baseName)

            Song(
                id = UUID.nameUUIDFromBytes(baseName.toByteArray()).toString(),
                title = title,
                artist = artist,
                audioPath = finalAudio.absolutePath,
                imagePath = selectedImage?.absolutePath,
                folderPath = outputFolder.absolutePath,
                duration = AudioDecoder.durationOf(finalAudio)
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to import downloaded beatmapset", e)
            // Only clean up folders this import created — never delete a previously imported map.
            if (!outputFolderExisted) outputFolder?.deleteRecursively()
            null
        }
    }

    /** Imports a .zip containing many beatmap folders (osu!droid backup-style archives). */
    fun importFullZip(file: File, onProgress: (String) -> Unit = {}): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val outputSongsDir = AppPaths.songsDir()
            val folderAudioMap = mutableMapOf<String, MutableList<File>>()
            val folderImageMap = mutableMapOf<String, MutableList<File>>()

            ZipInputStream(file.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val parts = entry.name.split("/")
                        val folderName = if (parts.size >= 2) parts[parts.size - 2] else "imported"
                        val fileName = parts.last()
                        val ext = fileName.substringAfterLast('.', "").lowercase()
                        val isExcluded = EXCLUDED_PREFIXES.any { fileName.lowercase().startsWith(it) }

                        val outputFolder = File(outputSongsDir, folderName)
                        outputFolder.mkdirs()

                        when {
                            !isExcluded && AudioConverter.isConvertible(ext) -> {
                                val destFile = File(outputFolder, fileName)
                                destFile.outputStream().use { out -> zip.copyTo(out) }
                                folderAudioMap.getOrPut(folderName) { mutableListOf() }.add(destFile)
                            }
                            ext == "jpg" || ext == "jpeg" || ext == "png" -> {
                                val destFile = File(outputFolder, fileName)
                                destFile.outputStream().use { out -> zip.copyTo(out) }
                                folderImageMap.getOrPut(folderName) { mutableListOf() }.add(destFile)
                            }
                            else -> zip.closeEntry()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            folderAudioMap.forEach { (folderName, audioFiles) ->
                val audioFile = audioFiles.maxByOrNull { it.length() } ?: return@forEach
                val finalAudio = AudioConverter.ensureMp3(audioFile)
                if (!AudioConverter.isDecodable(finalAudio)) return@forEach
                val imageFile = folderImageMap[folderName]?.maxByOrNull { it.length() }
                val (artist, title) = SongScanner.parseFolderName(folderName)
                onProgress("Imported: $title")
                songs.add(
                    Song(
                        id = UUID.nameUUIDFromBytes(folderName.toByteArray()).toString(),
                        title = title,
                        artist = artist,
                        audioPath = finalAudio.absolutePath,
                        imagePath = imageFile?.absolutePath,
                        folderPath = File(outputSongsDir, folderName).absolutePath,
                        duration = AudioDecoder.durationOf(finalAudio)
                    )
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to import zip", e)
        }
        return songs
    }

    fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
}
