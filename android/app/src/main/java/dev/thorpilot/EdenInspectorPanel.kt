package dev.thorpilot

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** No writes, persistent grants, network calls or raw document retention. */
class EdenInspectorPanel(context: Context, private val pick: (Boolean) -> Unit) {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var game: EdenConfig.Document? = null
    private var global: EdenConfig.Document? = null
    private var message = "Select a per-game config and, for inherited values, the matching global config."
    private var busy = false
    @Volatile private var closed = false
    private fun build(): String = runCatching {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo("dev.eden.eden_emulator", 0).versionName.orEmpty()
    }.getOrDefault("")
    fun close() { closed = true; root = null; worker.shutdown() }
    fun createView(context: Context): View = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; root = this; render() }
    fun accept(uri: Uri, isGlobal: Boolean) {
        if (busy || closed) return
        busy = true; message = "Reading locally…"; render()
        worker.execute {
            val result = runCatching {
                val resolver = app.contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: error("Document has no filename.")
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) {
                            val one = input.read()
                            if (one < 0) break
                            require(output.size() < EdenConfig.MAX_BYTES) { "Configuration exceeds 1 MiB." }
                            output.write(one)
                        } else {
                            require(output.size() <= EdenConfig.MAX_BYTES - count) { "Configuration exceeds 1 MiB." }
                            output.write(buffer, 0, count)
                        }
                    }
                    output.toByteArray()
                }
                    ?: error("Could not read this document.")
                EdenConfig.parse(name, bytes, isGlobal)
            }
            main.post {
                if (!closed) {
                    busy = false
                    // Never retain a previous document in the role whose replacement failed.
                    if (isGlobal) global = result.getOrNull() else game = result.getOrNull()
                    message = result.fold({ "Read selected file. No settings changed." }, { it.message ?: "Could not inspect this file." })
                    render()
                }
            }
        }
    }
    private fun render() {
        val host = root ?: return
        host.removeAllViews()
        val c = host.context
        fun dp(n: Int) = (n * c.resources.displayMetrics.density).toInt()
        fun text(value: String, size: Float = 13f) {
            host.addView(TextView(c).apply {
                text = value; textSize = size; setTextColor(0xfff4f2ec.toInt()); setPadding(0, dp(8), 0, dp(8))
            })
        }
        fun button(title: String, action: () -> Unit) {
            host.addView(Button(c).apply {
                text = title; isAllCaps = false; setTextColor(0xffffb547.toInt())
                background = PilotGlass(dp(16).toFloat()); isEnabled = !busy; setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        }
        text("Eden settings · read only", 24f)
        text("Installed build: ${build().ifBlank { "not detected" }}")
        text("Inspect selected files, not live emulator state. Exported copies may be stale. Choose both files from the same Eden installation; Thorpilot cannot prove their origin. Android may hide Eden's private directory from the picker.")
        button("Choose per-game INI") { pick(false) }
        button("Choose global config.ini") { pick(true) }
        listOf("Game" to game, "Global" to global).forEach { (role, doc) ->
            text(if (doc == null) "$role: not selected" else "$role: ${doc.name}\nSHA-256 ${doc.sha256}")
        }
        text(message)
        game?.let { selected ->
            runCatching { EdenConfig.inspect(build(), selected, global) }.onSuccess { settings ->
                settings.forEach { text("${it.name}: ${it.value}\n${it.origin}", 16f) }
            }.onFailure { text(it.message ?: "Could not resolve effective settings.") }
        }
        text("GPU mode and resolution only. This inspection does not apply a profile, verify an active game or back up a file. Selections clear when the activity is recreated; original file bytes are never saved by this screen.")
        button("Clear inspected files") { game = null; global = null; message = "Selections cleared."; render() }
    }
}
