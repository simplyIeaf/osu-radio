package com.osuradio.app.utils

import com.osuradio.app.data.NerinyanBeatmapSet
import com.osuradio.app.data.Song
import com.osuradio.app.network.NerinyanApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, FAILED, COMPLETED }

data class DownloadTask(
    val beatmapsetId: Long,
    val title: String,
    val artist: String,
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED
)

class DownloadManager(
    private val onSongImported: (Song) -> Unit
) {
    private val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val pending = Channel<Long>(Channel.UNLIMITED)
    private val runningJobs = ConcurrentHashMap<Long, Job>()

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads.asStateFlow()

    private val _queuedIds = MutableStateFlow<Set<Long>>(emptySet())
    val queuedIds: StateFlow<Set<Long>> = _queuedIds.asStateFlow()

    /** Whether new downloads request the no-video archive variant from osu.direct. */
    var noVideo: Boolean = true

    init {
        repeat(WORKER_COUNT) { startWorker() }
    }

    fun enqueue(set: NerinyanBeatmapSet) {
        if (_downloads.value.any { it.beatmapsetId == set.id }) return
        _downloads.value = _downloads.value + DownloadTask(set.id, set.title, set.artist)
        refreshQueuedIds()
        scope.launch { pending.send(set.id) }
    }

    /** Pauses an in-flight download, keeping the partial file so it can be resumed. */
    fun pause(beatmapsetId: Long) {
        val task = _downloads.value.find { it.beatmapsetId == beatmapsetId } ?: return
        if (task.status != DownloadStatus.DOWNLOADING) return
        runningJobs[beatmapsetId]?.cancel()
        updateStatus(beatmapsetId, DownloadStatus.PAUSED, task.progress)
    }

    /** Resumes a paused or failed download (continues from the partial file if possible). */
    fun resume(beatmapsetId: Long) {
        val task = _downloads.value.find { it.beatmapsetId == beatmapsetId } ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        updateStatus(beatmapsetId, DownloadStatus.QUEUED, task.progress)
        scope.launch {
            // Pause cancels asynchronously, so wait for the old worker to fully stop
            // before re-queuing, otherwise two writers could touch the same temp file.
            runningJobs[beatmapsetId]?.join()
            pending.send(beatmapsetId)
        }
    }

    /** Removes the download entirely and deletes any partial file. */
    fun cancel(beatmapsetId: Long) {
        runningJobs[beatmapsetId]?.cancel()
        tempFileFor(beatmapsetId).delete()
        _downloads.value = _downloads.value.filterNot { it.beatmapsetId == beatmapsetId }
        refreshQueuedIds()
    }

    private fun startWorker() {
        scope.launch {
            for (beatmapsetId in pending) {
                val job = scope.launch { runDownload(beatmapsetId) }
                runningJobs[beatmapsetId] = job
                job.join()
                runningJobs.remove(beatmapsetId)
            }
        }
    }

    private suspend fun runDownload(beatmapsetId: Long) {
        val task = _downloads.value.find { it.beatmapsetId == beatmapsetId } ?: return
        val tempFile = tempFileFor(beatmapsetId)
        try {
            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
            updateStatus(beatmapsetId, DownloadStatus.DOWNLOADING, 0)

            val success = NerinyanApi.downloadBeatmapset(
                beatmapsetId, tempFile,
                onProgress = { pct ->
                    val current = _downloads.value.find { it.beatmapsetId == beatmapsetId }
                    updateStatus(beatmapsetId, DownloadStatus.DOWNLOADING, pct ?: current?.progress ?: 0)
                },
                resumeFromBytes = existingBytes,
                noVideo = noVideo
            )

            if (success) {
                val song = OszImporter.importOszFromFile(tempFile, "${task.artist} - ${task.title}")
                tempFile.delete()
                if (song != null) {
                    updateStatus(beatmapsetId, DownloadStatus.COMPLETED, 100)
                    refreshQueuedIds()
                    try {
                        onSongImported(song)
                    } catch (e: Exception) {
                        Logger.error(TAG, "Failed to register imported song $beatmapsetId", e)
                    }
                    scope.launch {
                        delay(2_500L)
                        removeTask(beatmapsetId)
                    }
                } else if (isAlreadyImported(task.artist, task.title)) {
                    updateStatus(beatmapsetId, DownloadStatus.COMPLETED, 100)
                    refreshQueuedIds()
                    scope.launch {
                        delay(2_500L)
                        removeTask(beatmapsetId)
                    }
                } else {
                    updateStatus(beatmapsetId, DownloadStatus.FAILED, 100)
                }
            } else {
                if (isAlreadyImported(task.artist, task.title)) {
                    updateStatus(beatmapsetId, DownloadStatus.COMPLETED, 100)
                    refreshQueuedIds()
                    scope.launch {
                        delay(2_500L)
                        removeTask(beatmapsetId)
                    }
                } else {
                    updateStatus(beatmapsetId, DownloadStatus.FAILED)
                }
            }
        } catch (e: CancellationException) {
            // Paused or cancelled — status already updated by the caller.
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "Download failed for beatmapset $beatmapsetId", e)
            updateStatus(beatmapsetId, DownloadStatus.FAILED)
        }
    }

    private fun removeTask(beatmapsetId: Long) {
        _downloads.value = _downloads.value.filterNot { it.beatmapsetId == beatmapsetId }
        refreshQueuedIds()
    }

    private fun updateStatus(beatmapsetId: Long, status: DownloadStatus, progress: Int = 0) {
        _downloads.value = _downloads.value.map { task ->
            if (task.beatmapsetId == beatmapsetId) task.copy(status = status, progress = progress)
            else task
        }
    }

    private fun refreshQueuedIds() {
        _queuedIds.value = _downloads.value
            .filter { it.status != DownloadStatus.COMPLETED }
            .map { it.beatmapsetId }
            .toSet()
    }

    private fun tempFileFor(beatmapsetId: Long) = File(AppPaths.cacheDir(), "download_$beatmapsetId.osz")

    /** Whether a folder for this artist/title already exists in the library with an audio file. */
    private fun isAlreadyImported(artist: String, title: String): Boolean {
        val baseName = OszImporter.sanitizeFolderName("$artist - $title")
        val folder = File(AppPaths.songsDir(), baseName)
        if (!folder.isDirectory) return false
        return folder.listFiles()?.any {
            val ext = it.extension.lowercase()
            ext == "mp3" || ext == "ogg"
        } == true
    }

    fun release() {
        scope.cancel()
    }

    companion object {
        private const val WORKER_COUNT = 3
    }
}
