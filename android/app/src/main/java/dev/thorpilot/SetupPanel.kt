package dev.thorpilot

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** A local checklist, not a claim that another app's settings have been verified. */
class SetupPanel(
    context: Context,
    private val browseLibrary: () -> Unit,
    private val openApp: (String) -> Unit,
    private val finish: () -> Unit,
    private val connectServices: (() -> Unit)? = null,
    preferencesName: String = PREFS
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private var host: LinearLayout? = null
    private var expanded = firstIncomplete()

    companion object {
        const val PREFS = "guided-setup"
        const val COCOON_PACKAGE = "rip.moth.cocoonshell"
        fun hasSeenSetup(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("seen", false)
    }

    private fun checked(key: String) = prefs.getBoolean(key, false)
    private fun frontend() = prefs.getString("frontend", "").orEmpty()
    private fun complete(step: Int): Boolean = when (step) {
        0 -> frontend().isNotEmpty() && checked("frontend-ready")
        1 -> checked("folders-ready")
        2 -> checked("players-ready")
        else -> checked("first-launch")
    }
    private fun firstIncomplete() = (0..3).firstOrNull { !complete(it) } ?: 3
    private fun cocoonAvailable() = runCatching {
        app.packageManager.getLaunchIntentForPackage(COCOON_PACKAGE) != null
    }.getOrDefault(false)

    fun createView(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        host = this
        render()
    }
    /** Call when returning from a browser or frontend so app detection is current. */
    fun refresh() = render()
    fun close() { host = null }

    private fun dp(n: Int) = (n * app.resources.displayMetrics.density).toInt()
    private fun label(parent: LinearLayout, value: String, size: Float = 13f, strong: Boolean = false) {
        parent.addView(TextView(parent.context).apply {
            text = value; textSize = size
            setTextColor(if (strong) 0xfff2f6fc.toInt() else 0xffafc1d0.toInt())
            if (strong) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(5), 0, dp(7))
        })
    }
    private fun button(parent: LinearLayout, title: String, action: () -> Unit) {
        parent.addView(Button(parent.context).apply {
            text = title; textSize = 14f; isAllCaps = false
            setTextColor(0xff6cf7d0.toInt()); background = PilotGlass(dp(14).toFloat())
            minHeight = dp(48); setPadding(dp(12), dp(5), dp(12), dp(5))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
    }
    private fun confirmation(parent: LinearLayout, title: String, key: String) {
        parent.addView(CheckBox(parent.context).apply {
            text = title; textSize = 13f; setTextColor(0xffd6e7ed.toInt())
            minHeight = dp(48); isChecked = checked(key)
            setOnCheckedChangeListener { _, value ->
                prefs.edit().putBoolean(key, value).apply()
                render()
            }
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun link(parent: LinearLayout, title: String, url: String) = button(parent, title) {
        runCatching { parent.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(parent.context, "No browser available. Open $url on another device.", Toast.LENGTH_LONG).show() }
    }
    private fun selectFrontend(value: String) {
        if (value != frontend()) prefs.edit().putString("frontend", value)
            .putBoolean("frontend-ready", false).putBoolean("players-ready", false)
            .putBoolean("first-launch", false).apply()
        render()
    }
    private fun leave() {
        prefs.edit().putBoolean("seen", true).apply()
        finish()
    }
    private fun render() {
        val root = host ?: return
        root.removeAllViews()
        label(root, "Make yourself at home", 24f, true)
        label(root, "A short path to your first game. No account, server, or API key needed.")
        label(root, "${(0..3).count(::complete)} of 4 checked · Progress stays on this device", 12f)
        val titles = listOf("Choose your frontend", "Find your games", "Choose your players", "Play your first game")
        titles.forEachIndexed { index, title ->
            button(root, "${if (complete(index)) "✓" else "${index + 1}"}  $title   ${if (expanded == index) "−" else "+"}") {
                expanded = if (expanded == index) -1 else index
                render()
            }
            if (expanded == index) {
                val body = LinearLayout(root.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(5), dp(12), dp(10))
                }
                root.addView(body)
                when (index) {
                    0 -> {
                        label(body, "Your frontend is the home for browsing and launching games.")
                        button(body, "Cocoon${if (frontend() == "cocoon") " ✓" else ""}") { selectFrontend("cocoon") }
                        button(body, "Another frontend / already set up${if (frontend() == "other") " ✓" else ""}") { selectFrontend("other") }
                        when (frontend()) {
                            "cocoon" -> {
                                val available = cocoonAvailable()
                                label(body, if (available) "Cocoon is ready to open." else "Cocoon is not available to open yet.", 12f)
                                if (available) button(body, "Open Cocoon") { openApp(COCOON_PACKAGE) }
                                else link(body, "Get Cocoon from its official site", "https://cocoon-shell.com/")
                                link(body, "Cocoon getting started", "https://cocoon-shell.com/wiki/getting-started/")
                                button(body, "Check again") { refresh() }
                                confirmation(body, "I have opened and set up Cocoon", "frontend-ready")
                            }
                            "other" -> {
                                label(body, "Open your chosen frontend from Android. If it is new, install it from its developer's official site and follow its setup guide. You can use Thorpilot alongside it.")
                                confirmation(body, "My frontend is ready", "frontend-ready")
                            }
                        }
                    }
                    1 -> {
                        label(body, "Already have a ROMs folder? Keep it where it is. Browse either your SD card or internal storage, then choose that location's ROMs folder.")
                        button(body, "Browse my games") { browseLibrary() }
                        label(body, "Choose the same game folders inside your frontend. Browsing here does not configure another app or move your files.", 12f)
                        confirmation(body, "I found my games and chose their folders in my frontend", "folders-ready")
                    }
                    2 -> {
                        label(body, "Players are emulators: apps that run games for each system.")
                        if (frontend() == "cocoon") {
                            label(body, "In Cocoon's platform settings, choose a default player and ROM folder for each system, then Rescan Games. Install any missing emulator using its official instructions.")
                            link(body, "Cocoon emulator setup guide", "https://cocoon-shell.com/wiki/emulator-setup/")
                            if (cocoonAvailable()) button(body, "Open Cocoon") { openApp(COCOON_PACKAGE) }
                        } else {
                            label(body, "In your frontend, choose an installed emulator for each system and point it to that system's game folder. Follow your frontend's official guide, then scan or refresh the library.")
                        }
                        label(body, "Some systems need an extra setup step inside the emulator. Start with one system, then add more.", 12f)
                        confirmation(body, "I configured the players I want to use", "players-ready")
                    }
                    3 -> {
                        label(body, "Launch a game from your frontend. Check the picture, sound, controls, and the screens you want to use. Come back when it feels right.")
                        if (frontend() == "cocoon" && cocoonAvailable()) button(body, "Open Cocoon and try a game") { openApp(COCOON_PACKAGE) }
                        confirmation(body, "I launched a game and checked that it works", "first-launch")
                        label(body, "You can revisit these steps anytime. All tools remain available under My Thor.", 12f)
                    }
                }
                if (index < 3) button(body, "Next step") { expanded = index + 1; render() }
            }
        }
        button(root, if ((0..3).all(::complete)) "Start exploring" else "I'll finish this later") { leave() }
        label(root, "Optional, whenever you're ready", 15f, true)
        label(root, "Library servers, game requests, and connected chat can be added later. Local setup works without them.", 12f)
        connectServices?.let { callback -> button(root, "Connect services") { callback() } }
    }
}
