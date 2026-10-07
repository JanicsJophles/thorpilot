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
                "queue", "queueRecoveryStage", "queueRecoveryFinish" -> queueFixture(mode)
                "queueCleanup" -> cleanupQueueFixtures()
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
    /** Remove completed synthetic queue records only; the completed files remain untouched. */
    private fun cleanupQueueFixtures() {
        ThorDownloadService.initialize(targetContext)
        val fixtures = ThorDownloadStore(targetContext).states().filter { state ->
            runCatching {
                val tree = Uri.parse(state.target)
                tree.authority == "com.android.externalstorage.documents" &&
                    DocumentsContract.getTreeDocumentId(tree) == "primary:Documents/ThorpilotSyncTest/Target/ROMs" &&
                    state.entry.platform == "nds" && state.entry.fileName.startsWith("thorpilot-fixture-")
            }.getOrDefault(false)
        }
        require(fixtures.all { it.status == "completed" && !ThorDownloadService.isActive(it.jobId) })
        fixtures.forEach { ThorDownloadService.cancel(targetContext, it.jobId) }
        report("Removed ${fixtures.size} completed fixture queue records; no completed files deleted.")
    }
    /** Opt-in synthetic transfers: only an already granted, isolated test ROMs tree. */
    private fun queueFixture(mode: String) {
        val fixtureFile = File(targetContext.filesDir, "download-queue-test.json")
        require(fixtureFile.isFile && fixtureFile.length() in 1..32768)
        val fixture = JSONObject(fixtureFile.readText())
        val tree = Uri.parse(fixture.getString("treeUri"))
        require(tree.authority == "com.android.externalstorage.documents")
        require(DocumentsContract.getTreeDocumentId(tree) == "primary:Documents/ThorpilotSyncTest/Target/ROMs")
        require(targetContext.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission })
        val store = ThorDownloadStore(targetContext)
        ThorDownloadService.initialize(targetContext)
        val primary = ConnectionStore(targetContext, "downloads")
        val local = ConnectionStore(targetContext, "downloads-local")
        if (!fixture.has("restore")) {
            fun connection(store: ConnectionStore): JSONObject {
                val saved = store.snapshot()
                return JSONObject().put("url", saved.url).put("token", saved.token)
            }
            fixture.put("restore", JSONObject().put("primary", connection(primary)).put("local", connection(local))
                .put("parallelism", store.parallelism))
            fixtureFile.writeText(fixture.toString())
        }
        fun configureConnection(key: String, destination: ConnectionStore) {
            val value = fixture.getJSONObject(key)
            if (value.getString("url").isBlank()) destination.clear()
            else destination.save(value.getString("url"), value.getString("token"))
        }
        require(store.states().none { it.status in setOf("queued", "connecting", "downloading", "verifying") && it.target != tree.toString() })
        val idKey = if (mode == "queue") "entryIds" else "recoveryEntryIds"
        val ids = fixture.getJSONArray(idKey).let { rows -> (0 until rows.length()).map { rows.getString(it) } }
        require(ids.distinct().size == if (mode == "queue") 3 else 2)
        lateinit var entries: List<ThorDownloadEntry>
        fun jobs() = store.states().filter { it.target == tree.toString() && it.entry.id in ids }
        fun job(index: Int) = jobs().single { it.entry.id == ids[index] }
        fun await(label: String, seconds: Int = 60, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + seconds * 1000L
            while (SystemClock.elapsedRealtime() < end) {
                check(jobs().none { it.status == "error" })
                if (condition()) { report("PASS $label"); return }
                SystemClock.sleep(100)
            }
            error("Queue fixture timeout: $label")
        }
        fun stopFixtureWorkers() {
            jobs().filter { it.status in setOf("queued", "connecting", "downloading", "verifying") }.forEach { ThorDownloadService.pause(targetContext, it.jobId) }
            val end = SystemClock.elapsedRealtime() + 20000
            while (jobs().any { ThorDownloadService.isActive(it.jobId) } && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
            check(jobs().none { ThorDownloadService.isActive(it.jobId) })
        }
        var leaveForRecovery = false
        try {
            configureConnection("primary", primary)
            configureConnection("local", local)
            store.setParallelism(2)
            showPanel()
            entries = catalog().filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }
            require(entries.size == ids.size && entries.all { it.fileName.startsWith("thorpilot-fixture-") && it.platform == "nds" && it.sizeBytes >= 32L * 1024 * 1024 })
            if (mode != "queueRecoveryFinish") {
                // Never write a fixture over an existing final file, including prior test output.
                entries.forEach { entry ->
                    val document = DocumentsContract.buildDocumentUriUsingTree(tree, "${DocumentsContract.getTreeDocumentId(tree)}/${entry.destination()}")
                    val exists = runCatching { targetContext.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } ?: false }.getOrDefault(false)
                    require(!exists && jobs().none { it.entry.id == entry.id })
                }
                entries.forEach { entry -> runOnMainSync { ThorDownloadService.start(targetContext, entry, tree) } }
                await("two simultaneous transfers") { jobs().count { it.status == "downloading" && it.bytes > 0 } == 2 }
                if (mode == "queueRecoveryStage") {
                    leaveForRecovery = true
                    report("Recovery staged; force-stop app, then run queueRecoveryFinish.")
                    return
                }
                check(job(2).status == "queued")
                val before = jobs().size
                runOnMainSync { runCatching { ThorDownloadService.start(targetContext, entries[0], tree) } }
                check(jobs().size == before)
                report("PASS duplicate prevented; third transfer queued")
                val firstId = job(0).jobId
                runOnMainSync { ThorDownloadService.pause(targetContext, firstId) }
                await("per-job pause and queue advance") { job(0).status == "paused" && !ThorDownloadService.isActive(firstId) && job(2).bytes > 0 }
                val heldBytes = job(0).bytes
                require(heldBytes in 1 until entries[0].sizeBytes)
                SystemClock.sleep(300)
                check(job(0).bytes == heldBytes)
                runOnMainSync { ThorDownloadService.resume(targetContext, firstId) }
                await("paused transfer resumed") { job(0).bytes > heldBytes }
            } else {
                require(jobs().size == entries.size && jobs().all { it.bytes > 0 && it.status == "paused" && !ThorDownloadService.isActive(it.jobId) })
                report("PASS interrupted transfers recovered paused with saved bytes")
                jobs().filter { it.status != "completed" }.forEach { ThorDownloadService.resume(targetContext, it.jobId) }
            }
            await("all transfers completed", 300) { jobs().size == entries.size && jobs().all { it.status == "completed" } }
            entries.forEach { entry ->
                val document = DocumentsContract.buildDocumentUriUsingTree(tree, "${DocumentsContract.getTreeDocumentId(tree)}/${entry.destination()}")
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                requireNotNull(targetContext.contentResolver.openInputStream(document)).use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) { val count = input.read(buffer); if (count < 0) break; bytes += count; digest.update(buffer, 0, count) }
                }
                check(bytes == entry.sizeBytes && digest.digest().joinToString("") { "%02x".format(it) } == entry.sha256)
            }
            report("PASS independent readback SHA-256 for ${entries.size} fixture files")
        } finally {
            if (!leaveForRecovery) {
                stopFixtureWorkers()
                val saved = fixture.getJSONObject("restore")
                fun restore(key: String, destination: ConnectionStore) {
                    val value = saved.getJSONObject(key)
                    if (value.getString("url").isBlank()) destination.clear() else destination.save(value.getString("url"), value.getString("token"))
                }
                restore("primary", primary); restore("local", local)
                store.setParallelism(saved.getInt("parallelism"))
                fixture.remove("restore")
                fixtureFile.writeText(fixture.toString())
                if (mode == "queueRecoveryFinish" || !fixture.has("recoveryEntryIds")) check(fixtureFile.delete())
            }
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
