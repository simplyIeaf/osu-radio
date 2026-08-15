package com.leaf.osuradio.data

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList()
)
