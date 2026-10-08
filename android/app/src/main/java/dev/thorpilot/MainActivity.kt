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
import javax.net.ssl.HttpsURLConnection

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private val ink = Color.rgb(244, 242, 236)
    private val iris = Color.rgb(255, 181, 71)
    private val mist = Color.BLACK
    private lateinit var displays: DisplayManager
    private lateinit var store: ConnectionStore
    private lateinit var chatPanel: ChatPanel
    private lateinit var gameSession: GameSession
    private lateinit var gameCare: GameCarePanel
    private lateinit var configSnapshots: ConfigSnapshotPanel
    private lateinit var edenInspector: EdenInspectorPanel
    private lateinit var romSync: RomSyncPanel
    private lateinit var deviceLibrary: DeviceLibraryPanel
    private lateinit var downloads: DownloadPanel
    private lateinit var setup: SetupPanel
    private var companionPage = ""
    private lateinit var body: LinearLayout
    private lateinit var pageScroll: ScrollView
    private val navigation = mutableMapOf<String, Button>()
    private var companion: Presentation? = null
    private var page = "home"
    private lateinit var requests: RequestPanel
    private var lowerEnabled = true
    private var companionSuppressed = false
    private var active = false

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) onBackPressed()
            return true
        }
        PilotFeedback.key(currentFocus, event)
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Uses Android back dispatch for the native view shell")
    override fun onBackPressed() {
        if (page == "home") { super.onBackPressed(); return }
        val parent = when (page) {
            "snapshots", "eden-inspector" -> "care"
            "downloads", "device-library", "library-sync", "setup" -> "device"
            else -> "home"
        }
        val previous = page
        go(parent)
        fun find(view: View): View? {
            if (view.tag == previous && view.isFocusable) return view
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        find(body)?.requestFocus()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        companionSuppressed = state?.getBoolean("companion-suppressed")
            ?: ThorpilotTileService.suppressesCompanion(intent)
        displays = getSystemService(DisplayManager::class.java)
        store = ConnectionStore(this)
        gameSession = GameSession(this)
        gameCare = GameCarePanel(this).apply { restoreState(state?.getBundle("game-care")) }
        configSnapshots = ConfigSnapshotPanel(this) { pickConfiguration() }
        edenInspector = EdenInspectorPanel(this) { global -> pickEdenConfig(global) }
        romSync = RomSyncPanel(this) { destination -> pickRomFolder(destination) }
        deviceLibrary = DeviceLibraryPanel(this, { internal -> pickLibraryFolder(internal) }, { pkg -> launchPlayApp(pkg) })
        downloads = DownloadPanel(this) { internal -> pickDownloadFolder(internal) }
        requests = RequestPanel(this, { go("settings") }) { title ->
            downloads.search(title); go("downloads")
        }
        setup = SetupPanel(this, { go("device-library") }, { pkg -> launchPlayApp(pkg) }, { go("home") }, { go("settings") }, advancedTools = { go("device") })
        chatPanel = ChatPanel(this, store) { go("settings") }
        val existingSetup = store.url.isNotBlank() || gameSession.lastPackage.isNotBlank() ||
            getSharedPreferences("device-library", MODE_PRIVATE).all.isNotEmpty()
        page = state?.getString("page") ?: ThorpilotWidget.destination(intent) ?:
            if (!SetupPanel.hasSeenSetup(this) && !existingSetup) "setup" else "home"
        lowerEnabled = getPreferences(MODE_PRIVATE).getBoolean("lower", true)
        render()
    }
    private fun pickDownloadFolder(internal: Boolean) {
        val request = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        try { startActivityForResult(request, if (internal) 4131 else 4130) }
        catch (_: RuntimeException) { Toast.makeText(this, "Could not open the folder picker.", Toast.LENGTH_LONG).show() }
    }
    private fun pickLibraryFolder(internal: Boolean) {
        val request = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        try { startActivityForResult(request, if (internal) 4121 else 4120) }
        catch (_: RuntimeException) { Toast.makeText(this, "Could not open the folder picker.", Toast.LENGTH_LONG).show() }
    }
    private fun pickRomFolder(destination: Boolean) {
        val request = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        try { startActivityForResult(request, if (destination) 4111 else 4110) }
        catch (_: RuntimeException) { Toast.makeText(this, "Could not open the folder picker.", Toast.LENGTH_LONG).show() }
    }
    private fun pickEdenConfig(global: Boolean) {
        val request = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE); type = "*/*"
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivityForResult(request, if (global) 4103 else 4102) }
        catch (_: RuntimeException) { Toast.makeText(this, "Could not open the file picker.", Toast.LENGTH_LONG).show() }
    }
    private fun pickConfiguration() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { startActivityForResult(intent, 4101) } catch (_: RuntimeException) {
            Toast.makeText(this, "Could not open the file picker on this device.", Toast.LENGTH_LONG).show()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode in 4130..4131 && resultCode == RESULT_OK) {
            val selected = data?.data ?: return
            go("downloads")
            downloads.accept(selected, requestCode == 4131, data.flags)
            return
        }
        if (requestCode in 4120..4121 && resultCode == RESULT_OK) {
            val selected = data?.data ?: return
            go("device-library")
            deviceLibrary.accept(selected, requestCode == 4121, data.flags)
            return
        }
        if (requestCode in 4110..4111 && resultCode == RESULT_OK) {
            val selected = data?.data ?: return
            go("library-sync")
            romSync.accept(selected, requestCode == 4111, data.flags)
            return
        }
        if (requestCode in 4102..4103 && resultCode == RESULT_OK) {
            val selected = data?.data ?: return
            if (selected.scheme == "content") { go("eden-inspector"); edenInspector.accept(selected, requestCode == 4103) }
            return
        }
        if (requestCode != 4101 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (uri.scheme != "content") {
            Toast.makeText(this, "Choose a document from the Android file picker.", Toast.LENGTH_LONG).show()
            return
        }
        val flags = data.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            require(flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            if (flags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } else {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            configSnapshots.acceptDocument(uri)
            go("snapshots")
        } catch (_: RuntimeException) {
            Toast.makeText(this, "Access could not be saved. Choose a document provider that supports persistent access.", Toast.LENGTH_LONG).show()
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tileEntry = ThorpilotTileService.suppressesCompanion(intent)
        if (tileEntry) {
            companionSuppressed = true
            showCompanion()
        }
        ThorpilotWidget.destination(intent)?.let {
            if (tileEntry && it == page) render() else go(it)
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBundle("game-care", gameCare.saveState())
        out.putString("page", page)
        out.putBoolean("companion-suppressed", companionSuppressed)
        super.onSaveInstanceState(out)
    }
    override fun onStart() {
        super.onStart()
        active = true
        setup.refresh()
        romSync.start()
        downloads.start()
        displays.registerDisplayListener(this, null)
        showCompanion()
        if (page == "setup") render()
    }
    override fun onResume() {
        super.onResume()
        romSync.resume()
    }
    override fun onStop() {
        romSync.stop()
        downloads.stop()
        active = false
        displays.unregisterDisplayListener(this)
        companion?.dismiss()
        companion = null
        super.onStop()
    }
    override fun onDestroy() {
        chatPanel.close()
        configSnapshots.close()
        edenInspector.close()
        romSync.close()
        deviceLibrary.close()
        downloads.close()
        requests.close()
        setup.close()
        super.onDestroy()
    }
    override fun onDisplayAdded(id: Int) { showCompanion(); render() }
    override fun onDisplayRemoved(id: Int) { showCompanion(); render() }
    override fun onDisplayChanged(id: Int) { showCompanion(); if (page == "setup") render() }

    private val muted = Color.rgb(169, 166, 158)
    private val paper = Color.rgb(11, 12, 16)
    private val lavender = Color.rgb(19, 21, 27)
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
        if (bold && size >= 19f) PilotTypography.heading(this) else PilotTypography.body(this)
    }
    private fun button(c: Context, value: String, action: () -> Unit) = Button(c).apply {
        text = value; isAllCaps = false; textSize = 14f; setTextColor(ink)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33FFB547), PilotSurface(dp(c, 15).toFloat()), null)
        setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6))
        setOnClickListener { action() }

        minHeight = dp(c, 48); minimumHeight = dp(c, 48)
        stateListAnimator = null
        PilotTypography.body(this)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 6) }
    }
    private fun utilityButton(c: Context, value: String, action: () -> Unit) = button(c, value, action).apply {
        textSize = 12f
        background = android.graphics.drawable.InsetDrawable(
            android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffb547), PilotSurface(dp(c, 16).toFloat()), null),
            0, dp(c, 6), 0, dp(c, 6))
        setPadding(dp(c, 12), dp(c, 2), dp(c, 12), dp(c, 2))
        layoutParams = LinearLayout.LayoutParams(-2, dp(c, 48))
    }
    private fun surface(c: Context, color: Int = paper) = column(c).apply {
        background = PilotSurface(dp(c, 24).toFloat())
        elevation = dp(c, 3).toFloat()
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
        PilotMotion.reset(body)
        page = destination
        showCompanion()
        render()
        pageScroll.scrollTo(0, 0)
        PilotMotion.enter(body)
        showCompanion()
    }
    private fun render() {
        if (!::body.isInitialized) buildShell()
        navigation.forEach { (id, item) ->
            item.isSelected = page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads", "setup") && id == "device")
            item.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFB547), if (page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads", "setup") && id == "device")) android.graphics.drawable.InsetDrawable(PilotSurface(dp(this@MainActivity, 16).toFloat(), true), dp(this@MainActivity, 2), dp(this@MainActivity, 4), dp(this@MainActivity, 2), dp(this@MainActivity, 4)) else shape(Color.TRANSPARENT, 16f), null)
            item.setTextColor(if (page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads", "setup") && id == "device")) iris else muted)
        }
        body.removeAllViews()
        if (companionSuppressed) {
            body.addView(label(this, "Opened from Quick Settings. Your game may pause; the companion screen stays free until you choose to use it.", 12f).apply { setTextColor(muted) })
        }
        when(page) {
            "chat" -> body.addView(chatPanel.createView(this))
            "device" -> devicePage()
            "care" -> {
                val actions = horizontal(this)
                addWeighted(actions, utilityButton(this, "My Thor") { go("device") }, 1f, 12)
                if (gameSession.lastPackage.isNotBlank()) {
                    addWeighted(actions, utilityButton(this, "Return to ${playAppName(gameSession.lastPackage)}") { launchPlayApp(gameSession.lastPackage) })
                }
                body.addView(actions)
                body.addView(utilityButton(this, "Configuration snapshots") { go("snapshots") })
                body.addView(utilityButton(this, "Inspect Eden settings") { go("eden-inspector") })
                body.addView(gameCare.createView(this))
            }
            "eden-inspector" -> {
                body.addView(utilityButton(this, "Back to Game care") { go("care") })
                body.addView(edenInspector.createView(this))
            }
            "downloads" -> {
                body.addView(utilityButton(this, "Back to My Thor") { go("device") })
                body.addView(downloads.createView(this))
            }
            "device-library" -> {
                body.addView(utilityButton(this, "Back to My Thor") { go("device") })
                body.addView(deviceLibrary.createView(this))
            }
            "library-sync" -> {
                body.addView(utilityButton(this, "Back to My Thor") { go("device") })
                body.addView(romSync.createView(this))
            }
            "snapshots" -> {
                body.addView(utilityButton(this, "Back to Game care") { go("care") })
                body.addView(configSnapshots.createView(this))
            }
            "setup" -> body.addView(setup.createView(this,
                if (companion?.isShowing == true && companionPage == "setup") SetupLayout.OVERVIEW else SetupLayout.COMBINED))
            "requests" -> requestsPage()
            "settings" -> settingsPage()
            else -> homePage()
        }
        PilotTypography.applyTo(body)
    }
    private fun buildShell() {
        val root = column(this).apply { setBackgroundColor(Color.BLACK) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(dp(this, 22) + bars.left, dp(this, 8) + bars.top,
                dp(this, 22) + bars.right, dp(this, 8) + bars.bottom)
            insets
        }
        val header = horizontal(this)
        header.addView(label(this, "Thorpilot", 20f, true).apply { setPadding(0, 0, dp(context, 26), 0); gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(-2, dp(this, 48)))
        val nav = horizontal(this).apply {
            background = PilotSurface(dp(this@MainActivity, 23).toFloat())
            setPadding(dp(context, 3), 0, dp(context, 3), 0)
        }
        listOf("home" to "Home", "chat" to "Ask", "device" to "My Thor", "requests" to "Requests", "settings" to "Settings").forEach { (id, name) ->
            val item = button(this, name) { go(id) }.apply {
                isSelected = page == id
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFB547), if (page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads", "setup") && id == "device")) android.graphics.drawable.InsetDrawable(PilotSurface(dp(this@MainActivity, 16).toFloat(), true), dp(this@MainActivity, 2), dp(this@MainActivity, 4), dp(this@MainActivity, 2), dp(this@MainActivity, 4)) else shape(Color.TRANSPARENT, 16f), null)
                setTextColor(if (page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads", "setup") && id == "device")) iris else muted)
            }
            navigation[id] = item
            nav.addView(item, LinearLayout.LayoutParams(0, dp(this, 48), 1f))
        }
        if (resources.configuration.screenWidthDp >= 620) {
            header.addView(nav, LinearLayout.LayoutParams(0, dp(this, 48), 1f))
            root.addView(header)
        } else {
            root.addView(header)
            root.addView(nav)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(context, 18), 0, 0) }
        pageScroll = scroll
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        PilotTypography.applyTo(root)
        setContentView(root)
        PilotFocus.install(root)
    }
    private fun toggleCompanion() {
        if (gameSession.yielded || companionSuppressed) {
            reclaimCompanion()
            return
        }
        lowerEnabled = !lowerEnabled
        getPreferences(MODE_PRIVATE).edit().putBoolean("lower", lowerEnabled).apply()
        showCompanion(); render()
    }
    private fun homePage() {
        val header = horizontal(this)
        addWeighted(header, label(this, "Home", 26f, true))
        header.addView(label(this, Build.MODEL, 12f).apply { setTextColor(muted) })
        body.addView(header)
        sessionBar()
        if (store.url.isBlank()) {
            body.addView(PilotActionRow(this, "Set up your handheld", "Connect a library and choose your folders", "device") { go("setup") })
        }
        val columns = if (resources.configuration.screenWidthDp >= 620 && resources.configuration.fontScale <= 1.3f) 2 else 1
        homeActions().chunked(columns).forEach { actions ->
            val row = horizontal(this)
            actions.forEachIndexed { index, (title, detail, icon, destination) ->
                row.addView(PilotActionRow(this, title, detail, icon) { go(destination) }.apply { tag = destination },
                    LinearLayout.LayoutParams(0, -1, 1f).apply {
                        marginEnd = if (index < actions.lastIndex) dp(this@MainActivity, 10) else 0
                    })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(this@MainActivity, 8) })
        }
        body.addView(utilityButton(this, if (gameSession.yielded || companionSuppressed) "Reclaim companion screen" else if (lowerEnabled) "Release companion screen" else "Use companion screen") { toggleCompanion() })
    }

    private data class HomeAction(val title: String, val detail: String, val icon: String, val destination: String)
    private fun homeActions() = listOf(
        HomeAction("Ask Thorpilot", "Find a game or plan what to play", "chat", "chat"),
        HomeAction("Game care", "Keep notes, compare settings, track a fix", "care", "care"),
        HomeAction("Requests", "Follow requests from your connected library", "requests", "requests"),
        HomeAction("On this Thor", "Games, transfers and storage", "device", "device")
    )

    private fun requestsPage() { body.addView(requests.createView(this)) }
    private fun deviceAction(title: String, detail: String, icon: String, destination: String): Button =
        button(this, title) { go(destination) }.apply {
            tag = destination
            val copy = "$title\n$detail"
            text = android.text.SpannableString(copy).apply {
                setSpan(android.text.style.RelativeSizeSpan(.82f), title.length + 1, length, 0)
                setSpan(android.text.style.ForegroundColorSpan(muted), title.length + 1, length, 0)
            }
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            setLineSpacing(dp(context, 5).toFloat(), 1f)
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33ffb547), PilotSurface(dp(context, 22).toFloat()), null)
            val tile = PilotIcon(icon).apply { setBounds(0, 0, dp(context, 40), dp(context, 40)) }
            setCompoundDrawablesRelative(tile, null, null, null)
            compoundDrawablePadding = dp(context, 14)
            minHeight = dp(context, 82)
            minimumHeight = dp(context, 82)
        }

    private fun devicePage() {
        val title = horizontal(this)
        addWeighted(title, label(this, "Your ${Build.MODEL}", 23f, true))
        title.addView(label(this, "Android ${Build.VERSION.RELEASE}", 12f).apply { setTextColor(muted) })
        body.addView(title)
        body.addView(label(this, "Your games, wherever you keep them.", 13f).apply { setTextColor(muted) })
        val actions = listOf(
            deviceAction("Download to Thor", "Bring games from your library", "requests", "downloads"),
            deviceAction("On this device", "Browse SD and internal storage", "device", "device-library"),
            deviceAction("Library sync", "Preview and copy between folders", "sync", "library-sync"),
            deviceAction("Game care", "Track settings and graphics fixes", "care", "care")
        )
        val columns = if (resources.configuration.screenWidthDp >= 620 && resources.configuration.fontScale <= 1.3f) 2 else 1
        actions.chunked(columns).forEach { group ->
            val row = horizontal(this).apply { gravity = Gravity.TOP }
            group.forEachIndexed { index, action ->
                row.addView(action, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginEnd = if (index < group.lastIndex) dp(this@MainActivity, 12) else 0
                })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(this@MainActivity, 10) })
        }
        body.addView(label(this, "Your play space", 16f, true).apply { setPadding(0, dp(context, 18), 0, dp(context, 4)) })
        listOf("Cocoon" to "rip.moth.cocoonshell", "Azahar" to "org.azahar_emu.azahar",
            "Eden" to "dev.eden.eden_emulator", "melonDualDS" to "me.magnum.melondualds").chunked(columns * 2).forEach { group ->
            val row = horizontal(this)
            group.forEachIndexed { index, (name, pkg) ->
                val available = packageManager.getLaunchIntentForPackage(pkg) != null
                addWeighted(row, utilityButton(this, if (available) name else "$name · unavailable") {
                    if (available) launchPlayApp(pkg)
                }.apply { isEnabled = available; alpha = if (available) 1f else .5f }, 1f, if (index < group.lastIndex) 8 else 0)
            }
            body.addView(row)
        }
        body.addView(label(this, "Opening an app frees the other screen for play.", 11f).apply { setTextColor(muted) })
        val setupActions = horizontal(this)
        addWeighted(setupActions, utilityButton(this, "Set up my handheld") { go("setup") }, 1f, 12)
        addWeighted(setupActions, utilityButton(this, "Add Quick Settings tile") { ThorpilotTileService.requestAdd(this) })
        body.addView(setupActions)
        body.addView(label(this, "Device details", 15f, true).apply { setPadding(0, dp(context, 14), 0, dp(context, 3)) })
        displays.displays.forEach { display ->
            val mode = display.mode
            val role = if (display.displayId == Display.DEFAULT_DISPLAY) "Main" else "Companion"
            body.addView(label(this, "$role · ${mode.physicalWidth} × ${mode.physicalHeight} · ${mode.refreshRate.toInt()} Hz · Display ${display.displayId}", 11f).apply { setTextColor(muted) })
        }
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        body.addView(label(this, "Thorpilot $version · ${BuildConfig.BUILD_TYPE} · ${BuildConfig.SOURCE_REVISION}", 11f).apply { setTextColor(muted) })
        body.addView(utilityButton(this, "Install and update guide") {
            runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://thorpilot.rackmind.ai/docs/install.html"))) }
                .onFailure { Toast.makeText(this, "Open thorpilot.rackmind.ai on another device for the guide.", Toast.LENGTH_LONG).show() }
        })
    }
    private fun playAppName(pkg: String) = when (pkg) {
        "org.azahar_emu.azahar" -> "Azahar"
        "dev.eden.eden_emulator" -> "Eden"
        "me.magnum.melondualds" -> "melonDualDS"
        "rip.moth.cocoonshell" -> "Cocoon"
        else -> "emulator"
    }
    private fun launchPlayApp(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null || pkg !in GameSession.ALLOWED_PACKAGES) {
            Toast.makeText(this, "This app is not available on your device.", Toast.LENGTH_LONG).show()
            return
        }
        val previous = try { gameSession.begin(pkg) } catch (_: RuntimeException) {
            Toast.makeText(this, "Could not save the screen handoff. Please try again.", Toast.LENGTH_LONG).show()
            return
        }
        companion?.dismiss()
        companion = null
        try {
            startActivity(launch)
            render()
        } catch (_: RuntimeException) {
            val restored = runCatching { gameSession.rollback(previous) }.getOrDefault(false)
            if (restored) showCompanion()
            render()
            Toast.makeText(this, if (restored) "Could not open ${playAppName(pkg)}. Your screen layout was restored."
                else "Could not open ${playAppName(pkg)}. Reclaim the companion screen when ready.", Toast.LENGTH_LONG).show()
        }
    }
    private fun reclaimCompanion() {
        try { gameSession.reclaim() } catch (_: RuntimeException) {
            Toast.makeText(this, "Could not save the screen preference. Please try again.", Toast.LENGTH_LONG).show()
            return
        }
        companionSuppressed = false
        lowerEnabled = true
        getPreferences(MODE_PRIVATE).edit().putBoolean("lower", true).apply()
        showCompanion(); render()
    }
    private fun sessionBar() {
        if (gameSession.lastPackage.isBlank()) return
        val box = surface(this).apply { setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6)) }
        val app = playAppName(gameSession.lastPackage)
        val row = horizontal(this)
        val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        summary.addView(label(this, "Back to $app", 15f, true))
        summary.addView(label(this, if (gameSession.yielded) "Your other screen is free" else "Continue in your emulator", 11f).apply { setTextColor(muted) })
        addWeighted(row, summary, 1f, 12)
        row.addView(utilityButton(this, "Open $app") { launchPlayApp(gameSession.lastPackage) })
        if (gameSession.yielded || companionSuppressed) row.addView(utilityButton(this, "Reclaim screen") { reclaimCompanion() })
        box.addView(row)
        body.addView(box)
    }
    private fun settingsPage() {
        body.addView(label(this, "Feel and feedback", 22f, true))
        fun preference(title: String, enabled: Boolean, update: (Boolean) -> Unit) {
            body.addView(Switch(this).apply {
                text = title; textSize = 15f; setTextColor(ink)
                isChecked = enabled
                minHeight = dp(context, 48)
                setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
                background = PilotSurface(dp(context, 16).toFloat())
                setOnCheckedChangeListener { _, checked -> update(checked) }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(this@MainActivity, 8) }
            })
        }
        preference("Interface animations", PilotPreferences.motion(this)) { PilotPreferences.setMotion(this, it) }
        preference("Navigation haptics", PilotPreferences.haptics(this)) { PilotPreferences.setHaptics(this, it) }
        body.addView(label(this, "System vibration and reduced-motion settings still apply. A selects; B returns to the parent screen.", 12f).apply { setTextColor(muted) })
        body.addView(label(this, "Your library, your server", 24f, true))
        body.addView(label(this, "Connect a ROMarr instance with the optional game-requests adapter. Your API key stays on this device."))
        val address = EditText(this).apply {
            hint = "https://your-server.example"; setText(store.url)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            contentDescription = "ROMarr server address"; isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        }
        val token = EditText(this).apply {
            hint = "API key (blank keeps key for this server only)"
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
                store.save(address.text.toString(), token.text.toString())
                chatPanel.onConnectionChanged()
                requests.connectionChanged()
                go("requests")
            } catch (e: Exception) {
                AlertDialog.Builder(this).setTitle("Connection not saved")
                    .setMessage(if (e is IllegalArgumentException) e.message else "Unable to access secure storage. Clear the connection and try again.")
                    .setPositiveButton("OK", null).show()
            }
        })
        body.addView(button(this, "Forget connection") {
            store.clear(); chatPanel.onConnectionChanged(); requests.connectionChanged(); render()
        })
    }
    private fun showCompanion() {
        val target = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != display?.displayId }
        if (!active || !lowerEnabled || companionSuppressed || gameSession.yielded || target == null) {
            companion?.dismiss(); companion = null; return
        }
        val mode = when (page) { "chat" -> "chat"; "setup" -> "setup"; else -> "dock" }
        if (companion?.display?.displayId == target.displayId && companion?.isShowing == true && companionPage == mode) return
        val panel = if (companion?.display?.displayId == target.displayId && companion?.isShowing == true) companion!! else {
            companion?.dismiss()
            object : Presentation(this, target) {
                @Deprecated("Routes companion back to the workspace")
                override fun onBackPressed() { this@MainActivity.onBackPressed() }
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                    if (event.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) {
                        if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) this@MainActivity.onBackPressed()
                        return true
                    }
                    PilotFeedback.key(currentFocus, event)
                    return super.dispatchKeyEvent(event)
                }
            }
        }
        val c = panel.context
        val content = column(c).apply {
            setBackgroundColor(Color.BLACK)
        }
        if (page != "setup") {
        val header = horizontal(c)
        addWeighted(header, label(c, "Thorpilot", 19f, true))
        header.addView(label(c, "Companion", 12f).apply { setTextColor(iris) })
        content.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 12) })
        }
        if (page == "setup") {
            content.addView(setup.createView(c, SetupLayout.CONTROLS))
        } else if (page == "chat") {
            content.addView(utilityButton(c, "Back to Home") { go("home") })
            content.addView(chatPanel.createView(c, compact = true))
        } else {
        homeActions().forEach { (title, detail, icon, destination) ->
            content.addView(PilotActionRow(c, title, detail, icon) { go(destination) })
        }

        }
        content.addView(utilityButton(c, "Release this screen") {
            lowerEnabled = false
            getPreferences(MODE_PRIVATE).edit().putBoolean("lower", false).apply()
            showCompanion(); render()
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(c, 190), dp(c, 48)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(c, 6) } })
        val scroll = ScrollView(c).apply { setBackgroundColor(Color.TRANSPARENT); isFillViewport = true; addView(content) }
        PilotTypography.applyTo(scroll)
        panel.setContentView(scroll)
        PilotFocus.install(scroll)
        panel.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(mist))
        try {
            if (!panel.isShowing) panel.show()
            panel.window?.setLayout(-1, -1)
            companion = panel
            companionPage = mode
        } catch (_: WindowManager.InvalidDisplayException) { companion = null }
    }
}
