package com.osuradio.app.viewmodel

import com.osuradio.app.BuildConfig
import com.osuradio.app.audio.DesktopPlayer
import com.osuradio.app.data.AppSettings
import com.osuradio.app.data.AudioTransition
import com.osuradio.app.data.ModSettings
import com.osuradio.app.data.NerinyanBeatmapSet
import com.osuradio.app.data.Playlist
import com.osuradio.app.data.RepeatMode
import com.osuradio.app.data.Song
import com.osuradio.app.data.SongMod
import com.osuradio.app.network.NerinyanApi
import com.osuradio.app.utils.AppPaths
import com.osuradio.app.utils.ConfigManager
import com.osuradio.app.utils.DownloadManager
import com.osuradio.app.utils.DownloadTask
import com.osuradio.app.utils.Logger
import com.osuradio.app.utils.OszImporter
import com.osuradio.app.utils.Prefs
import com.osuradio.app.utils.SongScanner
import com.osuradio.app.utils.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.random.Random

data class UpdatePrompt(val latestVersion: String, val appImageUrl: String)

data class ReleaseNotesPrompt(val version: String, val notes: String)

class MainViewModel {
    private val TAG = "MainViewModel"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    private val _updateDownloaded = MutableStateFlow<String?>(null)
    val updateDownloaded: StateFlow<String?> = _updateDownloaded.asStateFlow()

    private val _releaseNotes = MutableStateFlow<ReleaseNotesPrompt?>(null)
    val releaseNotes: StateFlow<ReleaseNotesPrompt?> = _releaseNotes.asStateFlow()

    private val _updateFailed = MutableStateFlow(false)
    val updateFailed: StateFlow<Boolean> = _updateFailed.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val downloadManager: DownloadManager by lazy {
        DownloadManager { song ->
            scope.launch { mergeSong(song) }
        }
    }

    private val _downloadSearchQuery = MutableStateFlow("")
    val downloadSearchQuery: StateFlow<String> = _downloadSearchQuery.asStateFlow()

    private val _downloadSortOption = MutableStateFlow(NerinyanApi.SortOption.DEFAULT)
    val downloadSortOption: StateFlow<NerinyanApi.SortOption> = _downloadSortOption.asStateFlow()

    private val _downloadStatusOption = MutableStateFlow(NerinyanApi.StatusOption.ALL)
    val downloadStatusOption: StateFlow<NerinyanApi.StatusOption> = _downloadStatusOption.asStateFlow()

    private val _downloadResults = MutableStateFlow<List<NerinyanBeatmapSet>>(emptyList())
    val downloadResults: StateFlow<List<NerinyanBeatmapSet>> = _downloadResults.asStateFlow()

    private val _downloadLoading = MutableStateFlow(false)
    val downloadLoading: StateFlow<Boolean> = _downloadLoading.asStateFlow()

    val downloadTasks: StateFlow<List<DownloadTask>> = downloadManager.downloads
    val queuedDownloadIds: StateFlow<Set<Long>> = downloadManager.queuedIds

    private var downloadPage = 0
    private var downloadHasMore = true
    private var downloadSearchJob: Job? = null

    private val player = DesktopPlayer(scope)
    private var positionUpdaterJob: Job? = null

    private var restoreSongId: String? = null
    private var restorePositionMs = 0L

    private var prefs: Prefs? = null

    init {
        player.setListener(object : DesktopPlayer.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) persistPlaybackState()
            }

            override fun onSongEnded() {
                _isPlaying.value = false
                handleQueueEnded()
            }
        })
        startPositionUpdater()
    }

    fun release() {
        persistPlaybackState()
        positionUpdaterJob?.cancel()
        downloadManager.release()
        player.release()
        scope.cancel()
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    fun initialize() {
        if (!_isLoading.value) return
        scope.launch(Dispatchers.IO) {
            try {
                val dataDir = AppPaths.dataDir()
                Logger.init(dataDir)
                ConfigManager.init(AppPaths.configDir())
                prefs = Prefs(AppPaths.prefsFile())

                _settings.value = ConfigManager.getSettings()
                _playlists.value = ConfigManager.getPlaylists()

                val existingSongs = SongScanner.loadAlreadyScannedSongs()
                val newSongs: List<Song>
                if (_settings.value.syncWithOsuDroid) {
                    newSongs = SongScanner.scanAndCopySongs()
                } else {
                    newSongs = emptyList()
                }

                val allSongsMap = mutableMapOf<String, Song>()
                existingSongs.forEach { allSongsMap[it.id] = it }
                newSongs.forEach { allSongsMap[it.id] = it }
                val allSongs = allSongsMap.values.toList().sortedBy { it.artist }

                _songs.value = allSongs
                _queue.value = allSongs

                val lastId = prefs?.getString("last_song_id") ?: ""
                if (lastId.isNotEmpty()) {
                    val lastSong = allSongs.find { it.id == lastId }
                    if (lastSong != null) {
                        restoreSongId = lastId
                        restorePositionMs = prefs?.getLong("last_position_ms", 0L) ?: 0L
                        _currentSong.value = lastSong
                    }
                }
                applyRestorePosition()
                _isLoading.value = false
                checkAppUpdated()
                if (_settings.value.autoCheckUpdates) checkForUpdate()
            } catch (e: Exception) {
                Logger.error(TAG, "Initialization failed", e)
                _isLoading.value = false
            }
        }
    }

    // ── Playback state persistence ────────────────────────────────────────────

    private fun persistSongId(id: String) {
        try {
            prefs?.putString("last_song_id", id)
            prefs?.putLong("last_position_ms", 0L)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to persist song id", e)
        }
    }

    private fun persistPlaybackState() {
        val song = _currentSong.value ?: return
        try {
            prefs?.putString("last_song_id", song.id)
            prefs?.putLong("last_position_ms", _currentPositionMs.value)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to persist playback state", e)
        }
    }

    private fun applyRestorePosition() {
        val id = restoreSongId ?: return
        if (_currentSong.value?.id != id) return
        val ms = restorePositionMs
        if (ms <= 0L) return
        player.seekTo(ms)
        _currentPositionMs.value = ms
        restoreSongId = null
        restorePositionMs = 0L
    }

    // ── Position updater ──────────────────────────────────────────────────────

    private fun startPositionUpdater() {
        positionUpdaterJob?.cancel()
        positionUpdaterJob = scope.launch {
            var tick = 0
            while (true) {
                if (player.isLoaded()) {
                    _currentPositionMs.value = player.currentPositionMs()
                }
                tick++
                if (tick % 10 == 0 && _isPlaying.value) persistPlaybackState()
                delay(500)
            }
        }
    }

    // ── Queue helpers ─────────────────────────────────────────────────────────

    private fun currentIndex(): Int = _queue.value.indexOfFirst { it.id == _currentSong.value?.id }

    private fun handleQueueEnded() {
        if (_settings.value.repeat == RepeatMode.ONE) {
            val i = currentIndex()
            if (i >= 0) playQueueIndex(i)
            return
        }
        val songs = _queue.value
        if (songs.isEmpty()) return
        when (_settings.value.repeat) {
            RepeatMode.ALL -> {
                val i = currentIndex()
                playQueueIndex((i + 1) % songs.size)
            }
            else -> { // NONE — never stop: follow the queue order; only reshuffle at the end.
                val i = currentIndex()
                if (i >= 0 && i + 1 < songs.size) {
                    playQueueIndex(i + 1)
                } else {
                    if (_settings.value.shuffle) _queue.value = songs.shuffled()
                    _isPlaying.value = true
                    playQueueIndex(0)
                }
            }
        }
    }

    // ── Sync / library ────────────────────────────────────────────────────────

    fun syncSongs() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        scope.launch(Dispatchers.IO) {
            try {
                val existingSongs = SongScanner.loadAlreadyScannedSongs()
                val newSongs = SongScanner.scanAndCopySongs()
                val allSongsMap = mutableMapOf<String, Song>()
                existingSongs.forEach { allSongsMap[it.id] = it }
                newSongs.forEach { allSongsMap[it.id] = it }
                val allSongs = allSongsMap.values.toList().sortedBy { it.artist }
                val oldLibrary = _songs.value
                _songs.value = allSongs
                updateQueueAfterLibraryChange(oldLibrary, allSongs)
            } catch (e: Exception) {
                Logger.error(TAG, "Sync failed", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun deleteSong(song: Song) {
        scope.launch(Dispatchers.IO) {
            try { File(song.folderPath).takeIf { it.exists() }?.deleteRecursively() }
            catch (e: Exception) { Logger.error(TAG, "Failed to delete: ${song.folderPath}", e) }
            _songs.value = _songs.value.filter { it.id != song.id }
            _queue.value = _queue.value.filter { it.id != song.id }
            _playlists.value = _playlists.value.map { playlist ->
                if (playlist.songIds.contains(song.id)) {
                    playlist.copy(songIds = playlist.songIds.filter { it != song.id })
                        .also { ConfigManager.updatePlaylist(it) }
                } else playlist
            }
        }
    }

    // ── Update checks ─────────────────────────────────────────────────────────

    private fun checkAppUpdated() {
        val p = prefs ?: return
        val lastRun = p.getString("last_run_version")
        val current = BuildConfig.APP_VERSION
        p.putString("last_run_version", current)

        val wasUpdated = lastRun.isNotEmpty() && lastRun != current
        if (!wasUpdated) return
        scope.launch {
            val notes = UpdateChecker.fetchReleaseNotes(current)
            _releaseNotes.value = ReleaseNotesPrompt(
                version = current,
                notes = notes ?: "No release notes are available for this version."
            )
        }
    }

    fun dismissReleaseNotes() { _releaseNotes.value = null }

    fun checkForUpdate() {
        scope.launch {
            try {
                val release = UpdateChecker.fetchLatestRelease() ?: return@launch
                val current = BuildConfig.APP_VERSION
                val lastDismiss = _settings.value.lastDismissedVersion
                if (UpdateChecker.isNewerVersion(release.tagName, current) && release.tagName != lastDismiss) {
                    _updatePrompt.value = UpdatePrompt(release.tagName, release.appImageDownloadUrl)
                }
            } catch (e: Exception) { Logger.error(TAG, "Update check failed", e) }
        }
    }

    fun dismissUpdate() {
        val prompt = _updatePrompt.value ?: return
        updateSettings(_settings.value.copy(lastDismissedVersion = prompt.latestVersion))
        _updatePrompt.value = null
    }

    /** Downloads the new AppImage, makes it executable and reveals it in the file manager. */
    fun startDownloadAndUpdate() {
        val prompt = _updatePrompt.value ?: return
        _updateDownloading.value = true
        _updatePrompt.value = null
        scope.launch {
            val file = UpdateChecker.downloadAppImage(prompt.appImageUrl) { _updateDownloadProgress.value = it }
            _updateDownloading.value = false
            _updateDownloadProgress.value = 0
            if (file != null) {
                UpdateChecker.makeExecutable(file)
                UpdateChecker.revealInFileManager(file)
                _updateDownloaded.value = file.absolutePath
            } else {
                _updateFailed.value = true
            }
        }
    }

    fun dismissUpdateDownloaded() { _updateDownloaded.value = null }

    fun dismissUpdateFailed() { _updateFailed.value = false }

    // ── Download ──────────────────────────────────────────────────────────────

    fun setDownloadSearchQuery(query: String) {
        _downloadSearchQuery.value = query
        downloadSearchJob?.cancel()
        downloadSearchJob = scope.launch { delay(400); refreshDownloadSearch() }
    }

    fun setDownloadSortOption(option: NerinyanApi.SortOption) {
        _downloadSortOption.value = option; refreshDownloadSearch()
    }

    fun setDownloadStatusOption(option: NerinyanApi.StatusOption) {
        _downloadStatusOption.value = option; refreshDownloadSearch()
    }

    fun refreshDownloadSearch() {
        downloadPage = 0; downloadHasMore = true
        val query = _downloadSearchQuery.value
        val sort = _downloadSortOption.value
        val status = _downloadStatusOption.value
        scope.launch {
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
        scope.launch {
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

    // ── Playback ──────────────────────────────────────────────────────────────

    fun playSong(song: Song, source: List<Song>? = null) {
        val songs = source ?: _queue.value
        val idx = songs.indexOfFirst { it.id == song.id }
        if (idx < 0) return
        if (songs !== _queue.value) _queue.value = songs
        _currentSong.value = song
        _isPlaying.value = true
        startSong(song, 0L)
    }

    private fun startSong(song: Song, startMs: Long) {
        val mod = _modSettings.value
        if (player.isLoaded()) {
            val fadeMs = when (_settings.value.audioTransition) {
                AudioTransition.NONE -> 0L
                AudioTransition.FADE_IN_OUT -> 250L
                AudioTransition.CROSSFADE -> 800L
                AudioTransition.SWOOSH -> 800L
            }
            if (fadeMs > 0) player.fadeOutThenPlay(song, mod, fadeMs, startMs)
            else player.play(song, mod, startMs)
        } else {
            player.play(song, mod, startMs)
        }
    }

    fun togglePlayback() {
        val song = _currentSong.value ?: return
        if (player.isLoaded()) player.pauseResume()
        else startSong(song, _currentPositionMs.value)
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun skipToNext() {
        val songs = _queue.value
        if (songs.isEmpty()) return
        val idx = currentIndex()
        if (idx < 0) return
        when (_settings.value.repeat) {
            RepeatMode.ONE -> playQueueIndex(idx)
            RepeatMode.ALL -> playQueueIndex((idx + 1) % songs.size)
            RepeatMode.NONE ->
                if (idx + 1 < songs.size) playQueueIndex(idx + 1)
                else {
                    if (_settings.value.shuffle) _queue.value = songs.shuffled()
                    _isPlaying.value = true
                    playQueueIndex(0)
                }
        }
    }

    fun skipToPrev() {
        val songs = _queue.value
        if (songs.isEmpty()) return
        val idx = currentIndex()
        if (idx < 0) return
        if (player.currentPositionMs() > 3_000L) {
            seekTo(0L)
            return
        }
        playQueueIndex(if (idx > 0) idx - 1 else songs.size - 1)
    }

    fun playQueueIndex(index: Int) {
        val songs = _queue.value
        if (index in songs.indices) playSong(songs[index], songs)
    }

    fun applyMod(mod: SongMod, customSpeed: Float = 1.0f) {
        val newModSettings = ModSettings(activeMod = mod, customSpeed = customSpeed)
        _modSettings.value = newModSettings
        val song = _currentSong.value ?: return
        val wasPlaying = player.isPlaying()
        if (!player.applyMod(newModSettings)) {
            // Restretch needed: reload the song at the same position, but only
            // start playing if it was playing before, so a paused user stays paused.
            player.play(song, newModSettings, _currentPositionMs.value, startPlaying = wasPlaying)
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun updateSettings(settings: AppSettings) {
        _settings.value = settings
        ConfigManager.saveSettings(settings)
        player.setVolume(settings.volume)
        val eq = settings.equalizerSettings
        player.setEqualizer(eq.enabled, eq.bandLevels)
        player.setLoudness(settings.loudnessNormalization, settings.loudnessGainDb)
    }

    fun toggleShuffle() {
        val newShuffle = !_settings.value.shuffle
        if (newShuffle) {
            val songs = _queue.value
            if (songs.isNotEmpty()) {
                val current = _currentSong.value
                val rest = if (current == null) songs else songs.filter { it.id != current.id }
                val reshuffled = (if (current == null) emptyList() else listOf(current)) + rest.shuffled()
                _queue.value = reshuffled
            }
        }
        updateSettings(_settings.value.copy(shuffle = newShuffle))
    }

    fun toggleRepeat() {
        val next = when (_settings.value.repeat) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        updateSettings(_settings.value.copy(repeat = next))
    }

    // ── Search / filter ───────────────────────────────────────────────────────

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setLastTab(index: Int) {
        if (_settings.value.lastTab == index) return
        _settings.value = _settings.value.copy(lastTab = index)
        ConfigManager.saveSettings(_settings.value)
    }

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
        _queue.value = songs
        playSong(songs.first())
    }

    fun playPlaylistFrom(playlist: Playlist, song: Song) {
        val songs = _songs.value.filter { playlist.songIds.contains(it.id) }
        if (songs.isEmpty()) return
        _queue.value = songs
        playSong(song)
    }

    fun getSongsForPlaylist(playlist: Playlist): List<Song> =
        _songs.value.filter { playlist.songIds.contains(it.id) }

    // ── Import ────────────────────────────────────────────────────────────────

    fun importOszFile(file: File) {
        scope.launch(Dispatchers.IO) {
            OszImporter.importOszFromFile(file, file.nameWithoutExtension)?.let { mergeSong(it) }
        }
    }

    fun importZipFile(file: File) {
        scope.launch(Dispatchers.IO) {
            val imported = OszImporter.importFullZip(file)
            if (imported.isNotEmpty()) imported.forEach { mergeSong(it) }
        }
    }

    private fun mergeSong(song: Song) {
        if (_songs.value.none { it.id == song.id }) {
            val oldLibrary = _songs.value
            _songs.value = (oldLibrary + song).sortedBy { it.artist }
            updateQueueAfterLibraryChange(oldLibrary, _songs.value)
        }
    }

    /**
     * Keeps the queue in sync with the library after songs are added or removed.
     * Only touches the queue when it currently mirrors the full library (a playlist
     * queue is left alone). In shuffle mode the existing order is preserved and new
     * songs are inserted at a random position instead of breaking the shuffle.
     */
    private fun updateQueueAfterLibraryChange(oldLibrary: List<Song>, newLibrary: List<Song>) {
        val oldQueue = _queue.value
        if (oldQueue.isEmpty()) {
            _queue.value = newLibrary
            return
        }
        if (oldQueue.toSet() != oldLibrary.toSet()) return
        val added = newLibrary.filter { song -> oldLibrary.none { it.id == song.id } }
        if (added.isEmpty()) return
        _queue.value = if (_settings.value.shuffle) {
            val shuffled = oldQueue.toMutableList()
            added.forEach { shuffled.add(Random.nextInt(shuffled.size + 1), it) }
            shuffled
        } else {
            newLibrary
        }
    }
}
