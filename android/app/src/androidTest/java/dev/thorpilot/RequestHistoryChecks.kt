package dev.thorpilot

import android.content.Context

object RequestHistoryChecks {
    fun run(context: Context) {
        val history = RequestHistory(context, "request-history-test")
        history.clear()
        try {
            val parsed = RequestClient.parse("""{"items":[{"game":"Pokémon: Let's Go, Eevee!","platform":"switch","status":"imported","progress":100,"detail":"Ready","review_required":false},{"game":"Other","status":"new-server-state","review_required":true,"client_status":"paused","client_detail":"User paused"}],"client_warnings":[{"client":"Example","detail":"Check connection"}]}""")
            check(history.save("server-a", parsed, 12345))
            val loaded = RequestHistory(context, "request-history-test").load("server-a")!!
            check(loaded.result == parsed && loaded.checkedAt == 12345L)
            check(history.load("server-b") == null && history.load("") == null)
            check(!history.save("server-a", RequestResult(message = "Offline"), 99999))
            check(history.load("server-a") == loaded) { "Failed refresh replaced good snapshot" }
            check(!history.save("", parsed, 99999))
            check(history.save("server-a", RequestResult(), 99999))
            check(history.load("server-a")!!.result.rows.isEmpty()) { "Empty successful response retained old requests" }
            context.getSharedPreferences("request-history-test", 0).edit().putString("body", "not-json").commit()
            check(history.load("server-a") == null)
            history.clear()
            check(history.load("server-a") == null)
        } finally { history.clear() }
    }
}
