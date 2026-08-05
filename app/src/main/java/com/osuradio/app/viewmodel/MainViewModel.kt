package com.osuradio.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.osuradio.app.BuildConfig
import com.osuradio.app.data.AppSettings
import com.osuradio.app.data.EqualizerSettings
import com.osuradio.app.data.ModSettings
import com.osuradio.app.data.NerinyanBeatmapSet
import com.osuradio.app.data.Playlist
import com.osuradio.app.data.RepeatMode
import com.osuradio.app.data.Song
import com.osuradio.app.data.SongMod
import com.osuradio.app.network.NerinyanApi
import com.osuradio.app.service.MusicService
import com.osuradio.app.utils.ConfigManager
import com.osuradio.app.utils.DownloadManager
import com.osuradio.app.utils.DownloadTask
import com.osuradio.app.utils.Logger
import com.osuradio.app.utils.OszImporter
import com.osuradio.app.utils.SongScanner
import com.osuradio.app.utils.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class UpdatePrompt(val latestVersion: String, val apkUrl: String)

data class ReleaseNotesPrompt(val version: String, val notes: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    private val _songs            = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentSong      = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying        = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _settings         = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _playlists        = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _modSettings      = MutableStateFlow(ModSettings())
    val modSettings: StateFlow<ModSettings> = _modSettings.asStateFlow()

    private val _isLoading        = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage   = MutableStateFlow("Initializing...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _searchQuery      = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _queue            = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _updatePrompt     = MutableStateFlow<UpdatePrompt?>(null)
    val updatePrompt: StateFlow<UpdatePrompt?> = _updatePrompt.asStateFlow()

    private val _updateDownloading = MutableStateFlow(false)
    val updateDownloading: StateFlow<Boolean> = _updateDownloading.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(0)
    val updateDownloadProgress: StateFlow<Int> = _updateDownloadProgress.asStateFlow()

    private val _releaseNotes = MutableStateFlow<ReleaseNotesPrompt?>(null)
    val releaseNotes: StateFlow<ReleaseNotesPrompt?> = _releaseNotes.asStateFlow()

    private val _updateFailed     = MutableStateFlow(false)
    val updateFailed: StateFlow<Boolean> = _updateFailed.asStateFlow()

    private val _sleepTimerEndAtMs = MutableStateFlow<Long?>(null)
    val sleepTimerEndAtMs: StateFlow<Long?> = _sleepTimerEndAtMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    private val _isSyncing        = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val downloadManager: DownloadManager by lazy {
        DownloadManager(getApplication()) { song ->
            // DownloadManager runs its workers on Dispatchers.IO, but merging a song pushes
            // the queue to ExoPlayer, which must only be touched on the main thread.
            viewModelScope.launch(Dispatchers.Main) { mergeSong(song) }
        }
    }

    private val _downloadSearchQuery  = MutableStateFlow("")
    val downloadSearchQuery: StateFlow<String> = _downloadSearchQuery.asStateFlow()

    private val _downloadSortOption   = MutableStateFlow(NerinyanApi.SortOption.DEFAULT)
    val downloadSortOption: StateFlow<NerinyanApi.SortOption> = _downloadSortOption.asStateFlow()

    private val _downloadStatusOption = MutableStateFlow(NerinyanApi.StatusOption.ALL)
    val downloadStatusOption: StateFlow<NerinyanApi.StatusOption> = _downloadStatusOption.asStateFlow()

    private val _downloadNoVideo = MutableStateFlow(true)
    val downloadNoVideo: StateFlow<Boolean> = _downloadNoVideo.asStateFlow()

    private val _downloadResults  = MutableStateFlow<List<NerinyanBeatmapSet>>(emptyList())
    val downloadResults: StateFlow<List<NerinyanBeatmapSet>> = _downloadResults.asStateFlow()

    private val _downloadLoading  = MutableStateFlow(false)
    val downloadLoading: StateFlow<Boolean> = _downloadLoading.asStateFlow()

    val downloadTasks: StateFlow<List<DownloadTask>> = downloadManager.downloads
    val queuedDownloadIds: StateFlow<Set<Long>> = downloadManager.queuedIds

    private var downloadPage       = 0
    private var downloadHasMore    = true
    private var downloadSearchJob: Job? = null

    private var musicService: MusicService? = null
    private var serviceBound = false
    private var positionUpdaterJob: Job? = null

    // ── Player listener attached directly to ExoPlayer ────────────────────────
    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) _isPlaying.value = false
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) {
                val song = _queue.value.find { it.id == mediaItem.mediaId }
                if (song != null) _currentSong.value = song
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as? MusicService.LocalBinder)?.getService()
            serviceBound = true
            val service = musicService ?: return

            // Wire all settings into the freshly-connected service
            service.setTransition(_settings.value.audioTransition)
            service.setShuffleMode(_settings.value.shuffle)
            service.setRepeatMode(_settings.value.repeat)
            service.applyEqualizerSettings(_settings.value.equalizerSettings ?: EqualizerSettings())
            service.applyLoudnessSettings(
                _settings.value.loudnessNormalization,
                _settings.value.loudnessGainDb
            )

            // Keep the in-app repeat icon in sync with the media-session Loop button.
            service.onRepeatModeChanged = { repeatMode ->
                _settings.value = _settings.value.copy(
                    repeat = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> RepeatMode.NONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.ONE
                    }
                )
            }

            // Attach listener and push current queue
            service.getPlayer().addListener(playbackListener)
            pushQueueToPlayer()
            startPositionUpdater()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService?.getPlayer()?.removeListener(playbackListener)
            musicService = null
            serviceBound = false
        }
    }

    override fun onCleared() {
        musicService?.getPlayer()?.removeListener(playbackListener)
        positionUpdaterJob?.cancel()
        sleepTimerJob?.cancel()
        downloadManager.release()
        super.onCleared()
    }

    // ── Service binding ───────────────────────────────────────────────────────

    fun bindMusicService(context: Context) {
        val intent = Intent(context, MusicService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        context.startService(intent)
    }

    fun unbindMusicService(context: Context) {
        if (serviceBound) {
            musicService?.getPlayer()?.removeListener(playbackListener)
            context.unbindService(serviceConnection)
            serviceBound = false
        }
        positionUpdaterJob?.cancel()
        musicService = null
    }

    // ── Queue helpers ─────────────────────────────────────────────────────────

    /** Sends the current _queue to the ExoPlayer inside the service. */
    private fun pushQueueToPlayer() {
        val service = musicService ?: return
        service.setQueue(_queue.value)
    }

    private fun setQueueAndPush(songs: List<Song>) {
        _queue.value = songs
        pushQueueToPlayer()
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    fun initialize(context: Context) {
        if (!_isLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val osuRadioDir = SongScanner.getOsuRadioDir()
                Logger.init(osuRadioDir)
                ConfigManager.init(osuRadioDir)

                withContext(Dispatchers.Main) {
                    _settings.value = ConfigManager.getSettings()
                    _playlists.value = ConfigManager.getPlaylists()
                    _loadingMessage.value = "Loading library..."
                }

                val existingSongs = SongScanner.loadAlreadyScannedSongs(context)
                val newSongs: List<Song>
                if (_settings.value.syncWithOsuDroid) {
                    withContext(Dispatchers.Main) { _loadingMessage.value = "Scanning osu!droid..." }
                    newSongs = SongScanner.scanAndCopySongs(context) { msg ->
                        viewModelScope.launch(Dispatchers.Main) { _loadingMessage.value = msg }
                    }
                } else {
                    newSongs = emptyList()
                }

                val allSongsMap = mutableMapOf<String, Song>()
                existingSongs.forEach { allSongsMap[it.id] = it }
                newSongs.forEach { allSongsMap[it.id] = it }
                val allSongs = allSongsMap.values.toList().sortedBy { it.artist }

                withContext(Dispatchers.Main) {
                    _songs.value = allSongs
                    setQueueAndPush(allSongs)
                    _isLoading.value = false
                    checkAppUpdated(context)
                }
                if (_settings.value.autoCheckUpdates) checkForUpdate(context)
            } catch (e: Exception) {
                Logger.error(TAG, "Initialization failed", e)
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    fun syncSongs(context: Context) {
        if (_isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isSyncing.value = true }
            try {
                val existingSongs = SongScanner.loadAlreadyScannedSongs(context)
                val newSongs      = SongScanner.scanAndCopySongs(context)
                val allSongsMap   = mutableMapOf<String, Song>()
                existingSongs.forEach { allSongsMap[it.id] = it }
                newSongs.forEach { allSongsMap[it.id] = it }
                val allSongs = allSongsMap.values.toList().sortedBy { it.artist }
                withContext(Dispatchers.Main) {
                    _songs.value = allSongs
                    setQueueAndPush(allSongs)
                }
            } catch (e: Exception) {
                Logger.error(TAG, "Sync failed", e)
            } finally {
                withContext(Dispatchers.Main) { _isSyncing.value = false }
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try { File(song.folderPath).takeIf { it.exists() }?.deleteRecursively() }
            catch (e: Exception) { Logger.error(TAG, "Failed to delete: ${song.folderPath}", e) }
            withContext(Dispatchers.Main) {
                _songs.value = _songs.value.filter { it.id != song.id }
                setQueueAndPush(_queue.value.filter { it.id != song.id })
                _playlists.value = _playlists.value.map { playlist ->
                    if (playlist.songIds.contains(song.id)) {
                        playlist.copy(songIds = playlist.songIds.filter { it != song.id })
                            .also { ConfigManager.updatePlaylist(it) }
                    } else playlist
                }
            }
        }
    }

    // ── Update checks ─────────────────────────────────────────────────────────

    /**
     * Detects whether the app was updated since it last ran and, if so, shows the
     * release notes for the current version. Compares the previously stored run
     * version against [BuildConfig.APP_VERSION]; a first-ever install (no stored
     * version) does not trigger the dialog. Also consumes a "pending success"
     * marker written right before an in-app install.
     */
    private fun checkAppUpdated(context: Context) {
        val prefs = context.getSharedPreferences("osu_radio_update", Context.MODE_PRIVATE)
        val lastRun = prefs.getString("last_run_version", null)
        val current = BuildConfig.APP_VERSION
        val hadPendingInstall = prefs.contains("pending_success_version")
        prefs.edit()
            .putString("last_run_version", current)
            .remove("pending_success_version")
            .apply()

        val wasUpdated = (lastRun != null && lastRun != current) || hadPendingInstall
        if (!wasUpdated) return
        viewModelScope.launch {
            val notes = UpdateChecker.fetchReleaseNotes(current)
            _releaseNotes.value = ReleaseNotesPrompt(
                version = current,
                notes = notes ?: "No release notes are available for this version."
            )
        }
    }

    fun dismissReleaseNotes() { _releaseNotes.value = null }

    fun checkForUpdate(context: Context) {
        viewModelScope.launch {
            try {
                val release = UpdateChecker.fetchLatestRelease() ?: return@launch
                val current     = BuildConfig.APP_VERSION
                val lastDismiss = _settings.value.lastDismissedVersion
                if (UpdateChecker.isNewerVersion(release.tagName, current) && release.tagName != lastDismiss) {
                    _updatePrompt.value = UpdatePrompt(release.tagName, release.apkDownloadUrl)
                }
            } catch (e: Exception) { Logger.error(TAG, "Update check failed", e) }
        }
    }

    fun dismissUpdate() {
        val prompt = _updatePrompt.value ?: return
        updateSettings(_settings.value.copy(lastDismissedVersion = prompt.latestVersion))
        _updatePrompt.value = null
    }

    fun startDownloadAndInstall(context: Context) {
        val prompt = _updatePrompt.value ?: return
        _updateDownloading.value = true
        _updatePrompt.value = null
        viewModelScope.launch {
            val file = UpdateChecker.downloadApk(context, prompt.apkUrl) { _updateDownloadProgress.value = it }
            _updateDownloading.value = false
            _updateDownloadProgress.value = 0
            if (file != null) {
                context.getSharedPreferences("osu_radio_update", Context.MODE_PRIVATE)
                    .edit().putString("pending_success_version", prompt.latestVersion).apply()
                UpdateChecker.installApk(context, file)
            } else _updateFailed.value = true
        }
    }

    fun dismissUpdateFailed() { _updateFailed.value = false }

    // ── Sleep timer ───────────────────────────────────────────────────────────

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerEndAtMs.value = System.currentTimeMillis() + minutes * 60_000L
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            pausePlayback()
            _sleepTimerEndAtMs.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerEndAtMs.value = null
    }

    fun pausePlayback() { musicService?.pause(); _isPlaying.value = false }

    // ── Download ──────────────────────────────────────────────────────────────

    fun setDownloadSearchQuery(query: String) {
        _downloadSearchQuery.value = query
        downloadSearchJob?.cancel()
        downloadSearchJob = viewModelScope.launch { delay(400); refreshDownloadSearch() }
    }

    fun setDownloadSortOption(option: NerinyanApi.SortOption) {
        _downloadSortOption.value = option; refreshDownloadSearch()
    }

    fun setDownloadStatusOption(option: NerinyanApi.StatusOption) {
        _downloadStatusOption.value = option; refreshDownloadSearch()
    }

    fun setDownloadNoVideo(noVideo: Boolean) {
        _downloadNoVideo.value = noVideo
        downloadManager.noVideo = noVideo
    }

    fun refreshDownloadSearch() {
        downloadPage = 0; downloadHasMore = true
        val query = _downloadSearchQuery.value
        val sort  = _downloadSortOption.value
        val status = _downloadStatusOption.value
        viewModelScope.launch {
            _downloadLoading.value = true
            _downloadResults.value = emptyList()
            val results = NerinyanApi.search(query = query, page = downloadPage, sort = sort, status = status)
            _downloadResults.value = results
            downloadHasMore = results.isNotEmpty()
            _downloadLoading.value = false
        }
    }

    fun loadMoreDownloadResults() {
        if (!downloadHasMore || _downloadLoading.value) return
        viewModelScope.launch {
            _downloadLoading.value = true
            downloadPage += 1
            val results = NerinyanApi.search(
                query = _downloadSearchQuery.value, page = downloadPage,
                sort = _downloadSortOption.value, status = _downloadStatusOption.value
            )
            if (results.isEmpty()) downloadHasMore = false
            else _downloadResults.value = _downloadResults.value + results
            _downloadLoading.value = false
        }
    }

    fun downloadBeatmapset(set: NerinyanBeatmapSet) = downloadManager.enqueue(set)

    fun pauseDownload(beatmapsetId: Long) = downloadManager.pause(beatmapsetId)

    fun resumeDownload(beatmapsetId: Long) = downloadManager.resume(beatmapsetId)

    fun cancelDownload(beatmapsetId: Long) = downloadManager.cancel(beatmapsetId)

    // ── Position updater ──────────────────────────────────────────────────────

    private fun startPositionUpdater() {
        positionUpdaterJob?.cancel()
        positionUpdaterJob = viewModelScope.launch {
            while (true) {
                val player = musicService?.getPlayer()
                if (player != null) _currentPositionMs.value = player.currentPosition
                kotlinx.coroutines.delay(500)
            }
        }
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    fun playSong(song: Song, source: List<Song>? = null) {
        val service = musicService ?: run { _currentSong.value = song; return }
        val songs = source ?: _queue.value
        val idx = songs.indexOfFirst { it.id == song.id }
        if (idx < 0) return
        if (songs !== _queue.value) setQueueAndPush(songs)
        _currentSong.value = song
        _isPlaying.value   = true
        service.setTransition(_settings.value.audioTransition)
        service.playAtIndex(idx)
    }

    fun pauseResume() { musicService?.pauseResume() }

    fun seekTo(positionMs: Long) {
        musicService?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun skipToNext() {
        musicService?.getPlayer()?.seekToNextMediaItem()
    }

    fun skipToPrev() {
        val player = musicService?.getPlayer() ?: return
        if (player.currentPosition > 3_000L) player.seekTo(0L)
        else player.seekToPreviousMediaItem()
    }

    fun applyMod(mod: SongMod, customSpeed: Float = 1.0f) {
        val newModSettings = ModSettings(activeMod = mod, customSpeed = customSpeed)
        _modSettings.value = newModSettings
        musicService?.applyMod(newModSettings, _currentPositionMs.value)
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun updateSettings(settings: AppSettings) {
        _settings.value = settings
        ConfigManager.saveSettings(settings)
        val service = musicService ?: return
        service.setTransition(settings.audioTransition)
        service.setShuffleMode(settings.shuffle)
        service.setRepeatMode(settings.repeat)
        service.applyEqualizerSettings(settings.equalizerSettings ?: EqualizerSettings())
        service.applyLoudnessSettings(settings.loudnessNormalization, settings.loudnessGainDb)
    }

    fun toggleShuffle() = updateSettings(_settings.value.copy(shuffle = !_settings.value.shuffle))

    fun toggleRepeat() {
        val next = when (_settings.value.repeat) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL  -> RepeatMode.ONE
            RepeatMode.ONE  -> RepeatMode.NONE
        }
        updateSettings(_settings.value.copy(repeat = next))
    }

    // ── Search / filter ───────────────────────────────────────────────────────

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun getFilteredSongs(): List<Song> {
        val q = _searchQuery.value.lowercase()
        return if (q.isBlank()) _songs.value
        else _songs.value.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }

    // ── Playlists ─────────────────────────────────────────────────────────────

    fun createPlaylist(name: String) {
        ConfigManager.addPlaylist(Playlist(id = UUID.randomUUID().toString(), name = name))
        _playlists.value = ConfigManager.getPlaylists()
    }

    fun deletePlaylist(playlistId: String) {
        ConfigManager.removePlaylist(playlistId)
        _playlists.value = ConfigManager.getPlaylists()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(name = newName).also { ConfigManager.updatePlaylist(it) }
            else pl
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId && !pl.songIds.contains(songId))
                pl.copy(songIds = pl.songIds + songId).also { ConfigManager.updatePlaylist(it) }
            else pl
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId)
                pl.copy(songIds = pl.songIds.filter { it != songId }).also { ConfigManager.updatePlaylist(it) }
            else pl
        }
    }

    fun toggleSongInPlaylist(playlistId: String, songId: String) {
        val pl = _playlists.value.find { it.id == playlistId } ?: return
        if (pl.songIds.contains(songId)) removeSongFromPlaylist(playlistId, songId)
        else addSongToPlaylist(playlistId, songId)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        var songs = _songs.value.filter { playlist.songIds.contains(it.id) }
        if (songs.isEmpty()) return
        if (shuffle) songs = songs.shuffled()
        setQueueAndPush(songs)
        playSong(songs.first())
    }

    fun playPlaylistFrom(playlist: Playlist, song: Song) {
        val songs = _songs.value.filter { playlist.songIds.contains(it.id) }
        if (songs.isEmpty()) return
        setQueueAndPush(songs)
        playSong(song)
    }

    fun getSongsForPlaylist(playlist: Playlist): List<Song> =
        _songs.value.filter { playlist.songIds.contains(it.id) }

    // ── Import ────────────────────────────────────────────────────────────────

    fun importOszFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            OszImporter.importOsz(context, uri)?.let { withContext(Dispatchers.Main) { mergeSong(it) } }
        }
    }

    fun importZipFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = OszImporter.importFullZip(context, uri)
            if (imported.isNotEmpty()) withContext(Dispatchers.Main) { imported.forEach { mergeSong(it) } }
        }
    }

    private fun mergeSong(song: Song) {
        if (_songs.value.none { it.id == song.id }) {
            _songs.value = (_songs.value + song).sortedBy { it.artist }
            setQueueAndPush(_songs.value)
        }
    }
}
