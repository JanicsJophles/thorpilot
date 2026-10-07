package dev.thorpilot

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.SocketTimeoutException
import javax.net.ssl.HttpsURLConnection

/** Optional custom ROMarr discovery adapter. Call on a worker thread; never queues a game. */
class ChatClient {
    @Throws(ChatException::class)
    fun send(baseUrl: String, apiKey: String, messages: List<ChatMessage>): ChatReply {
        val uri = try { URI(baseUrl.trim()) } catch (_: Exception) { throw ChatException("Enter a valid HTTPS server address.") }
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null)
            throw ChatException("Enter an HTTPS server address without credentials or query parameters.")
        if (apiKey.isBlank() || apiKey.any { it == '\r' || it == '\n' }) throw ChatException("Save a valid server API key first.")
        if (messages.isEmpty() || messages.last().role != "user") throw ChatException("Write a question first.")
        val recent = messages.takeLast(20).map { if (it.role == "assistant") it.copy(content = it.content.take(3000)) else it }
        if (recent.any { it.role !in listOf("user", "assistant") || it.content.isBlank() || it.content.length > 3000 })
            throw ChatException("Each chat message must contain 1–3000 characters.")
        val body = JSONObject().put("messages", JSONArray().apply {
            recent.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
        }).toString().toByteArray(Charsets.UTF_8)
        if (body.size > 64000) throw ChatException("This conversation is too long. Start a new chat.")
        val connection = URI(uri.toString().trimEnd('/') + "/api/v1/game-chat").toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 90000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Api-Key", apiKey)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status != 200) throw ChatException(when (status) {
                401, 403 -> "Your server rejected this API key. Check Connection settings."
                404, 405 -> "This server does not provide the optional game-chat adapter."
                413 -> "This conversation is too long. Start a new chat."
                429 -> "Your server is busy. Try again shortly."
                in 300..399 -> "Your server redirected chat. Save its final HTTPS address in Connection settings."
                else -> "Chat is unavailable (HTTP $status). Try again shortly."
            })
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 128 * 1024) {
                    val count = stream.read(buffer, 0, minOf(buffer.size, 128 * 1024 + 1 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > 128 * 1024) throw ChatException("The server returned an oversized chat response.")
            return parseResponse(bytes.toString(Charsets.UTF_8))
        } catch (e: ChatException) { throw e
        } catch (_: SocketTimeoutException) { throw ChatException("Chat took too long. Please try again.")
        } catch (_: IOException) { throw ChatException("Could not reach your chat server. Check your connection.")
        } catch (_: Exception) { throw ChatException("The server returned an unreadable chat response.")
        } finally { connection.disconnect() }
    }

    companion object {
        fun parseResponse(raw: String): ChatReply {
            try {
                if (raw.length > 128 * 1024) throw ChatException("The server returned an oversized chat response.")
                val json = JSONObject(raw)
                if (json.has("error")) throw ChatException("The server could not complete this chat. Try again shortly.")
                val reply = json.getString("reply").take(4000)
                if (reply.isBlank()) throw ChatException("The server returned an empty chat response.")
                val games = json.optJSONArray("games") ?: JSONArray()
                return ChatReply(reply, (0 until minOf(5, games.length())).mapNotNull { i ->
                    games.optJSONObject(i)?.let { parseGame(it) }
                }, json.optInt("unverified", 0).coerceIn(0, 100))
            } catch (e: ChatException) { throw e
            } catch (_: Exception) { throw ChatException("The server returned an unreadable chat response.") }
        }

        internal fun parseGame(game: JSONObject): GameRecommendation? {
            val title = game.optString("title").take(160)
            val platform = game.optString("platform").take(30)
            if (title.isBlank() || platform.isBlank()) return null
            val cover = game.optString("cover").take(2048)
            val safeCover = runCatching { URI(cover) }.getOrNull()?.takeIf {
                it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null
            }?.toString() ?: ""
            return GameRecommendation(game.optLong("id"), title, platform, game.optString("platform_name").take(80),
                game.optString("reason").take(450), safeCover, game.optBoolean("owned"))
        }
    }
}

class ChatException(message: String) : IOException(message)
data class GameRecommendation(val id: Long, val title: String, val platform: String, val platformName: String,
    val reason: String, val cover: String = "", val owned: Boolean = false)
data class ChatReply(val reply: String, val games: List<GameRecommendation> = emptyList(), val unverified: Int = 0)
data class ChatMessage(val role: String, val content: String, val games: List<GameRecommendation> = emptyList())
