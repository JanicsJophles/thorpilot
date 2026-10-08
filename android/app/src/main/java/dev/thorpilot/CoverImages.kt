package dev.thorpilot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.net.URI
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Public IGDB artwork only. Provider credentials never leave the configured server. */
class CoverImages {
    private val worker = Executors.newFixedThreadPool(2)
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val waiting = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()
    @Volatile private var closed = false
    private val connections = java.util.concurrent.ConcurrentHashMap.newKeySet<HttpsURLConnection>()

    fun bind(view: ImageView, url: String) {
        if (closed || !supported(url)) return
        view.tag = url
        cache.get(url)?.let { view.setImageBitmap(it); return }
        synchronized(waiting) {
            waiting[url]?.let {
                if (it.size >= 16) it.removeAt(0)
                it.add(WeakReference(view)); return
            }
            if (waiting.size >= 64) return
            waiting[url] = mutableListOf(WeakReference(view))
        }
        worker.execute {
            val bitmap = cache.get(url) ?: runCatching { fetch(url) }.getOrNull()
            val targets = synchronized(waiting) { waiting.remove(url).orEmpty() }
            if (bitmap != null && !closed) {
                cache.put(url, bitmap)
                targets.forEach { target -> target.get()?.post {
                    target.get()?.takeIf { !closed && it.tag == url }?.setImageBitmap(bitmap)
                } }
            }
        }
    }

    private fun fetch(url: String): Bitmap? {
        val connection = URI(url).toURL().openConnection() as HttpsURLConnection
        connections.add(connection)
        try {
            if (closed) return null
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 6000; connection.readTimeout = 6000
            connection.setRequestProperty("Accept", "image/*")
            if (connection.responseCode != 200 || !connection.contentType.orEmpty().startsWith("image/")) return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (output.size() <= MAX_BYTES && !closed) {
                    val n = stream.read(chunk, 0, minOf(chunk.size, MAX_BYTES + 1 - output.size()))
                    if (n < 0) break
                    output.write(chunk, 0, n)
                }
                output.toByteArray()
            }
            if (closed || bytes.size > MAX_BYTES) return null
            return decode(bytes)
        } finally {
            connections.remove(connection); connection.disconnect()
        }
    }

    fun close() {
        closed = true
        worker.shutdownNow()
        connections.forEach { it.disconnect() }; connections.clear()
        synchronized(waiting) { waiting.clear() }
        cache.evictAll()
    }

    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024
        internal fun supported(value: String): Boolean = runCatching {
            val uri = URI(value)
            value.length <= 2048 && uri.scheme == "https" && uri.host == "images.igdb.com" &&
                uri.userInfo == null && uri.port == -1 && uri.query == null && uri.fragment == null &&
                uri.path.startsWith("/igdb/image/upload/")
        }.getOrDefault(false)
        internal fun decode(bytes: ByteArray): Bitmap? {
            if (bytes.size > MAX_BYTES) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth !in 1..8192 || options.outHeight !in 1..8192) return null
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}
