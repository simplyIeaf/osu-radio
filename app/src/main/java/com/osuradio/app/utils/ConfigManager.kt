package com.osuradio.app.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.osuradio.app.data.AppSettings
import com.osuradio.app.data.Playlist
import java.io.File

object ConfigManager {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var configFile: File? = null
    private const val TAG = "ConfigManager"

    data class Config(
        val settings: AppSettings = AppSettings(),
        val playlists: MutableList<Playlist> = mutableListOf()
    )

    private var currentConfig = Config()

    fun init(osuRadioDir: File) {
        configFile = File(osuRadioDir, "config.txt")
        load()
    }

    fun load() {
        try {
            val file = configFile ?: return
            if (file.exists()) {
                val json = file.readText()
                currentConfig = gson.fromJson(json, Config::class.java) ?: Config()
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to load config", e)
            currentConfig = Config()
        }
    }

    fun save() {
        try {
            val file = configFile ?: return
            val json = gson.toJson(currentConfig)
            file.writeText(json)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save config", e)
        }
    }

    fun getSettings(): AppSettings = currentConfig.settings

    fun saveSettings(settings: AppSettings) {
        currentConfig = currentConfig.copy(settings = settings)
        save()
    }

    fun getPlaylists(): List<Playlist> = currentConfig.playlists.toList()

    fun savePlaylists(playlists: List<Playlist>) {
        currentConfig.playlists.clear()
        currentConfig.playlists.addAll(playlists)
        save()
    }

    fun addPlaylist(playlist: Playlist) {
        currentConfig.playlists.add(playlist)
        save()
    }

    fun removePlaylist(playlistId: String) {
        currentConfig.playlists.removeAll { it.id == playlistId }
        save()
    }

    fun updatePlaylist(playlist: Playlist) {
        val idx = currentConfig.playlists.indexOfFirst { it.id == playlist.id }
        if (idx >= 0) {
            currentConfig.playlists[idx] = playlist
            save()
        }
    }
}
