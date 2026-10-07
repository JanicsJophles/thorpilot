package dev.thorpilot

import android.app.Activity
import android.app.AlertDialog
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private val ink = Color.rgb(242, 246, 252)
    private val iris = Color.rgb(131, 222, 207)
    private val mist = Color.rgb(24, 43, 59)
    private lateinit var displays: DisplayManager
    private lateinit var store: ConnectionStore
    private lateinit var chatPanel: ChatPanel
    private var companionPage = ""
    private lateinit var body: LinearLayout
    private lateinit var pageScroll: ScrollView
    private val navigation = mutableMapOf<String, Button>()
    private var companion: Presentation? = null
    private var page = "home"
    private var requestText = "Connect your own ROMarr server to see your requests here."
    private var lowerEnabled = true
    private var active = false
    private var loading = false
    private var generation = 0
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        displays = getSystemService(DisplayManager::class.java)
        store = ConnectionStore(this)
        chatPanel = ChatPanel(this, store) { go("settings") }
        page = state?.getString("page") ?: "home"
        lowerEnabled = getPreferences(MODE_PRIVATE).getBoolean("lower", true)
        requestText = state?.getString("requests") ?: requestText
        render()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("page", page)
        out.putString("requests", requestText)
        super.onSaveInstanceState(out)
    }
    override fun onStart() {
        super.onStart()
        active = true
        displays.registerDisplayListener(this, null)
        showCompanion()
    }
    override fun onStop() {
        active = false
        displays.unregisterDisplayListener(this)
        companion?.dismiss()
        companion = null
        super.onStop()
    }
    override fun onDestroy() {
        generation++
        chatPanel.close()
        worker.shutdownNow()
        super.onDestroy()
    }
    override fun onDisplayAdded(id: Int) { showCompanion(); render() }
    override fun onDisplayRemoved(id: Int) { showCompanion(); render() }
    override fun onDisplayChanged(id: Int) { showCompanion() }

    private val muted = Color.rgb(174, 192, 208)
    private val paper = Color.argb(235, 27, 41, 56)
    private val lavender = Color.rgb(46, 62, 86)
    private fun dp(c: Context, value: Int) = (value * c.resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Float = 24f) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * resources.displayMetrics.density
        if (color != Color.TRANSPARENT) setStroke(dp(this@MainActivity, 1), 0x22c4e4ed)
    }
    private fun column(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 16))
        setBackgroundColor(Color.TRANSPARENT)
    }
    private fun label(c: Context, value: String, size: Float = 16f, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(ink)
        typeface = Typeface.create(if (bold) "sans-serif-rounded" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setPadding(0, dp(c, 3), 0, dp(c, 7))
        setLineSpacing(dp(c, 2).toFloat(), 1f)
    }
    private fun button(c: Context, value: String, action: () -> Unit) = Button(c).apply {
        text = value; isAllCaps = false; textSize = 14f; setTextColor(ink)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x3383DECF), shape(lavender, 15f), null)
        setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6))
        setOnClickListener { action() }
        setOnFocusChangeListener { view, focused ->
            view.scaleX = if (focused) 1.025f else 1f
            view.scaleY = if (focused) 1.025f else 1f
            view.alpha = if (focused) 1f else .94f
        }
        minHeight = dp(c, 48); minimumHeight = dp(c, 48)
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 6) }
    }
    private fun surface(c: Context, color: Int = paper) = column(c).apply {
        background = shape(color)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 12) }
    }
    private fun horizontal(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private fun addWeighted(row: LinearLayout, view: View, weight: Float = 1f, end: Int = 0) {
        row.addView(view, LinearLayout.LayoutParams(0, -2, weight).apply { marginEnd = dp(row.context, end) })
    }
    private fun card(parent: LinearLayout, title: String, description: String) {
        val box = surface(parent.context)
        box.addView(label(box.context, title, 21f, true))
        box.addView(label(box.context, description, 15f).apply { setTextColor(muted) })
        parent.addView(box)
    }
    private fun go(destination: String) {
        if (page == destination) return
        page = destination
        render()
        pageScroll.scrollTo(0, 0)
        showCompanion()
    }
    private fun render() {
        if (!::body.isInitialized) buildShell()
        navigation.forEach { (id, item) ->
            item.isSelected = page == id
            item.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x3383DECF), shape(if (page == id) Color.rgb(52, 80, 91) else Color.TRANSPARENT, 14f), null)
            item.setTextColor(if (page == id) iris else muted)
        }
        body.removeAllViews()
        when(page) {
            "chat" -> body.addView(chatPanel.createView(this))
            "device" -> devicePage()
            "requests" -> requestsPage()
            "settings" -> settingsPage()
            else -> workspacePage()
        }
    }
    private fun buildShell() {
        val root = column(this).apply { background = PilotWallpaper() }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(dp(this, 22) + bars.left, dp(this, 8) + bars.top,
                dp(this, 22) + bars.right, dp(this, 8) + bars.bottom)
            insets
        }
        val header = horizontal(this)
        header.addView(label(this, "Thorpilot", 27f, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label(this, if (companion?.isShowing == true) "●  Two screens, one workspace" else "●  Your handheld companion", 12f).apply { setTextColor(iris) })
        root.addView(header)
        val nav = horizontal(this).apply {
            background = shape(Color.argb(225, 19, 29, 41), 18f)
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
        }
        listOf("home" to "Workspace", "chat" to "Chat", "device" to "My Thor", "requests" to "Requests", "settings" to "Connection").forEach { (id, name) ->
            val item = button(this, name) { go(id) }.apply {
                isSelected = page == id
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x3383DECF), shape(if (page == id) Color.rgb(52, 80, 91) else Color.TRANSPARENT, 14f), null)
                setTextColor(if (page == id) iris else muted)
            }
            navigation[id] = item
            nav.addView(item, LinearLayout.LayoutParams(0, dp(this, 44), 1f))
        }
        root.addView(nav)
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(context, 14), 0, 0) }
        pageScroll = scroll
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    private fun toggleCompanion() {
        lowerEnabled = !lowerEnabled
        getPreferences(MODE_PRIVATE).edit().putBoolean("lower", lowerEnabled).apply()
        showCompanion(); render()
    }
    private fun workspacePage() {
        val wide = resources.configuration.screenWidthDp >= 620
        val layout = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val welcome = surface(this, Color.argb(205, 30, 52, 66))
        val hello = horizontal(this)
        val emblem = ImageView(this).apply { setImageDrawable(PilotEmblem()); contentDescription = "Thorpilot compass" }
        hello.addView(emblem, LinearLayout.LayoutParams(dp(this, 86), dp(this, 86)).apply { marginEnd = dp(this@MainActivity, 14) })
        hello.addView(label(this, "Good games.\nGreat company.", 29f, true), LinearLayout.LayoutParams(0, -2, 1f))
        welcome.addView(hello)
        welcome.addView(label(this, "A copilot for your next adventure.", 16f).apply { setTextColor(muted) })
        welcome.addView(button(this, "Find my next game") { go("chat") }.apply { setTextColor(mist); background = shape(iris, 18f) })
        welcome.addView(label(this, "Discover together. Keep your library close.", 12f).apply { setTextColor(muted) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val device = surface(this)
        val title = horizontal(this)
        addWeighted(title, label(this, Build.MODEL, 21f, true))
        title.addView(label(this, "● Ready", 12f).apply { setTextColor(Color.rgb(125, 218, 177)) })
        device.addView(title)
        device.addView(label(this, "${displays.displays.size} displays detected • Android ${Build.VERSION.RELEASE}", 14f).apply { setTextColor(muted) })
        device.addView(button(this, if (lowerEnabled) "Release companion screen" else "Use companion screen") { toggleCompanion() })
        actions.addView(device)
        val library = surface(this)
        library.addView(label(this, "Your library, within reach", 19f, true))
        library.addView(button(this, if (store.url.isBlank()) "Connect ROMarr" else "See my requests") { go(if (store.url.isBlank()) "settings" else "requests") })
        actions.addView(library)
        if (wide) { addWeighted(layout, welcome, 1.05f, 14); addWeighted(layout, actions) }
        else { layout.addView(welcome); layout.addView(actions) }
        body.addView(layout)
        body.addView(label(this, "D-pad to move  •  A to select       Game tuning is still being built.", 12f).apply { setTextColor(muted) })
    }
    private fun requestsPage() {
        val heading = horizontal(this)
        addWeighted(heading, label(this, "Your requests", 24f, true))
        heading.addView(button(this, if (loading) "Refreshing…" else "Refresh") { fetchRequests() }.apply { isEnabled = !loading }, LinearLayout.LayoutParams(dp(this, 125), dp(this, 48)))
        body.addView(heading)
        body.addView(label(this, "Updates from your connected library server", 13f).apply { setTextColor(muted) })
        requestText.split("\n\n").forEach { request ->
            val lines = request.lines()
            val box = surface(this)
            box.addView(label(this, lines.firstOrNull().orEmpty(), 19f, true))
            if (lines.size > 1) box.addView(label(this, lines[1].replace(" · ", "  /  "), 13f).apply { setTextColor(iris) })
            if (lines.size > 2) box.addView(label(this, lines.drop(2).joinToString("\n"), 14f).apply { setTextColor(muted) })
            body.addView(box)
        }
        body.addView(label(this, "View-only for now. Manage requests in your ROMarr server.", 12f).apply { setTextColor(muted) })
    }
    private fun devicePage() {
        val title = horizontal(this)
        addWeighted(title, label(this, "Meet your ${Build.MODEL}", 25f, true))
        title.addView(label(this, "Android ${Build.VERSION.RELEASE}", 13f).apply { setTextColor(muted) })
        body.addView(title)
        val screens = horizontal(this).apply { gravity = Gravity.TOP }
        displays.displays.forEachIndexed { index, display ->
            val mode = display.mode
            val box = surface(this)
            box.addView(label(this, if (index == 0) "Main display" else "Companion display", 19f, true))
            box.addView(label(this, "${mode.physicalWidth} × ${mode.physicalHeight}", 27f, true).apply { setTextColor(iris) })
            box.addView(label(this, "${mode.refreshRate.toInt()} Hz   •   Display ${display.displayId}", 13f).apply { setTextColor(muted) })
            addWeighted(screens, box, 1f, if (index < displays.displays.size - 1) 12 else 0)
        }
        body.addView(screens)
        body.addView(label(this, "Your play space", 19f, true))
        val apps = horizontal(this)
        listOf("Cocoon" to "rip.moth.cocoonshell", "Azahar" to "org.azahar_emu.azahar", "melonDualDS" to "me.magnum.melondualds").forEachIndexed { index, (name, pkg) ->
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            addWeighted(apps, button(this, if (intent == null) "$name • unavailable" else "Open $name") {
                if (intent != null) startActivity(intent)
            }.apply { isEnabled = intent != null; alpha = if (intent == null) .5f else 1f }, 1f, if (index < 2) 10 else 0)
        }
        body.addView(apps)
        body.addView(label(this, "Opening an app releases the companion display for your game.", 12f).apply { setTextColor(muted) })
    }
    private fun settingsPage() {
        body.addView(label(this, "Your library, your server", 24f, true))
        body.addView(label(this, "Connect a ROMarr instance with the optional game-requests adapter. Your API key stays on this device."))
        val address = EditText(this).apply {
            hint = "https://your-server.example"; setText(store.url)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            contentDescription = "ROMarr server address"; isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        }
        val token = EditText(this).apply {
            hint = "API key (leave blank to keep saved key)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            contentDescription = "ROMarr API key"; isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        listOf(address, token).forEach { field ->
            field.textSize = 15f; field.setTextColor(ink); field.setHintTextColor(muted)
            field.background = shape(paper, 14f)
            field.setPadding(dp(this, 16), dp(this, 12), dp(this, 16), dp(this, 12))
            body.addView(field, LinearLayout.LayoutParams(-1, dp(this, 54)).apply { bottomMargin = dp(this@MainActivity, 10) })
        }
        body.addView(button(this, "Save connection") {
            try {
                store.save(address.text.toString(), token.text.toString().ifBlank { store.token() })
                generation++
                requestText = "Connection saved. Refresh to load requests."
                go("requests")
            } catch (e: Exception) {
                AlertDialog.Builder(this).setTitle("Connection not saved")
                    .setMessage(if (e is IllegalArgumentException) e.message else "Unable to access secure storage. Clear the connection and try again.")
                    .setPositiveButton("OK", null).show()
            }
        })
        body.addView(button(this, "Forget connection") {
            generation++; store.clear(); requestText = "Connect your own ROMarr server to see your requests here."; render()
        })
    }
    private fun fetchRequests() {
        if (loading) return
        if (store.url.isBlank()) { go("settings"); return }
        val base = store.url
        val token = try { store.token() } catch (_: Exception) {
            requestText = "Saved key is unavailable. Save your connection again."; render(); return
        }
        val current = generation
        loading = true; render()
        worker.execute {
            val result = RequestClient().fetch(base, token).summary()
            runOnUiThread {
                if (!isDestroyed) {
                    loading = false
                    if (generation == current) requestText = result
                    if (page == "requests") render()
                }
            }
        }
    }
    private fun dockTile(c: Context, title: String, glyph: String, color: Int, action: () -> Unit): Button = button(c, "$glyph\n$title", action).apply {
        textSize = 17f
        contentDescription = title
        minHeight = dp(c, 92)
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44ffffff),
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, Color.rgb(Color.red(color)*2/3, Color.green(color)*2/3, Color.blue(color)*2/3))).apply {
                cornerRadius = dp(c, 23).toFloat(); setStroke(dp(c, 1), 0x55ffffff)
            }, null)
        elevation = dp(c, 3).toFloat()
    }
    private fun showCompanion() {
        val target = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != display?.displayId }
        if (!active || !lowerEnabled || target == null) {
            companion?.dismiss(); companion = null; return
        }
        if (companion?.display?.displayId == target.displayId && companion?.isShowing == true && companionPage == (if (page == "chat") "chat" else "dock")) return
        val panel = if (companion?.display?.displayId == target.displayId && companion?.isShowing == true) companion!! else {
            companion?.dismiss()
            Presentation(this, target)
        }
        val c = panel.context
        val content = column(c).apply { background = PilotWallpaper() }
        val header = horizontal(c)
        addWeighted(header, label(c, "Thorpilot", 24f, true))
        header.addView(label(c, "Companion", 12f).apply { setTextColor(iris) })
        content.addView(header)
        if (page == "chat") {
            content.addView(button(c, "Back to workspace") { go("home") })
            content.addView(chatPanel.createView(c, compact = true))
        } else {
        val greeting = horizontal(c)
        greeting.addView(ImageView(c).apply { setImageDrawable(PilotEmblem()); contentDescription = "Thorpilot compass" }, LinearLayout.LayoutParams(dp(c, 68), dp(c, 68)))
        addWeighted(greeting, label(c, "Where to next?", 26f, true))
        content.addView(greeting)
        val shortcuts = horizontal(c)
        addWeighted(shortcuts, dockTile(c, "My Thor", "◈", Color.rgb(92, 127, 206)) { go("device") }, 1f, 10)
        addWeighted(shortcuts, dockTile(c, "Requests", "↓", Color.rgb(151, 102, 203)) { go("requests") })
        content.addView(shortcuts)
        val second = horizontal(c)
        addWeighted(second, dockTile(c, "Open chat", "✦", Color.rgb(43, 148, 136)) { go("chat") }, 1f, 10)
        addWeighted(second, dockTile(c, "Connection settings", "⚙", Color.rgb(152, 105, 77)) { go("settings") })
        content.addView(second)
        }
        content.addView(button(c, "Release this screen") {
            lowerEnabled = false
            getPreferences(MODE_PRIVATE).edit().putBoolean("lower", false).apply()
            showCompanion(); render()
        }.apply { background = shape(Color.rgb(35, 47, 64), 15f) })
        val scroll = ScrollView(c).apply { setBackgroundColor(Color.TRANSPARENT); isFillViewport = true; addView(content) }
        panel.setContentView(scroll)
        panel.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(mist))
        try {
            if (!panel.isShowing) panel.show()
            panel.window?.setLayout(-1, -1)
            companion = panel
            companionPage = if (page == "chat") "chat" else "dock"
        } catch (_: WindowManager.InvalidDisplayException) { companion = null }
    }
}
