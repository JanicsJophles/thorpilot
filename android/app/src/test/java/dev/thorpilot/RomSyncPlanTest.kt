package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class RomSyncPlanTest {
    private fun entry(path: String, hash: String = "a", size: Long = 10) = RomSyncPlan.Entry(path, size, hash.repeat(64))
    @Test fun additiveCopiesSkipVerifiedContentAndNeverOverwrite() {
        val source = listOf(entry("nds/Existing.nds"), entry("nds/Changed.nds"), entry("nds/New.nds"))
        val dest = listOf(entry("nds/Existing.nds"), entry("nds/Changed.nds", "b"))
        assertEquals(listOf(RomSyncPlan.Kind.SKIP, RomSyncPlan.Kind.CONFLICT, RomSyncPlan.Kind.COPY), RomSyncPlan.plan(source, dest).map { it.kind })
        assertEquals(RomSyncPlan.Kind.CONFLICT, RomSyncPlan.plan(listOf(entry("nds/Existing.nds", size = 11)), dest).single().kind)
    }
    @Test fun mapsAliasesToExistingFoldersAndPreservesDiscReferences() {
        val source = listOf(entry("ps1/Game/Game.cue"), entry("ps1/Game/Track 01.bin"), entry("3ds/Game.cia"))
        val plan = RomSyncPlan.plan(source, emptyList(), setOf("PSX", "n3ds"))
        assertEquals(listOf("PSX/Game/Game.cue", "PSX/Game/Track 01.bin", "n3ds/Game.cia"), plan.map { it.destinationPath })
        assertTrue(plan.all { it.kind == RomSyncPlan.Kind.COPY })
        // Identical bytes elsewhere must not break references to this exact filename.
        assertEquals(RomSyncPlan.Kind.COPY, RomSyncPlan.plan(listOf(entry("psx/Game/Track.bin")), listOf(entry("psx/Other/Track.bin"))).single().kind)
    }
    @Test fun rejectsUnsafePathsArchivesAndNonCia3dsContent() {
        val paths = listOf("../a.cia", "/n3ds/a.cia", "n3ds/../a.cia", "n3ds//a.cia", "n3ds/a\\b.cia", "n3ds/a.zip", "nds/a.7Z", "n3ds/a.3ds", "n3ds/a.bin", "n3ds/a.cia ", "nds/.hidden.nds", "nds/CON.nds", "nds/game.sav", "nds/game.ml0", "nds/systeminfo.txt", "nds/.nomedia")
        assertTrue(RomSyncPlan.plan(paths.map { entry(it) }, emptyList()).all { it.kind == RomSyncPlan.Kind.REJECT })
        assertEquals(RomSyncPlan.Kind.REJECT, RomSyncPlan.plan(listOf(entry("nds/a.nds").copy(sha256 = "unknown")), emptyList()).single().kind)
    }
    @Test fun unknownOrAmbiguousPlatformNeedsReview() {
        assertEquals(RomSyncPlan.Kind.REVIEW, RomSyncPlan.plan(listOf(entry("mystery/a.bin")), emptyList()).single().kind)
        assertEquals(RomSyncPlan.Kind.REVIEW, RomSyncPlan.plan(listOf(entry("ps1/a.bin")), emptyList(), setOf("ps1", "psx")).single().kind)
    }
    @Test fun rejectsCollisionsAndFileDirectoryOverlap() {
        val cases = listOf(listOf(entry("nds/A.nds"), entry("nds/a.nds")), listOf(entry("ps1/a.bin"), entry("psx/a.bin")), listOf(entry("psx/a.bin"), entry("psx/a.bin/b.bin")))
        cases.forEach { assertTrue(RomSyncPlan.plan(it, emptyList()).all { a -> a.kind == RomSyncPlan.Kind.CONFLICT }) }
        assertEquals(RomSyncPlan.Kind.CONFLICT, RomSyncPlan.plan(listOf(entry("psx/a.bin/b.bin")), listOf(entry("psx/a.bin"))).single().kind)
    }
    @Test fun invalidDestinationFailsClosed() {
        assertTrue(runCatching { RomSyncPlan.plan(listOf(entry("nds/a.nds")), listOf(entry("../bad.nds"))) }.isFailure)
        assertEquals(RomSyncPlan.Kind.CONFLICT, RomSyncPlan.plan(listOf(entry("nds/a.nds")), listOf(entry("nds/a.nds"), entry("nds/A.nds"))).single().kind)
    }
    @Test fun knownButMisplacedFormatsNeedReviewWithoutCopying() {
        val paths = listOf("nds/Game.cia", "gba/Game.nsp", "psp/Game.nds", "switch/Game.iso", "gb/Game.gbc", "snes/Game.bin")
        val actions = RomSyncPlan.plan(paths.map { entry(it) }, emptyList())
        assertTrue(actions.all { it.kind == RomSyncPlan.Kind.REVIEW && it.destinationPath == null })
    }
    @Test fun supportedPlatformFormatsAndAliasesStillCopy() {
        val paths = listOf("ds/Game.nds", "ps1/Game.chd", "psp/Game.cso", "gba/Game.gba", "gb/Game.gb", "gbc/Game.gbc",
            "nes/Game.nes", "snes/Game.sfc", "n64/Game.z64", "gc/Game.rvz", "wii/Game.wbfs", "switch/Game.nsp",
            "ps2/Game.chd", "dreamcast/Game/Track.raw", "megadrive/Game.smd", "saturn/Game.cue", "n3ds/Game.cia")
        assertTrue(RomSyncPlan.plan(paths.map { entry(it) }, emptyList()).all { it.kind == RomSyncPlan.Kind.COPY })
    }
}
