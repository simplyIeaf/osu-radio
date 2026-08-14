package com.osuradio.app.data

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val audioPath: String,
    val imagePath: String?,
    val folderPath: String,
    val duration: Long = 0L
)
