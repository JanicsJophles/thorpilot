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
    private val ink = Color.rgb(37, 50, 76)
    private val iris = Color.rgb(114, 102, 189)
    private val mist = Color.rgb(233, 237, 245)
    private lateinit var displays: DisplayManager
    private lateinit var store: ConnectionStore
    private lateinit var body: LinearLayout
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
        worker.shutdownNow()
        super.onDestroy()
    }
    override fun onDisplayAdded(id: Int) { showCompanion(); render() }
    override fun onDisplayRemoved(id: Int) { showCompanion(); render() }
    override fun onDisplayChanged(id: Int) { showCompanion() }

    private fun dp(c: Context, value: Int) = (value * c.resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Float = 24f) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * resources.displayMetrics.density
    }
    private fun column(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 26), dp(c, 18), dp(c, 26), dp(c, 18))
        setBackgroundColor(mist)
    }
    private fun label(c: Context, value: String, size: Float = 17f, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(ink)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(c, 4), 0, dp(c, 9))
    }
    private fun button(c: Context, value: String, action: () -> Unit) = Button(c).apply {
        text = value; isAllCaps = false; textSize = 15f; setTextColor(ink)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(235, 231, 251))
        setOnClickListener { action() }
        minHeight = dp(c, 48)
    }
    private fun card(parent: LinearLayout, title: String, description: String) {
        val box = column(parent.context).apply {
            background = shape(Color.rgb(249, 251, 255))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) }
        }
        box.addView(label(box.context, title, 21f, true))
        box.addView(label(box.context, description))
        parent.addView(box)
    }
    private fun go(destination: String) { page = destination; render() }
    private fun render() {
        val root = column(this)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(dp(this, 26) + bars.left, dp(this, 12) + bars.top,
                dp(this, 26) + bars.right, dp(this, 12) + bars.bottom)
            insets
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(this, "Thorpilot", 28f, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label(this, "On your handheld", 14f))
        root.addView(header)
        val nav = LinearLayout(this)
        listOf("home" to "Workspace", "device" to "My Thor", "requests" to "Requests", "settings" to "Connection").forEach { (id, name) ->
            nav.addView(button(this, name) { go(id) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(context, 14), 0, 0) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when(page) {
            "device" -> devicePage()
            "requests" -> {
                card(body, "Your requests", requestText)
                body.addView(button(this, if (loading) "Refreshing…" else "Refresh requests") { fetchRequests() }.apply { isEnabled = !loading })
                body.addView(label(this, "Read-only connection. This build never queues downloads.", 14f))
            }
            "settings" -> settingsPage()
            else -> {
                card(body, "A little help, on both screens.", "Your handheld, its emulators, and your library services in one place.")
                card(body, "${Build.MODEL} is ready", "${displays.displays.size} displays detected. Open My Thor to inspect the real device.")
                body.addView(button(this, if (lowerEnabled) "Release companion screen" else "Use companion screen") {
                    lowerEnabled = !lowerEnabled
                    getPreferences(MODE_PRIVATE).edit().putBoolean("lower", lowerEnabled).apply()
                    showCompanion(); render()
                })
                body.addView(label(this, "Native preview · AI chat and emulator tuning are not connected yet.", 14f))
            }
        }
        setContentView(root)
    }
    private fun devicePage() {
        card(body, Build.MODEL, "Android ${Build.VERSION.RELEASE}\n${Build.MANUFACTURER} · ${Build.SUPPORTED_ABIS.first()}")
        displays.displays.forEach { display ->
            val mode = display.mode
            card(body, display.name, "Display ${display.displayId}\n${mode.physicalWidth} × ${mode.physicalHeight} pixels · ${mode.refreshRate.toInt()} Hz")
        }
        listOf("Cocoon" to "rip.moth.cocoonshell", "Azahar" to "org.azahar_emu.azahar", "melonDualDS" to "me.magnum.melondualds").forEach { (name, pkg) ->
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            body.addView(button(this, if (intent == null) "$name not installed" else "Open $name") {
                if (intent != null) startActivity(intent)
            }.apply { isEnabled = intent != null })
        }
    }
    private fun settingsPage() {
        body.addView(label(this, "Your library, your server", 24f, true))
        body.addView(label(this, "Connect a ROMarr instance with the optional game-requests adapter. Your API key stays on this device."))
        val address = EditText(this).apply {
            hint = "https://your-server.example"; setText(store.url)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            contentDescription = "ROMarr server address"; isSingleLine = true
        }
        val token = EditText(this).apply {
            hint = "API key (leave blank to keep saved key)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            contentDescription = "ROMarr API key"; isSingleLine = true
        }
        body.addView(address); body.addView(token)
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
            val result = try {
                val connection = URL("$base/api/v1/game-requests").openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = 10000; connection.readTimeout = 15000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("X-Api-Key", token)
                    when (connection.responseCode) {
                        200 -> {
                            val data = connection.inputStream.bufferedReader().use { it.readText() }
                            val items = JSONObject(data).getJSONArray("items")
                            if (items.length() == 0) "No requests yet." else (0 until items.length()).joinToString("\n\n") { i ->
                                val row = items.getJSONObject(i)
                                "${row.optString("game", "Untitled")}\n${row.optString("platform")} · ${row.optString("status", "unknown")}\n${row.optString("detail")}".trim()
                            }
                        }
                        401, 403 -> "Access denied. Check your server API key."
                        404 -> "This server does not expose the game-requests adapter."
                        else -> "Server returned HTTP ${connection.responseCode}. Try again shortly."
                    }
                } finally { connection.disconnect() }
            } catch (_: Exception) { "Could not load requests. Check the server address, connection, and certificate." }
            runOnUiThread {
                if (!isDestroyed) {
                    loading = false
                    if (generation == current) requestText = result
                    render()
                }
            }
        }
    }
    private fun showCompanion() {
        val target = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != display?.displayId }
        if (!active || !lowerEnabled || target == null) {
            companion?.dismiss(); companion = null; return
        }
        if (companion?.display?.displayId == target.displayId && companion?.isShowing == true) return
        companion?.dismiss()
        val panel = Presentation(this, target)
        val c = panel.context
        val content = column(c)
        content.addView(label(c, "Along for the game.", 28f, true))
        content.addView(label(c, "Thorpilot companion", 15f))
        content.addView(button(c, "Show my device") { go("device") })
        content.addView(button(c, "Show my requests") { go("requests") })
        content.addView(button(c, "Connection settings") { go("settings") })
        content.addView(label(c, "Choose an action here. Your workspace updates on the other screen. AI chat is coming next.", 16f))
        content.addView(button(c, "Release this screen") {
            lowerEnabled = false
            getPreferences(MODE_PRIVATE).edit().putBoolean("lower", false).apply()
            showCompanion(); render()
        })
        val scroll = ScrollView(c).apply { setBackgroundColor(mist); isFillViewport = true; addView(content) }
        panel.setContentView(scroll)
        panel.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(mist))
        try {
            panel.show()
            panel.window?.setLayout(-1, -1)
            companion = panel
        } catch (_: WindowManager.InvalidDisplayException) { companion = null }
    }
}
