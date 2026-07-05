package com.osuradio.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.osuradio.app.data.NerinyanBeatmapSet
import com.osuradio.app.data.Song
import com.osuradio.app.network.NerinyanApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DownloadTask(
    val beatmapsetId: Long,
    val title: String,
    val artist: String,
    val progress: Int = 0
)

class DownloadManager(
    private val context: Context,
    private val onSongImported: (Song) -> Unit
) {
    private val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val queue = Channel<NerinyanBeatmapSet>(Channel.UNLIMITED)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _activeDownloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadTask>> = _activeDownloads.asStateFlow()

    private val _queuedIds = MutableStateFlow<Set<Long>>(emptySet())
    val queuedIds: StateFlow<Set<Long>> = _queuedIds.asStateFlow()

    init {
        createChannel()
        repeat(WORKER_COUNT) { startWorker() }
    }

    fun enqueue(set: NerinyanBeatmapSet) {
        if (_queuedIds.value.contains(set.id)) return
        _queuedIds.value = _queuedIds.value + set.id
        scope.launch { queue.send(set) }
    }

    private fun startWorker() {
        scope.launch {
            for (set in queue) {
                try {
                    downloadSet(set)
                } catch (e: Exception) {
                    Logger.error(TAG, "Worker failed for ${set.title}", e)
                }
            }
        }
    }

    private suspend fun downloadSet(set: NerinyanBeatmapSet) {
        val notificationId = NOTIFICATION_ID_BASE + (set.id % 100000).toInt()
        updateTask(set.id, set.title, set.artist, 0)
        showProgressNotification(notificationId, set, 0)

        val tempFile = File(context.cacheDir, "download_${set.id}.osz")
        val success = NerinyanApi.downloadBeatmapset(set.id, tempFile) { pct ->
            updateTask(set.id, set.title, set.artist, pct)
            showProgressNotification(notificationId, set, pct)
        }

        if (success) {
            val song = OszImporter.importOszFromFile(tempFile, "${set.artist} - ${set.title}")
            tempFile.delete()
            notificationManager.cancel(notificationId)
            if (song != null) onSongImported(song)
        } else {
            tempFile.delete()
            notificationManager.cancel(notificationId)
        }

        _activeDownloads.value = _activeDownloads.value.filterNot { it.beatmapsetId == set.id }
        _queuedIds.value = _queuedIds.value - set.id
    }

    private fun updateTask(id: Long, title: String, artist: String, progress: Int) {
        val current = _activeDownloads.value.toMutableList()
        val idx = current.indexOfFirst { it.beatmapsetId == id }
        val task = DownloadTask(id, title, artist, progress)
        if (idx >= 0) current[idx] = task else current.add(task)
        _activeDownloads.value = current
    }

    private fun showProgressNotification(notificationId: Int, set: NerinyanBeatmapSet, progress: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading ${set.title}")
            .setContentText("${set.artist} • $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
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
