package com.osuradio.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
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
    private val context: Context,
    private val onSongImported: (Song) -> Unit
) {
    private val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val pending = Channel<Long>(Channel.UNLIMITED)
    private val runningJobs = ConcurrentHashMap<Long, Job>()
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads.asStateFlow()

    private val _queuedIds = MutableStateFlow<Set<Long>>(emptySet())
    val queuedIds: StateFlow<Set<Long>> = _queuedIds.asStateFlow()

    /** Whether new downloads request the no-video archive variant from osu.direct. */
    var noVideo: Boolean = true

    init {
        createChannel()
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
        notificationManager.cancel(notificationIdFor(beatmapsetId))
    }

    /** Resumes a paused or failed download (continues from the partial file if possible). */
    fun resume(beatmapsetId: Long) {
        val task = _downloads.value.find { it.beatmapsetId == beatmapsetId } ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        updateStatus(beatmapsetId, DownloadStatus.QUEUED, task.progress)
        scope.launch { pending.send(beatmapsetId) }
    }

    /** Removes the download entirely and deletes any partial file. */
    fun cancel(beatmapsetId: Long) {
        runningJobs[beatmapsetId]?.cancel()
        tempFileFor(beatmapsetId).delete()
        _downloads.value = _downloads.value.filterNot { it.beatmapsetId == beatmapsetId }
        refreshQueuedIds()
        notificationManager.cancel(notificationIdFor(beatmapsetId))
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
        val notificationId = notificationIdFor(beatmapsetId)
        val tempFile = tempFileFor(beatmapsetId)
        try {
            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
            updateStatus(beatmapsetId, DownloadStatus.DOWNLOADING, 0)
            showProgressNotification(
                notificationId, task.title, task.artist, 0,
                indeterminate = existingBytes == 0L
            )

            val success = NerinyanApi.downloadBeatmapset(
                beatmapsetId, tempFile,
                onProgress = { pct ->
                    val current = _downloads.value.find { it.beatmapsetId == beatmapsetId }
                    updateStatus(beatmapsetId, DownloadStatus.DOWNLOADING, pct ?: current?.progress ?: 0)
                    showProgressNotification(
                        notificationId, task.title, task.artist, pct ?: 0,
                        indeterminate = pct == null
                    )
                },
                resumeFromBytes = existingBytes,
                noVideo = noVideo
            )

            if (success) {
                val song = OszImporter.importOszFromFile(tempFile, "${task.artist} - ${task.title}")
                tempFile.delete()
                notificationManager.cancel(notificationId)
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
                    // The import reported failure but the beatmap already exists in the
                    // library (e.g. a duplicate import), so there's nothing to fix.
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
                // Keep the partial file so the user can retry or resume later.
                if (isAlreadyImported(task.artist, task.title)) {
                    updateStatus(beatmapsetId, DownloadStatus.COMPLETED, 100)
                    refreshQueuedIds()
                    scope.launch {
                        delay(2_500L)
                        removeTask(beatmapsetId)
                    }
                } else {
                    updateStatus(beatmapsetId, DownloadStatus.FAILED)
                    notificationManager.cancel(notificationId)
                }
            }
        } catch (e: CancellationException) {
            // Paused or cancelled — status already updated by the caller.
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "Download failed for beatmapset $beatmapsetId", e)
            updateStatus(beatmapsetId, DownloadStatus.FAILED)
            notificationManager.cancel(notificationId)
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

    private fun tempFileFor(beatmapsetId: Long) = File(context.cacheDir, "download_$beatmapsetId.osz")
    private fun notificationIdFor(beatmapsetId: Long) = NOTIFICATION_ID_BASE + (beatmapsetId % 100000).toInt()

    /** Whether a folder for this artist/title already exists in the library with an audio file. */
    private fun isAlreadyImported(artist: String, title: String): Boolean {
        val baseName = OszImporter.sanitizeFolderName("$artist - $title")
        val folder = File(SongScanner.getOsuRadioDir(), "Songs/$baseName")
        if (!folder.isDirectory) return false
        return folder.listFiles()?.any {
            val ext = it.extension.lowercase()
            ext == "mp3" || ext == "ogg"
        } == true
    }

    private fun showProgressNotification(
        notificationId: Int,
        title: String,
        artist: String,
        progress: Int,
        indeterminate: Boolean
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading $title")
            .setContentText(if (indeterminate) artist else "${artist} • $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Beatmap downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progress for beatmap downloads" }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun release() {
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "osu_radio_downloads"
        private const val NOTIFICATION_ID_BASE = 90000
        private const val WORKER_COUNT = 3
    }
}
