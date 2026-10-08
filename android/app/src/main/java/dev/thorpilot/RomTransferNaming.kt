package dev.thorpilot

/** Filename preparation only: never rewrites ROM bytes, existing files, or sidecar references. */
object RomTransferNaming {
    // Only the observed 3DS base-application ID layout. Updates/DLC and other platforms stay exact.
    private val applicationPrefix = Regex("^00040000[0-9a-fA-F]{8} +(.+)$")
    private val sourceSuffix = Regex("\\.(?:piratelegit|legit)$", RegexOption.IGNORE_CASE)
    fun prepare(platform: String, fileName: String): String {
        if (platform.lowercase(java.util.Locale.ROOT) !in setOf("3ds", "n3ds") || !fileName.endsWith(".cia", true)) return fileName
        val stem = fileName.dropLast(4)
        val match = applicationPrefix.matchEntire(stem) ?: return fileName
        val title = match.groupValues[1].replace(sourceSuffix, "")
        // A bare ID or punctuation is not enough evidence of a usable title.
        if (title.isBlank() || !title.any { it.isLetter() } || title.startsWith('.') || title.endsWith(' ')) return fileName
        return "$title (${fileName.take(16)})" + fileName.takeLast(4)
    }
}
