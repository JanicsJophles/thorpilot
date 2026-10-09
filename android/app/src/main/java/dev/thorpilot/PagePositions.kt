package dev.thorpilot

/** UI-only positions, bounded to known screens. Never stores form values or credentials. */
class PagePositions {
    data class Position(val scrollY: Int = 0, val focusRoute: String? = null)
    private val saved = mutableMapOf<String, Position>()
    fun get(page: String) = saved[page] ?: Position()
    fun remember(page: String, scrollY: Int, focusRoute: String?) {
        if (page !in ROUTES) return
        saved[page] = Position(scrollY.coerceAtLeast(0), focusRoute?.takeIf { it in ROUTES })
    }
    fun focus(page: String, destination: String) {
        remember(page, get(page).scrollY, destination)
    }
    companion object {
        val ROUTES = setOf("home", "chat", "device", "care", "snapshots", "eden-inspector",
            "downloads", "device-library", "library-sync", "setup", "requests", "settings")
    }
}
