package dev.thorpilot

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Button

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
        for (width in listOf(220, 320)) {
            val view = ThorpilotWidget.buildViews(context, width, 64).apply(context, null)
            check(view.findViewById<Button>(R.id.widget_chat).text == if (width < 300) "Chat" else "Find a game")
            check(view.findViewById<Button>(R.id.widget_requests).hasOnClickListeners())
            check(view.findViewById<View>(R.id.widget_header).hasOnClickListeners())
            val density = context.resources.displayMetrics.density
            view.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((64 * density).toInt(), View.MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            for (id in listOf(R.id.widget_header, R.id.widget_chat, R.id.widget_requests)) {
                val target = view.findViewById<View>(id)
                check(target.height >= (48 * density).toInt()) { "Widget touch target is too short" }
                check(target.width >= (48 * density).toInt()) { "Widget touch target is too narrow" }
            }
        }
    }
}
