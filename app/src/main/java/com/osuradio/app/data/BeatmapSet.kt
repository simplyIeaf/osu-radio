package com.osuradio.app.data

data class NerinyanBeatmapSet(
    val id: Long = 0,
    val title: String = "",
    val artist: String = "",
    val creator: String = "",
    val status: String = "",
    val favourite_count: Int = 0,
    val play_count: Int = 0,
    val last_updated: String = "",
    val ranked_date: String? = null,
    val video: Boolean = false,
    val storyboard: Boolean = false,
    val covers: BeatmapCovers? = null,
    val beatmaps: List<NerinyanBeatmap> = emptyList()
)

data class BeatmapCovers(
    val cover: String? = null,
    val card: String? = null,
    val list: String? = null,
    val slimcover: String? = null
)

data class NerinyanBeatmap(
    val id: Long = 0,
    val beatmapset_id: Long = 0,
    val version: String = "",
    val difficulty_rating: Double = 0.0,
    val total_length: Long = 0,
    val mode: String = "",
    val status: String = ""
)
