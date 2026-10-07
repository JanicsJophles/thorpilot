package dev.thorpilot

import java.util.Locale

/** Inventory classification only; a known extension does not establish emulator compatibility. */
data class RomLibraryEntry(val path: String, val size: Long?) {
    val platform: String get() = path.substringBefore('/')
    val installPackage: Boolean get() = path.substringAfterLast('.', "").equals("cia", true)
    companion object {
        private val extensions = setOf("nds", "gba", "gb", "gbc", "nes", "sfc", "smc", "n64", "z64", "v64", "iso", "cso", "chd", "cue", "m3u", "pbp", "gcm", "rvz", "wbfs", "nsp", "xci", "cia", "cci", "3ds", "3dsx", "md", "gen", "sms", "gg", "pce")
        fun visible(path: String): Boolean {
            val parts = path.split('/')
            return parts.size >= 2 && parts.none { it.isBlank() || it.startsWith('.') || '\\' in it || it.any { c -> c.code < 32 } } &&
                path.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions
        }
        fun storageLabel(documentId: String) = if (documentId.substringBefore(':') == "primary") "Internal storage" else "SD / removable storage"
    }
}
