package dev.thorpilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local notes only. No emulator configuration or network access. */
class GameCareStore(context: Context, namespace: String = "game-care") {
    private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    companion object {
        val symptoms = listOf("Texture", "Flicker", "Shadows", "Stutter", "Other")
        val emulators = listOf("azahar", "eden")
        val scopes = listOf("Unknown", "Per-game", "Global")
        val results = listOf("Untested", "Better", "Same", "Worse")
        const val MAX_ENTRIES = 30
    }
    private fun bounded(e: GameCareEntry) = e.copy(
        id = e.id.take(64), game = e.game.trim().take(120),
        symptom = e.symptom.takeIf { it in symptoms } ?: symptoms.first(),
        result = e.result.takeIf { it in results } ?: results.first(),
        scene = e.scene.take(600), original = e.original.take(240), trial = e.trial.take(240),
        before = e.before.take(600), after = e.after.take(600), device = e.device.take(120), emulator = e.emulator.take(120),
        emulatorId = e.emulatorId.takeIf { it in emulators } ?: "azahar",
        gameRevision = e.gameRevision.take(120), driver = e.driver.take(160),
        scope = e.scope.takeIf { it in scopes } ?: "Unknown", sourceId = e.sourceId.take(64)
    )
    fun entries(): List<GameCareEntry> = runCatching {
        val raw = prefs.getString("entries", "[]") ?: "[]"
        if (raw.length > 600_000) return emptyList()
        val a = JSONArray(raw)
        (0 until minOf(a.length(), MAX_ENTRIES)).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            bounded(GameCareEntry(o.optString("id"), o.optString("game"), o.optString("symptom"),
                o.optString("scene"), o.optString("original"), o.optString("trial"), o.optString("result"),
                o.optString("before"), o.optString("after"), o.optString("device"), o.optString("emulator"), o.optLong("updated"),
                o.optString("emulatorId", "azahar"), o.optString("gameRevision"), o.optString("driver"),
                o.optString("scope", "Unknown"), o.optString("sourceId")))
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
            put("emulatorId", r.emulatorId); put("gameRevision", r.gameRevision); put("driver", r.driver)
            put("scope", r.scope); put("sourceId", r.sourceId)
        }) }
        check(prefs.edit().putString("entries", a.toString()).commit()) { "Could not save this note. Try again." }
        return e
    }
}
