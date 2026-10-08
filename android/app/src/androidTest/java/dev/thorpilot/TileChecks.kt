package dev.thorpilot

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Contract checks only; does not open System UI, add a tile or change the user's game session. */
object TileChecks {
    @Suppress("DEPRECATION")
    fun run(context: Context) {
        val session = GameSession(context)
        val yielded = session.yielded
        val lastPackage = session.lastPackage
        val intent = ThorpilotTileService.launchIntent(context)
        check(intent.component == ComponentName(context, SummonActivity::class.java))
        check(ThorpilotWidget.destination(intent) == null)
        check(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        check(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        check(!ThorpilotTileService.suppressesCompanion(intent))
        val activity = context.packageManager.getActivityInfo(intent.component!!, 0)
        check(!activity.exported)
        check(activity.taskAffinity == "dev.thorpilot.copilot")
        check(activity.flags and android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        check(!ThorpilotTileService.suppressesCompanion(ThorpilotWidget.launchIntent(context, "home")))
        check(!ThorpilotTileService.suppressesCompanion(Intent().putExtra(ThorpilotTileService.EXTRA_SUPPRESS_COMPANION, true)))
        check(!ThorpilotTileService.suppressesCompanion(null))
        val service = context.packageManager.getServiceInfo(ComponentName(context, ThorpilotTileService::class.java), 0)
        check(service.exported)
        check(service.permission == "android.permission.BIND_QUICK_SETTINGS_TILE")
        check(session.yielded == yielded && session.lastPackage == lastPackage) { "Building a tile handoff changed the game session" }
    }
}
