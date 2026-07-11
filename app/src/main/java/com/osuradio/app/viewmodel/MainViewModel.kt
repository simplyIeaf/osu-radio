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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.osuradio.app.BuildConfig
import com.osuradio.app.data.AppSettings
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

data class UpdatePrompt(
    val latestVersion: String,
    val apkUrl: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _modSettings = MutableStateFlow(ModSettings())
    val modSettings: StateFlow<ModSettings> = _modSettings.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow("Initializing...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _updatePrompt = MutableStateFlow<UpdatePrompt?>(null)
    val updatePrompt: StateFlow<UpdatePrompt?> = _updatePrompt.asStateFlow()

    private val _updateDownloading = MutableStateFlow(false)
    val updateDownloading: StateFlow<Boolean> = _updateDownloading.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(0)
    val updateDownloadProgress: StateFlow<Int> = _updateDownloadProgress.asStateFlow()

    private val _successUpdateVersion = MutableStateFlow<String?>(null)
    val successUpdateVersion: StateFlow<String?> = _successUpdateVersion.asStateFlow()

    private val _updateFailed = MutableStateFlow(false)
    val updateFailed: StateFlow<Boolean> = _updateFailed.asStateFlow()

    private val _sleepTimerEndAtMs = MutableStateFlow<Long?>(null)
    val sleepTimerEndAtMs: StateFlow<Long?> = _sleepTimerEndAtMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    private val downloadManager: DownloadManager by lazy {
        DownloadManager(getApplication()) { song -> mergeSong(song) }
    }

    private val _downloadSearchQuery = MutableStateFlow("")
    val downloadSearchQuery: StateFlow<String> = _downloadSearchQuery.asStateFlow()

    private val _downloadSortOption = MutableStateFlow(NerinyanApi.SortOption.PLAY_COUNT)
    val downloadSortOption: StateFlow<NerinyanApi.SortOption> = _downloadSortOption.asStateFlow()

    private val _downloadStatusOption = MutableStateFlow(NerinyanApi.StatusOption.RANKED)
    val downloadStatusOption: StateFlow<NerinyanApi.StatusOption> = _downloadStatusOption.asStateFlow()

    private val _downloadResults = MutableStateFlow<List<NerinyanBeatmapSet>>(emptyList())
    val downloadResults: StateFlow<List<NerinyanBeatmapSet>> = _downloadResults.asStateFlow()

    private val _downloadLoading = MutableStateFlow(false)
    val downloadLoading: StateFlow<Boolean> = _downloadLoading.asStateFlow()

    val activeDownloads: StateFlow<List<DownloadTask>> = downloadManager.activeDownloads
    val queuedDownloadIds: StateFlow<Set<Long>> = downloadManager.queuedIds

    private var downloadPage = 0
    private var downloadHasMore = true
    private var downloadSearchJob: Job? = null

    private var musicService: MusicService? = null
    private var serviceBound = false
    private var listenerAttachedPlayer: ExoPlayer? = null
    private var positionUpdaterJob: Job? = null

    private val playbackListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onSongEnded()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? MusicService.LocalBinder
            musicService = localBinder?.getService()
            serviceBound = true
            attachPlayerListener()
            startPositionUpdater()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }

    private fun attachPlayerListener() {
        val player = musicService?.getPlayer() ?: return
        if (listenerAttachedPlayer === player) return
        listenerAttachedPlayer?.removeListener(playbackListener)
        player.addListener(playbackListener)
        listenerAttachedPlayer = player
    }

    override fun onCleared() {
        listenerAttachedPlayer?.removeListener(playbackListener)
        listenerAttachedPlayer = null
        positionUpdaterJob?.cancel()
        sleepTimerJob?.cancel()
        downloadManager.release()
        super.onCleared()
    }

    fun bindMusicService(context: Context) {
        val intent = Intent(context, MusicService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        context.startService(intent)
    }

    fun unbindMusicService(context: Context) {
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
        positionUpdaterJob?.cancel()
        listenerAttachedPlayer?.removeListener(playbackListener)
        listenerAttachedPlayer = null
        musicService = null
    }

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

                withContext(Dispatchers.Main) {
                    _loadingMessage.value = "Scanning osu!droid..."
                }

                val newSongs = SongScanner.scanAndCopySongs(context) { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _loadingMessage.value = msg
                    }
                }

                val allSongsMap = mutableMapOf<String, Song>()
                existingSongs.forEach { allSongsMap[it.id] = it }
                newSongs.forEach { allSongsMap[it.id] = it }
                val allSongs = allSongsMap.values.toList().sortedBy { it.artist }

                withContext(Dispatchers.Main) {
                    _songs.value = allSongs
                    _queue.value = allSongs
                    _isLoading.value = false
                }

                withContext(Dispatchers.Main) {
                    checkSuccessfulUpdate(context)
                }

                if (_settings.value.autoCheckUpdates) {
                    checkForUpdate(context)
                }
            } catch (e: Exception) {
                Logger.error(TAG, "Initialization failed", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun checkSuccessfulUpdate(context: Context) {
        val prefs = context.getSharedPreferences("osu_radio_update", Context.MODE_PRIVATE)
        val pendingVersion = prefs.getString("pending_success_version", null)
        if (!pendingVersion.isNullOrEmpty()) {
            _successUpdateVersion.value = pendingVersion
            prefs.edit().remove("pending_success_version").apply()
        }
    }

    fun dismissSuccessUpdate() {
        _successUpdateVersion.value = null
    }

    fun checkForUpdate(context: Context) {
        viewModelScope.launch {
            try {
                val release = UpdateChecker.fetchLatestRelease() ?: return@launch
                val currentVersion = BuildConfig.APP_VERSION
                val lastDismissed = _settings.value.lastDismissedVersion
                if (UpdateChecker.isNewerVersion(release.tagName, currentVersion) &&
                    release.tagName != lastDismissed
                ) {
                    _updatePrompt.value = UpdatePrompt(
                        latestVersion = release.tagName,
                        apkUrl = release.apkDownloadUrl
                    )
                }
            } catch (e: Exception) {
                Logger.error(TAG, "Update check failed", e)
            }
        }
    }

    fun dismissUpdate() {
        val prompt = _updatePrompt.value ?: return
        val newSettings = _settings.value.copy(lastDismissedVersion = prompt.latestVersion)
        updateSettings(newSettings)
        _updatePrompt.value = null
    }

    fun startDownloadAndInstall(context: Context) {
        val prompt = _updatePrompt.value ?: return
        _updateDownloading.value = true
        _updatePrompt.value = null
        viewModelScope.launch {
            val file = UpdateChecker.downloadApk(context, prompt.apkUrl) { pct ->
                _updateDownloadProgress.value = pct
            }
            _updateDownloading.value = false
            _updateDownloadProgress.value = 0
            if (file != null) {
                val prefs = context.getSharedPreferences("osu_radio_update", Context.MODE_PRIVATE)
                prefs.edit().putString("pending_success_version", prompt.latestVersion).apply()
                UpdateChecker.installApk(context, file)
            } else {
                _updateFailed.value = true
            }
        }
    }

    fun dismissUpdateFailed() {
        _updateFailed.value = false
    }

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
        sleepTimerJob = null
        _sleepTimerEndAtMs.value = null
    }

    fun pausePlayback() {
        musicService?.pause()
        _isPlaying.value = false
    }

    fun setDownloadSearchQuery(query: String) {
        _downloadSearchQuery.value = query
        downloadSearchJob?.cancel()
        downloadSearchJob = viewModelScope.launch {
            delay(400)
            refreshDownloadSearch()
        }
    }

    fun setDownloadSortOption(option: NerinyanApi.SortOption) {
        _downloadSortOption.value = option
        refreshDownloadSearch()
    }

    fun setDownloadStatusOption(option: NerinyanApi.StatusOption) {
        _downloadStatusOption.value = option
        refreshDownloadSearch()
    }

    fun refreshDownloadSearch() {
        downloadPage = 0
        downloadHasMore = true
        viewModelScope.launch {
            _downloadLoading.value = true
            val results = NerinyanApi.search(
                query = _downloadSearchQuery.value,
                page = downloadPage,
                sort = _downloadSortOption.value,
{