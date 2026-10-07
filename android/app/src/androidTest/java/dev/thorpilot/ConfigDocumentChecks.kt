package dev.thorpilot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Opt-in physical SAF check. This cannot target an actual emulator configuration directory. */
object ConfigDocumentChecks {
    private const val FIXTURE_ID = "primary:Documents/ThorpilotTest/config.ini"
    private val marker = "# thorpilot-fixture\n".toByteArray(Charsets.UTF_8)

    fun run(context: Context, uriString: String) {
        val uri = Uri.parse(uriString)
        require(uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents")
        require(uri.query == null && uri.fragment == null)
        require(DocumentsContract.isDocumentUri(context, uri))
        require(DocumentsContract.getDocumentId(uri) == FIXTURE_ID) {
            "Physical configuration tests only accept the dedicated ThorpilotTest fixture."
        }
        require(uri.pathSegments.size == 2 && uri.pathSegments.first() == "document")
        val granted = context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        check(granted == PackageManager.PERMISSION_GRANTED) { "The fixture needs an explicit read/write SAF grant." }
        val document = AzaharConfigDocument(context, uri)
        check(document.name() == "config.ini")
        val original = document.validatedRead()
        fun marked(bytes: ByteArray) = bytes.size >= marker.size && bytes.copyOfRange(0, marker.size).contentEquals(marker)
        require(marked(original)) { "Refusing an unmarked file. No document was changed." }
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val originalHash = hash(original)
        val directory = File(context.cacheDir, "config-document-check-${UUID.randomUUID()}")
        val store = ConfigSnapshotStore(directory)
        var attemptedWrite = false
        var primaryFailure: Throwable? = null
        try {
            val snapshot = store.backup(uri.toString(), "synthetic SAF fixture") {
                ByteArrayInputStream(document.validatedRead())
            }
            check(snapshot.sha256 == originalHash)
            // Longer trial then shorter restore proves the real provider is truncated by "wt".
            val changed = original + "\n# integration-only temporary comment: restore must truncate these trailing bytes\n".toByteArray()
            check(marked(changed) && marked(document.read()))
            attemptedWrite = true
            document.write(changed)
            check(document.read().contentEquals(changed))
            val preview = store.preview(snapshot.id, uri.toString()) { ByteArrayInputStream(document.read()) }
            check(!preview.sameBytes && preview.currentSizeBytes > original.size)
            val result = store.restore(preview, { ByteArrayInputStream(document.read()) }) { bytes ->
                check(marked(bytes))
                document.write(bytes)
            }
            check(result.changed && result.recovery != null)
            check(result.verifiedSha256 == originalHash)
            val restored = document.validatedRead()
            check(restored.contentEquals(original) && hash(restored) == originalHash)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupVerified = !attemptedWrite
            try {
                if (attemptedWrite) {
                    // Exact allowlisted URI and initially verified marker are retained even if a
                    // provider failure left malformed bytes. Always put the fixture back.
                    check(marked(original))
                    document.write(original)
                    check(document.read().contentEquals(original)) { "Fixture cleanup could not be verified; private recovery copies are retained." }
                    cleanupVerified = true
                }
            } catch (cleanupFailure: Throwable) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure) else throw cleanupFailure
            } finally {
                if (cleanupVerified) directory.deleteRecursively()
            }
        }
    }
}
