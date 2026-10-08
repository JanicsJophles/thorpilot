package dev.thorpilot

import org.json.JSONObject

/** Real Android JSON round trips: restoring a legacy job must never change its destination. */
object TransferNamingChecks {
    fun run() {
        val name = "0004000000174600 Pokemon Super Mystery Dungeon (W).piratelegit.cia"
        val legacy = JSONObject().put("id", "naming-fixture").put("title", "Pokemon")
            .put("platform", "n3ds").put("file_name", name).put("size_bytes", 123)
            .put("sha256", "a".repeat(64))
        val old = ThorDownloadEntry.parse(legacy)
        check(old.fileName == name && old.originalFileName == null)
        check(ThorDownloadEntry.parse(old.json()).destination() == "n3ds/$name")
        val prepared = old.preparedForTransfer()
        check(prepared.originalFileName == name && prepared.fileName != name)
        check(ThorDownloadEntry.parse(prepared.json()) == prepared)
        check(ThorDownloadEntry.parse(prepared.json()).preparedForTransfer() == prepared)
        // Restoring even a prepared record uses its saved destination, not a future naming policy.
        val savedName = "Previously approved title.cia"
        check(ThorDownloadEntry.parse(prepared.json().put("file_name", savedName)).fileName == savedName)
    }
}
