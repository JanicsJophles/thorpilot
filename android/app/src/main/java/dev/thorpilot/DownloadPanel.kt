package dev.thorpilot

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

/** A small, truthful control surface for the durable transfer service. */
class DownloadPanel(context: Context, private val pick: (Boolean) -> Unit) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("download-folders", Context.MODE_PRIVATE)
    private val store = ConnectionStore(app, "downloads")
    private val jobs = ThorDownloadStore(app)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var jobHost: LinearLayout? = null
    private var catalogHost: LinearLayout? = null
    private var query = ""
    private var internal = false
    private var entries = emptyList<ThorDownloadEntry>()
    private var message = "Connect your library gateway to bring games to this device."
    private var loading = false
    private var closed = false
    private var generation = 0
    private var polling = false
    private var renderedJob = ""
    private val tick = object : Runnable {
        override fun run() { if (!polling || closed) return; renderJob(); main.postDelayed(this, 1000) }
    }
    fun start() { if (!polling && !closed) { polling = true; main.post(tick) } }
    fun stop() { polling = false; main.removeCallbacks(tick) }
    fun close() { stop(); closed = true; generation++; worker.shutdownNow(); root = null; jobHost = null; catalogHost = null }
    fun createView(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; root = this; render()
        if (entries.isEmpty() && store.url.isNotBlank() && !loading) refresh()
    }
    private fun selected(): Uri? {
        val candidates = mutableListOf<Uri>()
        prefs.getString(if (internal) "internal" else "sd", null)?.let { candidates += Uri.parse(it) }
        val library = app.getSharedPreferences("device-library", Context.MODE_PRIVATE)
        library.getString(if (internal) "internal" else "sd", null)?.let { candidates += Uri.parse(it) }
        return candidates.firstOrNull { uri -> runCatching {
            (RomLibraryMetadata.documentId(uri).substringBefore(':') == "primary") == internal &&
                app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        }.getOrDefault(false) }
    }
    fun accept(uri: Uri, onInternal: Boolean, flags: Int) {
        runCatching {
            val id = RomLibraryMetadata.documentId(uri)
            require((id.substringBefore(':') == "primary") == onInternal) { "Choose ${if (onInternal) "internal storage" else "the SD card"}'s ROMs folder." }
            val required = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            require(flags and required == required) { "Downloads need read and write access to this ROMs folder." }
            app.contentResolver.takePersistableUriPermission(uri, required)
            prefs.edit().putString(if (onInternal) "internal" else "sd", uri.toString()).apply()
            internal = onInternal; message = "Destination saved. Choose a game below."
        }.onFailure { message = it.message ?: "Could not save folder access." }
        render()
    }
    private fun refresh() {
        if (loading || closed) return
        val connection = runCatching { store.snapshot() }.getOrElse { message = "Save your library connection again."; render(); return }
        if (connection.url.isBlank()) { configure(false); return }
        loading = true; message = "Checking your library…"; val token = ++generation; render()
        worker.execute {
            val result = runCatching { ThorDownloadClient(connection).catalog() }
            main.post {
                if (closed || token != generation) return@post
                loading = false
                result.onSuccess { entries = it; message = "${it.size} games ready on your server" }
                    .onFailure { message = it.message ?: "Library unavailable. Check your connection and try again." }
                render()
            }
        }
    }
    private fun configure(local: Boolean) {
        val c = root?.context ?: return
        val target = if (local) ConnectionStore(app, "downloads-local") else store
        val form = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val url = EditText(c).apply { hint = "https://library.example.com"; setText(target.url); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; isSingleLine = true }
        val key = EditText(c).apply { hint = if (target.url.isBlank()) "Library access key" else "Access key (blank keeps saved key)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; isSingleLine = true }
        form.addView(TextView(c).apply { text = if (local) "Optional secure local gateway. Transfers try it first and fall back to your primary server. Use a trusted HTTPS certificate and its own access key." else "Connect your Thorpilot library gateway. This is separate from your ROMarr request connection." })
        form.addView(url); form.addView(key)
        val dialog = AlertDialog.Builder(c).setTitle(if (local) "Local library route" else "Download connection")
            .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null)
            .apply { if (target.url.isNotBlank()) setNeutralButton("Forget") { _, _ -> target.clear(); if (!local) { generation++; loading = false; entries = emptyList() }; render() } }.create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            runCatching { target.save(url.text.toString(), key.text.toString()) }.onSuccess {
                dialog.dismiss(); if (!local) { generation++; loading = false; entries = emptyList(); refresh() } else { message = "Local route saved."; render() }
            }.onFailure { url.error = it.message ?: "Could not save connection." }
        } }
        dialog.show()
    }
    private fun dp(n: Int) = (n * app.resources.displayMetrics.density).toInt()
    private fun label(parent: LinearLayout, text: String, size: Float = 13f, accent: Boolean = false) = TextView(parent.context).apply {
        this.text = text; textSize = size; setTextColor(if (accent) 0xff6cf7d0.toInt() else 0xffd6e7ed.toInt()); setPadding(0, dp(5), 0, dp(5)); parent.addView(this)
    }
    private fun button(parent: LinearLayout, title: String, action: () -> Unit) = Button(parent.context).apply {
        text = title; isAllCaps = false; textSize = 13f; setTextColor(0xff6cf7d0.toInt()); background = PilotGlass(dp(14).toFloat())
        setOnClickListener { runCatching(action).onFailure { message = it.message ?: "Could not complete that action."; render() } }
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
    }
    private fun row(parent: LinearLayout, items: List<Pair<String, () -> Unit>>) {
        val line = LinearLayout(parent.context).apply { orientation = LinearLayout.HORIZONTAL }; parent.addView(line)
        items.forEach { (title, action) -> button(line, title, action).layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6); topMargin = dp(6) } }
    }
    private fun render() {
        val host = root ?: return; host.removeAllViews()
        label(host, "Download to Thor", 23f, true)
        label(host, "Your library, on either storage. Verified before it is ready to play.")
        row(host, listOf((if (!internal) "SD card ✓" else "SD card") to { internal = false; render() }, (if (internal) "Internal ✓" else "Internal") to { internal = true; render() }))
        val folder = selected()
        label(host, folder?.let { RomLibraryMetadata.documentId(it) } ?: "Choose a writable ROMs folder", 12f)
        row(host, listOf("Choose folder" to { pick(internal) }, "Connection" to { configure(false) }, "Local route" to { configure(true) }))
        jobHost = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)); host.addView(this) }
        renderedJob = ""
        renderJob()
        row(host, listOf((if (loading) "Checking library…" else "Refresh library") to { refresh() }))
        label(host, message)
        host.addView(EditText(host.context).apply {
            hint = "Find a title or platform"; contentDescription = "Search download library"
            setTextColor(0xffd6e7ed.toInt()); setHintTextColor(0xffaebed0.toInt()); textSize = 14f
            isSingleLine = true; setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); renderCatalog() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
        catalogHost = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL; host.addView(this) }
        renderCatalog()
        label(host, "Existing files are verified, never replaced. CIA games need installation in Azahar. Use Cocoon to launch games from your configured folders.", 12f)
    }
    private fun renderCatalog() {
        val host = catalogHost ?: return
        host.removeAllViews()
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val filtered = entries.filter { entry ->
            val searchable = "${entry.title} ${entry.platform}".lowercase(Locale.ROOT)
            terms.all { it in searchable }
        }
        label(host, "${filtered.size} of ${entries.size} games", 12f)
        if (filtered.size > 100) label(host, "Showing 100 of ${filtered.size} matches. Refine your search to find more.", 12f)
        if (filtered.isEmpty() && entries.isNotEmpty()) label(host, "No matching games. Try another title or platform.")
        filtered.take(100).forEach { entry ->
            val card = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL; background = PilotGlass(dp(16).toFloat()); setPadding(dp(14), dp(8), dp(14), dp(12)) }
            host.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            label(card, entry.title, 17f)
            label(card, "${entry.platform.uppercase(Locale.ROOT)} · ${size(entry.sizeBytes)}", 12f, true)
            button(card, "Download to ${if (internal) "internal storage" else "SD card"}") {
                val destination = selected() ?: run { pick(internal); return@button }
                ThorDownloadService.start(app, entry, destination)
                renderJob()
            }
        }
    }
    private fun renderJob() {
        val host = jobHost ?: return
        val state = runCatching { jobs.state() }.getOrNull()
        val signature = state?.let { "${it.entry.id}:${it.target}:${it.status}:${it.bytes}:${it.message}:${ThorDownloadService.isRunning}" }.orEmpty()
        if (signature == renderedJob) return
        renderedJob = signature
        host.removeAllViews()
        if (state == null) return
        label(host, state.entry.title, 17f, true)
        label(host, state.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, 13f)
        val progress = ProgressBar(host.context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = if (state.entry.sizeBytes > 0) ((state.bytes.toDouble() / state.entry.sizeBytes) * 1000).toInt().coerceIn(0, 1000) else 0
        }
        host.addView(progress, LinearLayout.LayoutParams(-1, dp(6)))
        label(host, "${size(state.bytes)} / ${size(state.entry.sizeBytes)} · ${state.message}", 12f)
        val destination = runCatching { RomLibraryMetadata.documentId(Uri.parse(state.target)) }.getOrDefault("Selected ROMs folder")
        val storage = if (destination.substringBefore(':') == "primary") "Internal storage" else "SD card"
        val relativePath = runCatching { state.entry.destination() }.getOrDefault(state.entry.fileName)
        label(host, "$storage · $destination/$relativePath", 12f, true)
        when (state.status) {
            "queued", "connecting", "downloading", "verifying" -> {
                if (ThorDownloadService.isRunning) button(host, "Pause transfer") { ThorDownloadService.pause(app) }
                else button(host, "Resume transfer") { ThorDownloadService.resume(app) }
            }
            "paused", "error" -> button(host, "Resume transfer") { ThorDownloadService.resume(app) }
        }
        if (!ThorDownloadService.isRunning && state.status != "completed") button(host, "Cancel transfer…") {
            AlertDialog.Builder(host.context).setTitle("Cancel this download?")
                .setMessage("Remove this transfer's unfinished file. Existing games and saves stay in place.")
                .setNegativeButton("Keep download", null).setPositiveButton("Cancel transfer") { _, _ ->
                    runCatching { ThorDownloadService.cancel(app) }.onFailure { message = it.message ?: "Could not cancel transfer."; render() }
                }.show()
        }
    }
    private fun size(bytes: Long): String = if (bytes >= 1_000_000_000) String.format(Locale.ROOT, "%.2f GB", bytes / 1_000_000_000.0) else String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
}
