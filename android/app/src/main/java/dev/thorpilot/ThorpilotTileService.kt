package dev.thorpilot

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** User-invoked app handoff, with no overlay, game monitoring or background polling. */
class ThorpilotTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Thorpilot"
            subtitle = "Open workspace"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openWorkspace() } else openWorkspace()
    }

    private fun openWorkspace() {
        try {
            val intent = launchIntent(this)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 700, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } else {
                openOnOlderAndroid(intent)
            }
        } catch (_: RuntimeException) {
            Toast.makeText(this, "Could not open Thorpilot. Open it from your launcher.", Toast.LENGTH_LONG).show()
        }
    }

    // The PendingIntent overload does not exist before API 34. This helper is
    // called only by the SDK-gated branch above, never on Android 14+.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openOnOlderAndroid(intent: Intent) = startActivityAndCollapse(intent)

    companion object {
        const val EXTRA_SUPPRESS_COMPANION = "dev.thorpilot.extra.SUPPRESS_COMPANION"

        fun launchIntent(context: Context): Intent = ThorpilotWidget.launchIntent(context, "home").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_SUPPRESS_COMPANION, true)
        }

        fun suppressesCompanion(intent: Intent?): Boolean =
            ThorpilotWidget.destination(intent) == "home" &&
                intent?.getBooleanExtra(EXTRA_SUPPRESS_COMPANION, false) == true

        fun requestAdd(activity: Activity) {
            fun message(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT < 33) {
                message("Open Quick Settings, tap Edit, then drag Thorpilot into your tiles.")
                return
            }
            val manager = activity.getSystemService(StatusBarManager::class.java)
            if (manager == null) {
                message("Open Quick Settings and use Edit to add Thorpilot.")
                return
            }
            try {
                manager.requestAddTileService(ComponentName(activity, ThorpilotTileService::class.java),
                    "Thorpilot", Icon.createWithResource(activity, R.drawable.ic_thorpilot_tile),
                    activity.mainExecutor) { result ->
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> message("Thorpilot added to Quick Settings.")
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> message("Thorpilot is already in Quick Settings.")
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> message("Tile not added. You can add it later from Quick Settings → Edit.")
                        else -> message("Could not add the tile here. Use Quick Settings → Edit.")
                    }
                }
            } catch (_: RuntimeException) {
                message("Could not add the tile here. Use Quick Settings → Edit.")
            }
        }
    }
}
