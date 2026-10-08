package dev.thorpilot

import android.content.Context
import android.os.Bundle
import org.json.JSONObject

/** Isolated synthetic preferences only; never accesses emulator configuration. */
object GameCareDraftChecks {
    fun run(context: Context) {
        val namespace = "game-care-draft-test"
        val otherNamespace = "game-care-draft-other-test"
        val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        val otherPrefs = context.getSharedPreferences(otherNamespace, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        otherPrefs.edit().clear().commit()
        try {
            val history = GameCareStore(context, namespace)
            val note = history.save(GameCareEntry(id = "saved-note", game = "Existing note"))
            val store = GameCareDraftStore(context, namespace)
            check(store.load() == null)
            val state = Bundle().apply {
                putInt("step", 1); putLong("updated", 1_234_567_890_123L)
                putString("id", "draft-id"); putString("game", "Unsaved game")
                putString("symptom", "Flicker"); putString("scene", "Same room\nSecond line")
                putString("original", "Original setting"); putString("trial", "Trial setting")
                putString("result", "Better"); putString("before", "Before"); putString("after", "After")
                putString("device", "Synthetic device"); putString("emulator", "Synthetic build")
                putString("emulatorId", "eden"); putString("gameRevision", "1.2")
                putString("driver", "Synthetic driver"); putString("scope", "Per-game")
                putString("sourceId", "source-note")
            }
            store.save(state)
            // A newly constructed store reconstructs from persisted JSON, not a retained Bundle.
            val loaded = GameCareDraftStore(context, namespace).load()!!
            check(loaded.keySet() == state.keySet())
            @Suppress("DEPRECATION")
            state.keySet().forEach { check(loaded.get(it) == state.get(it)) }
            loaded.putString("game", "Mutated returned state")
            check(store.load()!!.getString("game") == "Unsaved game")
            check(history.entries() == listOf(note))
            val other = GameCareDraftStore(context, otherNamespace)
            check(other.load() == null)
            other.save(Bundle().apply { putString("game", "Independent") })
            check(store.load()!!.getString("game") == "Unsaved game")

            val long = "x".repeat(1500)
            store.save(Bundle().apply {
                state.keySet().filter { it !in listOf("updated", "step") }.forEach { putString(it, long) }
                putInt("step", 99); putLong("updated", -1L)
                putString("unapproved", "Must not persist")
            })
            val bounded = store.load()!!
            mapOf("id" to 64, "game" to 120, "scene" to 600, "original" to 240,
                "trial" to 240, "before" to 600, "after" to 600, "device" to 120,
                "emulator" to 120, "gameRevision" to 120, "driver" to 160, "sourceId" to 64
            ).forEach { (key, limit) -> check(bounded.getString(key)!!.length == limit) }
            check(!bounded.containsKey("unapproved"))
            check(bounded.getInt("step") == 2 && bounded.getLong("updated") == 0L)
            check(bounded.getString("symptom") == "Texture" && bounded.getString("result") == "Untested")
            check(bounded.getString("emulatorId") == "azahar" && bounded.getString("scope") == "Unknown")
            store.save(Bundle().apply {
                putInt("game", 4); putString("updated", "123"); putString("step", "2")
            })
            check(store.load()!!.getString("game") == "")
            check(store.load()!!.getLong("updated") == 0L && store.load()!!.getInt("step") == 0)

            listOf("not JSON", "[]", "{}", "x".repeat(32_001),
                """{"version":2,"state":{}}""", """{"version":1,"state":null}"""
            ).forEach { raw ->
                prefs.edit().putString("draft", raw).commit()
                check(store.load() == null)
            }
            prefs.edit().putInt("draft", 5).commit()
            check(store.load() == null)
            prefs.edit().putString("draft", JSONObject().put("version", 1).put("state",
                JSONObject().put("game", JSONObject()).put("updated", 1.5).put("step", false)
                    .put("unapproved", "ignored")).toString()).commit()
            val malformed = store.load()!!
            check(malformed.getString("game") == "" && malformed.getLong("updated") == 0L)
            check(malformed.getInt("step") == 0 && !malformed.containsKey("unapproved"))
            store.save(state)
            check(store.load()!!.getString("original") == "Original setting")
            store.clear()
            check(GameCareDraftStore(context, namespace).load() == null)
            check(other.load()!!.getString("game") == "Independent")
            check(history.entries() == listOf(note))
        } finally {
            prefs.edit().clear().commit()
            otherPrefs.edit().clear().commit()
        }
    }
}
