package com.verisonder.sonderchess.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Players' profile pictures, as linked in the game data. Kept in memory and in the cache
 * folder, so each picture is downloaded once.
 */
object Avatars {
    private val memory = LruCache<String, ImageBitmap>(32)

    suspend fun load(
        context: Context,
        url: String,
    ): ImageBitmap? {
        memory.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "avatars").apply { mkdirs() }
                val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
                val file = File(dir, name)
                if (!file.exists()) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SonderChess")
                    try {
                        if (conn.responseCode !in 200..299) return@runCatching null
                        val tmp = File(dir, "$name.tmp")
                        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        tmp.renameTo(file)
                    } finally {
                        conn.disconnect()
                    }
                }
                BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { memory.put(url, it) }
            }.getOrNull()
        }
    }
}

/** The picture behind [url], or null while it loads or when there is none. */
@Composable
fun rememberAvatar(url: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, url) {
        value = if (url == null) null else Avatars.load(context, url)
    }
    return image
}
