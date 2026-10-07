package dev.thorpilot

import java.io.ByteArrayInputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Deterministic transport tests: no internet, private server, or real API key required. */
object RequestChecks {
    fun run() {
        check(ServerAddress.normalize(" HTTPS://Example.com:443/romarr/ ") == "https://example.com/romarr")
        listOf("http://example.com", "https://user:pass@example.com", "https://example.com?key=x",
            "https://example.com#secret", "https://example.com:99999", "https://example.com/a/../b",
            "https://example.com/%2e%2e", "https://example.com/a//b").forEach {
            check(runCatching { ServerAddress.normalize(it) }.isFailure)
        }
        val wire = FakeConnection(200, """{"items":[{"game":"Test","platform":"nds","status":"metadata","progress":25,"detail":"Waiting"}]}""")
        val client = RequestClient { url ->
            check(url.path == "/prefix/api/v1/game-requests")
            wire
        }
        val result = client.fetch("https://example.com/prefix/", "dummy-test-key")
        check(result.isSuccess && result.rows.single().progress == 25.0)
        check(result.rows.single().status == "metadata")
        check(result.summary().contains("Waiting for metadata / peers"))
        val failed = RequestClient.parse("""{"items":[{"game":"Test","status":"import-failed","detail":"Unsupported format","review_required":true,"client_status":"downloaded","client_detail":"Transfer complete"}]}""")
        check(failed.rows.single().status == "import-failed")
        check(failed.summary().contains("Import failed") && failed.summary().contains("Unsupported format"))
        check(failed.summary().contains("Review this release") && failed.summary().contains("Client: Awaiting import"))
        check(RequestClient.parse("""{"items":[{"status":"imported"}]}""").summary().contains("does not confirm a copy"))
        check(RequestClient.parse("""{"items":[{"status":"new-server-state"}]}""").rows.single().statusLabel == "new-server-state")
        check(wire.getRequestProperty("X-Api-Key") == "dummy-test-key")
        check(!wire.instanceFollowRedirects && wire.disconnected)
        listOf(301, 401, 403, 404, 429, 500).forEach { code ->
            val fake = FakeConnection(code, "private server diagnostic")
            val failure = RequestClient { fake }.fetch("https://example.com", "dummy-test-key")
            check(!failure.isSuccess && !failure.summary().contains("private"))
            check(!fake.bodyRead && fake.disconnected)
        }
        val huge = FakeConnection(200, "x".repeat(RequestClient.MAX_BYTES + 1))
        check(RequestClient { huge }.fetch("https://example.com", "dummy-test-key").message!!.contains("too large"))
        check(huge.disconnected)
        check(!RequestClient.parse("<html>sign in</html>").isSuccess)
        check(!RequestClient.parse("""{"items":[null]}""").isSuccess)
        check(RequestClient.parse("""{"items":[]}""").isSuccess)
        check(RequestClient { error("Must not open network") }.fetch("https://example.com", "bad\r\nkey").message != null)
    }
    private class FakeConnection(private val status: Int, body: String) : HttpsURLConnection(URL("https://example.com")) {
        private val bytes = body.toByteArray()
        var disconnected = false
        var bodyRead = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(bytes).also { bodyRead = true }
        override fun getContentLengthLong() = -1L // Exercises streaming limit, including chunked responses.
        override fun disconnect() { disconnected = true }
        override fun connect() {}
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<java.security.cert.Certificate>? = null
        override fun getServerCertificates(): Array<java.security.cert.Certificate> = emptyArray()
    }
}
