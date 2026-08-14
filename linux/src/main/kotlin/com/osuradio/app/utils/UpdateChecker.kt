package com.osuradio.app.utils

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL = "https://api.github.com/repos/simplyIeaf/osu-radio/releases/latest"
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val downloadClient = client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class ReleaseInfo(
        val tagName: String,
        val appImageDownloadUrl: String,
        val body: String = ""
    )

    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .get()
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JsonParser.parseString(body).asJsonObject
                val tag = json.get("tag_name").asString
                val assets = json.getAsJsonArray("assets")
                var appImageUrl = ""
                for (i in 0 until assets.size()) {
                    val asset = assets.get(i).asJsonObject
                    if (asset.get("name").asString.endsWith(".AppImage")) {
                        appImageUrl = asset.get("browser_download_url").asString
                        break
                    }
                }
                if (appImageUrl.isEmpty()) return@withContext null
                ReleaseInfo(
                    tagName = tag,
                    appImageDownloadUrl = appImageUrl,
                    body = json.get("body")?.asString?.trim() ?: ""
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to fetch release", e)
            null
        }
    }

    suspend fun fetchReleaseNotes(version: String): String? = withContext(Dispatchers.IO) {
        try {
            val tag = version.removePrefix("v")
            val request = Request.Builder()
                .url("https://api.github.com/repos/simplyIeaf/osu-radio/releases/tags/v$tag")
                .get()
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                JsonParser.parseString(body).asJsonObject.get("body")?.asString?.trim()?.ifEmpty { null }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to fetch release notes for v$version", e)
            null
        }
    }

    fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        return try {
            val latest = parseVersion(latestTag.removePrefix("v"))
            val current = parseVersion(currentVersion.removePrefix("v"))
            compareVersions(latest, current) > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun parseVersion(v: String): List<Int> =
        v.split(".").map { it.trim().toIntOrNull() ?: 0 }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    /** Downloads the AppImage to the cache and returns the file. */
    suspend fun downloadAppImage(appImageUrl: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = AppPaths.cacheDir()
                val name = appImageUrl.substringAfterLast('/').ifBlank { "osu-radio.AppImage" }
                val file = File(dir, name)
                if (file.exists()) file.delete()

                val request = Request.Builder().url(appImageUrl).get().build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    val totalBytes = body.contentLength()
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
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
                                        withContext(Dispatchers.Main) { onProgress(pct) }
                                    }
                                }
                            }
                        }
                    }
                }
                file
            } catch (e: Exception) {
                Logger.error(TAG, "Download failed", e)
                null
            }
        }

    /** Makes the downloaded AppImage executable. */
    fun makeExecutable(file: File) {
        try {
            file.setExecutable(true)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to make AppImage executable", e)
        }
    }

    /** Opens the file manager at the folder containing [file]. */
    fun revealInFileManager(file: File) {
        try {
            val dir = file.parentFile ?: file
            val command = if (file.isDirectory) file else dir
            val process = ProcessBuilder("xdg-open", command.absolutePath)
                .redirectErrorStream(true)
                .start()
            Thread { process.inputStream.use { it.readBytes() } }.start()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to open file manager", e)
        }
    }
}
