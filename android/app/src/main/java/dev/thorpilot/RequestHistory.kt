package dev.thorpilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One bounded snapshot, scoped to the connection identity (including credential changes). */
class RequestHistory(context: Context, name: String = "request-history") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    data class Snapshot(val result: RequestResult, val checkedAt: Long)
    fun load(identity: String): Snapshot? {
        if (identity.isBlank() || prefs.getString("identity", "") != identity) return null
        val body = prefs.getString("body", null) ?: return null
        val result = RequestClient.parse(body)
        val time = prefs.getLong("checked-at", 0)
        return if (result.isSuccess && time > 0) Snapshot(result, time) else null
    }
    fun save(identity: String, result: RequestResult, checkedAt: Long): Boolean {
        if (identity.isBlank() || !result.isSuccess || checkedAt <= 0) return false
        val items = JSONArray()
        result.rows.forEach { row -> items.put(JSONObject().apply {
            put("game", row.title); put("platform", row.platform); put("status", row.status)
            put("detail", row.detail); put("progress", row.progress ?: JSONObject.NULL)
            put("review_required", row.reviewRequired); put("client_status", row.clientStatus); put("client_detail", row.clientDetail)
        }) }
        val warnings = JSONArray()
        result.clientWarnings.forEach { warning -> warnings.put(JSONObject().apply {
            put("client", warning.substringBefore(": ")); put("detail", warning.substringAfter(": ", ""))
        }) }
        val body = JSONObject().put("items", items).put("client_warnings", warnings).toString()
        if (body.toByteArray(Charsets.UTF_8).size > RequestClient.MAX_BYTES) return false
        return prefs.edit().putString("identity", identity).putString("body", body).putLong("checked-at", checkedAt).commit()
    }
    fun clear() { prefs.edit().clear().apply() }
}
