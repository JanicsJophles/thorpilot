package dev.thorpilot

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** Device-only choices, scoped to server credentials, platform and exact file content. */
class GameIdentityReviews(context: Context, name: String = "game-identity-reviews") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun key(connection: String, entry: ThorDownloadEntry): String = MessageDigest.getInstance("SHA-256")
        .digest("$connection\n${entry.platform}\n${entry.sha256.lowercase()}".toByteArray()).joinToString("") { "%02x".format(it) }
    fun load(connection: String, entry: ThorDownloadEntry): GameIdentityMetadata? {
        if (connection.isBlank()) return null
        return runCatching { prefs.getString(key(connection, entry), null)?.let {
            GameIdentityMetadata.parse(JSONObject(it), entry.platform, entry.sha256, trustDeviceReview = true)
        }?.takeIf { it.deviceReviewed } }.getOrNull()
    }
    fun save(connection: String, entry: ThorDownloadEntry, metadata: GameIdentityMetadata) {
        require(connection.isNotBlank() && metadata.deviceReviewed)
        require(GameIdentityMetadata.parse(metadata.json(), entry.platform, entry.sha256, trustDeviceReview = true) != null)
        val key = key(connection, entry)
        require(prefs.contains(key) || prefs.all.size < 500) { "Review storage is full. Reset an old choice first." }
        check(prefs.edit().putString(key, metadata.json().toString()).commit()) { "Could not save your choice." }
    }
    fun remove(connection: String, entry: ThorDownloadEntry) {
        check(prefs.edit().remove(key(connection, entry)).commit())
    }
}
