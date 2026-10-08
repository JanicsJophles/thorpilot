package dev.thorpilot

/** Filename preparation only: never rewrites ROM bytes, existing files, or sidecar references. */
object RomTransferNaming {
    // Only the observed 3DS base-application ID layout. Updates/DLC and other platforms stay exact.
    private val applicationPrefix = Regex("^00040000[0-9a-fA-F]{8} +(.+)$")
    private val sourceSuffix = Regex("\\.(?:piratelegit|legit)$", RegexOption.IGNORE_CASE)
    /** Replace only the known base-CIA title; keep its source product/version/region tags and ID. */
    fun prepareWithTitle(platform: String, fileName: String, title: String): String {
        val fallback = prepare(platform, fileName)
        if (fallback == fileName) return fileName
        val clean = title.trim().map { if (it in "\\/:*?\"<>|") '-' else it }.joinToString("").replace(Regex(" +"), " ").trim(' ', '.')
        if (clean.isEmpty() || clean.any { it.isISOControl() }) return fallback
        val sourceTitle = applicationPrefix.matchEntire(fileName.dropLast(4))?.groupValues?.get(1) ?: return fallback
        val tags = Regex("\\([^()]+\\)").findAll(sourceTitle).joinToString(" ") { it.value }
        val result = listOf(clean, tags, "(${fileName.take(16)})").filter { it.isNotBlank() }.joinToString(" ") + fileName.takeLast(4)
        return result.takeIf { it.toByteArray(Charsets.UTF_8).size <= 240 } ?: fallback
    }
    fun prepare(platform: String, fileName: String): String {
        if (platform.lowercase(java.util.Locale.ROOT) !in setOf("3ds", "n3ds") || !fileName.endsWith(".cia", true)) return fileName
        val stem = fileName.dropLast(4)
        val match = applicationPrefix.matchEntire(stem) ?: return fileName
        val title = match.groupValues[1].replace(sourceSuffix, "")
        // A bare ID or punctuation is not enough evidence of a usable title.
        if (title.isBlank() || !title.any { it.isLetter() } || title.startsWith('.') || title.endsWith(' ')) return fileName
        val result = "$title (${fileName.take(16)})" + fileName.takeLast(4)
        return result.takeIf { it.toByteArray(Charsets.UTF_8).size <= 240 } ?: fileName
    }
}
