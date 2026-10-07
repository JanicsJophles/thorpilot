package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class EdenConfigTest {
    private fun game(text: String) = EdenConfig.parse("0100F43008C44000.ini", text.toByteArray(), false)
    private fun global(text: String) = EdenConfig.parse("config.ini", text.toByteArray(), true)
    @Test fun defaultFlagsOverrideStaleRawValues() {
        val g = game("[Renderer]\ngpu_accuracy\\use_global=true")
        val base = global("[Renderer]\ngpu_accuracy\\default=true\ngpu_accuracy=2\nresolution_setup=12")
        val values = EdenConfig.inspect(EdenConfig.REVIEWED_BUILD, g, base)
        assertEquals(listOf("Fast", "1×"), values.map { it.value })
        assertTrue(values.all { it.origin.contains("compiled default") })
    }
    @Test fun gameOverrideWinsAndMissingGlobalIsNotInvented() {
        val g = game("[Renderer]\ngpu_accuracy\\use_global=false\ngpu_accuracy\\default=false\ngpu_accuracy=1")
        val values = EdenConfig.inspect(EdenConfig.REVIEWED_BUILD, g, null)
        assertEquals("Balanced", values[0].value)
        assertEquals("Per-game · explicit value", values[0].origin)
        assertEquals("Unknown", values[1].value)
    }
    @Test fun missingFlagsUseReviewedDefaultsAndHashPreservesBytes() {
        val a = game("\uFEFF[Renderer]\r\ngpu_accuracy=2\r\n")
        val b = game("[Renderer]\ngpu_accuracy=2\n")
        assertNotEquals(a.sha256, b.sha256)
        val base = global("[Renderer]\ngpu_accuracy\\default=false\ngpu_accuracy=2")
        assertEquals("Accurate", EdenConfig.inspect(EdenConfig.REVIEWED_BUILD, a, base)[0].value)
        assertTrue(runCatching { EdenConfig.inspect("future", a, base) }.isFailure)
    }
    @Test fun rejectsAmbiguousOrMalformedDocuments() {
        listOf("[Renderer]\ngpu_accuracy=1\ngpu_accuracy=2", "[Renderer]\ngpu_accuracy=1\n[Renderer]",
            "[Renderer]\ngpu_accuracy=\u0000", "[Controls]\ngpu_accuracy=1").forEach {
            assertTrue(runCatching { game(it) }.isFailure)
        }
        assertTrue(runCatching { EdenConfig.parse("config.ini", byteArrayOf(0xc3.toByte()), true) }.isFailure)
        assertTrue(runCatching { EdenConfig.parse("config.ini", ByteArray(EdenConfig.MAX_BYTES + 1), true) }.isFailure)
        assertTrue(runCatching { EdenConfig.parse("config.ini", "[Renderer]\ngpu_accuracy=1".toByteArray(), false) }.isFailure)
        val bad = game("[Renderer]\ngpu_accuracy\\use_global=maybe")
        assertTrue(runCatching { EdenConfig.inspect(EdenConfig.REVIEWED_BUILD, bad, null) }.isFailure)
        val invalid = game("[Renderer]\ngpu_accuracy\\use_global=false\ngpu_accuracy\\default=false\ngpu_accuracy=99")
        assertTrue(runCatching { EdenConfig.inspect(EdenConfig.REVIEWED_BUILD, invalid, null) }.isFailure)
    }
}
