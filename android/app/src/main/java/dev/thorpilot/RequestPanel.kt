package dev.thorpilot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Server observations stay separate from verified device transfers. No request or transfer side effects. */
class RequestPanel(context: Context, private val connect: () -> Unit, private val browse: (String) -> Unit) {
    private val app = context.applicationContext
    private val connection = ConnectionStore(app)
    private val history = RequestHistory(app)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var cards: LinearLayout? = null
    private var statusHost: LinearLayout? = null
    private var refreshButton: Button? = null
    private var showDiagnostics = false
    private var snapshot = history.load(connection.identity)
    private var query = ""
    private var message = ""
    private var loading = false
    private var closed = false
    private var generation = 0
    fun close() { closed = true; generation++; worker.shutdownNow(); root = null; cards = null; statusHost = null; refreshButton = null }
    fun connectionChanged() {
        generation++; loading = false; history.clear(); snapshot = null; query = ""; message = ""
    }
    fun createView(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; root = this; render()
        if (connection.url.isNotBlank()) refresh()
    }
    private fun refresh() {
        if (loading || closed) return
        if (connection.url.isBlank()) { connect(); return }
        val saved = runCatching { connection.snapshot() }.getOrElse {
            message = "Saved key is unavailable. Save your connection again."; renderStatus(); return
        }
        val token = ++generation
        loading = true; message = ""; renderStatus()
        worker.execute {
            val result = RequestClient().fetch(saved.url, saved.token)
            main.post {
                if (closed || generation != token || connection.identity != saved.identity) return@post
                loading = false
                if (result.isSuccess) {
                    snapshot = RequestHistory.Snapshot(result, System.currentTimeMillis())
                    message = if (history.save(saved.identity, result, snapshot!!.checkedAt)) "" else "Loaded, but this snapshot could not be saved for offline use."
                } else message = result.message.orEmpty()
                renderStatus(); renderCards()
            }
        }
    }
    private fun dp(value: Int) = (value * app.resources.displayMetrics.density).toInt()
    private fun label(parent: LinearLayout, text: String, size: Float = 13f, accent: Boolean = false) {
        parent.addView(TextView(parent.context).apply {
            this.text = text; textSize = size; setTextColor(if (accent) 0xffffb547.toInt() else 0xfff4f2ec.toInt())
            setPadding(0, dp(5), 0, dp(5))
        })
    }
    private fun button(parent: LinearLayout, text: String, action: () -> Unit): Button = Button(parent.context).apply {
        this.text = text; textSize = 13f; isAllCaps = false; setTextColor(0xffffb547.toInt()); background = PilotSurface(dp(14).toFloat())
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
    }
    private fun render() {
        val host = root ?: return
        host.removeAllViews(); cards = null; statusHost = null
        val heading = LinearLayout(host.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        host.addView(heading)
        val title = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        label(title, "Your requests", 24f, true)
        refreshButton = button(heading, if (loading) "Refreshing…" else "Refresh") { refresh() }.apply {
            isEnabled = !loading; layoutParams = LinearLayout.LayoutParams(dp(125), dp(46))
        }
        label(host, "Server library → Download to Thor → Ready on device")
        if (connection.url.isBlank()) {
            label(host, "Connect your own ROMarr server to see your requests here.")
            button(host, "Connect ROMarr", connect)
            return
        }
        statusHost = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL; host.addView(this) }
        renderStatus()
        val actions = LinearLayout(host.context).apply { orientation = LinearLayout.HORIZONTAL }
        host.addView(actions)
        button(actions, "Manage in ROMarr") {
            runCatching { host.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ServerAddress.normalize(connection.url) + "/#requests"))) }
                .onFailure { Toast.makeText(host.context, "Could not open your server in a browser.", Toast.LENGTH_LONG).show() }
        }.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) }
        button(actions, "Download to Thor") { browse("") }.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        host.addView(EditText(host.context).apply {
            hint = "Find a request or platform"; contentDescription = "Search requests"; isSingleLine = true
            setTextColor(0xfff4f2ec.toInt()); setHintTextColor(0xffa9a69e.toInt()); textSize = 14f; setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); renderCards() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
        cards = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL; host.addView(this) }
        renderCards()
    }
    private fun renderStatus() {
        refreshButton?.apply { text = if (loading) "Refreshing…" else "Refresh"; isEnabled = !loading }
        val host = statusHost ?: return
        host.removeAllViews()
        snapshot?.let { label(host, "Last checked ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.checkedAt))}. Refresh for the latest changes.", 12f) }
        if (message.isNotBlank()) label(host, message + if (snapshot != null) "\nShowing the last successful check." else "")
        val warnings = snapshot?.result?.clientWarnings.orEmpty()
        if (warnings.isNotEmpty()) {
            button(host, "${warnings.size} client notice${if (warnings.size == 1) "" else "s"} · ${if (showDiagnostics) "Hide" else "Show"}") {
                showDiagnostics = !showDiagnostics; renderStatus()
            }
            if (showDiagnostics) warnings.forEach { label(host, it, 12f) }
        }
    }
    private fun renderCards() {
        val host = cards ?: return
        host.removeAllViews()
        val result = snapshot?.result ?: return
        val matches = result.rows.filter { LibrarySearch.matches(query, "${it.title} ${it.platform} ${it.statusLabel}") }
        label(host, "${matches.size} of ${result.rows.size} requests", 12f)
        if (matches.isEmpty()) label(host, if (result.rows.isEmpty()) "No requests yet." else "No matching requests.")
        if (matches.size > 100) label(host, "Showing 100 matches. Refine your search to find more.", 12f)
        matches.take(100).forEach { request ->
            val card = LinearLayout(host.context).apply {
                orientation = LinearLayout.VERTICAL; background = PilotSurface(dp(16).toFloat()); setPadding(dp(14), dp(8), dp(14), dp(12))
            }
            host.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            label(card, request.title, 18f, true)
            label(card, "${request.platform} · ${request.statusLabel}")
            request.progress?.let { progress ->
                card.addView(ProgressBar(host.context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 1000; this.progress = (progress * 10).toInt(); contentDescription = "Server progress ${progress.toInt()} percent"
                    progressTintList = android.content.res.ColorStateList.valueOf(0xffffb547.toInt())
                }, LinearLayout.LayoutParams(-1, dp(5)))
                label(card, "Server progress: ${progress.toInt()}%", 12f)
            }
            if (request.description().isNotBlank()) label(card, request.description(), 13f)
            if (request.status == "imported") button(card, "Find in download library") { browse(request.title) }
        }
    }
}
