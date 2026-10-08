package dev.thorpilot

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** No network or credentials: validates artwork boundaries with generated pixels. */
object CoverImageChecks {
    fun run() {
        check(CoverImages.supported("https://images.igdb.com/igdb/image/upload/t_cover_big/example.jpg"))
        listOf("http://images.igdb.com/igdb/image/upload/a.jpg", "https://localhost/a.jpg",
            "https://images.igdb.com.evil.example/igdb/image/upload/a.jpg",
            "https://key@images.igdb.com/igdb/image/upload/a.jpg",
            "https://images.igdb.com:444/igdb/image/upload/a.jpg",
            "https://images.igdb.com/igdb/image/upload/a.jpg?key=secret").forEach { check(!CoverImages.supported(it)) }
        check(CoverImages.decode(byteArrayOf(1, 2, 3)) == null)
        check(CoverImages.decode(ByteArray(2 * 1024 * 1024 + 1)) == null)
        val source = Bitmap.createBitmap(1600, 2400, Bitmap.Config.ARGB_8888)
        try {
            val bytes = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            val decoded = checkNotNull(CoverImages.decode(bytes))
            check(decoded.width <= 512 && decoded.height <= 512)
            decoded.recycle()
        } finally { source.recycle() }
    }
}
