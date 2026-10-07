package dev.thorpilot

import java.text.Normalizer
import java.util.Locale

/** Read-only proposal. Executors must revalidate source hashes and destination absence before copying. */
object RomSyncPlan {
    data class Entry(val relativePath: String, val sizeBytes: Long, val sha256: String)
    enum class Kind { COPY, SKIP, CONFLICT, REJECT, REVIEW }
    data class Action(val source: Entry, val destinationPath: String?, val kind: Kind, val reason: String)

    private val aliases = mapOf(
        "n3ds" to "n3ds", "3ds" to "n3ds", "nds" to "nds", "ds" to "nds",
        "psx" to "psx", "ps1" to "psx", "psp" to "psp", "gba" to "gba",
        "gb" to "gb", "gbc" to "gbc", "nes" to "nes", "snes" to "snes",
        "n64" to "n64", "gc" to "gamecube", "gamecube" to "gamecube", "wii" to "wii",
        "switch" to "switch", "ps2" to "ps2", "dreamcast" to "dreamcast",
        "megadrive" to "genesis", "genesis" to "genesis", "saturn" to "saturn"
    )
    private val archives = setOf("zip", "7z", "rar", "gz", "bz2", "xz", "tar", "tgz", "zst")
    private val auxiliary = setOf("sav", "srm", "state", "bak", "ini", "cfg", "json", "xml", "txt", "png", "jpg", "jpeg", "webp")
    // Conservative format routing, not a claim that a particular emulator can run each file.
    private val platformFormats = mapOf(
        "n3ds" to setOf("cia"), "nds" to setOf("nds"),
        "psx" to setOf("chd", "cue", "bin", "m3u", "pbp", "iso", "img", "ccd", "sub"),
        "psp" to setOf("iso", "cso", "pbp"), "gba" to setOf("gba"),
        "gb" to setOf("gb"), "gbc" to setOf("gbc"), "nes" to setOf("nes"),
        "snes" to setOf("sfc", "smc"), "n64" to setOf("n64", "z64", "v64"),
        "gamecube" to setOf("iso", "gcm", "rvz", "m3u"),
        "wii" to setOf("iso", "rvz", "wbfs", "m3u"),
        "switch" to setOf("nsp", "xci"), "ps2" to setOf("iso", "chd", "cso", "bin", "cue", "m3u"),
        "dreamcast" to setOf("chd", "gdi", "cdi", "bin", "raw", "m3u"),
        "genesis" to setOf("md", "gen", "bin", "smd"),
        "saturn" to setOf("chd", "cue", "bin", "iso", "m3u", "img", "ccd", "sub")
    )
    private fun key(path: String) = Normalizer.normalize(path, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    private fun parts(path: String): List<String>? {
        if (path.isEmpty() || path.any { it.isISOControl() || it in "\\:*?\"<>|" }) return null
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || it.endsWith('.') || it.endsWith(' ') }) return null
        if (segments.any { it.substringBefore('.').uppercase(Locale.ROOT) in setOf("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9") }) return null
        return segments
    }
    private fun validEntry(e: Entry) = e.sizeBytes >= 0 && e.sha256.matches(Regex("[a-fA-F0-9]{64}"))
    private fun same(a: Entry, b: Entry) = a.sizeBytes == b.sizeBytes && a.sha256.equals(b.sha256, true)
    private fun overlaps(a: String, b: String) = a == b || a.startsWith("$b/") || b.startsWith("$a/")

    /** Paths are relative to each ROMs root. No deletion, overwrite, renaming, extraction, or I/O. */
    fun plan(source: List<Entry>, destination: List<Entry>, destinationFolders: Set<String> = emptySet()): List<Action> {
        // An incomplete or ambiguous destination inventory cannot authorize safe copies.
        require(destination.all { parts(it.relativePath)?.size?.let { n -> n >= 2 } == true && validEntry(it) }) { "Invalid destination inventory" }
        require(destinationFolders.all { parts(it)?.size == 1 }) { "Invalid destination folder" }
        val existing = destination.groupBy { key(it.relativePath) }
        val folders = (destinationFolders + destination.map { it.relativePath.substringBefore('/') }).groupBy { aliases[key(it)] ?: key(it) }
        val proposed = source.map { entry ->
            fun action(kind: Kind, reason: String, path: String? = null) = Action(entry, path, kind, reason)
            val p = parts(entry.relativePath)
            if (p == null || p.size < 2 || !validEntry(entry)) return@map action(Kind.REJECT, "Unsafe path or invalid size/hash")
            if (p.any { it.startsWith('.') }) return@map action(Kind.REJECT, "Hidden metadata is not a ROM")
            val ext = p.last().substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext in archives || ext == "3ds") return@map action(Kind.REJECT, "Archives and .3ds files are excluded")
            if (ext in auxiliary || ext.matches(Regex("(?:ml|ds|ss)[0-9]+"))) return@map action(Kind.REJECT, "Save data, configuration and artwork are not synced")
            val platform = aliases[key(p.first())] ?: return@map action(Kind.REVIEW, "Unknown platform folder")
            if (platform == "n3ds" && ext != "cia") return@map action(Kind.REJECT, "Nintendo 3DS accepts CIA files only")
            if (ext !in platformFormats.getValue(platform)) return@map action(Kind.REVIEW, "File format .$ext does not match the $platform folder; review its placement")
            val candidates = folders[platform].orEmpty()
            if (candidates.size > 1) return@map action(Kind.REVIEW, "Multiple destination folders match this platform")
            val path = (listOf(candidates.singleOrNull() ?: platform) + p.drop(1)).joinToString("/")
            val matches = existing[key(path)].orEmpty()
            when {
                matches.size > 1 -> action(Kind.CONFLICT, "Destination paths collide by case or Unicode", path)
                matches.size == 1 -> if (same(entry, matches.single())) action(Kind.SKIP, "Same path, size and SHA-256", path)
                    else action(Kind.CONFLICT, "Destination contains different content; never overwrite", path)
                existing.keys.any { overlaps(it, key(path)) } -> action(Kind.CONFLICT, "Destination file blocks this path", path)
                else -> action(Kind.COPY, "Add missing file; preserve nested paths", path)
            }
        }
        // Preserve CUE/M3U filenames: identical bytes at another path are not safely interchangeable.
        val targets = proposed.filter { it.destinationPath != null }
        return proposed.map { action ->
            val path = action.destinationPath ?: return@map action
            if (targets.any { it !== action && overlaps(key(path), key(it.destinationPath!!)) })
                action.copy(kind = Kind.CONFLICT, reason = "Source entries resolve to overlapping destination paths")
            else action
        }
    }
}
