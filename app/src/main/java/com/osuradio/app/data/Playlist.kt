package com.osuradio.app.data

data class Playlist(
    val id: String,
    val name: String,
    val songIds: MutableList<String> = mutableListOf()
)
