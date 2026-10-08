package dev.thorpilot

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView

/** All writes use a disposable preference namespace; no network, game, or app launch. */
object SetupChecks {
    fun runPanel(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val name = "guided-setup-test-${java.util.UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        var browsed = 0; var finished = 0; var opened = 0
        var panel: SetupPanel? = null
        try {
            fun newPanel() = SetupPanel(context, { browsed++ }, { opened++ }, { finished++ }, preferencesName = name)
            panel = newPanel()
            var overview = panel.createView(context, SetupLayout.OVERVIEW)
            var root = panel.createView(context, SetupLayout.CONTROLS)
            fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
            fun click(tag: String) {
                val target = root.findViewWithTag<View>(tag)
                check(target != null) { "Missing setup action $tag" }
                if (target is CheckBox) {
                    val before = target.isChecked
                    target.performClick()
                    check(prefs.getBoolean(tag, before) != before) { "Checkpoint did not change: $tag" }
                } else check(target.performClick()) { "Action did not handle click: $tag" }
            }
            fun contains(view: View, text: String) = views(view).filterIsInstance<TextView>().any { it.text.contains(text) }
            check(views(overview).none { it is Button }) { "Top screen should be read-only" }
            check(contains(overview, "No account"))
            check(root.findViewWithTag<View>("services") == null)
            click("frontend-other"); click("frontend-ready")
            click("next"); click("browse")
            check(browsed == 1 && !prefs.getBoolean("folders-ready", false))
            click("folders-ready"); click("next"); click("players-ready"); click("next")
            click("test-problem"); click("next")
            check(contains(overview, "Let's check"))
            check(root.findViewWithTag<View>("finish") == null) { "Failed launch must not show all-set action" }
            click("retry")
            check(prefs.getInt("current-step", -1) == 3)
            click("test-success"); click("next")
            check(contains(overview, "4 of 4"))
            check(root.findViewWithTag<View>("finish") != null)
            panel.close(); panel = newPanel()
            overview = panel.createView(context, SetupLayout.OVERVIEW)
            root = panel.createView(context, SetupLayout.CONTROLS)
            check(contains(overview, "4 of 4"))
            check(prefs.getInt("current-step", -1) == 4)
            click("review-0")
            check(root.findViewWithTag<CheckBox>("frontend-ready").isChecked)
            click("frontend-cocoon")
            check(prefs.getBoolean("folders-ready", false))
            check(!prefs.getBoolean("frontend-ready", false) && !prefs.getBoolean("players-ready", false))
            check(!prefs.getBoolean("first-launch", false) && prefs.getString("test-outcome", "") == "untested")
            click("skip")
            check(finished == 1 && prefs.getBoolean("seen", false))
            check(!prefs.getBoolean("first-launch", false) && opened == 0)
            // A same-step rerender retains action identities for keyboard/controller focus.
            val beforeId = root.findViewWithTag<View>("frontend-cocoon").id
            click("details")
            check(root.findViewWithTag<View>("frontend-cocoon").id == beforeId)
            check(contains(overview, "A home"))
            // Old first-launch checkpoint migrates without clearing unrelated data.
            panel.close()
            prefs.edit().remove("test-outcome").putBoolean("first-launch", true).putInt("current-step", 3).commit()
            panel = newPanel(); root = panel.createView(context)
            check(prefs.getString("test-outcome", "") == "success")
            click("test-untested"); click("next")
            check(root.findViewWithTag<View>("finish") == null)
            check(!prefs.getBoolean("first-launch", true))
        } finally {
            panel?.close(); prefs.edit().clear().commit(); context.deleteSharedPreferences(name)
        }
    }
}
