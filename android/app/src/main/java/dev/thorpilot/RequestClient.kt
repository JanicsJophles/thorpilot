package dev.thorpilot

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

object ServerAddress {
    fun normalize(raw: String): String {
        val uri = try { URI(raw.trim()) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid HTTPS server address.") }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.port == -1 || uri.port in 1..65535)) {
            "Enter an HTTPS server address without credentials, query parameters, or fragments."
        }
        // Reject ambiguous escaped separators / traversal; preserve ordinary reverse-proxy subpaths.
        val path = uri.rawPath.orEmpty()
        require(!Regex("(?i)%2f|%5c|%2e").containsMatchIn(path) && !path.contains('\\') &&
            path.split('/').none { it == "." || it == ".." } && !path.contains("//")) {
            "Enter a server address without relative or escaped path segments."
        }
        val host = uri.host.lowercase(Locale.ROOT)
        return "https://$host" + (if (uri.port != -1 && uri.port != 443) ":${uri.port}" else "") + path.trimEnd('/')
    }
}

data class GameRequest(
    val title: String, val platform: String, val status: String, val detail: String, val progress: Double?,
    val reviewRequired: Boolean = false, val clientStatus: String = "", val clientDetail: String = ""
) {
    val statusLabel: String get() = label(status)
    fun description(): String = buildList {
        if (detail.isNotBlank()) add(detail)
        if (status == "imported") add("In your server library. This does not confirm a copy on your Thor or SD card.")
        if (clientStatus.isNotBlank() && (clientStatus != status || clientDetail.trim() != detail.trim()))
            add("Client: ${label(clientStatus)}${if (clientDetail.isBlank()) "" else " — $clientDetail"}")
        if (reviewRequired || status in setOf("failed", "import-failed", "interrupted"))
            add("Review this release in ROMarr before retrying.")
    }.joinToString("\n")
    companion object {
        fun label(status: String): String = when (status) {
            "searching" -> "Searching"
            "wanted" -> "Waiting for a release"
            "queued" -> "Queued"
            "downloading", "grabbed" -> "Downloading"
            "metadata" -> "Waiting for metadata / peers"
            "stalled" -> "Stalled"
            "paused" -> "Paused"
            "verifying" -> "Verifying"
            "repairing" -> "Repairing"
            "extracting" -> "Extracting"
            "processing" -> "Processing"
            "downloaded" -> "Awaiting import"
            "importing" -> "Importing"
            "imported" -> "Imported to library"
            "import-failed" -> "Import failed"
            "failed" -> "Request failed"
            "interrupted" -> "Interrupted"
            "unknown", "" -> "Status unavailable"
            else -> status // Preserve new server states rather than claiming success or failure.
        }
    }
}
data class RequestResult(
    val rows: List<GameRequest> = emptyList(), val message: String? = null,
    val clientWarnings: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = message == null
    fun summary(): String {
        if (message != null) return message
        val requests = if (rows.isEmpty()) "No requests yet." else rows.joinToString("\n\n") {
            "${it.title}\n${it.platform} · ${it.statusLabel}\n${it.description()}".trim()
        }
        if (clientWarnings.isEmpty()) return requests
        return "Download client needs attention\n" +
            "These observations are separate from each request's search or import result.\n" +
            clientWarnings.joinToString("\n") + "\n\n" + requests
    }
}

/** Read-only optional ROMarr adapter. Never follows redirects or surfaces raw response/error bodies. */
class RequestClient(private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) {
    fun fetch(baseUrl: String, apiKey: String): RequestResult {
        return try {
            val base = ServerAddress.normalize(baseUrl)
            require(apiKey.isNotBlank() && apiKey.length <= 4096 && apiKey.none { it.code < 32 || it.code == 127 })
            val connection = open(URL("$base/api/v1/game-requests"))
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("X-Api-Key", apiKey)
                connection.setRequestProperty("Accept", "application/json")
                when (val status = connection.responseCode) {
                    200 -> if (connection.contentLengthLong > MAX_BYTES) tooLarge() else connection.inputStream.use { parse(readBounded(it)) }
                    401, 403 -> RequestResult(message = "Access denied. Check your server API key.")
                    404 -> RequestResult(message = "This server does not expose the game-requests adapter.")
                    in 300..399 -> RequestResult(message = "The server redirected this request. Save its final HTTPS address; your key was not forwarded.")
                    429 -> RequestResult(message = "The server is busy. Wait a moment before refreshing.")
                    else -> RequestResult(message = "Server returned HTTP $status. Try again shortly.")
                }
            } finally { connection.disconnect() }
        } catch (_: ResponseTooLarge) { tooLarge()
        } catch (_: SocketTimeoutException) { RequestResult(message = "The server timed out. Check its connection and try again.")
        } catch (_: UnknownHostException) { RequestResult(message = "Server address could not be found. Check the address and your network.")
        } catch (_: SSLException) { RequestResult(message = "Could not verify a secure connection. Check the server certificate.")
        } catch (_: IllegalArgumentException) { RequestResult(message = "Check your HTTPS server address and API key.")
        } catch (_: Exception) { RequestResult(message = "Could not reach the server. Check your network and try again.") }
    }
    companion object {
        const val MAX_BYTES = 1_048_576
        private const val MAX_ROWS = 1000
        private class ResponseTooLarge : Exception()
        private fun tooLarge() = RequestResult(message = "The request list is too large. Narrow it on your server and try again.")
        private fun readBounded(stream: InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_BYTES) throw ResponseTooLarge()
                output.write(buffer, 0, read)
            }
            return output.toString("UTF-8")
        }
        fun parse(body: String): RequestResult {
            if (body.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return tooLarge()
            return try {
                val document = JSONObject(body)
                val items = document.getJSONArray("items")
                if (items.length() > MAX_ROWS) return tooLarge()
                val rows = (0 until items.length()).map { i ->
                    val row = items.getJSONObject(i)
                    fun text(key: String, fallback: String = "", limit: Int = 500): String =
                        (row.opt(key) as? String)?.take(limit)?.filter { it == '\n' || it == '\t' || !it.isISOControl() } ?: fallback
                    val progress = (row.opt("progress") as? Number)?.toDouble()?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)
                    GameRequest(text("game", "Untitled", 200), text("platform", limit = 50), text("status", "unknown", 50), text("detail"), progress, row.opt("review_required") == true, text("client_status", limit = 50), text("client_detail"))
                }
                val warnings = document.optJSONArray("client_warnings")
                val clientWarnings = if (warnings == null) emptyList() else
                    (0 until minOf(warnings.length(), 5)).mapNotNull { i ->
                        val warning = warnings.optJSONObject(i) ?: return@mapNotNull null
                        fun clean(value: Any?, limit: Int) = (value as? String)?.take(limit)
                            ?.filter { it == '\n' || it == '\t' || !it.isISOControl() }.orEmpty()
                        val client = clean(warning.opt("client"), 50)
                        val detail = clean(warning.opt("detail"), 500)
                        if (client.isBlank() || detail.isBlank()) null else "$client: $detail"
                    }
                RequestResult(rows, clientWarnings = clientWarnings)
            } catch (_: Exception) { RequestResult(message = "The server returned an incompatible request list. Check the game-requests adapter.") }
        }
    }
}
