package dev.thorpilot

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic documents only. Never opens a SAF URI or touches emulator configuration. */
object ConfigSnapshotChecks {
    fun run(context: Context) {
        val directory = File(context.cacheDir, "config-snapshot-checks")
        directory.deleteRecursively()
        val store = ConfigSnapshotStore(directory)
        val uri = "content://synthetic/config.ini"
        val original = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) +
            "[Renderer]\r\nresolution_factor=1\r\n; untouched comment\r\n".toByteArray()
        var current = original.copyOf()
        var writes = 0
        fun read() = ByteArrayInputStream(current.copyOf())
        fun write(bytes: ByteArray) { writes++; current = bytes.copyOf() }
        fun failure(action: () -> Unit): Throwable = runCatching(action).exceptionOrNull()
            ?: error("Expected refusal")
        try {
            val baseline = store.backup(uri, "synthetic 2126.0", ::read)
            check(baseline.sizeBytes == original.size && baseline.sha256.length == 64)
            check(ConfigSnapshotStore(directory).snapshots().single() == baseline)
            check(failure { store.preview(baseline.id, "content://other/config.ini", ::read) } is ConfigSnapshotException)
            check(writes == 0)
            val unchanged = store.restore(store.preview(baseline.id, uri, ::read), ::read, ::write)
            check(!unchanged.changed && unchanged.recovery == null && writes == 0)

            current = "[Renderer]\nresolution_factor=2\n".toByteArray()
            val beforeRestore = current.copyOf()
            val preview = store.preview(baseline.id, uri, ::read)
            check(!preview.sameBytes && preview.currentSizeBytes == current.size)
            val restored = store.restore(preview, ::read, ::write)
            check(restored.changed && writes == 1 && current.contentEquals(original))
            check(restored.verifiedSha256 == baseline.sha256 && restored.recovery?.recoveryOf == baseline.id)
            val recovery = restored.recovery!!
            check(store.snapshots().size == 2)
            store.restore(store.preview(recovery.id, uri, ::read), ::read, ::write)
            check(current.contentEquals(beforeRestore)) // restore is exact, including original newlines/BOM

            // Old and recreated Activity stores must serialize the entire provider write/readback.
            val sharedDocument = AtomicReference("cross-instance trial".toByteArray())
            fun sharedRead() = ByteArrayInputStream(sharedDocument.get().copyOf())
            val otherStore = ConfigSnapshotStore(directory)
            val sharedPreview = store.preview(baseline.id, uri, ::sharedRead)
            val writing = CountDownLatch(1)
            val releaseWrite = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            val secondRead = CountDownLatch(1)
            val sharedWrites = AtomicInteger(0)
            val firstError = AtomicReference<Throwable?>(null)
            val secondError = AtomicReference<Throwable?>(null)
            val first = Thread {
                try {
                    store.restore(sharedPreview, ::sharedRead) { bytes ->
                        sharedWrites.incrementAndGet(); writing.countDown()
                        check(releaseWrite.await(5, TimeUnit.SECONDS))
                        sharedDocument.set(bytes.copyOf())
                    }
                } catch (e: Throwable) { firstError.set(e) }
            }
            val second = Thread {
                secondStarted.countDown()
                try {
                    otherStore.restore(sharedPreview, { secondRead.countDown(); sharedRead() }) {
                        sharedWrites.incrementAndGet(); sharedDocument.set(it.copyOf())
                    }
                } catch (e: Throwable) { secondError.set(e) }
            }
            try {
                first.start()
                check(writing.await(5, TimeUnit.SECONDS))
                second.start()
                check(secondStarted.await(5, TimeUnit.SECONDS))
                check(!secondRead.await(100, TimeUnit.MILLISECONDS))
            } finally {
                releaseWrite.countDown()
                first.join(5000); second.join(5000)
            }
            check(!first.isAlive && !second.isAlive && firstError.get() == null)
            check(secondError.get() is ConfigSnapshotException && sharedWrites.get() == 1)
            check(sharedDocument.get().contentEquals(original))

            val stale = store.preview(baseline.id, uri, ::read)
            current = "changed after preview".toByteArray()
            val beforeConflictWrites = writes
            check(failure { store.restore(stale, ::read, ::write) }.message!!.contains("changed since"))
            check(writes == beforeConflictWrites)

            // A change during recovery persistence is also refused, without overwrite.
            val latePreview = store.preview(baseline.id, uri, ::read)
            var reads = 0
            val lateFailure = failure {
                store.restore(latePreview, {
                    reads++
                    if (reads == 2) current = "changed during backup".toByteArray()
                    read()
                }, ::write)
            } as ConfigSnapshotException
            check(lateFailure.recoverySnapshotId != null && writes == beforeConflictWrites)
            check(store.snapshots().any { it.id == lateFailure.recoverySnapshotId })

            // Partial provider failure keeps the verified pre-write recovery copy.
            val expectedRecoveryBytes = current.copyOf()
            val partialPreview = store.preview(baseline.id, uri, ::read)
            val partialFailure = failure {
                store.restore(partialPreview, ::read) { bytes ->
                    current = bytes.copyOf(7)
                    throw IOException("synthetic interrupted write")
                }
            } as ConfigSnapshotException
            check(partialFailure.recoverySnapshotId != null)
            val partialRecovery = partialFailure.recoverySnapshotId!!
            store.restore(store.preview(partialRecovery, uri, ::read), ::read, ::write)
            check(current.contentEquals(expectedRecoveryBytes))

            // A provider claiming success but retaining/truncating bytes is not reported as restored.
            val verificationPreview = store.preview(baseline.id, uri, ::read)
            val verificationFailure = failure {
                store.restore(verificationPreview, ::read) { current = it.copyOf(3) }
            } as ConfigSnapshotException
            check(verificationFailure.message!!.contains("could not be verified"))
            check(verificationFailure.recoverySnapshotId != null)

            current = ByteArray(ConfigSnapshotStore.MAX_BYTES + 1)
            check(failure { store.backup(uri, "version", ::read) } is ConfigSnapshotException)
            check(failure { store.preview(baseline.id, uri, ::read) } is ConfigSnapshotException)
            current = ByteArray(ConfigSnapshotStore.MAX_BYTES) { 65 }
            val max = store.backup(uri, "version", ::read)
            check(max.sizeBytes == ConfigSnapshotStore.MAX_BYTES)
            store.delete(max.id)

            current = original.copyOf()
            val damaged = store.backup(uri, "version", ::read)
            File(directory, "${damaged.id}.json").writeText("{\"schema\":1}")
            val damageWrites = writes
            check(failure { store.preview(damaged.id, uri, ::read) } is ConfigSnapshotException)
            check(writes == damageWrites)
            store.delete(damaged.id)
            check(failure { store.delete("../../anything") } is IllegalArgumentException)

            // AtomicFile recovery restores the last complete record if only its .bak remains.
            val file = File(directory, "${baseline.id}.json")
            check(file.renameTo(File(file.path + ".bak")))
            check(ConfigSnapshotStore(directory).snapshots().any { it.id == baseline.id })

            while (store.snapshots().size < ConfigSnapshotStore.MAX_SNAPSHOTS) store.backup(uri, "version", ::read)
            current = "needs recovery slot".toByteArray()
            val fullPreview = store.preview(baseline.id, uri, ::read)
            val fullWrites = writes
            check(failure { store.restore(fullPreview, ::read, ::write) }.message!!.contains("full"))
            check(writes == fullWrites && store.snapshots().size == ConfigSnapshotStore.MAX_SNAPSHOTS)
        } finally { directory.deleteRecursively() }
    }
}
