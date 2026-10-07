package dev.thorpilot

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

/** Runs against Android's actual Keystore and Activity, without a test framework dependency. */
class DeviceChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            RequestChecks.run()
            ChatChecks.runChecks(targetContext)
            val store = ConnectionStore(targetContext, "connection-test")
            store.clear()
            check(runCatching { store.save("http://example.com", "test-token") }.isFailure)
            check(runCatching { store.save("https://user:pass@example.com", "test-token") }.isFailure)
            store.save("https://example.com", "test-token")
            check(ConnectionStore(targetContext, "connection-test").token() == "test-token")
            val saved = targetContext.getSharedPreferences("connection-test", 0).getString("token", "")!!
            check(!saved.contains("test-token"))
            // A second preference namespace must not decrypt a copied credential envelope.
            val other = ConnectionStore(targetContext, "connection-isolation-test")
            other.save("https://example.org", "other-test-token")
            targetContext.getSharedPreferences("connection-isolation-test", 0).edit().putString("token", saved).commit()
            check(runCatching { other.token() }.isFailure)
            other.clear()
            check(store.token() == "test-token")
            check(runCatching { store.save("https://example.com", "bad\nkey") }.isFailure)
            check(store.token() == "test-token")
            store.clear()
            check(store.url.isEmpty() && store.token().isEmpty())

            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            var uiFailure: Throwable? = null
            runOnMainSync {
                try {
                fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
                    (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
                fun click(text: String) {
                    views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == text }.performClick()
                }
                fun contains(text: String) = views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains(text) }
                val stableTab = views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "My Thor" }
                repeat(8) { click("Requests"); click("My Thor") }
                check(views(activity.window.decorView).any { it === stableTab }) { "Navigation rebuilt the stable tab shell" }
                click("My Thor")
                check(contains(android.os.Build.MODEL))
                check(contains("Display "))
                click("Requests")
                check(contains("Your requests"))
                click("Connection")
                check(contains("Save connection"))
                click("Workspace")
                check(contains(android.os.Build.MODEL))
                } catch (failure: Throwable) { uiFailure = failure }
            }
            uiFailure?.let { throw it }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: chat parsing/history, bounded request transport, redirects, error handling, HTTPS validation, Keystore persistence/isolation, encrypted storage, clear, device inventory, and native navigation\n") })
        } catch (e: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${e.javaClass.simpleName}: ${e.message} at ${e.stackTrace.firstOrNull()}\n") })
        }
    }
}
