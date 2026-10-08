package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class RomTransferNamingTest {
    private val original = "0004000000174600 Pokemon Super Mystery Dungeon (CTR-P-BPXE) (v0.0.0) (W).piratelegit.cia"
    private val prepared = "Pokemon Super Mystery Dungeon (CTR-P-BPXE) (v0.0.0) (W) (0004000000174600).cia"
    private fun entry(name: String = original) = ThorDownloadEntry("server-id", "Server title", "n3ds", name, 123, "a".repeat(64))

    @Test fun observedBaseApplicationGetsSearchableTitleWithoutLosingIdentity() {
        assertEquals(prepared, RomTransferNaming.prepare("n3ds", original))
        assertEquals(prepared, RomTransferNaming.prepare("3ds", original))
        assertEquals(prepared, RomTransferNaming.prepare("n3ds", prepared))
    }

    @Test fun transferKeepsOriginalProvenanceAndContentIdentity() {
        val old = entry()
        val next = old.preparedForTransfer()
        assertEquals(original, old.fileName)
        assertEquals("n3ds/$original", old.destination())
        assertEquals("n3ds/$prepared", next.destination())
        assertEquals(original, next.originalFileName)
        assertEquals(old.id, next.id)
        assertEquals(old.sha256, next.sha256)
        assertEquals(old.sizeBytes, next.sizeBytes)
        assertEquals(old.title, next.title)
        assertEquals(next, next.preparedForTransfer())
    }

    @Test fun observedSuffixesAreCaseInsensitiveAndOnlyRemovedAtTheEnd() {
        val animalCrossing = "0004000000086300 Animal Crossing - New Leaf (CTR-P-EGDE) (v3.0.0) (U)"
        val expected = "Animal Crossing - New Leaf (CTR-P-EGDE) (v3.0.0) (U) (0004000000086300).cia"
        listOf(".legit", ".LEGIT", ".piratelegit", ".PiRaTeLeGiT").forEach {
            assertEquals(expected, RomTransferNaming.prepare("n3ds", "$animalCrossing$it.cia"))
        }
        assertEquals("Game.legitimate (0004000000086300).cia", RomTransferNaming.prepare("n3ds", "0004000000086300 Game.legitimate.cia"))
        assertEquals("Game.legit Edition (0004000000086300).cia", RomTransferNaming.prepare("n3ds", "0004000000086300 Game.legit Edition.cia"))
        assertEquals("Game.legit (0004000000086300).cia", RomTransferNaming.prepare("n3ds", "0004000000086300 Game.legit.legit.cia"))
    }

    @Test fun unrelatedNumbersUpdatesDlcAndSidecarsAreNotRenamed() {
        listOf("007 James Bond.cia", "0004000000174600.cia", "0004000000174600 123.cia",
            "0004000e00174600 Game.cia", "0004008c00174600 Game.cia", "ABCDEF0123456789 Game.cia",
            "Game.piratelegit.cia", "0004000000174600 Game.cue").forEach {
            assertEquals(it, RomTransferNaming.prepare("n3ds", it))
        }
        assertEquals(original, RomTransferNaming.prepare("nds", original))
        assertEquals("007 James Bond.nds", RomTransferNaming.prepare("nds", "007 James Bond.nds"))
    }

    @Test fun regionVersionAndDifferentTitleIdsStayDistinct() {
        assertNotEquals(prepared, RomTransferNaming.prepare("n3ds", original.replace("174600", "174700")))
        assertNotEquals(prepared, RomTransferNaming.prepare("n3ds", original.replace("(W)", "(USA)")))
        assertNotEquals(prepared, RomTransferNaming.prepare("n3ds", original.replace("v0.0.0", "v1.0.0")))
    }

    @Test fun samePreparedDestinationStillUsesConflictRules() {
        val next = entry().preparedForTransfer()
        val source = RomSyncPlan.Entry(next.destination(), next.sizeBytes, next.sha256)
        val conflicting = source.copy(sha256 = "b".repeat(64))
        assertEquals(RomSyncPlan.Kind.CONFLICT, RomSyncPlan.plan(listOf(source), listOf(conflicting)).single().kind)
        assertEquals(RomSyncPlan.Kind.SKIP, RomSyncPlan.plan(listOf(source), listOf(source)).single().kind)
        assertTrue(RomSyncPlan.plan(listOf(source, source), emptyList()).all { it.kind == RomSyncPlan.Kind.CONFLICT })
    }

    @Test fun sourceValidationCannotBeBypassedByCleanup() {
        assertThrows(IllegalArgumentException::class.java) { entry("0004000000174600 ../Game.cia").preparedForTransfer() }
        assertThrows(IllegalArgumentException::class.java) { entry("0004000000174600 Game\u0000.cia").preparedForTransfer() }
    }
}
