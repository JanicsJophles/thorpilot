package dev.thorpilot

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.*

/** A guided manual experiment journal. Saving a note never applies a setting. */
class GameCarePanel(context: Context, private val store: GameCareStore = GameCareStore(context)) {
    private val app = context.applicationContext
    private var draft = fresh()
    private var message = ""
    private var step = 0
    private var detailsOpen = false
    /** Bounded activity state, including unsaved rollback notes, without writing a journal entry. */
    fun saveState(): Bundle = Bundle().apply {
        putInt("step", step.coerceIn(0, 2))
        putString("id", draft.id.take(64)); putString("game", draft.game.take(120))
        putString("symptom", draft.symptom); putString("scene", draft.scene.take(600))
        putString("original", draft.original.take(240)); putString("trial", draft.trial.take(240))
        putString("result", draft.result); putString("before", draft.before.take(600)); putString("after", draft.after.take(600))
        putString("device", draft.device.take(120)); putString("emulator", draft.emulator.take(120))
        putLong("updated", draft.updated)
        putString("emulatorId", draft.emulatorId); putString("gameRevision", draft.gameRevision.take(120))
        putString("driver", draft.driver.take(160)); putString("scope", draft.scope); putString("sourceId", draft.sourceId.take(64))
    }
    fun restoreState(state: Bundle?) {
        if (state == null) return
        val restored = runCatching {
            fun value(key: String, limit: Int) = state.getString(key).orEmpty().take(limit)
            val base = fresh(value("emulatorId", 16).takeIf { it in GameCareStore.emulators } ?: "azahar")
            GameCareEntry(
                id = value("id", 64).ifBlank { base.id }, game = value("game", 120),
                symptom = value("symptom", 32).takeIf { it in GameCareStore.symptoms } ?: "Texture",
                scene = value("scene", 600), original = value("original", 240), trial = value("trial", 240),
                result = value("result", 32).takeIf { it in GameCareStore.results } ?: "Untested",
                before = value("before", 600), after = value("after", 600),
                device = value("device", 120).ifBlank { base.device }, emulator = value("emulator", 120).ifBlank { base.emulator },
                updated = state.getLong("updated", 0L).coerceAtLeast(0L),
                emulatorId = value("emulatorId", 16).takeIf { it in GameCareStore.emulators } ?: "azahar",
                gameRevision = value("gameRevision", 120), driver = value("driver", 160),
                scope = value("scope", 16).takeIf { it in GameCareStore.scopes } ?: "Unknown", sourceId = value("sourceId", 64)
            ) to state.getInt("step", 0).coerceIn(0, 2)
        }.getOrNull() ?: return
        draft = restored.first; step = restored.second; message = ""
    }
    private fun fresh(id: String = "azahar") = GameCareEntry(device = Build.MODEL, emulatorId = id, emulator = emulatorVersion(id))
    private fun emulatorVersion(id: String): String {
        val name = if (id == "eden") "Eden" else "Azahar"
        val pkg = if (id == "eden") "dev.eden.eden_emulator" else "org.azahar_emu.azahar"
        return runCatching {
            @Suppress("DEPRECATION")
            val info = app.packageManager.getPackageInfo(pkg, 0)
            "$name ${info.versionName ?: "unknown version"}"
        }.getOrDefault("$name not detected")
    }
    private fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    private fun label(c: Context, value: String, size: Float = 14f, strong: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(if (strong) 0xfff2f6fc.toInt() else 0xffafc1d0.toInt())
        if (strong) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(c, 5), 0, dp(c, 8))
    }
    fun createView(context: Context, compact: Boolean = false): View = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; tag = compact; render(this) }
    private fun showStart(root: LinearLayout) {
        render(root)
        root.post { root.requestRectangleOnScreen(android.graphics.Rect(0, 0, root.width, dp(root.context, 60)), false) }
    }
    private fun render(root: LinearLayout) {
        val c = root.context
        val compact = root.tag == true
        root.removeAllViews()
        if (!compact) {
            root.addView(label(c, "Game care", 24f, true))
            root.addView(label(c, "One change. The same scene. A clearer answer.", 13f))
        }
        root.addView(label(c, "Local notes · Record a change and compare the same scene. Settings stay manual.", 12f))
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; background = if (compact) PilotBubble(dp(c, 24).toFloat()) else PilotGlass(dp(c, 20).toFloat())
            setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 16))
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 10) })
        val steps = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Baseline", "Trial", "Result").forEachIndexed { index, title ->
            steps.addView(Button(c).apply {
                text = title; isAllCaps = false; textSize = 13f
                contentDescription = "$title step ${index + 1} of 3"
                isSelected = index == step; setTextColor(0xff6cf7d0.toInt())
                background = if (compact) PilotBubble(dp(c, 24).toFloat(), index == step) else PilotGlass(dp(c, 16).toFloat(), index == step)
                setOnClickListener {
                    (c.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(windowToken, 0)
                    step = index; message = ""; render(root)
                }
            }, LinearLayout.LayoutParams(0, dp(c, 48), 1f).apply { if (index < 2) marginEnd = dp(c, 8) })
        }
        card.addView(steps)
        card.addView(label(c, "${draft.device} · ${draft.emulator}", 11f))
        val installed = emulatorVersion(draft.emulatorId)
        if (draft.emulator != installed) card.addView(label(c, "Recorded build differs from detection: $installed. Previous results do not verify this build.", 12f))
        if (draft.sourceId.isNotBlank()) card.addView(label(c, "Reused trial · Confirm this game's current settings before testing again.", 12f))
        fun field(title: String, value: String, limit: Int, change: (String) -> Unit) {
            card.addView(label(c, title, 13f, true))
            card.addView(EditText(c).apply {
                setText(value); textSize = 14f; setTextColor(0xfff2f6fc.toInt()); setHintTextColor(0xff8498ad.toInt())
                contentDescription = title
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    (if (limit > 240) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NEXT
                minHeight = dp(c, 48); maxLines = 2; filters = arrayOf(InputFilter.LengthFilter(limit))
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xff61caba.toInt())
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { change(s.toString()) }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }, LinearLayout.LayoutParams(-1, -2))
        }
        fun choice(title: String, values: List<String>, value: String, change: (String) -> Unit) {
            card.addView(label(c, title, 13f, true))
            card.addView(Spinner(c).apply {
                contentDescription = title
                adapter = ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, values)
                setSelection(values.indexOf(value).coerceAtLeast(0))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { change(values[position]) }
                }
            }, LinearLayout.LayoutParams(-1, dp(c, 48)))
        }
        fun button(parent: LinearLayout, title: String, click: () -> Unit) {
            parent.addView(Button(c).apply {
                text = title; isAllCaps = false; textSize = 14f; setTextColor(0xff6cf7d0.toInt())
                background = if (compact) PilotBubble(dp(c, 24).toFloat()) else PilotGlass(dp(c, 18).toFloat()); setOnClickListener { click() }
            }, LinearLayout.LayoutParams(-1, dp(c, 48)).apply { topMargin = dp(c, 10) })
        }
        if (compact && step == 0) {
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            card.addView(row)
            listOf("azahar" to "New Azahar note", "eden" to "New Eden note").forEach { (id, title) ->
                val slot = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                row.addView(slot, LinearLayout.LayoutParams(0, -2, 1f))
                button(slot, title) {
                    fun reset() { draft = fresh(id); detailsOpen = false; message = ""; showStart(root) }
                    if (draft.game.isBlank() && draft.scene.isBlank() && draft.original.isBlank() && draft.before.isBlank()) reset()
                    else android.app.AlertDialog.Builder(c).setTitle("Start a new note?")
                        .setMessage("This replaces the unfinished draft. Saved journal notes stay available below.")
                        .setNegativeButton("Keep draft", null).setPositiveButton("Start new") { _, _ -> reset() }.show()
                }
            }
        }
        when (step) {
            0 -> {
                field("Game", draft.game, 120) { draft = draft.copy(game = it) }
                choice("What needs attention?", GameCareStore.symptoms, draft.symptom) { draft = draft.copy(symptom = it) }
                field("Scene to repeat", draft.scene, 600) { draft = draft.copy(scene = it) }
                field("Before: what you observed", draft.before, 600) { draft = draft.copy(before = it) }
                if (compact) button(card, if (detailsOpen) "Hide build details" else "Build and driver details") {
                    detailsOpen = !detailsOpen; render(root)
                }
                if (!compact || detailsOpen) {
                    field("Game revision (if known)", draft.gameRevision, 120) { draft = draft.copy(gameRevision = it) }
                    field("Emulator build (recorded)", draft.emulator, 120) { draft = draft.copy(emulator = it) }
                    field("Graphics driver / backend (if known)", draft.driver, 160) { draft = draft.copy(driver = it) }
                }
            }
            1 -> {
                choice("Setting scope", GameCareStore.scopes, draft.scope) { draft = draft.copy(scope = it) }
                field("Original setting — your rollback", draft.original, 240) { draft = draft.copy(original = it) }
                field("One setting to try manually", draft.trial, 240) { draft = draft.copy(trial = it) }
                card.addView(label(c, "Change one value in the emulator, then repeat the same scene. Keep every other setting unchanged.", 12f))
            }
            else -> {
                choice("Observed result", GameCareStore.results, draft.result) { draft = draft.copy(result = it) }
                field("After: what changed?", draft.after, 600) { draft = draft.copy(after = it) }
                card.addView(label(c, "If worse, restore your original setting manually: ${draft.original.ifBlank { "not recorded yet" }}. This journal cannot perform rollback.", 12f))
            }
        }
        button(card, "Save local note") {
            runCatching { store.save(draft) }.onSuccess { draft = it; message = "Saved locally. No emulator settings changed." }
                .onFailure { message = it.message ?: "Could not save note." }
            render(root)
        }
        if (message.isNotBlank()) card.addView(label(c, message, 13f))
        if (step == 1 && draft.emulatorId == "azahar") {
        root.addView(label(c, "Azahar: a possible graphics experiment", 16f, true))
        root.addView(label(c, "Accurate multiplication can fix shader rendering in games that need it, but may reduce performance. Azahar recommends leaving it disabled unless required. Availability and behavior depend on the build; this is not a universal fix.", 12f))
        root.addView(label(c, "Source: https://azahar-emu.org/blog/one-year-citra-takedown/", 12f).apply {
            Linkify.addLinks(this, Linkify.WEB_URLS); movementMethod = LinkMovementMethod.getInstance(); setLinkTextColor(0xff6cf7d0.toInt())
        })
        }
        if (step == 1 && draft.emulatorId == "eden") {
            root.addView(label(c, "Eden: keep the experiment specific", 16f, true))
            root.addView(label(c, "Record the actual GPU mode and whether it inherits global settings. Use the game's own settings screen for a per-game trial. Driver and resolution changes are separate experiments. A successful scene does not verify the whole game. Eden configuration backup and automatic rollback are not available here.", 12f))
        }
        if (draft.updated > 0) button(root, "Reuse as untested trial") {
            draft = draft.newTrial(Build.MODEL, emulatorVersion(draft.emulatorId))
            step = 0; message = "New draft. Record the current driver, revision, scope and original setting before testing."; showStart(root)
        }
        root.addView(label(c, "Your experiments", 18f, true))
        if (!compact) button(root, "New experiment") { draft = fresh(); step = 0; message = ""; showStart(root) }
        if (!compact) button(root, "New Eden experiment") { draft = fresh("eden"); step = 0; message = ""; showStart(root) }
        val entries = store.entries()
        if (entries.isEmpty()) root.addView(label(c, "Your saved notes will appear here. Up to 30 most recently saved experiments are kept.", 12f))
        else root.addView(label(c, "${entries.size} of 30 notes · Open a note to continue it. Oldest notes are replaced when full.", 12f))
        entries.forEach { entry -> button(root, "${entry.game} · ${entry.result}") { draft = entry; step = 0; message = "Editing saved note"; showStart(root) } }
    }
}
