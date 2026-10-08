package dev.thorpilot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Browse storage in place. Emulator/launcher handoff never pretends to launch a selected game. */
class DeviceLibraryPanel(context: Context, private val pick: (Boolean) -> Unit, private val openApp: (String) -> Unit) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("device-library", Context.MODE_PRIVATE)
    private val syncPrefs = app.getSharedPreferences("rom-sync", Context.MODE_PRIVATE)
    private val access = RomLibraryMetadata(app)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var host: LinearLayout? = null
    private var internal = false
    private var platform = ""
    private var entries = emptyList<RomLibraryEntry>()
    private var message = "Select a storage location to browse its games. Copying is optional."
    private var busy = false
    private var generation = 0
    private var closed = false
    private var cancellation: CancellationSignal? = null
    private fun selected(onInternal: Boolean): Uri? {
        prefs.getString(if (onInternal) "internal" else "sd", null)?.let { return Uri.parse(it) }
        return listOf("source", "destination").mapNotNull { syncPrefs.getString(it, null)?.let(Uri::parse) }.firstOrNull {
            runCatching { (RomLibraryMetadata.documentId(it).substringBefore(':') == "primary") == onInternal }.getOrDefault(false)
        }
    }
    fun accept(uri: Uri, onInternal: Boolean, flags: Int) {
        runCatching {
            val id = RomLibraryMetadata.documentId(uri)
            require((id.substringBefore(':') == "primary") == onInternal) { "Choose ${if (onInternal) "internal storage" else "the SD card"}'s ROMs folder." }
            require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "Read access is required." }
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString(if (onInternal) "internal" else "sd", uri.toString()).apply()
            internal = onInternal; refresh()
        }.onFailure { message = it.message ?: "Could not save folder access."; render() }
    }
    fun close() { closed = true; generation++; cancellation?.cancel(); worker.shutdownNow(); host = null }
    fun createView(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; host = this; refresh()
    }
    private fun refresh() {
        cancellation?.cancel(); val token = ++generation
        entries = emptyList(); platform = ""
        val tree = selected(internal)
        if (tree == null) { busy = false; message = "Choose this storage location's ROMs folder first."; render(); return }
        busy = true; message = "Reading filenames and sizes…"; render()
        val signal = CancellationSignal(); cancellation = signal
        worker.execute {
            val result = runCatching { access.list(tree, signal) }
            main.post {
                if (closed || token != generation) return@post
                busy = false
                result.onSuccess { entries = it; message = "${it.size} game files · No copying needed" }
                    .onFailure { message = "Storage unavailable: ${it.message ?: "insert the card or choose its folder again"}" }
                render()
            }
        }
    }
    private fun render() {
        val root = host ?: return; val c = root.context; root.removeAllViews()
        fun dp(n: Int) = (n * c.resources.displayMetrics.density).toInt()
        fun text(value: String, size: Float = 14f) { root.addView(TextView(c).apply {
            text = value; textSize = size; setTextColor(0xfff4f2ec.toInt()); setPadding(0, dp(8), 0, dp(8))
        }) }
        fun button(title: String, action: () -> Unit) { root.addView(Button(c).apply {
            text = title; isAllCaps = false; setTextColor(0xffffb547.toInt()); background = PilotSurface(dp(16).toFloat()); setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }) }
        fun row(items: List<Pair<String, () -> Unit>>, scroll: Boolean = false) {
            val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            items.forEach { (title, action) -> line.addView(Button(c).apply {
                text = title; textSize = 13f; isAllCaps = false; setTextColor(0xffffb547.toInt())
                background = PilotSurface(dp(14).toFloat()); setOnClickListener { action() }
            }, LinearLayout.LayoutParams(if (scroll) -2 else 0, dp(48), if (scroll) 0f else 1f).apply { marginEnd = dp(6) }) }
            if (scroll) root.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(line) })
            else root.addView(line)
        }
        text("On this device", 22f)
        text("Browse where your games live. No copying needed.", 13f)
        row(listOf((if (!internal) "SD card ✓" else "SD card") to { internal = false; refresh() },
            (if (internal) "Internal ✓" else "Internal") to { internal = true; refresh() }))
        row(listOf("Choose folder" to { pick(internal) }, (if (busy) "Restart scan" else "Refresh") to { refresh() }))
        selected(internal)?.let { text(runCatching { RomLibraryMetadata.documentId(it) }.getOrDefault("Selected ROMs folder"), 12f) }
        text(message)
        if (entries.isNotEmpty()) {
            val filters = mutableListOf<Pair<String, () -> Unit>>((if (platform.isEmpty()) "All ✓" else "All") to { platform = ""; render() })
            entries.groupBy { it.platform }.forEach { (name, rows) -> filters.add("$name · ${rows.size}${if (platform == name) " ✓" else ""}" to { platform = name; render() }) }
            row(filters, scroll = true)
            val shown = entries.filter { platform.isEmpty() || it.platform == platform }
            text("Files on ${if (internal) "internal storage" else "SD card"}", 18f)
            shown.take(100).forEach { entry ->
                text(entry.path + "\n" + (entry.size?.let { "${it / (1024 * 1024)} MB" } ?: "Size unavailable") +
                    if (entry.installPackage) " · Install in Azahar; not a direct-play file" else " · Open through your configured emulator", 13f)
            }
            if (shown.size > 100) text("Showing 100 of ${shown.size}. Choose a platform to narrow the view.")
        }
        text("Play without copying", 18f)
        text("Cocoon can use SD and internal folders together. This panel lists files; launch games in Cocoon or your emulator.", 13f)
        button("Open Cocoon") { openApp("rip.moth.cocoonshell") }
        button("Folder setup help") {
            android.app.AlertDialog.Builder(c).setTitle("Use both locations in Cocoon")
                .setMessage("Settings → Library & Data → Platforms: add each platform's SD and internal folders, choose its player, then Rescan Games. Enable Scan Subfolders if needed. Existing games and saves stay in place.")
                .setPositiveButton("Got it", null).show()
        }
        if (entries.any { it.installPackage }) {
            text("CIA files need installation in Azahar before playing. This panel does not install games.", 13f)
            button("Open Azahar") { openApp("org.azahar_emu.azahar") }
        }
        text("Filenames alone do not verify compatibility. Removing the card makes its files unavailable; refresh after inserting it again. Saves and emulator settings are separate from this read-only inventory.", 12f)
    }
}
