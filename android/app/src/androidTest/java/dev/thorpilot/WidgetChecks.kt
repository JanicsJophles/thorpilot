package dev.thorpilot

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Registration and RemoteViews checks, run on Android's main thread. */
object WidgetChecks {
    fun run(context: Context) {
        val registered = AppWidgetManager.getInstance(context).installedProviders.any {
            it.provider == ComponentName(context, ThorpilotWidget::class.java)
        }
        check(registered) { "Thorpilot widget provider is not registered" }
        for (page in listOf("home", "chat", "requests")) {
            val intent = ThorpilotWidget.launchIntent(context, page)
            check(intent.component == ComponentName(context, MainActivity::class.java))
            check(ThorpilotWidget.destination(intent) == page)
        }
        check(ThorpilotWidget.destination(Intent().putExtra(ThorpilotWidget.EXTRA_PAGE, "chat")) == null)
        check(ThorpilotWidget.destination(Intent(ThorpilotWidget.ACTION_OPEN).putExtra(ThorpilotWidget.EXTRA_PAGE, "settings")) == null)
        check(runCatching { ThorpilotWidget.launchIntent(context, "shell") }.isFailure)
        for (width in listOf(180, 320)) {
            val view = ThorpilotWidget.buildViews(context, width, 160).apply(context, null)
            check(view.findViewById<Button>(R.id.widget_chat).text == if (width < 250) "Chat" else "Find a game")
            check(view.findViewById<TextView>(R.id.widget_hint).visibility == if (width < 250) View.GONE else View.VISIBLE)
            check(view.findViewById<Button>(R.id.widget_requests).hasOnClickListeners())
        }
        val short = ThorpilotWidget.buildViews(context, 320, 120).apply(context, null)
        check(short.findViewById<TextView>(R.id.widget_hint).visibility == View.GONE)
    }
}
