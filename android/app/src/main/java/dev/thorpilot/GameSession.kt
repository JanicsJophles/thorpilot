package dev.thorpilot

import android.content.Context
import java.util.UUID

/** Records only an explicit app handoff, never infers a running game or performance. */
class GameSession(context: Context, preferencesName: String = "game-session") {
    companion object {
        val ALLOWED_PACKAGES = setOf("dev.eden.eden_emulator", "org.azahar_emu.azahar", "me.magnum.melondualds", "rip.moth.cocoonshell")
        private val lock = Any()
    }
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    val yielded: Boolean get() = synchronized(lock) { prefs.getBoolean("yielded", false) }
    val lastPackage: String get() = synchronized(lock) {
        prefs.getString("last-package", "").orEmpty().takeIf { it in ALLOWED_PACKAGES }.orEmpty()
    }

    /** Persist before launching so recreation cannot reclaim a game's display. */
    fun begin(packageName: String): GameSessionSnapshot = synchronized(lock) {
        require(packageName in ALLOWED_PACKAGES) { "Unsupported companion app." }
        val revision = UUID.randomUUID().toString()
        val snapshot = GameSessionSnapshot(yielded, lastPackage, revision)
        check(prefs.edit().putBoolean("yielded", true).putString("last-package", packageName)
            .putString("revision", revision).commit()) { "Could not save screen handoff." }
        snapshot
    }

    /** A delayed launch failure cannot undo a newer launch or an explicit reclaim. */
    fun rollback(snapshot: GameSessionSnapshot): Boolean = synchronized(lock) {
        if (prefs.getString("revision", null) != snapshot.revision) return false
        check(prefs.edit().putBoolean("yielded", snapshot.previousYielded)
            .putString("last-package", snapshot.previousPackage)
            .putString("revision", UUID.randomUUID().toString()).commit()) { "Could not restore screen handoff." }
        true
    }

    /** This never changes the user's independent lower-screen preference. */
    fun reclaim() = synchronized(lock) {
        check(prefs.edit().putBoolean("yielded", false).putString("revision", UUID.randomUUID().toString())
            .commit()) { "Could not reclaim companion screen." }
    }

    /** Forget an unavailable app without taking a screen back from another app. */
    fun clearLast() = synchronized(lock) {
        check(prefs.edit().remove("last-package").putString("revision", UUID.randomUUID().toString())
            .commit()) { "Could not forget companion app." }
    }
}

class GameSessionSnapshot internal constructor(
    internal val previousYielded: Boolean,
    internal val previousPackage: String,
    internal val revision: String,
)
