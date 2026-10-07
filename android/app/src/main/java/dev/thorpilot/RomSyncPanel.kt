package dev.thorpilot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** User-selected local trees only. Mount events invalidate previews; they never start copies. */
class RomSyncPanel(context: Context, private val pick: (Boolean) -> Unit) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("rom-sync", Context.MODE_PRIVATE)
    private val access = RomTreeAccess(app)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var source: RomTreeAccess.Snapshot? = null
    private var destination: RomTreeAccess.Snapshot? = null
    private var plan: List<RomSyncPlan.Action>? = null
    private var message = "Choose the two ROMs folders, then preview the missing games."
    private var busy = false
    @Volatile private var generation = 0
    private var listening = false
    @Volatile private var closed = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidate("Storage changed. Reinsert the card if needed, then preview again. Nothing is copied automatically.")
        }
    }
    private fun selected(destination: Boolean): Uri? = prefs.getString(if (destination) "destination" else "source", null)?.let(Uri::parse)
    private fun invalidate(reason: String) {
        generation++; source = null; destination = null; plan = null; message = reason; render()
    }
    fun start() {
        if (listening || closed) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED); addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT); addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL); addDataScheme("file")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(receiver, filter)
        listening = true
    }
    fun stop() { if (listening) { app.unregisterReceiver(receiver); listening = false } }
    fun close() { stop(); closed = true; generation++; root = null; worker.shutdownNow() }
    fun resume() {
        if (closed || busy) return
        val roots = listOfNotNull(selected(false), selected(true))
        if (roots.isEmpty()) return
        val token = generation
        worker.execute {
            val available = roots.all { uri -> runCatching {
                val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                app.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
            }.getOrDefault(false) }
            main.post { if (!closed && token == generation && !available) invalidate("A selected ROMs folder is unavailable. Insert its storage or choose the folder again, then preview.") }
        }
    }
    fun accept(uri: Uri, isDestination: Boolean, flags: Int) {
        if (busy || closed) return
        runCatching {
            require(uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isTreeUri(uri)) { "Choose a ROMs folder with the Android folder picker." }
            require(DocumentsContract.getTreeDocumentId(uri).substringAfter(':').split('/').last().equals("ROMs", true)) { "Select the ROMs folder itself." }
            val required = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (isDestination) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
            require(flags and required == required) { "The destination needs read and write access; the source needs read access." }
            val other = selected(!isDestination)
            if (other != null && other.authority == uri.authority) {
                val a = DocumentsContract.getTreeDocumentId(uri).trimEnd('/').lowercase()
                val b = DocumentsContract.getTreeDocumentId(other).trimEnd('/').lowercase()
                require(a != b && !a.startsWith("$b/") && !b.startsWith("$a/")) { "Choose separate ROMs folders; one cannot contain the other." }
            }
            app.contentResolver.takePersistableUriPermission(uri, required)
            prefs.edit().putString(if (isDestination) "destination" else "source", uri.toString()).apply()
            invalidate("Folder saved. Preview both folders before copying.")
        }.onFailure { message = it.message ?: "Folder access could not be saved."; render() }
    }
    fun createView(context: Context): View = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; root = this; render() }
    private fun preview() {
        val from = selected(false) ?: return
        val to = selected(true) ?: return
        invalidate("Scanning and hashing both folders. Large libraries can take a while…")
        busy = true; render(); val token = generation
        worker.execute {
            val result = runCatching {
                val a = access.scan(from); val b = access.scan(to)
                Triple(a, b, RomSyncPlan.plan(a.entries, b.entries, b.folders))
            }
            main.post {
                if (!closed) {
                    busy = false
                    if (token == generation) result.onSuccess { (a, b, actions) ->
                        source = a; destination = b; plan = actions
                        message = "Preview ready. Review the counts and paths below before copying."
                    }.onFailure { message = it.message ?: "Could not scan these folders." }
                    render()
                }
            }
        }
    }
    private fun copyMissing() {
        val a = source ?: return; val b = destination ?: return
        val actions = plan?.filter { it.kind == RomSyncPlan.Kind.COPY } ?: return
        if (actions.isEmpty() || busy) return
        busy = true; plan = null; message = "Copying missing games and verifying their contents…"; render()
        val token = generation
        worker.execute {
            val result = runCatching { access.copy(a, b, actions) { progress ->
                check(!closed && token == generation) { "Storage changed. Preview again before continuing." }
                main.post { if (!closed && token == generation) { message = progress; render() } }
            } }
            main.post {
                if (!closed) {
                    busy = false; source = null; destination = null
                    message = result.fold({ "$it files copied and verified. Preview again to check the updated library." },
                        { "Copy stopped: ${it.message ?: "storage unavailable"}. Some files may already have completed. Preview again before retrying." })
                    render()
                }
            }
        }
    }
    private fun render() {
        val host = root ?: return; val c = host.context; host.removeAllViews()
        fun dp(n: Int) = (n * c.resources.displayMetrics.density).toInt()
        fun text(value: String, size: Float = 13f) { host.addView(TextView(c).apply {
            text = value; textSize = size; setTextColor(0xffd6e7ed.toInt()); setPadding(0, dp(8), 0, dp(8))
        }) }
        fun button(title: String, enabled: Boolean = true, action: () -> Unit) { host.addView(Button(c).apply {
            text = title; isAllCaps = false; isEnabled = enabled && !busy; setTextColor(0xff6cf7d0.toInt())
            background = PilotGlass(dp(16).toFloat()); setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }) }
        text("Library sync", 24f)
        text("Bring missing games into your existing ROMs layout. Choose a source and destination; reverse them to copy in the other direction. Existing files and saves stay untouched.")
        fun folder(uri: Uri?) = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrDefault("Selected folder") } ?: "Not selected"
        text("From · ${folder(selected(false))}")
        button("Choose source ROMs folder") { pick(false) }
        text("To · ${folder(selected(true))}")
        button("Choose destination ROMs folder") { pick(true) }
        button("Preview missing games", selected(false) != null && selected(true) != null) { preview() }
        button("Forget selected folders", selected(false) != null || selected(true) != null) {
            // Other features may hold grants to these URIs, so remove only this feature's selections.
            prefs.edit().remove("source").remove("destination").apply()
            invalidate("Folder selections cleared. Files were not changed.")
        }
        text(message)
        plan?.let { actions ->
            val counts = actions.groupingBy { it.kind }.eachCount()
            text("${counts[RomSyncPlan.Kind.COPY] ?: 0} to copy · ${counts[RomSyncPlan.Kind.SKIP] ?: 0} already present\n${counts[RomSyncPlan.Kind.CONFLICT] ?: 0} conflicts · ${counts[RomSyncPlan.Kind.REVIEW] ?: 0} need review · ${counts[RomSyncPlan.Kind.REJECT] ?: 0} excluded", 15f)
            actions.take(30).forEach { text("${it.kind} · ${it.destinationPath ?: it.source.relativePath}\n${it.reason}") }
            if (actions.size > 30) text("Showing the first 30 of ${actions.size} files. Counts include the complete preview.")
            button("Copy ${counts[RomSyncPlan.Kind.COPY] ?: 0} missing ${if (counts[RomSyncPlan.Kind.COPY] == 1) "file" else "files"}", (counts[RomSyncPlan.Kind.COPY] ?: 0) > 0) { copyMissing() }
        }
        text("No background sync yet. Inserting or removing storage clears the preview while Thorpilot is open. Preview and confirm each copy. Nintendo 3DS uses n3ds and CIA files; archives and .3ds files are excluded. CIA installation in Azahar is a separate step.")
    }
}
