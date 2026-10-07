package dev.thorpilot

import android.content.Context

/** Isolated storage only: no emulator launches, games, or production settings. */
object GameSessionChecks {
    fun run(context: Context) {
        val name = "game-session-test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("lower", false).commit()
        try {
            val session = GameSession(context, name)
            check(!session.yielded && session.lastPackage.isEmpty())
            check(runCatching { session.begin("untrusted.example") }.isFailure)
            check(!session.yielded && session.lastPackage.isEmpty())
            val first = session.begin("org.azahar_emu.azahar")
            val restored = GameSession(context, name)
            check(restored.yielded && restored.lastPackage == "org.azahar_emu.azahar")
            check(restored.rollback(first))
            check(!session.yielded && session.lastPackage.isEmpty())
            check(!session.rollback(first)) // Rollback is one-shot.
            val old = session.begin("org.azahar_emu.azahar")
            val newer = session.begin("me.magnum.melondualds")
            check(!session.rollback(old))
            check(session.yielded && session.lastPackage == "me.magnum.melondualds")
            check(session.rollback(newer))
            check(session.yielded && session.lastPackage == "org.azahar_emu.azahar")
            val beforeReclaim = session.begin("rip.moth.cocoonshell")
            session.reclaim()
            check(!GameSession(context, name).yielded)
            check(session.lastPackage == "rip.moth.cocoonshell")
            check(!session.rollback(beforeReclaim))
            session.begin("org.azahar_emu.azahar")
            session.clearLast()
            check(session.yielded && session.lastPackage.isEmpty())
            check(!prefs.getBoolean("lower", true)) // Unrelated preferences preserved.
            prefs.edit().putString("last-package", "untrusted.example").commit()
            check(session.lastPackage.isEmpty())
        } finally { prefs.edit().clear().commit() }
    }
}
