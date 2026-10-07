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
        fun runChecks(context: Context) {
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
