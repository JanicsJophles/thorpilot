package dev.thorpilot

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView

/** Synthetic journal records, confined to a separate disposable namespace. */
object GameCareChecks {
    /** Run on the UI thread. Exercise the real panel callbacks with isolated notes. */
    fun runPanel(context: Context) {
        val namespace = "game-care-panel-test"
        val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = GameCareStore(context, namespace)
            var panel = GameCarePanel(context, store)
            var root = panel.createView(context)
            fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
                (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
            fun click(title: String) {
                check(views(root).filterIsInstance<Button>().single { it.text.toString() == title }.performClick())
            }
            fun field(title: String) = views(root).filterIsInstance<EditText>()
                .single { it.contentDescription.toString() == title }
            fun has(text: String) = views(root).filterIsInstance<TextView>().any { it.text.contains(text) }
            field("Game").setText("Synthetic UI game")
            field("Scene to repeat").setText("Same room")
            field("Before: what you observed").setText("Visible flicker")
            click("Trial")
            field("Original setting — your rollback").setText("Original value")
            field("One setting to try manually").setText("One trial value")
            check(store.entries().isEmpty()) // Recreation must not implicitly save a note.
            val restoredState = panel.saveState()
            panel = GameCarePanel(context, store)
            panel.restoreState(restoredState)
            root = panel.createView(context)
            check(views(root).filterIsInstance<Button>().single { it.text.toString() == "Trial" }.isSelected)
            check(field("Original setting — your rollback").text.toString() == "Original value")
            check(field("One setting to try manually").text.toString() == "One trial value")
            check(store.entries().isEmpty())
            click("Baseline")
            check(field("Game").text.toString() == "Synthetic UI game")
            check(field("Scene to repeat").text.toString() == "Same room")
            check(field("Before: what you observed").text.toString() == "Visible flicker")
            click("Trial")
            check(field("Original setting — your rollback").text.toString() == "Original value")
            check(field("One setting to try manually").text.toString() == "One trial value")
            click("Result")
            val result = views(root).filterIsInstance<Spinner>().single()
            val position = GameCareStore.results.indexOf("Better")
            result.setSelection(position)
            // Detached panel has no layout pass: dispatch the same selection callback explicitly.
            result.onItemSelectedListener!!.onItemSelected(result, null, position, position.toLong())
            field("After: what changed?").setText("Flicker improved")
            click("Save local note")
            check(has("Saved locally. No emulator settings changed."))
            val saved = GameCareStore(context, namespace).entries().single()
            check(saved.result == "Better" && saved.after == "Flicker improved")
            check(saved.original == "Original value" && saved.trial == "One trial value")
            check(saved.game == "Synthetic UI game" && saved.scene == "Same room")
            root = GameCarePanel(context, GameCareStore(context, namespace)).createView(context)
            click("Synthetic UI game · Better")
            check(field("Game").text.toString() == saved.game)
            click("Result")
            check(views(root).filterIsInstance<Spinner>().single().selectedItem.toString() == "Better")
            check(field("After: what changed?").text.toString() == saved.after)
            field("After: what changed?").setText("Retested the same scene")
            click("Save local note")
            val edited = store.entries().single()
            check(edited.id == saved.id && edited.after == "Retested the same scene")
            check(edited.device == saved.device && edited.emulator == saved.emulator)
            val malformed = GameCarePanel(context, store)
            malformed.restoreState(null)
            malformed.restoreState(Bundle().apply {
                putInt("step", 99); putString("game", "x".repeat(200))
                putString("original", "x".repeat(400)); putString("trial", "x".repeat(400))
                putString("result", "Imagined"); putString("symptom", "Imagined")
            })
            val sanitized = malformed.saveState()
            check(sanitized.getInt("step") in 0..2)
            check(sanitized.getString("game").orEmpty().length <= 120)
            check(sanitized.getString("original").orEmpty().length <= 240)
            check(sanitized.getString("trial").orEmpty().length <= 240)
            check(sanitized.getString("result") == "Untested")
            check(sanitized.getString("symptom") == "Texture")
            check(store.entries().single() == edited)
        } finally { prefs.edit().clear().commit() }
    }
    fun run(context: Context) {
        val namespace = "game-care-test"
        val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = GameCareStore(context, namespace)
            val original = store.save(GameCareEntry(id = "stable-id", game = " Test game ",
                device = "Synthetic Android 13 device", emulator = "Synthetic emulator 1.0",
                original = "Original setting", trial = "One changed setting"))
            check(original.game == "Test game" && original.updated > 0)
            val reloaded = GameCareStore(context, namespace).entries().single()
            check(reloaded == original)
            store.save(reloaded.copy(result = "Better", after = "Observed improvement"))
            val edited = store.entries().single()
            check(edited.id == original.id && edited.result == "Better")
            check(edited.device == original.device && edited.emulator == original.emulator)
            check(edited.original == original.original && edited.trial == original.trial)
            check(runCatching { store.save(edited.copy(game = "  ")) }.isFailure)
            check(runCatching { store.save(edited.copy(id = "")) }.isFailure)
            check(store.entries().single() == edited)

            val long = "x".repeat(1200)
            val bounded = store.save(GameCareEntry(id = long, game = long, symptom = "Invalid", result = "Invented",
                scene = long, original = long, trial = long, before = long, after = long, device = long, emulator = long))
            check(bounded.id.length == 64 && bounded.game.length == 120)
            check(bounded.scene.length == 600 && bounded.before.length == 600 && bounded.after.length == 600)
            check(bounded.original.length == 240 && bounded.trial.length == 240)
            check(bounded.device.length == 120 && bounded.emulator.length == 120)
            check(bounded.result == "Untested" && bounded.symptom in GameCareStore.symptoms)
            GameCareStore.results.forEach { result ->
                check(store.save(bounded.copy(result = result)).result == result)
            }
            repeat(GameCareStore.MAX_ENTRIES + 5) { index ->
                store.save(GameCareEntry(id = "entry-$index", game = "Game $index"))
            }
            val capped = GameCareStore(context, namespace).entries()
            check(capped.size == GameCareStore.MAX_ENTRIES)
            check(capped.map { it.id }.distinct().size == capped.size)
            check(capped.first().id == "entry-${GameCareStore.MAX_ENTRIES + 4}")
            check(capped.none { it.id == "entry-0" })

            listOf("not JSON", "{}", "x".repeat(600_001)).forEach { corrupt ->
                prefs.edit().putString("entries", corrupt).commit()
                check(store.entries().isEmpty())
            }
            prefs.edit().putInt("entries", 12).commit()
            check(store.entries().isEmpty())
            prefs.edit().putString("entries", """[null,1,{"id":"","game":"Missing id"},{"id":"bad","game":""},{"id":"valid","game":"Recovered","result":"Fabricated"}]""").commit()
            check(store.entries().single().result == "Untested")
            store.save(GameCareEntry(id = "recovered", game = "Fresh note"))
            check(GameCareStore(context, namespace).entries().first().game == "Fresh note")
        } finally { prefs.edit().clear().commit() }
    }
}
