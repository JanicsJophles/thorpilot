package dev.thorpilot

import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

data class ConfigSnapshot(
    val id: String,
    val documentUri: String,
    val emulatorVersion: String,
    val createdAt: Long,
    val sha256: String,
    val sizeBytes: Int,
    /** A pre-restore recovery copy, not a suggested emulator setting. */
    val recoveryOf: String? = null
)

data class ConfigRestorePreview(
    val snapshot: ConfigSnapshot,
    val currentSha256: String,
    val currentSizeBytes: Int
) {
    val sameBytes: Boolean get() = snapshot.sha256 == currentSha256 && snapshot.sizeBytes == currentSizeBytes
}

data class ConfigRestoreResult(
    val snapshot: ConfigSnapshot,
    val recovery: ConfigSnapshot?,
    val verifiedSha256: String,
    val changed: Boolean
)

class ConfigSnapshotException(message: String, val recoverySnapshotId: String? = null, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Exact-byte, private-storage snapshots only: no generated edits and no emulator/provider access.
 * The caller supplies a newly opened stream for each read and an overwrite callback for the
 * selected document. The caller must ensure the emulator is stopped and its URI grant is valid.
 * AtomicFile protects each LOCAL snapshot. An external document write is NOT atomic; concurrent
 * emulator/provider writes cannot be locked here. Recovery copies are retained even on failure.
 */
class ConfigSnapshotStore(private val directory: File) {
    companion object {
        const val MAX_BYTES = 1024 * 1024
        const val MAX_SNAPSHOTS = 20
        private const val MAX_RECORD_BYTES = 1_500_000
        // Activity recreation can leave an older worker finishing a provider operation.
        private val processLock = Any()
        private val idPattern = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    init {
        check(directory.isDirectory || directory.mkdirs()) { "Could not open private configuration storage." }
    }

    private fun file(id: String): File {
        require(idPattern.matches(id)) { "Invalid snapshot ID." }
        return File(directory, "$id.json")
    }

    private fun boundedRead(read: () -> InputStream, limit: Int = MAX_BYTES): ByteArray = read().use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (count == 0) {
                val one = input.read()
                if (one == -1) break
                if (output.size() == limit) throw ConfigSnapshotException("Configuration exceeds the 1 MiB limit.")
                output.write(one)
            } else {
                if (output.size() > limit - count) throw ConfigSnapshotException("Configuration exceeds the 1 MiB limit.")
                output.write(buffer, 0, count)
            }
        }
        output.toByteArray()
    }

    private fun ids(): List<String> = directory.listFiles().orEmpty().mapNotNull { entry ->
        // AtomicFile's old .bak is recoverable if a process died mid-write.
        val name = entry.name.removeSuffix(".bak")
        if (name.endsWith(".json")) name.removeSuffix(".json").takeIf { idPattern.matches(it) } else null
    }.distinct()

    private fun load(id: String): Pair<ConfigSnapshot, ByteArray> = try {
        val raw = boundedRead({ AtomicFile(file(id)).openRead() }, MAX_RECORD_BYTES)
        val json = JSONObject(String(raw, Charsets.UTF_8))
        check(json.getInt("schema") == 1)
        val snapshot = ConfigSnapshot(json.getString("id"), json.getString("documentUri"),
            json.getString("emulatorVersion"), json.getLong("createdAt"), json.getString("sha256"),
            json.getInt("sizeBytes"), json.optString("recoveryOf").takeIf { it.isNotBlank() })
        check(snapshot.id == id && snapshot.sizeBytes in 0..MAX_BYTES)
        check(snapshot.documentUri.isNotBlank() && snapshot.documentUri.length <= 8192)
        check(snapshot.emulatorVersion.length <= 240 && snapshot.createdAt > 0)
        check(snapshot.recoveryOf == null || idPattern.matches(snapshot.recoveryOf))
        val bytes = Base64.getDecoder().decode(json.getString("bytes"))
        check(bytes.size == snapshot.sizeBytes && digest(bytes) == snapshot.sha256)
        snapshot to bytes
    } catch (e: Exception) {
        throw ConfigSnapshotException("Snapshot is missing or damaged. No document was changed.", cause = e)
    }

    fun snapshots(): List<ConfigSnapshot> = synchronized(processLock) {
        ids().map { load(it).first }.sortedByDescending { it.createdAt }
    }

    private fun persist(uri: String, version: String, bytes: ByteArray, recoveryOf: String? = null): ConfigSnapshot {
        require(uri.isNotBlank() && uri.length <= 8192) { "Select a valid configuration document." }
        require(version.length <= 240) { "Emulator version is too long." }
        check(bytes.size <= MAX_BYTES)
        if (ids().size >= MAX_SNAPSHOTS) throw ConfigSnapshotException(
            "Snapshot storage is full ($MAX_SNAPSHOTS copies). Remove an unneeded copy before continuing.")
        val snapshot = ConfigSnapshot(UUID.randomUUID().toString(), uri, version, System.currentTimeMillis(),
            digest(bytes), bytes.size, recoveryOf)
        val record = JSONObject().apply {
            put("schema", 1); put("id", snapshot.id); put("documentUri", uri); put("emulatorVersion", version)
            put("createdAt", snapshot.createdAt); put("sha256", snapshot.sha256); put("sizeBytes", bytes.size)
            put("recoveryOf", recoveryOf ?: ""); put("bytes", Base64.getEncoder().encodeToString(bytes))
        }.toString().toByteArray(Charsets.UTF_8)
        check(record.size <= MAX_RECORD_BYTES)
        val atomic = AtomicFile(file(snapshot.id))
        val stream = atomic.startWrite()
        try {
            stream.write(record)
            stream.fd.sync()
            atomic.finishWrite(stream) // fsync before atomic local-file rename
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw ConfigSnapshotException("Could not save the private backup. No document was changed.", cause = e)
        }
        // A successful return always means the durable copy was read and hash-verified.
        check(load(snapshot.id).second.contentEquals(bytes))
        return snapshot
    }

    fun backup(documentUri: String, emulatorVersion: String, read: () -> InputStream): ConfigSnapshot = synchronized(processLock) {
        persist(documentUri, emulatorVersion, boundedRead(read))
    }

    fun preview(snapshotId: String, documentUri: String, read: () -> InputStream): ConfigRestorePreview = synchronized(processLock) {
        val snapshot = load(snapshotId).first
        if (snapshot.documentUri != documentUri) throw ConfigSnapshotException(
            "This backup belongs to a different document. Select its original configuration file.")
        val current = boundedRead(read)
        return@synchronized ConfigRestorePreview(snapshot, digest(current), current.size)
    }

    fun restore(
        preview: ConfigRestorePreview,
        read: () -> InputStream,
        write: (ByteArray) -> Unit
    ): ConfigRestoreResult = synchronized(processLock) {
        val (snapshot, target) = load(preview.snapshot.id)
        if (snapshot != preview.snapshot) throw ConfigSnapshotException("Backup changed. Create a new restore preview.")
        val current = boundedRead(read)
        if (digest(current) != preview.currentSha256 || current.size != preview.currentSizeBytes)
            throw ConfigSnapshotException("Configuration changed since the preview. Nothing was written; preview it again.")
        if (current.contentEquals(target)) return@synchronized ConfigRestoreResult(snapshot, null, snapshot.sha256, false)
        // Never overwrite unless we have a verified exact copy of the current document to recover.
        val recovery = persist(snapshot.documentUri, snapshot.emulatorVersion, current, snapshot.id)
        try {
            // Saving the recovery copy takes time: detect changes during that interval as well.
            if (!boundedRead(read).contentEquals(current)) throw ConfigSnapshotException(
                "Configuration changed while saving recovery. Nothing was written; preview it again.", recovery.id)
            write(target.copyOf())
            val restored = boundedRead(read)
            if (!restored.contentEquals(target)) throw ConfigSnapshotException(
                "Restore could not be verified. The document may be partially written. The recovery copy is retained.", recovery.id)
            return@synchronized ConfigRestoreResult(snapshot, recovery, digest(restored), true)
        } catch (e: Exception) {
            if (e is ConfigSnapshotException && e.recoverySnapshotId != null) throw e
            throw ConfigSnapshotException(
                "Restore failed. The document may be partially written. The recovery copy is retained.", recovery.id, e)
        }
    }

    /** Explicit removal only; capacity never silently evicts a recovery backup. */
    fun delete(snapshotId: String) = synchronized(processLock) {
        AtomicFile(file(snapshotId)).delete()
        check(!file(snapshotId).exists() && !File(file(snapshotId).path + ".bak").exists()) { "Could not remove backup." }
    }
}
