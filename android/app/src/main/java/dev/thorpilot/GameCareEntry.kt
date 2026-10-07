package dev.thorpilot

import java.util.UUID

data class GameCareEntry(
    val id: String = UUID.randomUUID().toString(), val game: String = "",
    val symptom: String = "Texture", val scene: String = "", val original: String = "",
    val trial: String = "", val result: String = "Untested", val before: String = "",
    val after: String = "", val device: String = "", val emulator: String = "",
    val updated: Long = 0L, val emulatorId: String = "azahar",
    val gameRevision: String = "", val driver: String = "",
    val scope: String = "Unknown", val sourceId: String = ""
) {
    /** Reusing observations never carries forward success, current environment or a rollback value. */
    fun newTrial(device: String, emulatorVersion: String): GameCareEntry = copy(
        id = UUID.randomUUID().toString(), sourceId = id, device = device, emulator = emulatorVersion,
        original = "", before = "", after = "", result = "Untested", updated = 0L,
        driver = "", gameRevision = "", scope = "Unknown"
    )
}

