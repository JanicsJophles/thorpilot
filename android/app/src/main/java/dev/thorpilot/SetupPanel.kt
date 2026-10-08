package dev.thorpilot

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

enum class SetupLayout { COMBINED, OVERVIEW, CONTROLS }

/** Local guide. A checkpoint records the user's confirmation, never inferred emulator configuration. */
class SetupPanel(
    context: Context,
    private val browseLibrary: () -> Unit,
    private val openApp: (String) -> Unit,
    private val finish: () -> Unit,
    private val connectServices: (() -> Unit)? = null,
    preferencesName: String = PREFS,
    private val advancedTools: (() -> Unit)? = null
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val hosts = linkedMapOf<SetupLayout, LinearLayout>()
    private val ids = mutableMapOf<String, Int>()
    private val titles = listOf("Your frontend", "Your games", "Your players", "First launch")
    private var detailOpen = false
    private val amber = 0xffffb547.toInt()
    private val mint = 0xff7ee2ae.toInt()
    private val lavender = 0xffb9a9ff.toInt()
    private val white = 0xfff4f2ec.toInt()
    private val muted = 0xffa9a69e.toInt()

    init {
        if (!prefs.contains("test-outcome")) prefs.edit()
            .putString("test-outcome", if (checked("first-launch")) "success" else "untested").apply()
        if (!prefs.contains("current-step")) prefs.edit()
            .putInt("current-step", (0..3).firstOrNull { !complete(it) } ?: 4).apply()
    }
    companion object {
        const val PREFS = "guided-setup"
        const val COCOON_PACKAGE = "rip.moth.cocoonshell"
        fun hasSeenSetup(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("seen", false)
    }
    private fun checked(key: String) = prefs.getBoolean(key, false)
    private fun frontend() = prefs.getString("frontend", "").orEmpty()
    private fun outcome() = prefs.getString("test-outcome", "untested").orEmpty()
    private fun complete(step: Int): Boolean = when (step) {
        0 -> frontend().isNotEmpty() && checked("frontend-ready")
        1 -> checked("folders-ready")
        2 -> checked("players-ready")
        else -> outcome() == "success"
    }
    private fun current() = prefs.getInt("current-step", (0..3).firstOrNull { !complete(it) } ?: 4).coerceIn(0, 4)
    private fun cocoonAvailable() = runCatching {
        app.packageManager.getLaunchIntentForPackage(COCOON_PACKAGE) != null
    }.getOrDefault(false)

    fun createView(context: Context, layout: SetupLayout = SetupLayout.COMBINED): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xff000000.toInt())
        setPadding(dp(this, 12), dp(this, 12), dp(this, 12), dp(this, 12))
        hosts[layout] = this
        render()
    }
    fun refresh() = render()
    fun close() { hosts.clear(); ids.clear() }
    private fun dp(view: View, n: Int) = (n * view.resources.displayMetrics.density).toInt()
    private fun label(parent: LinearLayout, value: String, size: Float = 14f, color: Int = muted, strong: Boolean = false) {
        parent.addView(TextView(parent.context).apply {
            text = value; textSize = size; setTextColor(color)
            typeface = Typeface.create(if (strong) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setPadding(0, dp(this, 5), 0, dp(this, 7))
            setLineSpacing(dp(this, 2).toFloat(), 1f)
        })
    }
    private fun surface(view: View, fill: Int, border: Int = 0xff2a2e38.toInt()) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(view, 13).toFloat(); setStroke(dp(view, 1), border)
    }
    private fun identify(view: View, layout: SetupLayout, action: String) {
        view.tag = action
        view.id = ids.getOrPut("$layout:$action") { View.generateViewId() }
    }
    private fun button(parent: LinearLayout, layout: SetupLayout, key: String, title: String,
                       primary: Boolean = false, selected: Boolean = false, action: () -> Unit) {
        parent.addView(Button(parent.context).apply {
            identify(this, layout, key)
            text = title; textSize = 14f; isAllCaps = false; gravity = Gravity.CENTER_VERTICAL or Gravity.START
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) 0xff1e1404.toInt() else if (selected) amber else white)
            val fill = if (primary) amber else if (selected) 0xff1c1f27.toInt() else 0xff13151b.toInt()
            val control = this
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), surface(control, fill, amber))
                addState(intArrayOf(android.R.attr.state_pressed), surface(control, fill, amber))
                addState(intArrayOf(), surface(control, fill, if (selected) amber else 0xff2a2e38.toInt()))
            }
            minHeight = dp(this, 48); minimumHeight = dp(this, 48)
            setPadding(dp(this, 14), dp(this, 6), dp(this, 14), dp(this, 6))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(parent, 6) })
    }
    private fun confirm(parent: LinearLayout, layout: SetupLayout, title: String, key: String) {
        parent.addView(CheckBox(parent.context).apply {
            identify(this, layout, key); text = title; textSize = 14f; setTextColor(white)
            buttonTintList = ColorStateList.valueOf(mint); minHeight = dp(this, 48)
            isChecked = checked(key)
            setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean(key, value).apply(); render() }
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun link(parent: LinearLayout, layout: SetupLayout, key: String, title: String, url: String) =
        button(parent, layout, key, title) {
            runCatching { parent.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { Toast.makeText(parent.context, "Open $url in your browser.", Toast.LENGTH_LONG).show() }
        }
    private fun selectFrontend(value: String) {
        if (value != frontend()) prefs.edit().putString("frontend", value)
            .putBoolean("frontend-ready", false).putBoolean("players-ready", false)
            .putBoolean("first-launch", false).putString("test-outcome", "untested").apply()
        render()
    }
    private fun setOutcome(value: String) {
        prefs.edit().putString("test-outcome", value).putBoolean("first-launch", value == "success").apply()
        render()
    }
    private fun go(step: Int) {
        prefs.edit().putInt("current-step", step.coerceIn(0, 4)).apply()
        detailOpen = false
        render(transition = true)
    }
    private fun leave() { prefs.edit().putBoolean("seen", true).apply(); finish() }

    private fun overview(root: LinearLayout, showRail: Boolean = true) {
        val step = current()
        val row = LinearLayout(root.context).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(row)
        val rail = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(this, 20), 0)
        }
        if (showRail) row.addView(rail, LinearLayout.LayoutParams(0, -2, 1f))
        label(rail, "thorpilot", 19f, white, true)
        titles.forEachIndexed { i, title ->
            label(rail, "${if (complete(i)) "✓" else if (step == i) "●" else "○"}  $title", 13f,
                if (complete(i)) mint else if (step == i) amber else muted, step == i)
        }
        label(rail, "Saved on this device", 11f, muted)
        val explanation = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL }
        row.addView(explanation, LinearLayout.LayoutParams(0, -2, 2.4f))
        label(explanation, if (step == 4) "YOUR SETUP" else "STEP ${step + 1} OF 4", 11f, amber, true)
        val heading = when (step) {
            0 -> "A home for your games."
            1 -> "Keep your games where they are."
            2 -> "Choose what plays them."
            3 -> "Try one. Find your rhythm."
            else -> if ((0..3).all(::complete)) "Your first game is ready." else if (outcome() == "problem") "Let's check that first launch." else "Pick up where you left off."
        }
        label(explanation, heading, 26f, white, true)
        label(explanation, when (step) {
            0 -> "A frontend brings your library together. Choose Cocoon, or keep the one you already use."
            1 -> "Use your SD card, internal storage, or both. Choose your existing ROMs folder; no copying needed."
            2 -> "Players are emulators: apps that run games for each system. Start with one system and add more later."
            3 -> "Launch a game from your frontend. Check its picture, sound, controls, and screen layout."
            else -> "${(0..3).count(::complete)} of 4 checkpoints confirmed by you. You can revisit any step; these are not automatic compatibility checks."
        }, 15f)
        label(explanation, if (step == 0) "No account, server, or API key needed." else "Your choices stay local. Continue at your own pace.", 12f, lavender)
    }
    private fun controls(root: LinearLayout, layout: SetupLayout) {
        val step = current()
        label(root, if (step == 4) "Your next move" else titles[step], 18f, white, true)
        when (step) {
            0 -> {
                button(root, layout, "frontend-cocoon", "Cocoon", selected = frontend() == "cocoon") { selectFrontend("cocoon") }
                button(root, layout, "frontend-other", "Another frontend / already set up", selected = frontend() == "other") { selectFrontend("other") }
                if (frontend() == "cocoon") {
                    val available = cocoonAvailable()
                    label(root, if (available) "Cocoon is available to open." else "Install Cocoon from its official site, then return here.", 12f, lavender)
                    if (available) button(root, layout, "open-cocoon", "Open Cocoon") { openApp(COCOON_PACKAGE) }
                    else link(root, layout, "get-cocoon", "Get Cocoon ↗", "https://cocoon-shell.com/")
                }
                if (frontend().isNotEmpty()) confirm(root, layout, "My frontend is set up", "frontend-ready")
            }
            1 -> {
                button(root, layout, "browse", "Browse my games") { browseLibrary() }
                confirm(root, layout, "I chose these folders in my frontend", "folders-ready")
            }
            2 -> {
                if (frontend() == "cocoon") link(root, layout, "emulator-guide", "Cocoon emulator setup ↗", "https://cocoon-shell.com/wiki/emulator-setup/")
                confirm(root, layout, "I configured the players I want to use", "players-ready")
            }
            3 -> {
                if (frontend() == "cocoon" && cocoonAvailable()) button(root, layout, "open-cocoon", "Open Cocoon and try a game") { openApp(COCOON_PACKAGE) }
                button(root, layout, "test-success", "✓  It plays well", selected = outcome() == "success") { setOutcome("success") }
                button(root, layout, "test-problem", "Something needs attention", selected = outcome() == "problem") { setOutcome("problem") }
                button(root, layout, "test-untested", "I haven't tested it yet", selected = outcome() == "untested") { setOutcome("untested") }
            }
            4 -> {
                titles.forEachIndexed { i, title ->
                    button(root, layout, "review-$i", "${if (complete(i)) "✓" else "○"}  $title${if (i == 3 && outcome() == "problem") " · Needs attention" else ""}") { go(i) }
                }
            }
        }
        if (step < 4) {
            button(root, layout, "details", "${if (detailOpen) "−" else "+"} More detail") { detailOpen = !detailOpen; render() }
            if (detailOpen) {
                label(root, when (step) {
                    0 -> "Use your frontend's official setup guide. Thorpilot can open Cocoon, but cannot verify its configuration. Other frontends can be opened from Android."
                    1 -> "Browsing here does not configure another app. Choose the same folders in your frontend. On Cocoon, add each platform's folders under Library & Data → Platforms, then Rescan Games."
                    2 -> "Choose a default player and ROM folder for each platform, then rescan. Some emulators need an extra setup step. Follow their official instructions."
                    else -> if (outcome() == "problem") "Check the selected player and game format in your frontend, then try launching the game inside the emulator. Leave the result as needing attention until you have tested a fix." else "Check picture, sound, input, and both screens. A successful launch is your confirmation, not a guarantee that every game or scene works."
                }, 13f, lavender)
                if (step == 0 && frontend() == "cocoon") link(root, layout, "getting-started", "Official getting started guide ↗", "https://cocoon-shell.com/wiki/getting-started/")
            }
        }
        val navigation = LinearLayout(root.context).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(navigation)
        fun navSlot(): LinearLayout = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            navigation.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(root, 6) })
        }
        if (step > 0) button(navSlot(), layout, "back", "← Back") { go(step - 1) }
        if (step < 4) button(navSlot(), layout, "next", if (step == 3) "Review setup →" else "Next →", primary = true) { go(step + 1) }
        else if ((0..3).all(::complete)) button(navSlot(), layout, "finish", "Start exploring →", primary = true) { leave() }
        else button(navSlot(), layout, "retry", if (outcome() == "problem") "Check first launch →" else "Continue setup →", primary = true) {
            go(if (outcome() == "problem") 3 else (0..3).firstOrNull { !complete(it) } ?: 3)
        }
        button(navSlot(), layout, "skip", "Finish later") { leave() }
        if (step == 4) {
            label(root, "Optional, whenever you're ready", 12f, lavender)
            connectServices?.let { button(root, layout, "services", "Connect services") { it() } }
            advancedTools?.let { button(root, layout, "tools", "Open device tools") { it() } }
        }
    }
    private fun render(transition: Boolean = false) {
        hosts.forEach { (layout, root) ->
            val focusTag = root.findFocus()?.tag as? String
            root.animate().cancel(); root.alpha = 1f; root.translationX = 0f
            root.removeAllViews()
            val wideCombined = layout == SetupLayout.COMBINED && root.resources.configuration.screenWidthDp >= 600
            root.orientation = if (wideCombined) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wideCombined) {
                val guidance = LinearLayout(root.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, dp(this, 24), 0)
                }
                val actions = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL }
                root.addView(guidance, LinearLayout.LayoutParams(0, -2, 1f))
                root.addView(actions, LinearLayout.LayoutParams(0, -2, 1f))
                overview(guidance, showRail = false)
                controls(actions, layout)
            } else {
                if (layout != SetupLayout.CONTROLS) overview(root)
                if (layout != SetupLayout.OVERVIEW) controls(root, layout)
            }
            if (focusTag != null) (root.findViewWithTag<View>(focusTag)
                ?: root.findViewWithTag<View>("next") ?: root.findViewWithTag<View>("retry") ?: root.findViewWithTag<View>("finish"))?.requestFocus()
            if (transition && ValueAnimator.areAnimatorsEnabled()) {
                root.alpha = .7f
                root.animate().alpha(1f).setDuration(140).start()
            }
        }
    }
}
