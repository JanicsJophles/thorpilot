package dev.thorpilot

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/** Explicit device harness. Secrets come only from app-private storage; default is read-only status. */
class DownloadChecks : Instrumentation() {
    private lateinit var args: Bundle
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); args = arguments ?: Bundle(); start() }
    override fun onStart() {
        try {
            val mode = args.getString("mode") ?: "status"
            when (mode) {
                "configure" -> configure()
                "catalog" -> report("Catalog count: ${catalog().size}")
                "startInternal", "verifyExistingSd" -> {
                    val title = args.getString("title") ?: error("An exact title argument is required.")
                    val entry = catalog().singleOrNull { it.title == title } ?: error("No unique matching catalog title.")
                    val tree = selected(mode == "startInternal")
                    if (mode == "verifyExistingSd") requireExisting(tree, entry)
                    showPanel()
                    runOnMainSync { ThorDownloadService.start(targetContext, entry, tree) }
                    awaitTransfer()
                }
                "pause" -> { runOnMainSync { ThorDownloadService.pause(targetContext) }; waitForIdleSync() }
                "resume" -> { showPanel(); runOnMainSync { ThorDownloadService.resume(targetContext) }; awaitTransfer() }
                "ui" -> showPanel()
                "status" -> Unit
                else -> error("Unknown mode.")
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS $mode\n${status()}\n") })
        } catch (failure: Throwable) {
            // Exception bodies from parsers and network libraries can contain private fixture content.
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL ${failure.javaClass.simpleName}\n${status()}\n") })
        }
    }
    private fun configure() {
        val fixture = File(targetContext.filesDir, "download-test-config.json")
        require(fixture.isFile && fixture.length() in 1..32768)
        val json = JSONObject(fixture.readText())
        fun save(key: String, namespace: String) {
            json.optJSONObject(key)?.let { ConnectionStore(targetContext, namespace).save(it.getString("url"), it.getString("token")) }
        }
        save("primary", "downloads"); save("local", "downloads-local")
        require(ConnectionStore(targetContext, "downloads").url.isNotBlank())
        check(fixture.delete())
        report("Connections saved; private fixture removed.")
    }
    private fun catalog() = ThorDownloadClient(ConnectionStore(targetContext, "downloads").snapshot()).catalog()
    private fun selected(internal: Boolean): Uri {
        val saved = listOf("download-folders" to listOf(if (internal) "internal" else "sd"),
            "device-library" to listOf(if (internal) "internal" else "sd"))
            .flatMap { (name, keys) -> keys.mapNotNull { targetContext.getSharedPreferences(name, 0).getString(it, null)?.let(Uri::parse) } }
        val grants = targetContext.contentResolver.persistedUriPermissions.filter { it.isReadPermission && it.isWritePermission }.map { it.uri }
        return saved.distinct().firstOrNull { uri -> uri in grants && runCatching {
            (RomLibraryMetadata.documentId(uri).substringBefore(':') == "primary") == internal
        }.getOrDefault(false) } ?: error("No matching writable ROMs grant.")
    }
    /** This test mode cannot silently become a download if the SD copy is absent. */
    private fun requireExisting(tree: Uri, entry: ThorDownloadEntry) {
        val root = RomLibraryMetadata.documentId(tree)
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, "$root/${entry.destination()}")
        val found = targetContext.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use {
            it.moveToFirst() && it.getLong(0) == entry.sizeBytes
        } ?: false
        require(found) { "Existing SD file required." }
    }
    private fun awaitTransfer() {
        val seconds = (args.getString("waitSeconds")?.toLongOrNull() ?: 300).coerceIn(1, 900)
        val pauseAt = args.getString("pauseAfterBytes")?.toLongOrNull()
        val until = SystemClock.elapsedRealtime() + seconds * 1000
        var paused = false
        var nextReport = 0L
        while (SystemClock.elapsedRealtime() < until) {
            val state = ThorDownloadStore(targetContext).state()
            if (SystemClock.elapsedRealtime() >= nextReport) { report(status()); nextReport = SystemClock.elapsedRealtime() + 5000 }
            if (!paused && pauseAt != null && (state?.bytes ?: 0) >= pauseAt && state?.status == "downloading") {
                paused = true; runOnMainSync { ThorDownloadService.pause(targetContext) }
            }
            if (state?.status == "error") error("Transfer failed.")
            if (state?.status in setOf("completed", "paused")) {
                val stopBy = SystemClock.elapsedRealtime() + 5000
                while (ThorDownloadService.isRunning && SystemClock.elapsedRealtime() < stopBy) SystemClock.sleep(100)
                return
            }
            SystemClock.sleep(200)
        }
        // Leave a durable, cleanly stopped partial instead of killing an active test transfer.
        runOnMainSync { ThorDownloadService.pause(targetContext) }
        val stopBy = SystemClock.elapsedRealtime() + 10000
        while (ThorDownloadService.isRunning && SystemClock.elapsedRealtime() < stopBy) SystemClock.sleep(100)
        error("Timed out; transfer paused.")
    }
    private fun status(): String {
        val state = ThorDownloadStore(targetContext).state() ?: return "No download job"
        return "status=${state.status} bytes=${state.bytes}/${state.entry.sizeBytes} message=${state.message}"
    }
    private fun report(value: String) { sendStatus(0, Bundle().apply { putString("stream", "$value\n") }) }
    private fun showPanel() {
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForIdleSync()
        var failure: Throwable? = null
        runOnMainSync {
            try {
                fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
                fun click(label: String) { views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }.performClick() }
                click("My Thor"); click("Download to Thor")
                val labels = views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                check("Download to Thor" in labels && "Choose folder" in labels && "Connection" in labels && "Local route" in labels)
                check(labels.any { it.startsWith("SD card") } && labels.any { it.startsWith("Internal") })
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
        waitForIdleSync()
    }
}
