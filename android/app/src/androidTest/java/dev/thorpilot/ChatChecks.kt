package dev.thorpilot

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle

/** Optional standalone runner for parsing, bounds, and process-persistent conversation state. */
class ChatChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            runChecks(targetContext)
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: chat response validation, HTTPS requirement, bounded history, cards, drafts, clear\n") })
        } catch (e: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${e.javaClass.simpleName}: ${e.message}\n") })
        }
    }
    companion object {
        private fun connectionBoundaryChecks(context: Context) {
            val store = ConnectionStore(context, "chat-boundary-connection-test")
            val history = ChatHistory(context, "chat-boundary-history-test")
            store.clear(); history.clear()
            try {
                store.save("https://a.example/romarr", "key-a")
                // Upgrade migration binds legacy messages before the settings UI
                // can save another connection, without losing the current chat.
                history.save(listOf(ChatMessage("user", "private-to-a")), "draft-a")
                val conversation = ChatConversation(store, history)
                val firstIdentity = store.identity
                check(conversation.messages.single().content == "private-to-a")
                check(conversation.draft == "draft-a")
                check(!store.save("HTTPS://A.EXAMPLE:443/romarr/", ""))
                check(store.identity == firstIdentity && store.token() == "key-a")
                check(!conversation.rebind() && conversation.draft == "draft-a")
                check(runCatching { store.save("https://b.example", "") }.isFailure)
                check(store.url == "https://a.example/romarr" && store.token() == "key-a")
                check(store.identity == firstIdentity)

                val pendingA = conversation.sendDraft()!!
                check(pendingA.credentials.url == "https://a.example/romarr")
                check(pendingA.credentials.token == "key-a")
                store.save("https://b.example", "key-b")
                // Simulate process death after durable settings save but before
                // the old panel receives its settings-change notification.
                val restartedB = ChatConversation(store, ChatHistory(context, "chat-boundary-history-test"))
                check(restartedB.messages.isEmpty() && restartedB.draft.isEmpty())
                check(conversation.rebind())
                conversation.updateDraft("late-a-editor", firstIdentity)
                check(conversation.draft.isEmpty())
                conversation.updateDraft("only-for-b")
                val pendingB = conversation.sendDraft()!!
                val calls = mutableListOf<Triple<String, String, List<ChatMessage>>>()
                fun fakeTransport(pending: PendingChat) {
                    calls += Triple(pending.credentials.url, pending.credentials.token, pending.messages)
                }
                fakeTransport(pendingB)
                check(calls.single().first == "https://b.example")
                check(calls.single().second == "key-b")
                check(calls.single().third.map { it.content } == listOf("only-for-b"))
                check(!conversation.complete(pendingA, ChatReply("late-a", emptyList(), 0)))
                check(conversation.busy) // An old callback cannot unlock B's send button.
                check(conversation.complete(pendingB, ChatReply("reply-b", emptyList(), 0)))

                // URL+key comparisons alone miss an A -> B -> A transition.
                store.save("https://a.example/romarr", "key-a")
                check(conversation.rebind())
                check(store.identity != firstIdentity)
                check(!conversation.complete(pendingA, ChatReply("old-a", emptyList(), 0)))
                check(conversation.messages.isEmpty())
                conversation.updateDraft("new-a")
                val clearedTurn = conversation.sendDraft()!!
                conversation.clear()
                check(!conversation.complete(clearedTurn, ChatReply("discard", emptyList(), 0)))
                check(conversation.retry() == null && conversation.messages.isEmpty())

                conversation.updateDraft("before-forget")
                val forgotten = conversation.sendDraft()!!
                store.clear()
                check(conversation.rebind())
                check(!conversation.complete(forgotten, ChatReply("discard", emptyList(), 0)))
                check(conversation.messages.isEmpty() && conversation.draft.isEmpty())
                check(conversation.retry() == null)
                store.save("https://a.example/romarr", "key-a")
                check(ChatConversation(store, history).messages.isEmpty())
                conversation.rebind()
                conversation.updateDraft("old-account")
                val sameServerOldAccount = conversation.sendDraft()!!
                store.save("https://a.example/romarr", "replacement-key")
                check(conversation.rebind())
                check(conversation.messages.isEmpty())
                check(!conversation.complete(sameServerOldAccount, ChatReply("discard", emptyList(), 0)))
            } finally { store.clear(); history.clear() }
        }
        fun runChecks(context: Context) {
            connectionBoundaryChecks(context)
            val parsed = ChatClient.parseResponse("""{"reply":"Try this", "games":[{"id":1,"title":"Example","platform":"nds","cover":"http://insecure.example/art"}],"unverified":2}""")
            check(parsed.games.single().cover == "")
            check(parsed.unverified == 2)
            check(runCatching { ChatClient.parseResponse("""{"error":"sensitive detail"}""") }.exceptionOrNull()?.message?.contains("sensitive detail") == false)
            check(runCatching { ChatClient.parseResponse("not JSON") }.isFailure)
            check(runCatching { ChatClient().send("http://example.com", "secret", listOf(ChatMessage("user", "Hello"))) }.isFailure)
            val history = ChatHistory(context, "chat-test")
            history.clear()
            history.save((1..30).map { ChatMessage("user", "Message $it") }, "draft")
            val restored = ChatHistory(context, "chat-test").load()
            check(restored.messages.size == 20 && restored.messages.first().content == "Message 11")
            check(restored.draft == "draft")
            history.save(listOf(ChatMessage("assistant", parsed.reply, parsed.games)))
            check(history.load().messages.single().games.single().title == "Example")
            history.saveDraft("x".repeat(4000))
            check(history.load().draft.length == 3000)
            history.clear()
            check(history.load().messages.isEmpty() && history.load().draft.isEmpty())
        }
    }

}
