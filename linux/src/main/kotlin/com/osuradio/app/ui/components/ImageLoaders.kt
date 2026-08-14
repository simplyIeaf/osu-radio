package com.osuradio.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.io.File
import java.util.concurrent.TimeUnit

private val imageCache = object {
    private val lock = Any()
    private val map = LinkedHashMap<String, ImageBitmap>(0, 0.75f, true)
    private const val MAX_ENTRIES = 512

    fun get(key: String): ImageBitmap? = synchronized(lock) {
        map[key]?.also { map.remove(key); map[key] = it }
    }

    fun put(key: String, bitmap: ImageBitmap) = synchronized(lock) {
        map[key] = bitmap
        while (map.size > MAX_ENTRIES) {
            val oldest = map.entries.iterator().next().key
            map.remove(oldest)
        }
    }
}

private val imageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

private fun isRemoteUrl(model: String): Boolean = model.startsWith("http://") || model.startsWith("https://")

private fun loadImageBitmap(model: String): ImageBitmap? {
    imageCache.get(model)?.let { return it }
    val bytes = when {
        isRemoteUrl(model) -> runCatching {
            val request = Request.Builder().url(model).build()
            imageClient.newCall(request).execute().use { it.body?.bytes() }
        }.getOrNull()
        else -> runCatching { File(model).readBytes() }.getOrNull()
    }
    val bitmap = bytes?.let { runCatching { Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
    if (bitmap != null) imageCache.put(model, bitmap)
    return bitmap
}

/**
 * Desktop replacement for Coil's AsyncImage. Loads both local file paths and
 * remote URLs. Renders nothing while loading and on failure, so callers should
 * provide their own placeholder behind it.
 */
@Composable
fun OsuImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, model) {
        value = if (model != null) {
            withContext(Dispatchers.IO) { loadImageBitmap(model) }
        } else null
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}
