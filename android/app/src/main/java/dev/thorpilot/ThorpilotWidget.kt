package dev.thorpilot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/** A launcher entry point, not a second agent or a background polling service. */
class ThorpilotWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it, manager.getAppWidgetOptions(it)) }
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, manager, id, options)
    }
    private fun update(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        manager.updateAppWidget(id, buildViews(context, width, height))
    }
    companion object {
        const val ACTION_OPEN = "dev.thorpilot.action.OPEN_COMPANION"
        const val EXTRA_PAGE = "dev.thorpilot.extra.PAGE"
        private val pages = setOf("home", "chat", "requests")
        fun destination(intent: Intent?): String? =
            if (intent?.action == ACTION_OPEN) intent.getStringExtra(EXTRA_PAGE)?.takeIf { it in pages } else null
        fun launchIntent(context: Context, page: String): Intent {
            require(page in pages)
            return Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN
                putExtra(EXTRA_PAGE, page)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
        fun buildViews(context: Context, widthDp: Int, heightDp: Int): RemoteViews {
            val narrow = widthDp < 250
            return RemoteViews(context.packageName, R.layout.thorpilot_widget).apply {
                setTextViewText(R.id.widget_title, "Thorpilot")
                setTextViewText(R.id.widget_hint, "Your next adventure starts here")
                setViewVisibility(R.id.widget_hint, if (narrow || heightDp < 125) View.GONE else View.VISIBLE)
                setTextViewText(R.id.widget_chat, if (narrow) "Chat" else "Find a game")
                setTextViewText(R.id.widget_requests, "Requests")
                listOf(R.id.widget_header to "home", R.id.widget_chat to "chat", R.id.widget_requests to "requests").forEachIndexed { index, (view, page) ->
                    setOnClickPendingIntent(view, PendingIntent.getActivity(context, index, launchIntent(context, page), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
        }
    }
}
