package dev.thorpilot

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/** Explicitly selected document only; SAF does not establish that Azahar uses this file. */
class AzaharConfigDocument(context: Context, val uri: Uri) {
    private val resolver = context.applicationContext.contentResolver
    fun name(): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: throw IllegalArgumentException("The selected document has no filename.")
    fun read(): ByteArray {
        require(name() == "config.ini") { "Select Azahar's config.ini document." }
        return resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 1024 * 1024) { "The selected config is larger than 1 MiB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalStateException("The selected config could not be opened.")
    }
    fun validatedRead(): ByteArray {
        val bytes = read()
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { throw IllegalArgumentException("The selected config is not valid UTF-8 text.") }
        require('\u0000' !in text) { "The selected config contains binary data." }
        val lines = text.lineSequence().map { it.trim() }.toList()
        require(lines.count { it == "[Renderer]" } == 1) { "Select a config with exactly one Azahar Renderer section." }
        val renderer = lines.dropWhile { it != "[Renderer]" }.drop(1).takeWhile { !it.startsWith("[") }
        require(renderer.any { Regex("^(graphics_api|resolution_factor|shaders_accurate_mul)\\s*=").containsMatchIn(it) }) {
            "The selected config does not contain recognized Azahar renderer settings."
        }
        return bytes
    }
    fun write(bytes: ByteArray) {
        require(name() == "config.ini") { "The selected document is no longer config.ini." }
        require(bytes.size <= 1024 * 1024)
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
            ?: throw IllegalStateException("The selected config is not writable.")
    }
}
