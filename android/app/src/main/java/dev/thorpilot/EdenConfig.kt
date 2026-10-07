package dev.thorpilot

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Read-only interpretation of explicitly selected Eden v0.2.1 Android documents. */
object EdenConfig {
    const val MAX_BYTES = 1024 * 1024
    const val REVIEWED_BUILD = "1f6734c"
    data class Document(val name: String, val sha256: String, val renderer: Map<String, String>)
    data class Setting(val name: String, val value: String, val origin: String)
    private val keys = setOf("gpu_accuracy", "resolution_setup")
    fun parse(name: String, bytes: ByteArray, global: Boolean): Document {
        require(if (global) name == "config.ini" else Regex("[0-9a-fA-F]{16}\\.ini").matches(name)) {
            if (global) "Choose Eden's global config.ini." else "Choose a per-game INI named with its 16-digit title ID."
        }
        require(bytes.size <= MAX_BYTES) { "Configuration exceeds 1 MiB." }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require('\u0000' !in text) { "Configuration contains binary data." }
        var section = ""
        var count = 0
        val values = mutableMapOf<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(';') || line.startsWith('#')) continue
            if (line.startsWith('[')) {
                require(line.endsWith(']')) { "Malformed INI section." }
                section = line
                if (section == "[Renderer]") count++
                continue
            }
            if (section != "[Renderer]") continue
            val split = line.indexOf('=')
            require(split > 0) { "Malformed Renderer entry." }
            val key = line.substring(0, split).trim()
            if (key.substringBefore('\\') !in keys) continue
            require(key !in values) { "Duplicate setting: $key" }
            values[key] = line.substring(split + 1).trim()
        }
        require(count == 1 && values.isNotEmpty()) { "Choose an Eden config with one recognized Renderer section." }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        return Document(name, hash, values.toMap())
    }
    fun inspect(build: String, game: Document, global: Document?): List<Setting> {
        require(build == REVIEWED_BUILD) { "Effective settings are reviewed only for Eden Android v0.2.1 build $REVIEWED_BUILD. Installed build: ${build.ifBlank { "not detected" }}." }
        fun flag(doc: Document, key: String): Boolean = when (doc.renderer[key]) {
            null, "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Unrecognized flag: $key")
        }
        fun resolve(key: String, label: String, default: Int, names: List<String>): Setting {
            val inherited = flag(game, "$key\\use_global")
            val selected = if (inherited) global else game
            if (selected == null) return Setting(label, "Unknown", "Inherits global; select global config.ini")
            val compiled = flag(selected, "$key\\default")
            val raw = selected.renderer[key]
            val number = if (compiled || raw == null) default else raw.toIntOrNull()
            require(number != null && number in names.indices) { "Unsupported $label value; no inference made." }
            val origin = (if (inherited) "Global" else "Per-game") + if (compiled || raw == null) " · compiled default" else " · explicit value"
            return Setting(label, names[number], origin)
        }
        return listOf(resolve("gpu_accuracy", "GPU mode", 0, listOf("Fast", "Balanced", "Accurate")),
            resolve("resolution_setup", "Resolution", 3, listOf("0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×", "3×", "4×", "5×", "6×", "7×", "8×")))
    }
}
