package dev.thorpilot

import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

/** Pure queue policy: stable FIFO admission and normalized destination ownership. */
object ThorDownloadQueue {
    val runningStatuses = setOf("queued", "connecting", "downloading", "verifying")
    fun next(waiting: List<String>, active: Set<String>, limit: Int): List<String> {
        require(limit in 1..3)
        return waiting.distinct().filterNot { it in active }.take((limit-active.size).coerceAtLeast(0))
    }
    fun targetKey(tree: String, destination: String): String {
        val uri=URI(tree)
        val path=uri.rawPath.orEmpty().substringAfter("/tree/").substringBefore("/document/")
        val document=URLDecoder.decode(path.replace("+","%2B"),"UTF-8")
        return Normalizer.normalize("${uri.authority}/$document/$destination",Normalizer.Form.NFC).lowercase(Locale.ROOT)
    }
}
