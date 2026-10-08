package dev.thorpilot

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Toast
import android.view.View

/** A user-invoked dialog activity. Never owns a second display or inspects another app. */
class SummonActivity : Activity() {
    private lateinit var chat: ChatPanel
    private var expanded = false
    private var mode = "discovery"
    private lateinit var care: GameCarePanel
    private lateinit var careDraft: GameCareDraftStore
    private lateinit var content: ScrollView
    private lateinit var expandButton: Button
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        expanded = state?.getBoolean("expanded") ?: false
        mode = state?.getString("mode") ?: getPreferences(MODE_PRIVATE).getString("mode", "discovery").orEmpty()
        if (mode !in setOf("discovery", "care")) mode = "discovery"
        careDraft = GameCareDraftStore(this)
        care = GameCarePanel(this).apply { restoreState(state?.getBundle("care") ?: careDraft.load()) }
        if (state == null && mode == "care") expanded = true
        setFinishOnTouchOutside(true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
            background = PilotSurface(dp(30).toFloat())
            isFocusableInTouchMode = true
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "✦ Thorpilot"; textSize = 18f; setTextColor(0xffffb547.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        fun button(title: String, action: () -> Unit) = Button(this).apply {
            text = title; textSize = 13f; isAllCaps = false
            minHeight = dp(48); setTextColor(0xfff4f2ec.toInt())
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), PilotSurface(dp(24).toFloat(), true))
                addState(intArrayOf(android.R.attr.state_pressed), PilotSurface(dp(24).toFloat(), true))
                addState(intArrayOf(), PilotSurface(dp(24).toFloat()))
            }
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(-2, dp(44)).apply { marginStart = dp(6) }
            stateListAnimator = null
            setOnClickListener { action() }
        }
        val expand = button(if (expanded) "Compact" else "Expand") { }
        expandButton = expand
        expand.setOnClickListener {
            expanded = !expanded
            expand.text = if (expanded) "Compact" else "Expand"
            resize()
        }
        header.addView(expand)
        header.addView(button("Workspace") { openWorkspace() })
        header.addView(button("Dismiss") { finish() })
        root.addView(header)
        val tools = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val modes = listOf("Find games", "Game care")
        tools.addView(Spinner(this).apply {
            contentDescription = "Copilot mode"
            adapter = ArrayAdapter(this@SummonActivity, android.R.layout.simple_spinner_dropdown_item, modes)
            setSelection(if (mode == "care") 1 else 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = if (position == 1) "care" else "discovery"
                    if (next == mode) return
                    persistCare()
                    mode = next
                    getPreferences(MODE_PRIVATE).edit().putString("mode", mode).apply()
                    if (mode == "care") { expanded = true; expandButton.text = "Compact"; resize() }
                    showMode()
                }
            }
        }, LinearLayout.LayoutParams(dp(150), dp(48)))
        tools.addView(TextView(this).apply {
            text = "Your game may pause while this is open."; textSize = 12f
            setTextColor(0xffb9a9ff.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tools)
        chat = ChatPanel(this, ConnectionStore(this), quick = true) { openWorkspace() }
        content = ScrollView(this).apply { isFillViewport = true }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        showMode()
        PilotTypography.applyTo(root)
        setContentView(root)
        PilotFocus.install(root)
        root.requestFocus() // Opening the tile must not summon the keyboard.
        resize()
    }

    private fun showMode() {
        content.removeAllViews()
        content.addView(if (mode == "care") care.createView(this, compact = true) else chat.createView(this))
        PilotTypography.applyTo(content)
        content.scrollTo(0, 0)
    }

    private fun persistCare() {
        if (::care.isInitialized) runCatching { careDraft.save(care.saveState()) }
            .onFailure { Toast.makeText(this, "Could not save your troubleshooting draft.", Toast.LENGTH_LONG).show() }
    }

    override fun onPause() {
        persistCare()
        super.onPause()
    }

    private fun resize() {
        val bounds = windowManager.currentWindowMetrics.bounds
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setLayout(minOf(dp(640), (bounds.width() * .94).toInt()),
            minOf(dp(if (expanded) 390 else 250), (bounds.height() * .78).toInt()))
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("expanded", expanded)
        out.putString("mode", mode)
        out.putBundle("care", care.saveState())
        super.onSaveInstanceState(out)
    }

    private fun openWorkspace() {
        startActivity(ThorpilotWidget.launchIntent(this, "home").apply {
            putExtra(ThorpilotTileService.EXTRA_SUPPRESS_COMPANION, true)
        })
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (::chat.isInitialized) chat.onConnectionChanged()
    }

    override fun onDestroy() {
        if (::chat.isInitialized) chat.close()
        super.onDestroy()
    }
}
