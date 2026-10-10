package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class PagePositionsTest {
    @Test fun sectionsKeepIndependentPositionsAndBackKeepsScroll() {
        val positions = PagePositions()
        positions.remember("device", 820, "downloads")
        positions.remember("requests", 140, null)
        positions.focus("device", "device-library")
        assertEquals(PagePositions.Position(820, "device-library"), positions.get("device"))
        assertEquals(PagePositions.Position(140), positions.get("requests"))
        assertEquals(PagePositions.Position(), positions.get("home"))
    }
    @Test fun restoredStateRejectsUnknownRoutesAndNegativeOffsets() {
        val positions = PagePositions()
        positions.remember("settings", -20, "private-form-value")
        positions.remember("unknown", 42, "home")
        assertEquals(PagePositions.Position(), positions.get("settings"))
        assertEquals(PagePositions.Position(), positions.get("unknown"))
    }
}
