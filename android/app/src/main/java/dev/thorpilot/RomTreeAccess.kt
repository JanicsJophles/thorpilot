package dev.thorpilot

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as D
import java.security.MessageDigest
import java.util.UUID
import java.util.Locale
import java.text.Normalizer

/** Local external-storage SAF access only. Never opens an existing destination for writing. */
class RomTreeAccess(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    data class Snapshot(val tree: Uri, val entries: List<RomSyncPlan.Entry>, val folders: Set<String>,
                        internal val documents: Map<String, Uri> = emptyMap())
    private data class Node(val uri: Uri, val name: String, val directory: Boolean, val size: Long?)
    private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    private fun id(tree: Uri): String {
        require(tree.scheme == "content" && tree.authority == "com.android.externalstorage.documents" && D.isTreeUri(tree)) {
            "Choose a local ROMs folder from device storage or the SD card."
        }
        return D.getTreeDocumentId(tree).also {
            require(it.substringAfter(':').split('/').last().equals("ROMs", true)) { "Select the ROMs folder itself." }
        }
    }
    private fun root(tree: Uri) = D.buildDocumentUriUsingTree(tree, id(tree))
    private fun children(tree: Uri, parent: Uri): List<Node> {
        check(!Thread.currentThread().isInterrupted) { "Sync cancelled." }
        val parentId = D.getDocumentId(parent)
        val uri = D.buildChildDocumentsUriUsingTree(tree, parentId)
        return resolver.query(uri, arrayOf(D.Document.COLUMN_DOCUMENT_ID, D.Document.COLUMN_DISPLAY_NAME,
            D.Document.COLUMN_MIME_TYPE, D.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            val result = mutableListOf<Node>()
            while (cursor.moveToNext()) {
                require(result.size < 20000) { "Folder has too many entries to scan safely." }
                val childId = cursor.getString(0)
                require(childId.startsWith(parentId.trimEnd('/') + "/")) { "Provider returned an item outside the selected folder." }
                val name = cursor.getString(1)
                require(name.isNotEmpty() && name !in setOf(".", "..") && '/' !in name && '\\' !in name) { "Unsafe filename." }
                result += Node(D.buildDocumentUriUsingTree(tree, childId), name, cursor.getString(2) == D.Document.MIME_TYPE_DIR, if (cursor.isNull(3)) null else cursor.getLong(3))
            }
            result
        } ?: error("Folder unavailable. Reinsert the card or select its ROMs folder again.")
    }
    private fun digest(uri: Uri, output: java.io.OutputStream? = null, progress: (Long) -> Unit = {}): Pair<Long, String> {
        val md = MessageDigest.getInstance("SHA-256")
        var size = 0L
        var lastProgress = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                check(!Thread.currentThread().isInterrupted) { "Sync cancelled." }
                val n = input.read(buffer)
                if (n < 0) break
                check(n > 0) { "Storage stopped responding." }
                size = Math.addExact(size, n.toLong())
                md.update(buffer, 0, n); output?.write(buffer, 0, n)
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastProgress >= 250) { progress(size); lastProgress = now }
            }
        } ?: error("File unavailable. Check that the card is inserted.")
        progress(size)
        return size to md.digest().joinToString("") { "%02x".format(it) }
    }
    fun scan(tree: Uri, hashContents: Boolean = true, progress: (String) -> Unit = {}): Snapshot {
        val entries = mutableListOf<RomSyncPlan.Entry>()
        val documents = mutableMapOf<String, Uri>()
        val folders = linkedSetOf<String>()
        fun walk(parent: Uri, prefix: String, depth: Int) {
            require(depth <= 12) { "Folder nesting is too deep." }
            for (node in children(tree, parent)) {
                if (node.name.startsWith('.')) continue
                val path = if (prefix.isEmpty()) node.name else "$prefix/${node.name}"
                if (node.directory) {
                    if (depth == 0) folders += node.name
                    walk(node.uri, path, depth + 1)
                } else if (depth > 0) {
                    // Save/config/artwork files stay untouched and are not candidates for transfer.
                    val ext = node.name.substringAfterLast('.', "").lowercase()
                    if (ext in setOf("txt", "sav", "srm", "state", "ini", "cfg", "json", "xml", "png", "jpg", "jpeg", "webp") || ext.matches(Regex("(?:ml|ds|ss)[0-9]+"))) continue
                    require(entries.size < 20000) { "Too many files for this sync preview." }
                    val (size, hash) = if (hashContents || node.size == null || node.size < 0)
                        digest(node.uri, progress = { bytes -> progress("Checking $path · ${bytes / (1024 * 1024)} MiB read") })
                    else node.size to ""
                    entries += RomSyncPlan.Entry(path, size, hash); documents[path] = node.uri
                }
            }
        }
        walk(root(tree), "", 0)
        return Snapshot(tree, entries, folders, documents)
    }
    fun preview(from: Uri, to: Uri, progress: (String) -> Unit = {}): Pair<Snapshot, Snapshot> {
        val source = scan(from, progress = progress)
        progress("Listing destination folders; unrelated games do not need checksums…")
        val inventory = scan(to, hashContents = false, progress = progress)
        val verified = RomSyncPlan.verifyDestination(source.entries, inventory.entries, inventory.folders) { entry ->
            val uri = inventory.documents[entry.relativePath] ?: error("Destination is no longer available.")
            val (size, hash) = digest(uri, progress = { bytes -> progress("Comparing ${entry.relativePath} · ${bytes / (1024 * 1024)} MiB read") })
            entry.copy(sizeBytes = size, sha256 = hash)
        }
        return source to inventory.copy(entries = verified)
    }
    fun copy(source: Snapshot, destination: Snapshot, actions: List<RomSyncPlan.Action>, progress: (String) -> Unit): Int {
        val a = key(id(source.tree)); val b = key(id(destination.tree))
        require(a != b && !a.startsWith("$b/") && !b.startsWith("$a/")) { "Choose separate, non-overlapping ROMs folders." }
        // Only execute actions recomputed from the exact approved snapshots.
        val allowed = RomSyncPlan.plan(source.entries, destination.entries, destination.folders)
        require(actions.all { it in allowed && it.kind == RomSyncPlan.Kind.COPY }) { "Preview changed. Scan again." }
        var copied = 0
        for (action in actions) {
            val path = requireNotNull(action.destinationPath)
            progress("Copying ${copied + 1} of ${actions.size}: $path")
            val parts = path.split('/')
            var parent = root(destination.tree)
            for (part in parts.dropLast(1)) {
                val matches = children(destination.tree, parent).filter { key(it.name) == key(part) }
                require(matches.size <= 1 && matches.all { it.directory }) { "Destination folder conflict. Scan again." }
                parent = matches.singleOrNull()?.uri ?: (D.createDocument(resolver, parent, D.Document.MIME_TYPE_DIR, part)
                    ?: error("Could not create destination folder."))
            }
            fun absent() = children(destination.tree, parent).none { key(it.name) == key(parts.last()) }
            require(absent()) { "Destination now exists; nothing overwritten. Scan again." }
            var temporary: Uri? = null
            try {
                temporary = D.createDocument(resolver, parent, "application/octet-stream", ".thorpilot-${UUID.randomUUID()}.partial")
                    ?: error("Could not create a temporary file. Check storage space and access.")
                val sourceUri = source.documents[action.source.relativePath] ?: error("Source is no longer available.")
                val copiedDigest = resolver.openOutputStream(temporary, "w")?.use { digest(sourceUri, it) }
                    ?: error("Could not write to destination storage.")
                require(copiedDigest == (action.source.sizeBytes to action.source.sha256)) { "Source changed during sync. Scan again." }
                require(digest(temporary) == copiedDigest) { "Copy verification failed." }
                progress("Finishing verified copy: $path")
                require(absent()) { "Destination appeared during sync; nothing overwritten." }
                val completed = D.renameDocument(resolver, temporary, parts.last()) ?: error("Could not finish the copy.")
                temporary = completed
                val node = children(destination.tree, parent).singleOrNull { it.uri == completed }
                require(node?.name == parts.last()) { "Destination changed during sync. Scan again." }
                temporary = null
                copied++
            } finally {
                // Only the new file created by this operation may be removed on failure.
                temporary?.let { runCatching { D.deleteDocument(resolver, it) } }
            }
        }
        return copied
    }
}
