package dev.thorpilot

/** Connection-bound conversation state, shared by both screens and testable
 * without a live server. All methods run on the UI thread. */
class ChatConversation(private val store: ConnectionStore, private val history: ChatHistory) {
    var identity = store.identity
        private set
    private val restored = history.bind(identity)
    var messages = restored.messages
        private set
    var draft = restored.draft
        private set
    var busy = false
        private set
    private var epoch = 0L

    fun rebind(): Boolean {
        val next = store.identity
        if (next == identity) return false
        identity = next
        epoch++
        busy = false
        val clean = history.bind(identity)
        messages = clean.messages
        draft = clean.draft
        return true
    }
    fun updateDraft(value: String, fromIdentity: String = identity) {
        rebind()
        if (fromIdentity != identity) return // Ignore a detached old screen's editor.
        draft = value.take(3000)
        history.saveDraft(draft)
    }
    fun clear() {
        rebind()
        epoch++
        busy = false
        messages = emptyList()
        draft = ""
        history.clear()
        history.bind(identity)
    }
    fun sendDraft(): PendingChat? {
        if (rebind() || busy || draft.isBlank()) return null
        val credentials = store.snapshot()
        if (credentials.identity != identity || credentials.url.isBlank() || credentials.token.isBlank()) return null
        messages = (messages + ChatMessage("user", draft.trim())).takeLast(20)
        draft = ""
        history.save(messages, draft)
        busy = true
        return PendingChat(credentials, messages.toList(), ++epoch)
    }
    fun retry(): PendingChat? {
        if (rebind() || busy || messages.lastOrNull()?.role != "user") return null
        val credentials = store.snapshot()
        if (credentials.identity != identity || credentials.url.isBlank() || credentials.token.isBlank()) return null
        busy = true
        return PendingChat(credentials, messages.toList(), ++epoch)
    }
    /** Returns false for replies from another connection or a cleared turn. */
    fun complete(pending: PendingChat, reply: ChatReply?): Boolean {
        rebind()
        if (pending.credentials.identity != identity || pending.epoch != epoch) return false
        busy = false
        if (reply != null) {
            val note = if (reply.unverified > 0) "\n\nSome suggestions could not be verified in the catalog." else ""
            messages = (messages + ChatMessage("assistant", reply.reply + note, reply.games)).takeLast(20)
            history.save(messages, draft)
        }
        return true
    }
}

class PendingChat(val credentials: ConnectionSnapshot, val messages: List<ChatMessage>, val epoch: Long)
