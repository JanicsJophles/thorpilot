package dev.thorpilot

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A user-invoked dialog activity. Never owns a second display or inspects another app. */
class SummonActivity : Activity() {
    private lateinit var chat: ChatPanel
    private var expanded = false
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        expanded = state?.getBoolean("expanded") ?: false
        setFinishOnTouchOutside(true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.BLACK); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0xff45675f.toInt())
            }
            isFocusableInTouchMode = true
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "✦ Thorpilot"; textSize = 18f; setTextColor(0xff6cf7d0.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        fun button(title: String, action: () -> Unit) = Button(this).apply {
            text = title; textSize = 13f; isAllCaps = false
            minHeight = dp(48); setTextColor(0xfff4f2ec.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xff152323.toInt())
            setOnClickListener { action() }
        }
        val expand = button(if (expanded) "Compact" else "Expand") { }
        expand.setOnClickListener {
            expanded = !expanded
            expand.text = if (expanded) "Compact" else "Expand"
            resize()
        }
        header.addView(expand)
        header.addView(button("Workspace") { openWorkspace() })
        header.addView(button("Dismiss") { finish() })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Your game may pause while this is open."; textSize = 12f
            setTextColor(0xffb9a9ff.toInt()); setPadding(0, dp(4), 0, dp(6))
        })
        chat = ChatPanel(this, ConnectionStore(this), quick = true) { openWorkspace() }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(chat.createView(this@SummonActivity))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.requestFocus() // Opening the tile must not summon the keyboard.
        resize()
    }

    private fun resize() {
        val bounds = windowManager.currentWindowMetrics.bounds
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setLayout(minOf(dp(640), (bounds.width() * .94).toInt()),
            minOf(dp(if (expanded) 390 else 250), (bounds.height() * .78).toInt()))
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("expanded", expanded)
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
