package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class HandheldSectionsTest {
    @Test fun shouldersWrapAndNestedToolsUseDeviceSection() {
        assertEquals("settings", HandheldSections.adjacent("home", -1))
        assertEquals("home", HandheldSections.adjacent("settings", 1))
        assertEquals("requests", HandheldSections.adjacent("eden-inspector", 1))
        assertEquals("chat", HandheldSections.adjacent("downloads", -1))
        assertEquals("My Thor", HandheldSections.label("care"))
    }
}
