package dev.thorpilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded local conversation. App backup is disabled; never store API keys here. */
class ChatHistory(context: Context, preferencesName: String = "chat-history") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    /** The first upgrade binds legacy history before settings can change. Later
     * changes clear data and update its binding in one durable preferences edit. */
    fun bind(identity: String): ChatSnapshot = synchronized(this) {
        val previous = prefs.getString("connection-identity", null)
        if (previous != identity) {
            val edit = prefs.edit()
            if (previous != null || identity.isBlank()) edit.clear()
            check(edit.putString("connection-identity", identity).commit()) { "Could not bind conversation storage." }
        }
        load()
    }
    fun load(): ChatSnapshot = synchronized(this) {
        val draft = (prefs.getString("draft", "") ?: "").take(3000)
        val raw = prefs.getString("messages", "[]") ?: "[]"
        if (raw.length > 512 * 1024) return@synchronized ChatSnapshot(emptyList(), draft)
        val messages = runCatching {
            val list = JSONArray(raw)
            (maxOf(0, list.length() - 20) until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                val role = item.optString("role")
                val text = item.optString("content").take(3000)
                if (role !in listOf("user", "assistant") || text.isBlank()) return@mapNotNull null
                val cards = item.optJSONArray("games") ?: JSONArray()
                ChatMessage(role, text, (0 until minOf(cards.length(), 5)).mapNotNull { i ->
                    cards.optJSONObject(i)?.let { ChatClient.parseGame(it) }
                })
            }
        }.getOrDefault(emptyList())
        ChatSnapshot(messages, draft)
    }

    fun save(messages: List<ChatMessage>, draft: String = "") = synchronized(this) {
        val list = JSONArray()
        messages.takeLast(20).filter { it.role in listOf("user", "assistant") && it.content.isNotBlank() }.forEach { message ->
            val cards = JSONArray()
            message.games.take(5).forEach { g -> cards.put(JSONObject().put("id", g.id).put("title", g.title.take(160))
                .put("platform", g.platform.take(30)).put("platform_name", g.platformName.take(80))
                .put("reason", g.reason.take(450)).put("cover", g.cover.take(2048)).put("owned", g.owned)) }
            list.put(JSONObject().put("role", message.role).put("content", message.content.take(3000)).put("games", cards))
        }
        prefs.edit().putString("messages", list.toString()).putString("draft", draft.take(3000)).apply()
    }
    fun saveDraft(draft: String) { prefs.edit().putString("draft", draft.take(3000)).apply() }
    fun clear() { prefs.edit().clear().apply() }
}

data class ChatSnapshot(val messages: List<ChatMessage>, val draft: String)
