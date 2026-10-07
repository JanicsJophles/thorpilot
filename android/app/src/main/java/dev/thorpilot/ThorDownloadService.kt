package dev.thorpilot

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import android.provider.DocumentsContract as D
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Durable bounded parallel queue; each worker owns one SAF partial and never overwrites games. */
class ThorDownloadService : Service() {
    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        private const val CHANNEL = "library-downloads"
        private const val NOTICE = 47
        private var initialized = false
        @Volatile private var liveService: ThorDownloadService? = null
        private val active = java.util.concurrent.ConcurrentHashMap<String, Worker>()
        private val publicationLock = Any()
        @Synchronized fun initialize(context: Context) {
            if(!initialized) { ThorDownloadStore(context).recover(); initialized=true }
        }
        fun isActive(jobId: String): Boolean = active.containsKey(jobId)
        @Synchronized fun start(context: Context, entry: ThorDownloadEntry, targetUri: Uri) {
            initialize(context); entry.destination(); validateTree(targetUri)
            require(context.contentResolver.persistedUriPermissions.any { it.uri == targetUri && it.isWritePermission && it.isReadPermission }) {
                "Choose the ROMs folder and allow read/write access."
            }
            ThorDownloadStore(context).enqueue(entry,targetUri.toString())
            launch(context)
        }
        private fun launch(context: Context) {
            try { context.startForegroundService(Intent(context, ThorDownloadService::class.java)) }
            catch(e:Exception) {
                ThorDownloadStore(context).states().filter { it.status == "queued" && !isActive(it.jobId) }.forEach {
                    ThorDownloadStore(context,it.jobId).update("paused",it.bytes,"Android could not start the transfer. Open Downloads and resume.")
                }
                throw e
            }
        }
        fun refresh(context: Context) { initialize(context); launch(context) }
        /** Clears only this job's exact hidden partial, never a completed ROM. Call after pausing. */
        @Synchronized fun cancel(context: Context, jobId: String? = null) {
            initialize(context)
            val queue=ThorDownloadStore(context)
            val state=queue.state(jobId) ?: return
            require(!isActive(state.jobId)) { "Pause and wait for this transfer to stop before cancelling." }
            val store = ThorDownloadStore(context,state.jobId)
            val partial = store.prefs.getString("partial", null)
            if (partial != null) {
                val tree = Uri.parse(state.target); val rootId = validateTree(tree)
                val uri = Uri.parse(partial); val docId = D.getDocumentId(uri)
                val expectedName = ".thorpilot-${store.prefs.getString("job", "")}.partial"
                require(uri.authority == tree.authority && D.getTreeDocumentId(uri) == rootId &&
                    docId.startsWith(rootId.trimEnd('/') + "/") && docId.substringAfterLast('/') == expectedName) { "Partial ownership could not be verified." }
                val resolver = context.contentResolver
                val parentId = docId.substringBeforeLast('/')
                fun readableDirectory(id: String) {
                    val document = D.buildDocumentUriUsingTree(tree, id)
                    val readable = resolver.query(document,
                        arrayOf(D.Document.COLUMN_DOCUMENT_ID, D.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                        cursor.moveToFirst() && cursor.getString(0) == id && cursor.getString(1) == D.Document.MIME_TYPE_DIR
                    } ?: false
                    require(readable) { "Storage is unavailable. Reinsert the original card before cancelling." }
                }
                fun partialPresent(): Boolean {
                    // A missing-card provider can return an empty child cursor. Require real root
                    // and parent documents before interpreting an empty listing as deletion.
                    readableDirectory(rootId)
                    readableDirectory(parentId)
                    val children = D.buildChildDocumentsUriUsingTree(tree, parentId)
                    return resolver.query(children, arrayOf(D.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
                        var present = false
                        var count = 0
                        while (cursor.moveToNext()) {
                            require(++count <= 20000) { "Folder contains too many items." }
                            val childId = cursor.getString(0)
                            require(childId.startsWith(parentId.trimEnd('/') + "/")) { "Invalid storage response." }
                            if (childId == docId) present = true
                        }
                        // Recheck after enumeration in case the card was removed during the query.
                        readableDirectory(rootId)
                        readableDirectory(parentId)
                        present
                    } ?: error("Storage is unavailable. Reinsert the original card before cancelling.")
                }
                if (partialPresent()) {
                    val deletion = runCatching { D.deleteDocument(resolver, uri) }
                    if (!deletion.getOrDefault(false)) {
                        require(!partialPresent()) { "Could not remove the partial. Reinsert the card and retry." }
                    }
                }
            }
            queue.remove(state.jobId)
        }
        @Synchronized fun pause(context: Context, jobId: String? = null) {
            initialize(context)
            val state=ThorDownloadStore(context).state(jobId) ?: return
            if(state.status == "completed") return
            val store=ThorDownloadStore(context,state.jobId)
            check(store.prefs.edit().putBoolean("pause",true).commit())
            active[state.jobId]?.connection?.disconnect()
            if(!isActive(state.jobId)) store.update("paused",state.bytes,"Paused. Your partial download is saved.")
        }
        @Synchronized fun resume(context: Context, jobId: String? = null) {
            initialize(context)
            val state=ThorDownloadStore(context).state(jobId) ?: error("Choose a game first.")
            require(!isActive(state.jobId)) { "This download is already running." }
            require(state.status != "completed") { "This download is already complete." }
            val store=ThorDownloadStore(context,state.jobId)
            check(store.prefs.edit().putBoolean("pause",false).putString("status","queued").commit())
            launch(context)
        }
        private fun validateTree(tree: Uri): String {
            require(tree.scheme == "content" && tree.authority == "com.android.externalstorage.documents" && D.isTreeUri(tree)) { "Choose a local ROMs folder." }
            return D.getTreeDocumentId(tree).also { require(it.substringAfter(':').split('/').last().equals("ROMs",true)) { "Choose the ROMs folder itself." } }
        }
    }
    private val executor = Executors.newFixedThreadPool(3)
    private lateinit var store: ThorDownloadStore
    private var latestStartId = 0
    override fun onCreate() {
        super.onCreate(); initialize(this); store = ThorDownloadStore(this); liveService=this
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Game downloads",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun notification(text: String): Notification = Notification.Builder(this,CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Download to Thor").setContentText(text)
        .setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE))
        .addAction(Notification.Action.Builder(null,"Pause all",PendingIntent.getService(this,1,Intent(this,ThorDownloadService::class.java).setAction("pause"),PendingIntent.FLAG_IMMUTABLE)).build())
        .setOngoing(true).build()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId=startId
        startForeground(NOTICE,notification("Preparing downloads…"))
        if(intent?.action == "pause") {
            store.states().filter { it.status in ThorDownloadQueue.runningStatuses }.forEach { pause(this,it.jobId) }
        }
        schedule()
        return START_NOT_STICKY
    }
    private fun schedule(): Unit = synchronized(Companion) {
        val waiting=store.states().filter { it.status == "queued" }.map { it.jobId }
        ThorDownloadQueue.next(waiting,active.keys,store.parallelism).forEach { id ->
            val worker=Worker(id)
            worker.store.update("connecting",worker.store.state()?.bytes ?: 0,"Connecting to the download server…")
            active[id]=worker; isRunning=true
            executor.execute {
                try { worker.transfer() } catch(e:Exception) {
                    val paused=worker.store.prefs.getBoolean("pause",false)
                    worker.store.update(if(paused) "paused" else "error",worker.store.state()?.bytes ?: 0,
                        if(paused) "Paused. Your partial download is saved." else safeError(e))
                } finally {
                    worker.connection?.disconnect()
                    synchronized(Companion) { active.remove(id); isRunning=active.isNotEmpty() }
                    android.os.Handler(mainLooper).post { liveService?.let { if(!it.executor.isShutdown) it.schedule() } }
                }
            }
        }
        if(active.isEmpty()) { isRunning=false; if(stopSelfResult(latestStartId)) stopForeground(STOP_FOREGROUND_REMOVE) }
        else getSystemService(NotificationManager::class.java).notify(NOTICE,notification("${active.size} transferring · ${store.states().count { it.status == "queued" && !isActive(it.jobId) }} queued"))
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        store.states().filter { it.status in ThorDownloadQueue.runningStatuses }.forEach { pause(this,it.jobId) }
        stopSelf(startId)
    }
    override fun onDestroy() {
        synchronized(Companion) {
            if(liveService === this) liveService=null
            store.states().filter { it.status in ThorDownloadQueue.runningStatuses }.forEach { pause(this,it.jobId) }
            active.values.forEach { worker -> worker.store.prefs.edit().putBoolean("pause",true).commit(); worker.connection?.disconnect() }
            executor.shutdownNow()
        }
        super.onDestroy()
    }
    private fun safeError(e: Exception): String = when(e) {
        is SecurityException -> "Storage access changed. Choose the ROMs folder again."
        is java.io.IOException -> "Connection or storage interrupted. Resume to retry; the partial file is saved."
        is IllegalArgumentException, is IllegalStateException -> e.message?.take(220) ?: "Download could not be verified."
        else -> "Download stopped. Your existing games are untouched."
    }
    private data class Node(val uri: Uri,val name: String,val directory: Boolean,val size: Long)
    private inner class Worker(val jobId: String) {
    val store=ThorDownloadStore(this@ThorDownloadService,jobId)
    @Volatile var connection: HttpsURLConnection? = null
    private fun checkpoint() { check(!store.prefs.getBoolean("pause",false) && !Thread.currentThread().isInterrupted) { "Paused." } }
    private fun children(tree: Uri, parent: Uri): List<Node> {
        val parentId = D.getDocumentId(parent)
        val uri = D.buildChildDocumentsUriUsingTree(tree,parentId)
        return contentResolver.query(uri,arrayOf(D.Document.COLUMN_DOCUMENT_ID,D.Document.COLUMN_DISPLAY_NAME,D.Document.COLUMN_MIME_TYPE,D.Document.COLUMN_SIZE),null,null,null)?.use { c ->
            val rows = mutableListOf<Node>()
            while(c.moveToNext()) {
                require(rows.size < 20000) { "Folder contains too many items." }
                val id=c.getString(0); require(id.startsWith(parentId.trimEnd('/')+"/")) { "Invalid storage response." }
                rows += Node(D.buildDocumentUriUsingTree(tree,id),c.getString(1),c.getString(2)==D.Document.MIME_TYPE_DIR,if(c.isNull(3))0 else c.getLong(3))
            }; rows
        } ?: error("Storage unavailable. Reinsert the SD card.")
    }
    private fun sameName(a: String,b: String) = java.text.Normalizer.normalize(a,java.text.Normalizer.Form.NFC).equals(java.text.Normalizer.normalize(b,java.text.Normalizer.Form.NFC),true)
    private fun hash(uri: Uri): Pair<Long,String> {
        val md=MessageDigest.getInstance("SHA-256"); var size=0L
        contentResolver.openInputStream(uri)?.use { input -> val b=ByteArray(256*1024); while(true) { checkpoint(); val n=input.read(b); if(n<0) break; if(n==0) continue; md.update(b,0,n);size+=n } } ?: error("Could not read back the downloaded file.")
        return size to md.digest().joinToString("") { "%02x".format(it) }
    }
    fun transfer() {
        val state=store.state() ?: error("No saved download."); val entry=state.entry; val tree=Uri.parse(state.target)
        val (parent,folder)=synchronized(publicationLock) {
            checkpoint()
            val root=D.buildDocumentUriUsingTree(tree,validateTree(tree)); val rootChildren=children(tree,root)
            val entrySource=RomSyncPlan.Entry("${entry.platform}/${entry.fileName}",entry.sizeBytes,entry.sha256)
            val action=RomSyncPlan.plan(listOf(entrySource),emptyList(),rootChildren.filter { it.directory }.map { it.name }.toSet()).single()
            require(action.kind==RomSyncPlan.Kind.COPY) { action.reason }
            val folder=action.destinationPath!!.substringBefore('/')
            val dirs=rootChildren.filter { sameName(it.name,folder) }; require(dirs.size<=1 && dirs.all { it.directory }) { "Platform folder conflicts with an existing file." }
            val parent=dirs.singleOrNull()?.uri ?: D.createDocument(contentResolver,root,D.Document.MIME_TYPE_DIR,folder) ?: error("Could not create platform folder.")
            parent to folder
        }
        fun existing()=children(tree,parent).filter { sameName(it.name,entry.fileName) }
        val found=existing()
        if(found.isNotEmpty()) {
            require(found.size==1 && !found.single().directory) { "Existing destination conflict; nothing overwritten." }
            store.update("verifying",entry.sizeBytes,"Checking the existing game…")
            require(hash(found.single().uri)==(entry.sizeBytes to entry.sha256.lowercase())) { "A different file already exists. Nothing overwritten." }
            store.update("completed",entry.sizeBytes,"Already on this device · SHA-256 verified."); return
        }
        val partialName=".thorpilot-${store.prefs.getString("job","")}.partial"
        val saved=store.prefs.getString("partial",null)
        val partial=if(saved!=null) {
            val uri=Uri.parse(saved)
            require(children(tree,parent).any { it.uri==uri && it.name==partialName && !it.directory }) { "Saved partial is unavailable. Reinsert the original card." };uri
        } else {
            val uri=D.createDocument(contentResolver,parent,"application/octet-stream",partialName) ?: error("Could not create partial download.")
            check(store.prefs.edit().putString("partial",uri.toString()).commit());uri
        }
        var offset=children(tree,parent).single { it.uri==partial }.size
        require(offset in 0..entry.sizeBytes) { "Partial file size is invalid." }
        if(offset<entry.sizeBytes) {
            checkpoint()
            val routes=listOf("downloads-local","downloads").mapNotNull { name -> runCatching { ConnectionStore(this@ThorDownloadService,name).snapshot() }.getOrNull()?.takeIf { it.url.isNotBlank() && it.token.isNotBlank() } }
            require(routes.isNotEmpty()) { "Connect a download server first." }
            var conn: HttpsURLConnection?=null
            for(route in routes) {
                var candidate: HttpsURLConnection?=null
                try {
                    candidate=ThorDownloadClient(route).content(entry);connection=candidate
                    if(offset>0) { candidate.setRequestProperty("Range","bytes=$offset-");store.prefs.getString("etag",null)?.let { candidate.setRequestProperty("If-Range",it) } }
                    ThorDownloadProtocol.validateRange(candidate.responseCode,candidate.getHeaderField("Content-Range"),offset,entry.sizeBytes)
                    val etag=candidate.getHeaderField("ETag")
                    val previous=store.prefs.getString("etag",null)
                    require(offset==0L || previous==null || previous==etag) { "Remote file changed; partial file preserved." }
                    if(etag!=null) check(store.prefs.edit().putString("etag",etag).commit())
                    conn=candidate;break
                } catch(e:Exception) { candidate?.disconnect(); if(route===routes.last()) throw e }
            }
            val active=conn ?: error("Download server unavailable.")
            val routeLabel=if(routes.size>1 && active.url.host==java.net.URI(routes.first().url).host) "local" else "server"
            store.update("downloading",offset,"Downloading from $routeLabel…")
            try {
                contentResolver.openFileDescriptor(partial,"rw")?.use { descriptor ->
                    java.io.FileOutputStream(descriptor.fileDescriptor).use { output ->
                        require(output.channel.size()==offset) { "Partial file changed. Nothing appended." };output.channel.position(offset)
                        active.inputStream.use { input ->
                            val buffer=ByteArray(256*1024);var last=0L
                            while(true) {
                                checkpoint();val n=input.read(buffer);if(n<0)break;if(n==0)continue
                                require(offset+n<=entry.sizeBytes) { "Server sent more bytes than the manifest." }
                                output.write(buffer,0,n);offset+=n
                                val now=SystemClock.elapsedRealtime()
                                if(now-last>=500) { store.update("downloading",offset,"Downloading from $routeLabel…");last=now }
                            }
                        };output.flush();output.fd.sync()
                    }
                } ?: error("Cannot write the selected storage.")
            } finally { active.disconnect(); store.update("verifying",offset,"Checking SHA-256…") }
            require(offset==entry.sizeBytes) { "Download ended early. Resume to finish." }
        }
        checkpoint();store.update("verifying",offset,"Reading back and verifying SHA-256…")
        require(hash(partial)==(entry.sizeBytes to entry.sha256.lowercase())) { "Checksum mismatch. Partial preserved; the game was not installed." }
        synchronized(publicationLock) {
            checkpoint()
            require(existing().isEmpty()) { "Destination appeared during transfer. Nothing overwritten." }
            val result=D.renameDocument(contentResolver,partial,entry.fileName) ?: error("Could not finish the verified download.")
            require(children(tree,parent).any { it.uri==result && it.name==entry.fileName }) { "Storage renamed the file unexpectedly. Check the platform folder." }
            check(store.prefs.edit().remove("partial").commit())
        }
        store.update("completed",entry.sizeBytes,"Ready in $folder · SHA-256 verified.")
    }
}
}
