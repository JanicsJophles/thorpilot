package dev.thorpilot

/** Shared navigation order for touch tabs, shoulder buttons and display labels. */
object HandheldSections {
    val entries = listOf("home" to "Home", "chat" to "Ask", "device" to "My Thor", "requests" to "Requests", "settings" to "Settings")
    fun section(page: String): String = if (entries.any { it.first == page }) page else "device"
    fun label(page: String) = entries.first { it.first == section(page) }.second
    fun adjacent(page: String, direction: Int): String {
        val index = entries.indexOfFirst { it.first == section(page) }
        return entries[Math.floorMod(index + direction, entries.size)].first
    }
}
