package dev.thorpilot

import android.content.Context
import org.json.JSONObject

data class ThorDownloadState(val entry: ThorDownloadEntry, val target: String, val status: String, val bytes: Long, val message: String)
class ThorDownloadStore(context: Context) {
    internal val prefs = context.getSharedPreferences("thor-download-job", Context.MODE_PRIVATE)
    fun state(): ThorDownloadState? = runCatching {
        val raw = prefs.getString("entry", null) ?: return null
        ThorDownloadState(ThorDownloadEntry.parse(JSONObject(raw)), prefs.getString("target", "")!!, prefs.getString("status", "paused")!!, prefs.getLong("bytes",0), prefs.getString("message", "")!!)
    }.getOrNull()
    internal fun update(status: String, bytes: Long, message: String) {
        check(prefs.edit().putString("status",status).putLong("bytes",bytes).putString("message",message).commit())
    }
}
