package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class RomLibraryEntryTest {
    @Test fun inventoryKeepsExistingFormatsWithoutPretendingCiaIsPlayable() {
        assertTrue(RomLibraryEntry.visible("n3ds/Existing.3ds"))
        assertTrue(RomLibraryEntry.visible("nds/Game.nds"))
        assertTrue(RomLibraryEntry.visible("psx/Set/Game.m3u"))
        assertTrue(RomLibraryEntry("n3ds/Game.CIA", 10).installPackage)
        assertFalse(RomLibraryEntry("nds/Game.nds", 10).installPackage)
    }
    @Test fun inventoryExcludesArchivesSavesAndUnsafePaths() {
        listOf("Game.nds", "nds/Game.zip", "nds/Game.sav", "nds/Game.ml0", "nds/.hidden.nds", "../Game.nds", "nds//Game.nds", "nds/a\\b.nds", "nds/bad\nname.nds").forEach {
            assertFalse(it, RomLibraryEntry.visible(it))
        }
    }
    @Test fun storageNamesDistinguishPrimaryFromRemovableRoots() {
        assertEquals("Internal storage", RomLibraryEntry.storageLabel("primary:Cocoon/Games/ROMs"))
        assertEquals("SD / removable storage", RomLibraryEntry.storageLabel("A12B-3456:ROMs"))
    }
}
