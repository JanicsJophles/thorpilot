package dev.thorpilot

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.File

/** Exercises the real review dialog with offline data and disposable preferences/credentials. */
object MetadataPanelChecks {
    fun run(instrumentation: Instrumentation, activity: Activity) {
        check(Looper.myLooper() != Looper.getMainLooper())
        // Fixed namespace also reuses the test Keystore alias across instrumentation runs.
        val namespace = "metadata-panel-test"
        val connection = ConnectionStore(activity, namespace)
        val reviews = GameIdentityReviews(activity, namespace)
        val prefs = activity.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        lateinit var panel: DownloadPanel
        var panelCreated = false
        lateinit var root: View
        fun ui(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (e: Throwable) { failure = e } }
            instrumentation.waitForIdleSync()
            failure?.let { throw it }
        }
        fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        fun contains(view: View, text: String) = views(view).filterIsInstance<TextView>().any { it.text.toString() == text }
        fun dialogOrNull(): View? = WindowInspector.getGlobalWindowViews().firstOrNull { contains(it, "Review game metadata") }
        fun dialog(): View = requireNotNull(dialogOrNull()) { "Metadata review dialog did not open" }
        fun click(view: View, text: String) {
            check(views(view).filterIsInstance<Button>().first { it.text.toString() == text }.performClick())
        }
        fun inject(name: String, value: Any) {
            DownloadPanel::class.java.getDeclaredField(name).apply { isAccessible = true }.set(panel, value)
        }
        fun open() {
            ui { click(root, "Review title & artwork") }
            ui { check(contains(dialog(), "Possible matches")) }
        }
        fun title() = views(dialog()).filterIsInstance<EditText>().single()
        val identity = GameIdentityMetadata("n3ds", "c".repeat(64), "Fixture Adventure", "needs_review",
            providerIds = mapOf("igdb" to 111L), region = "USA",
            candidates = listOf(GameIdentityMetadata.Candidate("Correct Fixture Adventure", mapOf("igdb" to 222L))))
        val entry = ThorDownloadEntry("metadata-panel-fixture", "Fixture Adventure", "n3ds", "fixture.cia", 100,
            identity.sha256, metadata = identity)
        try {
            prefs.edit().clear().commit()
            connection.clear()
            connection.save("https://metadata-fixture.example", "offline-fixture-token")
            ui {
                panel = DownloadPanel(activity) { error("Review must not ask for a download folder") }
                panelCreated = true
                inject("store", connection)
                inject("reviews", reviews)
                inject("loading", true)
                inject("entries", listOf(entry))
                root = panel.createView(activity)
                check(contains(root, "Review before download"))
            }
            open()
            ui {
                check(title().text.toString() == "Fixture Adventure")
                check(contains(dialog(), "Source: fixture.cia"))
                check(contains(dialog(), "Use Correct Fixture Adventure"))
            }
            // Keep this local fixture capture for visual QA; it is not a production preview mode.
            android.os.SystemClock.sleep(350) // Let the dialog enter animation finish; off the main thread.
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(activity.cacheDir, "metadata-review-test.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            ui { title().setText(" "); click(dialog(), "Save choice") }
            ui {
                check(title().error != null)
                check(reviews.load(connection.identity, entry) == null)
                click(dialog(), "Cancel")
            }
            ui { check(dialogOrNull() == null) }

            open()
            ui { click(dialog(), "Use Correct Fixture Adventure") }
            ui {
                val selected = requireNotNull(GameIdentityReviews(activity, namespace).load(connection.identity, entry))
                check(selected.canonicalTitle == "Correct Fixture Adventure" && selected.providerIds["igdb"] == 222L && selected.deviceReviewed)
                check(dialogOrNull() == null)
                check(contains(root, "Correct Fixture Adventure") && contains(root, "Your saved metadata choice"))
                check(!contains(root, "Review before download"))
            }

            open()
            ui {
                check(title().text.toString() == "Correct Fixture Adventure")
                title().setText("Manually Corrected Fixture")
                click(dialog(), "Save choice")
            }
            ui {
                val manual = requireNotNull(reviews.load(connection.identity, entry))
                check(manual.canonicalTitle == "Manually Corrected Fixture" && manual.providerIds.isEmpty() && manual.coverUrl == null)
                check(contains(root, "Manually Corrected Fixture"))
            }
            open()
            ui { click(dialog(), "Reset choice") }
            ui {
                check(reviews.load(connection.identity, entry) == null)
                check(contains(root, "Fixture Adventure"))
                check(contains(root, "Review before download"))
                check(dialogOrNull() == null)
            }
        } finally {
            ui {
                dialogOrNull()?.let { view -> views(view).filterIsInstance<Button>().firstOrNull { it.text.toString() == "Cancel" }?.performClick() }
                if (panelCreated) panel.close()
            }
            connection.clear()
            prefs.edit().clear().commit()
        }
    }
}
