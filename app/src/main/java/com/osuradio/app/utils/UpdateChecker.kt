package com.osuradio.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val RELEASES_URL = "https://api.github.com/repos/simplyIeaf/osu-radio/releases/latest"

    data class ReleaseInfo(
        val tagName: String,
        val apkDownloadUrl: String
    )

    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            if (code != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == "app-debug.apk") {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isEmpty()) return@withContext null
            ReleaseInfo(tagName = tag, apkDownloadUrl = apkUrl)
        } catch (e: Exception) {
            Logger.error("UpdateChecker", "Failed to fetch release", e)
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

    suspend fun downloadApk(context: Context, apkUrl: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val apkDir = File(context.filesDir, "apk").also { it.mkdirs() }
                val apkFile = File(apkDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connect()
                val totalBytes = conn.contentLength.toLong()
                val input = conn.inputStream
                val output = apkFile.outputStream()
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (totalBytes > 0) {
                        val pct = ((downloaded * 100) / totalBytes).toInt()
                        withContext(Dispatchers.Main) { onProgress(pct) }
                    }
                }
                output.flush()
                output.close()
                input.close()
                apkFile
            } catch (e: Exception) {
                Logger.error("UpdateChecker", "Download failed", e)
                null
            }
        }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
