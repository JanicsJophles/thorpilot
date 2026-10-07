package dev.thorpilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GameCareEntry(
    val id: String = UUID.randomUUID().toString(), val game: String = "",
    val symptom: String = "Texture", val scene: String = "", val original: String = "",
    val trial: String = "", val result: String = "Untested", val before: String = "",
    val after: String = "", val device: String = "", val emulator: String = "",
    val updated: Long = 0L
)

/** Local notes only. No emulator configuration or network access. */
class GameCareStore(context: Context, namespace: String = "game-care") {
    private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    companion object {
        val symptoms = listOf("Texture", "Flicker", "Shadows", "Stutter", "Other")
        val results = listOf("Untested", "Better", "Same", "Worse")
        const val MAX_ENTRIES = 30
    }
    private fun bounded(e: GameCareEntry) = e.copy(
        id = e.id.take(64), game = e.game.trim().take(120),
        symptom = e.symptom.takeIf { it in symptoms } ?: symptoms.first(),
        result = e.result.takeIf { it in results } ?: results.first(),
        scene = e.scene.take(600), original = e.original.take(240), trial = e.trial.take(240),
        before = e.before.take(600), after = e.after.take(600), device = e.device.take(120), emulator = e.emulator.take(120)
    )
    fun entries(): List<GameCareEntry> = runCatching {
        val raw = prefs.getString("entries", "[]") ?: "[]"
        if (raw.length > 600_000) return emptyList()
        val a = JSONArray(raw)
        (0 until minOf(a.length(), MAX_ENTRIES)).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            bounded(GameCareEntry(o.optString("id"), o.optString("game"), o.optString("symptom"),
                o.optString("scene"), o.optString("original"), o.optString("trial"), o.optString("result"),
                o.optString("before"), o.optString("after"), o.optString("device"), o.optString("emulator"), o.optLong("updated")))
        }.filter { it.id.isNotEmpty() && it.game.isNotEmpty() }
    }.getOrDefault(emptyList())
    fun save(entry: GameCareEntry): GameCareEntry {
        val e = bounded(entry).copy(updated = System.currentTimeMillis())
        require(e.game.isNotBlank()) { "Add a game name first." }
        require(e.id.isNotBlank()) { "Invalid journal entry." }
        val rows = (listOf(e) + entries().filterNot { it.id == e.id }).take(MAX_ENTRIES)
        val a = JSONArray()
        rows.forEach { r -> a.put(JSONObject().apply {
            put("id", r.id); put("game", r.game); put("symptom", r.symptom); put("scene", r.scene)
            put("original", r.original); put("trial", r.trial); put("result", r.result)
            put("before", r.before); put("after", r.after); put("device", r.device)
            put("emulator", r.emulator); put("updated", r.updated)
        }) }
        check(prefs.edit().putString("entries", a.toString()).commit()) { "Could not save this note. Try again." }
        return e
    }
}
