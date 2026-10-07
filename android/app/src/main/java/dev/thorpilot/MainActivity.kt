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
    private val ink = Color.rgb(242, 246, 252)
    private val iris = Color.rgb(108, 247, 208)
    private val mist = Color.rgb(3, 10, 17)
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
    private var companionPage = ""
    private lateinit var body: LinearLayout
    private lateinit var pageScroll: ScrollView
    private val navigation = mutableMapOf<String, Button>()
    private var companion: Presentation? = null
    private var page = "home"
    private lateinit var requests: RequestPanel
    private var lowerEnabled = true
    private var active = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
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
        chatPanel = ChatPanel(this, store) { go("settings") }
        page = state?.getString("page") ?: ThorpilotWidget.destination(intent) ?: "home"
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
        ThorpilotWidget.destination(intent)?.let { go(it) }
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBundle("game-care", gameCare.saveState())
        out.putString("page", page)
        super.onSaveInstanceState(out)
    }
    override fun onStart() {
        super.onStart()
        active = true
        romSync.start()
        downloads.start()
        displays.registerDisplayListener(this, null)
        showCompanion()
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
        super.onDestroy()
    }
    override fun onDisplayAdded(id: Int) { showCompanion(); render() }
    override fun onDisplayRemoved(id: Int) { showCompanion(); render() }
    override fun onDisplayChanged(id: Int) { showCompanion() }

    private val muted = Color.rgb(174, 192, 208)
    private val paper = Color.argb(245, 12, 23, 35)
    private val lavender = Color.rgb(25, 40, 62)
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
    private fun utilityButton(c: Context, value: String, action: () -> Unit) = button(c, value, action).apply {
        textSize = 12f
        background = android.graphics.drawable.InsetDrawable(
            android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3383decf), PilotGlass(dp(c, 16).toFloat()), null),
            0, dp(c, 6), 0, dp(c, 6))
        setPadding(dp(c, 12), dp(c, 2), dp(c, 12), dp(c, 2))
        layoutParams = LinearLayout.LayoutParams(-2, dp(c, 48))
    }
    private fun surface(c: Context, color: Int = paper) = column(c).apply {
        background = PilotGlass(dp(c, 24).toFloat())
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
        page = destination
        render()
        pageScroll.scrollTo(0, 0)
        showCompanion()
    }
    private fun render() {
        if (!::body.isInitialized) buildShell()
        navigation.forEach { (id, item) ->
            item.isSelected = page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads") && id == "device")
            item.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x3383DECF), if (page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads") && id == "device")) android.graphics.drawable.InsetDrawable(PilotGlass(dp(this@MainActivity, 16).toFloat(), true), dp(this@MainActivity, 2), dp(this@MainActivity, 4), dp(this@MainActivity, 2), dp(this@MainActivity, 4)) else shape(Color.TRANSPARENT, 16f), null)
            item.setTextColor(if (page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads") && id == "device")) iris else muted)
        }
        body.removeAllViews()
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
        header.addView(label(this, "Thorpilot", 20f, true).apply { setPadding(0, 0, dp(context, 26), 0); gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(-2, dp(this, 48)))
        val nav = horizontal(this).apply {
            background = PilotGlass(dp(this@MainActivity, 23).toFloat())
            setPadding(dp(context, 3), 0, dp(context, 3), 0)
        }
        listOf("home" to "Workspace", "chat" to "Chat", "device" to "My Thor", "requests" to "Requests", "settings" to "Connection").forEach { (id, name) ->
            val item = button(this, name) { go(id) }.apply {
                isSelected = page == id
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x3383DECF), if (page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads") && id == "device")) android.graphics.drawable.InsetDrawable(PilotGlass(dp(this@MainActivity, 16).toFloat(), true), dp(this@MainActivity, 2), dp(this@MainActivity, 4), dp(this@MainActivity, 2), dp(this@MainActivity, 4)) else shape(Color.TRANSPARENT, 16f), null)
                setTextColor(if (page == id || (page in setOf("care", "snapshots", "eden-inspector", "library-sync", "device-library", "downloads") && id == "device")) iris else muted)
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
        setContentView(root)
    }
    private fun toggleCompanion() {
        if (gameSession.yielded) {
            reclaimCompanion()
            return
        }
        lowerEnabled = !lowerEnabled
        getPreferences(MODE_PRIVATE).edit().putBoolean("lower", lowerEnabled).apply()
        showCompanion(); render()
    }
    private fun workspacePage() {
        val wide = resources.configuration.screenWidthDp >= 620
        val scene = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), dp(context, 5), dp(context, 26), dp(context, 18))
        }
        val artwork = ImageView(this).apply { setImageDrawable(PilotJourney()); contentDescription = "A handheld drifting around a little moon" }
        if (wide) scene.addView(artwork, LinearLayout.LayoutParams(0, dp(this, 166), 1.05f))
        else scene.addView(artwork, LinearLayout.LayoutParams(-1, dp(this, 175)))
        val introduction = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(context, 20), 0, 0, 0) }
        introduction.addView(label(this, "Your next adventure\nstarts with a conversation.", 24f, true))
        introduction.addView(label(this, "Find a hidden gem. Check your library.\nLet’s make more time for play.", 14f).apply { setTextColor(muted) })
        introduction.addView(button(this, "Find my next game") { go("chat") }.apply {
            setTextColor(mist); background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xffabffe0.toInt(), iris)).apply { cornerRadius = dp(this@MainActivity, 22).toFloat() }
        }, LinearLayout.LayoutParams(dp(this, 230), dp(this, 48)).apply { topMargin = dp(this@MainActivity, 10) })
        if (wide) scene.addView(introduction, LinearLayout.LayoutParams(0, -2, 1f)) else scene.addView(introduction)
        body.addView(scene)
        sessionBar()
        val dock = horizontal(this).apply {
            background = PilotGlass(dp(this@MainActivity, 24).toFloat())
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            elevation = dp(context, 4).toFloat()
        }
        val device = horizontal(this)
        device.addView(ImageView(this).apply { setImageDrawable(PilotIcon("device")) }, LinearLayout.LayoutParams(dp(this, 46), dp(this, 46)).apply { marginEnd = dp(this@MainActivity, 10) })
        val status = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status.addView(label(this, Build.MODEL, 14f, true))
        status.addView(label(this, "${displays.displays.size} screens detected", 11f).apply { setTextColor(muted) })
        device.addView(status)
        addWeighted(dock, device, 1f, 18)
        addWeighted(dock, utilityButton(this, if (store.url.isBlank()) "Connect library" else "My requests") { go(if (store.url.isBlank()) "settings" else "requests") }, 1f, 16)
        addWeighted(dock, utilityButton(this, if (gameSession.yielded) "Reclaim screen" else if (lowerEnabled) "Release screen" else "Use lower screen") { toggleCompanion() })
        body.addView(dock)
        body.addView(utilityButton(this, "Game care · track a graphics experiment") { go("care") })
        body.addView(label(this, "Touch or D-pad to explore     •     Changes stay in your control", 11f).apply { setTextColor(muted); gravity = Gravity.CENTER; setPadding(0, dp(context, 10), 0, 0) })
    }
    private fun requestsPage() { body.addView(requests.createView(this)) }
    private fun devicePage() {
        val title = horizontal(this)
        addWeighted(title, label(this, "Meet your ${Build.MODEL}", 25f, true))
        title.addView(label(this, "Android ${Build.VERSION.RELEASE}", 13f).apply { setTextColor(muted) })
        body.addView(title)
        sessionBar()
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
        listOf("Cocoon" to "rip.moth.cocoonshell", "Azahar" to "org.azahar_emu.azahar",
            "Eden" to "dev.eden.eden_emulator", "melonDualDS" to "me.magnum.melondualds").chunked(2).forEach { row ->
            val apps = horizontal(this)
            row.forEachIndexed { index, (name, pkg) ->
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                addWeighted(apps, button(this, if (intent == null) "$name • unavailable" else "Open $name") {
                    if (intent != null) launchPlayApp(pkg)
                }.apply { isEnabled = intent != null; alpha = if (intent == null) .5f else 1f }, 1f, if (index == 0) 10 else 0)
            }
            body.addView(apps, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(this@MainActivity, 8) })
        }
        body.addView(label(this, "Opening an app reserves the other screen until you reclaim it.", 12f).apply { setTextColor(muted) })
        body.addView(button(this, "Game care") { go("care") })
        body.addView(button(this, "On this device · SD / internal") { go("device-library") })
        body.addView(button(this, "Download to Thor") { go("downloads") })
        body.addView(button(this, "Library sync") { go("library-sync") })
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
        showCompanion(); render()
    }
    private fun sessionBar() {
        if (gameSession.lastPackage.isBlank()) return
        val box = surface(this).apply { setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 10)) }
        val app = playAppName(gameSession.lastPackage)
        box.addView(label(this, if (gameSession.yielded) "Room for your game" else "Back to your play space", 16f, true))
        box.addView(label(this, if (gameSession.yielded) "Thorpilot is leaving the other screen free. Resume $app, or reclaim it when you’re ready." else "Open $app again. Your emulator manages the game session.", 12f).apply { setTextColor(muted) })
        val actions = horizontal(this)
        addWeighted(actions, utilityButton(this, "Return to $app") { launchPlayApp(gameSession.lastPackage) }, 1f, 12)
        if (gameSession.yielded) addWeighted(actions, utilityButton(this, "Reclaim companion") { reclaimCompanion() })
        box.addView(actions)
        body.addView(box)
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
    private fun launchTile(c: Context, title: String, kind: String, action: () -> Unit): View = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        isClickable = true; isFocusable = true; contentDescription = title
        setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 12))
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), PilotGlass(dp(c, 24).toFloat(), true))
            addState(intArrayOf(android.R.attr.state_pressed), PilotGlass(dp(c, 24).toFloat(), true))
            addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        addView(ImageView(c).apply { setImageDrawable(PilotIcon(kind)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(c, 96), dp(c, 96)))
        addView(label(c, title, 13f, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 9), 0, 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        setOnClickListener { action() }
        setOnFocusChangeListener { view, focused -> view.scaleX = if (focused) 1.035f else 1f; view.scaleY = if (focused) 1.035f else 1f }
    }
    private fun showCompanion() {
        val target = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != display?.displayId }
        if (!active || !lowerEnabled || gameSession.yielded || target == null) {
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
        addWeighted(header, label(c, "Thorpilot", 19f, true))
        header.addView(label(c, "Companion", 12f).apply { setTextColor(iris) })
        content.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 12) })
        if (page == "chat") {
            content.addView(utilityButton(c, "Back to workspace") { go("home") })
            content.addView(chatPanel.createView(c, compact = true))
        } else {
        val shortcuts = horizontal(c).apply { gravity = Gravity.CENTER }
        addWeighted(shortcuts, launchTile(c, "Find a game", "chat") { go("chat") }, 1f, 16)
        addWeighted(shortcuts, launchTile(c, "My Thor", "device") { go("device") })
        content.addView(shortcuts, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 10) })
        val second = horizontal(c).apply { gravity = Gravity.CENTER }
        addWeighted(second, launchTile(c, "Requests", "requests") { go("requests") }, 1f, 16)
        addWeighted(second, launchTile(c, "Connection", "settings") { go("settings") })
        content.addView(second, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 10) })
        }
        content.addView(utilityButton(c, "Release this screen") {
            lowerEnabled = false
            getPreferences(MODE_PRIVATE).edit().putBoolean("lower", false).apply()
            showCompanion(); render()
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(c, 190), dp(c, 48)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(c, 6) } })
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
