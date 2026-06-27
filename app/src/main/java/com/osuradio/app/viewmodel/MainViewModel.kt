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
import com.osuradio.app.data.AppSettings
import com.osuradio.app.data.ModSettings
import com.osuradio.app.data.Playlist
import com.osuradio.app.data.RepeatMode
import com.osuradio.app.data.Song
import com.osuradio.app.data.SongMod
import com.osuradio.app.service.MusicService
import com.osuradio.app.utils.ConfigManager
import com.osuradio.app.utils.Logger
import com.osuradio.app.utils.OszImporter
import com.osuradio.app.utils.SongScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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

    private var musicService: MusicService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? MusicService.LocalBinder
            musicService = localBinder?.getService()
            serviceBound = true
            startPositionUpdater()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
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
            } catch (e: Exception) {
                Logger.error(TAG, "Initialization failed", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun startPositionUpdater() {
        viewModelScope.launch {
            while (true) {
                val service = musicService
                if (service != null && service.getPlayer().isPlaying) {
                    _currentPositionMs.value = service.getPlayer().currentPosition
                    _isPlaying.value = true
                } else if (service != null) {
                    _isPlaying.value = service.getPlayer().isPlaying
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun playSong(song: Song) {
        _currentSong.value = song
        _isPlaying.value = true
        val service = musicService ?: return
        service.setTransition(_settings.value.audioTransition)
        service.playAudio(song.audioPath)
        service.getPlayer().addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    onSongEnded()
                }
            }
        })
    }

    fun previewSong(song: Song) {
        musicService?.previewAudio(song.audioPath)
    }

    fun pauseResume() {
        musicService?.pauseResume()
        _isPlaying.value = musicService?.getPlayer()?.isPlaying ?: false
    }

    fun seekTo(positionMs: Long) {
        musicService?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun skipToNext() {
        val currentQueue = _queue.value
        val current = _currentSong.value ?: return
        val idx = currentQueue.indexOfFirst { it.id == current.id }
        val nextSong = when {
            _settings.value.repeat == RepeatMode.ONE -> current
            _settings.value.shuffle -> currentQueue.random()
            idx >= 0 && idx < currentQueue.size - 1 -> currentQueue[idx + 1]
            _settings.value.repeat == RepeatMode.ALL -> currentQueue.firstOrNull()
            else -> null
        }
        nextSong?.let { playSong(it) }
    }

    fun skipToPrev() {
        val currentQueue = _queue.value
        val current = _currentSong.value ?: return
        val idx = currentQueue.indexOfFirst { it.id == current.id }
        val prevSong = if (idx > 0) currentQueue[idx - 1] else {
            if (_settings.value.repeat == RepeatMode.ALL) currentQueue.lastOrNull() else null
        }
        prevSong?.let { playSong(it) } ?: musicService?.seekTo(0)
    }

    private fun onSongEnded() {
        when (_settings.value.repeat) {
            RepeatMode.ONE -> _currentSong.value?.let { playSong(it) }
            RepeatMode.ALL, RepeatMode.NONE -> skipToNext()
        }
    }

    fun applyMod(mod: SongMod, customSpeed: Float = 1.0f) {
        val currentPos = _currentPositionMs.value
        val newModSettings = ModSettings(activeMod = mod, customSpeed = customSpeed)
        _modSettings.value = newModSettings
        musicService?.applyMod(newModSettings, currentPos)
    }

    fun updateSettings(settings: AppSettings) {
        _settings.value = settings
        ConfigManager.saveSettings(settings)
        musicService?.setTransition(settings.audioTransition)
    }

    fun toggleShuffle() {
        val newSettings = _settings.value.copy(shuffle = !_settings.value.shuffle)
        updateSettings(newSettings)
    }

    fun toggleRepeat() {
        val next = when (_settings.value.repeat) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        updateSettings(_settings.value.copy(repeat = next))
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredSongs(): List<Song> {
        val q = _searchQuery.value.lowercase()
        return if (q.isBlank()) _songs.value
        else _songs.value.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    fun createPlaylist(name: String) {
        val playlist = Playlist(id = UUID.randomUUID().toString(), name = name)
        ConfigManager.addPlaylist(playlist)
        _playlists.value = ConfigManager.getPlaylists()
    }

    fun deletePlaylist(playlistId: String) {
        ConfigManager.removePlaylist(playlistId)
        _playlists.value = ConfigManager.getPlaylists()
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (!playlist.songIds.contains(songId)) {
            playlist.songIds.add(songId)
            ConfigManager.updatePlaylist(playlist)
            _playlists.value = ConfigManager.getPlaylists()
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        playlist.songIds.remove(songId)
        ConfigManager.updatePlaylist(playlist)
        _playlists.value = ConfigManager.getPlaylists()
    }

    fun playPlaylist(playlist: Playlist) {
        val playlistSongs = _songs.value.filter { playlist.songIds.contains(it.id) }
        if (playlistSongs.isNotEmpty()) {
            _queue.value = playlistSongs
            playSong(playlistSongs.first())
        }
    }

    fun importOszFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val song = OszImporter.importOsz(context, uri)
            if (song != null) {
                withContext(Dispatchers.Main) {
                    mergeSong(song)
                }
            }
        }
    }

    fun importZipFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = OszImporter.importFullZip(context, uri)
            if (imported.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    imported.forEach { mergeSong(it) }
                }
            }
        }
    }

    private fun mergeSong(song: Song) {
        val current = _songs.value.toMutableList()
        if (current.none { it.id == song.id }) {
            current.add(song)
            _songs.value = current.sortedBy { it.artist }
            _queue.value = _songs.value
        }
    }

    fun getSongsForPlaylist(playlist: Playlist): List<Song> {
        return _songs.value.filter { playlist.songIds.contains(it.id) }
    }
}
