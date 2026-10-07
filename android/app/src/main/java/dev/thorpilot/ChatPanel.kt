package dev.thorpilot

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** The same conversation can render on the workspace or a secondary display. */
class ChatPanel(private val activity: Activity, private val store: ConnectionStore, private val connect: () -> Unit) {
    private val history = ChatHistory(activity)
    private val initial = history.load()
    private var messages = initial.messages
    private var draft = initial.draft
    private var busy = false
    private var error: String? = null
    private var closed = false
    private val worker = Executors.newSingleThreadExecutor()
    private val roots = mutableListOf<WeakReference<LinearLayout>>()
    private val ink = Color.rgb(242, 246, 252)
    private val muted = Color.rgb(175, 193, 208)
    private val iris = Color.rgb(108, 247, 208)

    fun createView(context: Context, compact: Boolean = false): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        tag = compact
        roots.removeAll { it.get() == null || it.get()?.isAttachedToWindow == false }
        roots.add(WeakReference(this))
        draw(this)
    }
    fun close() { closed = true; worker.shutdownNow(); roots.clear() }
    private fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    private fun surface(c: Context, color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(c, 18).toFloat(); setStroke(dp(c, 1), 0x304faaa7) }
    private fun text(c: Context, value: String, size: Float = 15f, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(ink)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(c, 3), 0, dp(c, 7))
    }
    private fun action(c: Context, value: String, callback: () -> Unit) = Button(c).apply {
        text = value; isAllCaps = false; textSize = 14f; setTextColor(iris)
        minHeight = dp(c, 48); setOnClickListener { callback() }
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3383decf), surface(c, Color.rgb(29, 49, 73)), null)
        stateListAnimator = null
        setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 6); bottomMargin = dp(c, 4) }
    }
    private fun refresh() {
        roots.removeAll { it.get() == null }
        roots.mapNotNull { it.get() }.forEach { draw(it) }
    }
    private fun draw(root: LinearLayout) {
        root.removeAllViews()
        val c = root.context
        val compact = root.tag == true
        val heading = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(c, if (compact) "Let’s find a game" else "Find your next favorite", 23f, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (messages.isNotEmpty()) heading.addView(action(c, "Clear") {
            android.app.AlertDialog.Builder(activity).setTitle("Clear this conversation?")
                .setMessage("This removes the conversation saved on this handheld.")
                .setNegativeButton("Keep", null).setPositiveButton("Clear") { _, _ ->
                    if (!busy) { messages = emptyList(); draft = ""; error = null; history.clear(); refresh() }
                }.show()
        }.apply { isEnabled = !busy; layoutParams = LinearLayout.LayoutParams(-2, -2) })
        root.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 12); bottomMargin = dp(c, 6) })
        root.addView(text(c, "Game discovery through your server. Suggestions never start a download.", 13f).apply { setTextColor(muted) })
        if (store.url.isBlank()) {
            root.addView(text(c, "Connect your ROMarr server to start a conversation. Your model provider runs through that server."))
            root.addView(action(c, "Connect server", connect))
            return
        }
        if (messages.isEmpty()) {
            root.addView(text(c, "What are you in the mood for?", 18f, true))
            root.addView(action(c, "A cozy game for a short session") { draft = "Find me a cozy game for short sessions on Nintendo DS or PSP."; history.saveDraft(draft); refresh() })
            root.addView(action(c, "Something like my favorites") { draft = "Help me find a game like my favorites. Ask me what I enjoy."; history.saveDraft(draft); refresh() })
        }
        if (compact && messages.isNotEmpty()) root.addView(text(c, "Your conversation and game ideas appear on the other screen.", 14f))
        for (message in if (compact) emptyList() else messages.takeLast(12)) {
            val block = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 16))
                background = surface(c, if (message.role == "user") Color.rgb(29, 49, 73) else Color.rgb(10, 22, 34))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 16) }
            }
            block.addView(text(c, if (message.role == "user") "You" else "Thorpilot", 12f, true).apply { setTextColor(muted) })
            block.addView(text(c, message.content))
            message.games.forEach { game ->
                block.addView(text(c, game.title, 17f, true))
                block.addView(text(c, "${game.platformName}${if (game.owned) " · In your library" else ""}", 12f).apply { setTextColor(iris) })
                block.addView(text(c, game.reason, 14f))
            }
            root.addView(block)
        }
        error?.let { root.addView(text(c, it, 14f).apply { setTextColor(Color.rgb(255, 158, 164)) }) }
        if (busy) root.addView(text(c, "Finding ideas and checking the catalog…", 14f).apply { setTextColor(iris) })
        val input = EditText(c).apply {
            hint = "Tell me what you like…"; contentDescription = "Message Thorpilot"
            textSize = 15f; setTextColor(ink); setHintTextColor(muted); setText(draft)
            background = surface(c, Color.rgb(5, 14, 24))
            setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 16))
            minLines = 2; maxLines = 4
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(3000))
            isEnabled = !busy
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { draft = s.toString(); history.saveDraft(draft) }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 10); bottomMargin = dp(c, 8) })
        root.addView(action(c, if (busy) "Thinking…" else "Send message") { send() }.apply { isEnabled = !busy })
        if (!busy && messages.lastOrNull()?.role == "user") root.addView(action(c, "Retry last message") { submit(messages) })
    }
    private fun send() {
        if (busy || draft.isBlank()) return
        messages = (messages + ChatMessage("user", draft.trim())).takeLast(20)
        draft = ""
        history.save(messages, draft)
        submit(messages)
    }
    private fun submit(conversation: List<ChatMessage>) {
        if (busy || closed) return
        val base = store.url
        val token = try { store.token() } catch (_: Exception) {
            error = "Saved key is unavailable. Save your connection again."; refresh(); return
        }
        busy = true; error = null; refresh()
        worker.execute {
            val result = runCatching { ChatClient().send(base, token, conversation) }
            activity.runOnUiThread {
                if (closed || activity.isDestroyed) return@runOnUiThread
                busy = false
                if (store.url != base || runCatching { store.token() }.getOrDefault("") != token) {
                    error = "Connection changed. The previous server's response was discarded."
                } else result.fold(onSuccess = { reply ->
                    val note = if (reply.unverified > 0) "\n\nSome suggestions could not be verified in the catalog." else ""
                    messages = (messages + ChatMessage("assistant", reply.reply + note, reply.games)).takeLast(20)
                    history.save(messages, draft)
                }, onFailure = {
                    error = if (it is ChatException) it.message else "Chat is unavailable. Try again shortly."
                })
                refresh()
            }
        }
    }
}
