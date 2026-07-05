package com.osuradio.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.osuradio.app.data.NerinyanBeatmapSet
import com.osuradio.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object NerinyanApi {
    private const val TAG = "NerinyanApi"
    private const val BASE_URL = "https://api.nerinyan.moe"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloadClient = client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    enum class SortOption(val label: String, val param: String) {
        DEFAULT("Default", "default"),
        TITLE("Title", "title"),
        ARTIST("Artist", "artist"),
        LAST_UPDATED("Last updated", "last_updated"),
        RANKED_DATE("Ranked date", "ranked_date"),
        FAVORITE_COUNT("Favorite count", "favourite_count"),
        PLAY_COUNT("Play count", "play_count")
    }

    enum class StatusOption(val label: String, val param: String) {
        ALL("All", "all"),
        RANKED("Ranked", "ranked"),
        QUALIFIED("Qualified", "qualified"),
        LOVED("Loved", "loved"),
        PENDING("Pending", "pending"),
        WIP("WIP", "wip"),
        GRAVEYARD("Graveyard", "graveyard"),
        UNRANKED("Unranked", "unranked")
    }

    suspend fun search(
        query: String,
        page: Int,
        pageSize: Int = 50,
        sort: SortOption,
        status: StatusOption
    ): List<NerinyanBeatmapSet> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("p", page.toString())
                .addQueryParameter("ps", pageSize.toString())
                .addQueryParameter("sort", sort.param)
                .addQueryParameter("s", status.param)
                .addQueryParameter("m", "all")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.warn(TAG, "Search failed with code ${response.code}")
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                val type = object : TypeToken<List<NerinyanBeatmapSet>>() {}.type
                gson.fromJson<List<NerinyanBeatmapSet>>(body, type) ?: emptyList()
            }
        } catch (e: IOException) {
            Logger.error(TAG, "Search request failed", e)
            emptyList()
        } catch (e: Exception) {
            Logger.error(TAG, "Search parsing failed", e)
            emptyList()
        }
    }

    fun backgroundImageUrl(beatmapsetId: Long): String = "$BASE_URL/bg/$beatmapsetId"

    suspend fun downloadBeatmapset(
        beatmapsetId: Long,
        destination: File,
        onProgress: (Int?) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/d/$beatmapsetId".toHttpUrl().newBuilder()
                .addQueryParameter("nh", "true")
                .addQueryParameter("nsb", "true")
                .addQueryParameter("nv", "true")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/x-osu-beatmap-archive")
                .build()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.warn(TAG, "Download failed with code ${response.code}")
                    return@withContext false
                }
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength()
                onProgress(if (totalBytes > 0) 0 else null)
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var lastReportedPct = -1
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100) / totalBytes).toInt()
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    onProgress(pct)
                                }
                            } else {
                                onProgress(null)
                            }
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Download failed for beatmapset $beatmapsetId", e)
            false
        }
    }
}
