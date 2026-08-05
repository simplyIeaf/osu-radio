package com.osuradio.app.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.osuradio.app.data.Song
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

object OszImporter {
    private const val TAG = "OszImporter"
    private val EXCLUDED_PREFIXES = listOf("soft-", "normal-", "drum-")

    fun importOsz(context: Context, uri: Uri): Song? {
        return try {
            val osuRadioDir = SongScanner.getOsuRadioDir()
            val outputSongsDir = SongScanner.getOrCreateOutputSongsDir(osuRadioDir)

            val fileName = getFileName(context, uri) ?: "imported_${System.currentTimeMillis()}"
            val baseName = fileName.removeSuffix(".osz.zip").removeSuffix(".osz")
            val outputFolder = File(outputSongsDir, baseName)
            outputFolder.mkdirs()

            val audioFiles = mutableListOf<File>()
            val imageFiles = mutableListOf<File>()

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = File(entry.name).name
                            val ext = entryName.substringAfterLast('.', "").lowercase()
                            val isExcluded = EXCLUDED_PREFIXES.any { entryName.lowercase().startsWith(it) }

                            when {
                                !isExcluded && (ext == "mp3" || ext == "ogg") -> {
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
            }

            val audioFile = audioFiles.maxByOrNull { it.length() } ?: run {
                outputFolder.deleteRecursively()
                return null
            }

            val selectedImage = imageFiles.maxByOrNull { it.length() }
            val (artist, title) = SongScanner.parseFolderName(baseName)

            Song(
                id = UUID.nameUUIDFromBytes(baseName.toByteArray()).toString(),
                title = title,
                artist = artist,
                audioPath = audioFile.absolutePath,
                imagePath = selectedImage?.absolutePath,
                folderPath = outputFolder.absolutePath,
                duration = readAudioDuration(audioFile.absolutePath)
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to import .osz file", e)
            null
        }
    }

    fun importFullZip(context: Context, uri: Uri, onProgress: (String) -> Unit = {}): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val osuRadioDir = SongScanner.getOsuRadioDir()
            val outputSongsDir = SongScanner.getOrCreateOutputSongsDir(osuRadioDir)

            val folderAudioMap = mutableMapOf<String, MutableList<File>>()
            val folderImageMap = mutableMapOf<String, MutableList<File>>()

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
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
                                !isExcluded && (ext == "mp3" || ext == "ogg") -> {
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
            }

            folderAudioMap.forEach { (folderName, audioFiles) ->
                val audioFile = audioFiles.maxByOrNull { it.length() } ?: return@forEach
                val imageFile = folderImageMap[folderName]?.maxByOrNull { it.length() }
                val (artist, title) = SongScanner.parseFolderName(folderName)
                onProgress("Imported: $title")
                songs.add(
                    Song(
                        id = UUID.nameUUIDFromBytes(folderName.toByteArray()).toString(),
                        title = title,
                        artist = artist,
                        audioPath = audioFile.absolutePath,
                        imagePath = imageFile?.absolutePath,
                        folderPath = File(outputSongsDir, folderName).absolutePath,
                        duration = readAudioDuration(audioFile.absolutePath)
                    )
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to import zip", e)
        }
        return songs
    }

    fun importOszFromFile(file: File, fallbackName: String): Song? {
        return try {
            val osuRadioDir = SongScanner.getOsuRadioDir()
            val outputSongsDir = SongScanner.getOrCreateOutputSongsDir(osuRadioDir)
            val baseName = sanitizeFolderName(fallbackName.ifBlank { file.nameWithoutExtension })
            val outputFolder = File(outputSongsDir, baseName)
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
                            !isExcluded && (ext == "mp3" || ext == "ogg") -> {
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
            val selectedImage = imageFiles.maxByOrNull { it.length() }
            val (artist, title) = SongScanner.parseFolderName(baseName)

            Song(
                id = UUID.nameUUIDFromBytes(baseName.toByteArray()).toString(),
                title = title,
                artist = artist,
                audioPath = audioFile.absolutePath,
                imagePath = selectedImage?.absolutePath,
                folderPath = outputFolder.absolutePath,
                duration = readAudioDuration(audioFile.absolutePath)
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to import downloaded beatmapset", e)
            null
        }
    }

    private fun readAudioDuration(audioPath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioPath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name ?: uri.lastPathSegment
    }
}
