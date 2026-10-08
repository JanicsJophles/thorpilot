package dev.thorpilot

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class ThorDownloadEntry(val id: String, val title: String, val platform: String, val fileName: String, val sizeBytes: Long, val sha256: String, val originalFileName: String? = null) {
    fun destination(): String {
        require(id.isNotBlank() && id.length <= 512 && sizeBytes > 0) { "Invalid download manifest." }
        require('/' !in fileName && '\\' !in fileName) { "Invalid download filename." }
        val action = RomSyncPlan.plan(listOf(RomSyncPlan.Entry("$platform/$fileName", sizeBytes, sha256)), emptyList()).single()
        require(action.kind == RomSyncPlan.Kind.COPY) { action.reason }
        return requireNotNull(action.destinationPath)
    }
    /** Only new catalog/enqueue entries pass here. Stored jobs keep their exact destination. */
    fun preparedForTransfer(): ThorDownloadEntry {
        destination() // Reject unsafe source names before attempting cleanup.
        if (originalFileName != null) return this
        val prepared = RomTransferNaming.prepare(platform, fileName)
        return if (prepared == fileName) this else copy(fileName = prepared, originalFileName = fileName).also { it.destination() }
    }
    fun json() = JSONObject().put("id",id).put("title",title).put("platform",platform).put("file_name",fileName).put("size_bytes",sizeBytes).put("sha256",sha256).put("original_file_name",originalFileName)
    companion object { fun parse(j: JSONObject) = ThorDownloadEntry(j.getString("id"), j.getString("title"), j.getString("platform"), j.getString("file_name"), j.getLong("size_bytes"), j.getString("sha256"), j.optString("original_file_name").takeIf { it.isNotEmpty() }).also { it.destination() } }
}

class ThorDownloadClient(private val connection: ConnectionSnapshot) {
    fun open(path: String): HttpsURLConnection {
        val base = ServerAddress.normalize(connection.url)
        return (URL(base.trimEnd('/') + path).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 8000; readTimeout = 15000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", "Thorpilot/0.1 Android")
            setRequestProperty("Authorization", "Bearer ${connection.token}")
            setRequestProperty("Accept-Encoding", "identity")
        }
    }
    fun content(entry: ThorDownloadEntry) = open("/api/downloads/" + URLEncoder.encode(entry.id,"UTF-8").replace("+","%20") + "/content")
    fun catalog(): List<ThorDownloadEntry> {
        val conn = open("/api/downloads")
        try {
            check(conn.responseCode == 200) { "Download library unavailable (HTTP ${conn.responseCode})." }
            val text = conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    require(output.size() + n <= 2 * 1024 * 1024) { "Download manifest is too large." }
                    output.write(buffer, 0, n)
                }
                output.toString("UTF-8")
            }
            val rows = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text).getJSONArray("items")
            require(rows.length() <= 5000) { "Download library is too large." }
            return (0 until rows.length()).map { ThorDownloadEntry.parse(rows.getJSONObject(it)).preparedForTransfer() }
        } finally { conn.disconnect() }
    }
}

/** Pure protocol checks used before appending any remote bytes. */
object ThorDownloadProtocol {
    fun validateRange(status: Int, range: String?, offset: Long, total: Long) {
        if (offset == 0L && status == 200) return
        require(status == 206) { "Server did not honor resume. Partial file preserved." }
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(range.orEmpty()) ?: error("Invalid resume response.")
        val (start,end,size) = match.destructured
        require(start.toLong() == offset && size.toLong() == total && end.toLong() == total - 1) { "Remote file changed; partial file preserved." }
    }
}
