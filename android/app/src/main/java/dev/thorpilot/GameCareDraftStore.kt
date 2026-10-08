package dev.thorpilot

import android.content.Context
import android.os.Bundle
import org.json.JSONObject

/** One private, local draft. This store never creates journal entries or changes emulator settings. */
class GameCareDraftStore(context: Context, namespace: String = "copilot-care-draft") {
    private val prefs = context.applicationContext.getSharedPreferences(namespace, Context.MODE_PRIVATE)

    companion object {
        private const val KEY = "draft"
        private const val VERSION = 1
        // Includes worst-case JSON escaping of every bounded text field.
        private const val MAX_JSON_LENGTH = 32_000
        private val textLimits = mapOf(
            "id" to 64, "game" to 120, "scene" to 600,
            "original" to 240, "trial" to 240, "before" to 600, "after" to 600,
            "device" to 120, "emulator" to 120, "gameRevision" to 120,
            "driver" to 160, "sourceId" to 64
        )
        private val choices = mapOf(
            "symptom" to GameCareStore.symptoms, "result" to GameCareStore.results,
            "emulatorId" to GameCareStore.emulators, "scope" to GameCareStore.scopes
        )
    }

    fun load(): Bundle? = runCatching {
        val raw = prefs.getString(KEY, null) ?: return null
        if (raw.length > MAX_JSON_LENGTH) return null
        val json = JSONObject(raw)
        if (json.opt("version") != VERSION) return null
        val state = json.optJSONObject("state") ?: return null
        bounded { state.opt(it) }
    }.getOrNull()

    fun save(state: Bundle) {
        @Suppress("DEPRECATION")
        val safe = bounded { state.get(it) }
        val json = JSONObject()
        textLimits.keys.forEach { json.put(it, safe.getString(it)) }
        choices.keys.forEach { json.put(it, safe.getString(it)) }
        json.put("step", safe.getInt("step"))
        json.put("updated", safe.getLong("updated"))
        val raw = JSONObject().put("version", VERSION).put("state", json).toString()
        check(prefs.edit().putString(KEY, raw).commit()) { "Could not save the game care draft." }
    }

    fun clear() {
        check(prefs.edit().remove(KEY).commit()) { "Could not clear the game care draft." }
    }

    private fun bounded(value: (String) -> Any?): Bundle = Bundle().apply {
        textLimits.forEach { (key, limit) -> putString(key, (value(key) as? String).orEmpty().take(limit)) }
        choices.forEach { (key, options) ->
            putString(key, (value(key) as? String)?.takeIf { it in options } ?: options.first())
        }
        putInt("step", (value("step") as? Int ?: 0).coerceIn(0, 2))
        // JSONObject represents small integral values as Int, larger values as Long.
        val updated = when (val raw = value("updated")) {
            is Long -> raw
            is Int -> raw.toLong()
            else -> 0L
        }
        putLong("updated", updated.coerceAtLeast(0L))
    }
}
