package dev.thorpilot

import java.text.Normalizer
import java.util.Locale

/** Title search only; never treats a fuzzy text match as proof of game identity or availability. */
object LibrarySearch {
    private fun words(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("['’‘]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    fun matches(query: String, text: String): Boolean {
        val haystack = words(text)
        return words(query).split(' ').filter { it.isNotEmpty() }.all { it in haystack }
    }
}
