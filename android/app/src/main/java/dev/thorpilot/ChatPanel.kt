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
class ChatPanel(private val activity: Activity, private val store: ConnectionStore, private val quick: Boolean = false, private val connect: () -> Unit) {
    private val conversation = ChatConversation(store, ChatHistory(activity, if (quick) "copilot-chat-history" else "chat-history"))
    private val messages get() = conversation.messages
    private val draft get() = conversation.draft
    private val busy get() = conversation.busy
    private var error: String? = null
    private var closed = false
    private var worker = Executors.newSingleThreadExecutor()
    private val roots = mutableListOf<WeakReference<LinearLayout>>()
    private val ink = Color.rgb(242, 246, 252)
    private val muted = Color.rgb(175, 193, 208)
    private val iris = Color.rgb(108, 247, 208)

    fun createView(context: Context, compact: Boolean = false): View = LinearLayout(context).apply {
        syncConnection()
        orientation = LinearLayout.VERTICAL
        tag = compact
        roots.removeAll { it.get() == null || it.get()?.isAttachedToWindow == false }
        roots.add(WeakReference(this))
        draw(this)
    }
    private fun syncConnection(): Boolean {
        if (!conversation.rebind()) return false
        worker.shutdownNow()
        worker = Executors.newSingleThreadExecutor()
        error = null
        return true
    }
    fun onConnectionChanged() { if (syncConnection()) refresh() }
    fun close() { closed = true; worker.shutdownNow(); roots.clear() }
    private fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    private fun surface(c: Context, color: Int): android.graphics.drawable.Drawable =
        if (quick) PilotBubble(dp(c, 24).toFloat(), color == Color.rgb(29, 49, 73))
        else GradientDrawable().apply { setColor(color); cornerRadius = dp(c, 18).toFloat(); setStroke(dp(c, 1), 0x304faaa7) }
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
        heading.addView(text(c, if (quick) "Find a game" else if (compact) "Let’s find a game" else "Find your next favorite", if (compact) 20f else 23f, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (messages.isNotEmpty()) heading.addView(action(c, "Clear") {
            android.app.AlertDialog.Builder(activity).setTitle("Clear this conversation?")
                .setMessage("This removes the conversation saved on this handheld.")
                .setNegativeButton("Keep", null).setPositiveButton("Clear") { _, _ ->
                    conversation.clear()
                    worker.shutdownNow()
                    worker = Executors.newSingleThreadExecutor()
                    error = null
                    refresh()
                }.show()
        }.apply {
            textSize = 12f
            background = android.graphics.drawable.InsetDrawable(surface(c, Color.rgb(16, 32, 46)), 0, dp(c, 7), 0, dp(c, 7))
            layoutParams = LinearLayout.LayoutParams(dp(c, 70), dp(c, 48))
        })
        if (!quick || messages.isNotEmpty()) root.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 12); bottomMargin = dp(c, 6) })
        root.addView(text(c, if (quick) "Game discovery · separate from your Workspace chat" else "Game discovery through your server. Suggestions never start a download.", 13f).apply { setTextColor(muted) })
        if (store.url.isBlank()) {
            root.addView(text(c, "Connect your ROMarr server to start a conversation. Your model provider runs through that server."))
            root.addView(action(c, if (quick) "Open Workspace to connect" else "Connect server", connect))
            return
        }
        if (messages.isEmpty() && !quick) {
            root.addView(text(c, "What are you in the mood for?", 18f, true))
            root.addView(action(c, "A cozy game for a short session") { conversation.updateDraft("Find me a cozy game for short sessions on Nintendo DS or PSP."); refresh() })
            root.addView(action(c, "Something like my favorites") { conversation.updateDraft("Help me find a game like my favorites. Ask me what I enjoy."); refresh() })
        }
        if (compact && messages.isNotEmpty()) root.addView(text(c, "Your conversation and game ideas appear on the other screen.", 14f))
        for (message in if (compact) emptyList() else messages.takeLast(if (quick) 2 else 12)) {
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
        val editorIdentity = conversation.identity
        val input = EditText(c).apply {
            hint = "Tell me what you like…"; contentDescription = "Message Thorpilot"
            textSize = 15f; setTextColor(ink); setHintTextColor(muted); setText(draft)
            background = surface(c, Color.rgb(5, 14, 24))
            setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 16))
            minLines = if (quick) 1 else 2; maxLines = 4
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(3000))
            isEnabled = !busy
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { conversation.updateDraft(s.toString(), editorIdentity) }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 10); bottomMargin = dp(c, 8) })
        root.addView(action(c, if (busy) "Thinking…" else "Send message") { send() }.apply {
            isEnabled = !busy
            layoutParams = LinearLayout.LayoutParams(dp(c, 170), dp(c, 48)).apply { gravity = Gravity.END; topMargin = dp(c, 4); bottomMargin = dp(c, 4) }
        })
        if (!busy && messages.lastOrNull()?.role == "user") root.addView(action(c, "Retry last message") { submit(retry = true) })
    }
    private fun send() { submit(retry = false) }
    private fun submit(retry: Boolean) {
        if (closed) return
        if (syncConnection()) { refresh(); return }
        val pending = try { if (retry) conversation.retry() else conversation.sendDraft() } catch (_: Exception) {
            error = "Saved key is unavailable. Save your connection again."; refresh(); return
        } ?: return
        error = null
        refresh()
        worker.execute {
            val result = runCatching { ChatClient().send(pending.credentials.url, pending.credentials.token, pending.messages) }
            activity.runOnUiThread {
                if (closed || activity.isDestroyed) return@runOnUiThread
                if (!conversation.complete(pending, result.getOrNull())) { refresh(); return@runOnUiThread }
                error = result.exceptionOrNull()?.let {
                    if (it is ChatException) it.message else "Chat is unavailable. Try again shortly."
                }
                refresh()
            }
        }
    }
}
