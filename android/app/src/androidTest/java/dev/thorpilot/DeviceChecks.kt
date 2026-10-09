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
    private var fixtureUri: String? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); fixtureUri = arguments?.getString("configFixtureUri"); start() }
    override fun onStart() {
        try {
            ConfigSnapshotChecks.run(targetContext)
            fixtureUri?.let { ConfigDocumentChecks.run(targetContext, it) }
            GameCareChecks.run(targetContext)
            GameCareDraftChecks.run(targetContext)
            GameSessionChecks.run(targetContext)
            RequestChecks.run()
            RequestHistoryChecks.run(targetContext)
            ChatChecks.runChecks(targetContext)
            CoverImageChecks.run()
            TransferNamingChecks.run()
            GameIdentityMetadataChecks.run(targetContext)
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
            MetadataPanelChecks.run(this, activity)
            var uiFailure: Throwable? = null
            runOnMainSync {
                try {
                GameCareChecks.runPanel(activity)
                SetupChecks.runPanel(activity)
                WidgetChecks.run(targetContext)
                TileChecks.run(targetContext)
                fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
                    (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
                fun click(text: String) {
                    views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == text }.performClick()
                }
                fun contains(text: String) = views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains(text) }
                val stableTab = views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "My Thor" }
                val beforeFocus = stableTab.foreground
                stableTab.requestFocusFromTouch()
                check(stableTab.foreground is PilotFocusRing) { "Controller focus has no visible ring" }
                val otherTab = views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Requests" }
                otherTab.requestFocusFromTouch()
                check(stableTab.foreground === beforeFocus) { "Focus left a stale decoration behind" }
                check(otherTab.foreground is PilotFocusRing) { "Focus ring did not follow navigation" }
                repeat(8) { click("Requests"); click("My Thor") }
                check(views(activity.window.decorView).any { it === stableTab }) { "Navigation rebuilt the stable tab shell" }
                click("My Thor")
                check(contains(android.os.Build.MODEL))
                check(contains("Display "))
                click("Requests")
                check(contains("Your requests"))
                click("Settings")
                check(contains("Save connection"))
                click("Home")
                check(contains(android.os.Build.MODEL))
                views(activity.window.decorView).filterIsInstance<PilotActionRow>()
                    .first { it.contentDescription.startsWith("Requests.") }.performClick()
                check(contains("Your requests")) { "Home request row did not open the request panel" }
                click("Home")
                views(activity.window.decorView).filterIsInstance<PilotActionRow>()
                    .first { it.contentDescription.startsWith("Game care.") }.performClick()
                check(contains("Configuration snapshots")) { "Home game care row lost its tools" }
                click("Inspect Eden settings")
                fun backButton() {
                    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_B))
                    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_B))
                }
                backButton()
                check(contains("Configuration snapshots")) { "B skipped the parent Game care screen" }
                backButton()
                check(views(activity.window.decorView).filterIsInstance<PilotActionRow>().isNotEmpty()) { "B did not return to Home" }
                check(!activity.isFinishing) { "B exited instead of navigating back" }
                fun shoulderKey(code: Int) {
                    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
                }
                shoulderKey(android.view.KeyEvent.KEYCODE_BUTTON_L1)
                check(contains("Save connection")) { "Left shoulder did not wrap Home to Settings" }
                val editor = views(activity.window.decorView).filterIsInstance<android.widget.EditText>().first()
                editor.requestFocusFromTouch()
                shoulderKey(android.view.KeyEvent.KEYCODE_BUTTON_R1)
                check(contains("Save connection")) { "Shoulder navigation interrupted text editing" }
                views(activity.window.decorView).filterIsInstance<Button>().first { it.text == "Settings" }.requestFocusFromTouch()
                shoulderKey(android.view.KeyEvent.KEYCODE_BUTTON_R1)
                check(views(activity.window.decorView).filterIsInstance<PilotActionRow>().isNotEmpty()) { "Right shoulder did not wrap Settings to Home" }
                val motionBefore = PilotPreferences.motion(activity)
                val hapticsBefore = PilotPreferences.haptics(activity)
                try {
                    click("Settings")
                    val motion = views(activity.window.decorView).filterIsInstance<android.widget.Switch>().first { it.text == "Interface animations" }
                    val haptics = views(activity.window.decorView).filterIsInstance<android.widget.Switch>().first { it.text == "Navigation haptics" }
                    motion.isChecked = false; haptics.isChecked = false
                    check(!PilotPreferences.animate(activity) && !PilotPreferences.haptics(activity))
                    click("Home"); click("Settings")
                    check(!views(activity.window.decorView).filterIsInstance<android.widget.Switch>().first { it.text == "Interface animations" }.isChecked)
                } finally {
                    PilotPreferences.setMotion(activity, motionBefore)
                    PilotPreferences.setHaptics(activity, hapticsBefore)
                }
                click("Home")
                } catch (failure: Throwable) { uiFailure = failure }
            }
            uiFailure?.let { throw it }
            var feedbackTarget: Button? = null
            runOnMainSync {
                fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
                    (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
                feedbackTarget = all(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Home" }
                feedbackTarget!!.requestFocusFromTouch()
                PilotFeedback.key(feedbackTarget, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER))
                PilotFeedback.key(feedbackTarget, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER))
            }
            android.os.SystemClock.sleep(500)
            runOnMainSync {
                try {
                    check(feedbackTarget!!.scaleX == 1f && feedbackTarget!!.scaleY == 1f) { "Rapid presses left the button scaled" }
                    check((feedbackTarget!!.foreground as PilotFocusRing).reveal == 1f) { "Focus reveal did not settle" }
                } catch (failure: Throwable) { uiFailure = failure }
            }
            uiFailure?.let { throw it }
            val sessionBefore = GameSession(targetContext).let { it.yielded to it.lastPackage }
            val quick = startActivitySync(ThorpilotTileService.launchIntent(targetContext))
            waitForIdleSync()
            runOnMainSync {
                try {
                    fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
                        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
                    fun button(text: String) = descendants(quick.window.decorView)
                        .filterIsInstance<Button>().first { it.text.toString() == text }
                    check(quick.currentFocus !is android.widget.EditText) { "Summon stole editor focus" }
                    descendants(quick.window.decorView).filterIsInstance<Button>()
                        .firstOrNull { it.text.toString() == "Compact" }?.performClick()
                    val compactHeight = quick.window.attributes.height
                    button("Expand").performClick()
                    check(quick.window.attributes.height >= compactHeight)
                    button("Compact").performClick()
                    check(quick.window.attributes.height == compactHeight)
                    button("Dismiss").performClick()
                    check(quick.isFinishing)
                    check(GameSession(targetContext).let { it.yielded to it.lastPackage } == sessionBefore)
                } catch (failure: Throwable) { uiFailure = failure }
            }
            uiFailure?.let { throw it }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: configuration snapshot recovery and game care store/panel and session handoff state, chat parsing/history, bounded request transport, redirects, error handling, HTTPS validation, Keystore persistence/isolation, encrypted storage, clear, device inventory, native navigation, and widget registration/layout/routing\n") })
        } catch (e: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${e.javaClass.simpleName}: ${e.message} at ${e.stackTrace.firstOrNull()}\n") })
        }
    }
}
