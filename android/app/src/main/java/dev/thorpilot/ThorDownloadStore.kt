package dev.thorpilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ThorDownloadState(val entry: ThorDownloadEntry, val target: String, val status: String, val bytes: Long, val message: String, val jobId: String = "")

/** Queue metadata and each job commit independently; partial ownership survives upgrades. */
class ThorDownloadStore(private val context: Context, private val jobId: String? = null) {
    companion object { internal val lock = Any() }
    private val queue = context.getSharedPreferences("thor-download-queue", Context.MODE_PRIVATE)
    internal val prefs = context.getSharedPreferences(if(jobId == null) "thor-download-job" else "thor-download-job-$jobId", Context.MODE_PRIVATE)
    private fun ids(): List<String> = JSONArray(queue.getString("jobs", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
    private fun saveIds(ids: List<String>) { check(queue.edit().putString("jobs", JSONArray(ids).toString()).commit()) }
    private fun migrate() {
        if(queue.getBoolean("migrated", false)) return
        val old = context.getSharedPreferences("thor-download-job", Context.MODE_PRIVATE)
        if(old.contains("entry")) {
            val id = old.getString("job", null) ?: UUID.randomUUID().toString()
            val dest = context.getSharedPreferences("thor-download-job-$id", Context.MODE_PRIVATE).edit()
            old.all.forEach { (key,value) -> when(value) { is String -> dest.putString(key,value); is Long -> dest.putLong(key,value); is Boolean -> dest.putBoolean(key,value) } }
            check(dest.putString("job",id).commit())
            saveIds((ids() + id).distinct())
        }
        check(queue.edit().putBoolean("migrated",true).commit())
    }
    fun states(): List<ThorDownloadState> = synchronized(lock) { migrate(); ids().mapNotNull { ThorDownloadStore(context,it).state() } }
    fun state(id: String? = null): ThorDownloadState? = synchronized(lock) {
        if(id != null) return@synchronized ThorDownloadStore(context,id).state()
        if(jobId == null) return@synchronized states().lastOrNull()
        runCatching {
            val raw = prefs.getString("entry",null) ?: return@synchronized null
            ThorDownloadState(ThorDownloadEntry.parse(JSONObject(raw)),prefs.getString("target","")!!,prefs.getString("status","paused")!!,prefs.getLong("bytes",0),prefs.getString("message","")!!,requireNotNull(jobId))
        }.getOrNull()
    }
    val parallelism: Int get() = queue.getInt("parallelism",2).coerceIn(1,3)
    fun setParallelism(value: Int) { require(value in 1..3); check(queue.edit().putInt("parallelism",value).commit()) }
    internal fun enqueue(entry: ThorDownloadEntry,target: String): String = synchronized(lock) {
        migrate()
        require(ids().size < 100) { "Queue is full. Clear completed records before adding more games." }
        val key = ThorDownloadQueue.targetKey(target,entry.destination())
        require(states().none { ThorDownloadQueue.targetKey(it.target,it.entry.destination()) == key }) { "This destination already has a download. Resume it or remove its queue record first." }
        val id = UUID.randomUUID().toString()
        check(ThorDownloadStore(context,id).prefs.edit().putString("entry",entry.json().toString()).putString("target",target).putString("job",id).putString("status","queued").putString("message","Waiting for a transfer slot…").commit())
        saveIds(ids()+id); id
    }
    internal fun remove(id: String) = synchronized(lock) { saveIds(ids().filterNot { it == id }); check(ThorDownloadStore(context,id).prefs.edit().clear().commit()) }
    internal fun update(status: String, bytes: Long, message: String) {
        check(jobId != null)
        check(prefs.edit().putString("status",status).putLong("bytes",bytes).putString("message",message).commit())
    }
    internal fun recover() = synchronized(lock) {
        states().filter { it.status in ThorDownloadQueue.runningStatuses }.forEach {
            val store=ThorDownloadStore(context,it.jobId)
            store.prefs.edit().putBoolean("pause",true).commit()
            store.update("paused",it.bytes,"App restarted. Resume when ready; your partial is saved.")
        }
    }
}
