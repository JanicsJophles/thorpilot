package dev.thorpilot

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView

/** Exercises local onboarding in its own preferences; never opens apps, URLs, or storage pickers. */
object SetupChecks {
    fun runPanel(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Setup checks need the main thread" }
        val preferencesName = "guided-setup-instrumentation-${java.util.UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        var browseCount = 0
        var finishCount = 0
        var openCount = 0
        var panel: SetupPanel? = null
        try {
            fun newPanel() = SetupPanel(context, browseLibrary = { browseCount++ },
                openApp = { openCount++ }, finish = { finishCount++ }, preferencesName = preferencesName)
            panel = newPanel()
            var root = panel.createView(context)
            fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
            fun click(label: String) {
                val matches = views(root).filterIsInstance<Button>().filter { it.text.toString().contains(label) }
                check(matches.size == 1) { "Expected one setup button containing '$label'; found ${matches.size}" }
                check(matches.single().performClick())
            }
            fun confirm() {
                val checkbox = views(root).filterIsInstance<CheckBox>().single()
                check(!checkbox.isChecked)
                checkbox.performClick()
            }
            fun textContains(value: String) = views(root).filterIsInstance<TextView>().any { it.text.contains(value) }

            check(textContains("No account")) { "Local onboarding should explain the account-free path" }
            check(views(root).filterIsInstance<Button>().none { it.text.toString() == "Connect services" })
            click("Another frontend / already set up")
            confirm()
            click("Next step")
            click("Browse my games")
            check(browseCount == 1 && openCount == 0)
            check(!prefs.getBoolean("folders-ready", false)) { "Opening the browser must not mark folder setup done" }
            confirm()
            click("Next step")
            confirm()
            click("Next step")
            confirm()
            check(listOf("frontend-ready", "folders-ready", "players-ready", "first-launch").all {
                prefs.getBoolean(it, false)
            })
            check(!prefs.getBoolean("seen", false)) { "Checking steps alone must not dismiss onboarding" }

            panel.close()
            panel = newPanel()
            root = panel.createView(context)
            check(textContains("4 of 4")) { "Completed checkpoints did not survive recreation" }
            check(views(root).filterIsInstance<CheckBox>().single().isChecked)
            click("Choose your frontend")
            check(textContains("Another frontend / already set up ✓")) { "Frontend selection did not survive recreation" }
            check(views(root).filterIsInstance<CheckBox>().single().isChecked)
            click("Cocoon")
            check(prefs.getString("frontend", "") == "cocoon")
            check(prefs.getBoolean("folders-ready", false))
            check(listOf("frontend-ready", "players-ready", "first-launch").none {
                prefs.getBoolean(it, false)
            }) { "Switching frontend must reset its configuration and launch confirmations" }
            check(textContains("1 of 4"))
            click("I'll finish this later")
            check(finishCount == 1 && prefs.getBoolean("seen", false))
            check(!prefs.getBoolean("first-launch", false)) { "Skipping must not claim a successful launch" }
            check(openCount == 0) { "Checklist interaction unexpectedly opened an app" }
        } finally {
            panel?.close()
            prefs.edit().clear().commit()
            context.deleteSharedPreferences(preferencesName)
        }
    }
}
