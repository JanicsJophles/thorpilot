package dev.thorpilot

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract as D

/** Metadata queries only: no ROM content reads, copies, writes or hashes. */
class RomLibraryMetadata(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    companion object {
        fun documentId(tree: Uri): String {
            require(tree.scheme == "content" && tree.authority == "com.android.externalstorage.documents" && D.isTreeUri(tree)) { "Choose a local ROMs folder." }
            return D.getTreeDocumentId(tree).also {
                require(it.substringAfter(':').split('/').last().equals("ROMs", true)) { "Select the ROMs folder itself." }
            }
        }
    }
    fun list(tree: Uri, cancellation: CancellationSignal): List<RomLibraryEntry> {
        val result = mutableListOf<RomLibraryEntry>()
        var visited = 0
        fun walk(parent: String, prefix: String, depth: Int) {
            require(depth <= 12) { "Folder nesting is too deep." }
            cancellation.throwIfCanceled()
            val children = mutableListOf<Pair<String, String>>()
            resolver.query(D.buildChildDocumentsUriUsingTree(tree, parent), arrayOf(D.Document.COLUMN_DOCUMENT_ID,
                D.Document.COLUMN_DISPLAY_NAME, D.Document.COLUMN_MIME_TYPE, D.Document.COLUMN_SIZE), null, null, null, cancellation)?.use { cursor ->
                while (cursor.moveToNext()) {
                    cancellation.throwIfCanceled()
                    require(++visited <= 20000) { "Library is too large for this view." }
                    val id = cursor.getString(0); val name = cursor.getString(1)
                    require(id.startsWith(parent.trimEnd('/') + "/") && name.isNotEmpty() && name !in setOf(".", "..") && '/' !in name && '\\' !in name) { "Storage returned an unsafe path." }
                    if (name.startsWith('.')) continue
                    val path = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (cursor.getString(2) == D.Document.MIME_TYPE_DIR) children += id to path
                    else if (RomLibraryEntry.visible(path)) result += RomLibraryEntry(path, if (cursor.isNull(3)) null else cursor.getLong(3).takeIf { it >= 0 })
                }
            } ?: error("Folder unavailable. Insert its storage or select it again.")
            children.forEach { (id, path) -> walk(id, path, depth + 1) }
        }
        walk(documentId(tree), "", 0)
        return result.sortedBy { it.path.lowercase(java.util.Locale.ROOT) }
    }
}
