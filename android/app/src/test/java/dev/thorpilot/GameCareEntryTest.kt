package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class GameCareEntryTest {
    @Test fun reusedSuccessIsAnIndependentUntestedTrial() {
        val source = GameCareEntry(game = "Example", emulatorId = "eden", emulator = "Eden old",
            device = "Old device", gameRevision = "1.0", driver = "Old driver", scope = "Per-game",
            symptom = "Flicker", scene = "Station", original = "Fast", trial = "Balanced",
            result = "Better", before = "Flashing", after = "Clear", updated = 123)
        val trial = source.newTrial("New device", "Eden new")
        assertNotEquals(source.id, trial.id)
        assertEquals(source.id, trial.sourceId)
        assertEquals("Untested", trial.result)
        assertEquals(0L, trial.updated)
        assertEquals("", trial.original)
        assertEquals("", trial.before)
        assertEquals("", trial.after)
        assertEquals("", trial.driver)
        assertEquals("", trial.gameRevision)
        assertEquals("Unknown", trial.scope)
        assertEquals("New device", trial.device)
        assertEquals("Eden new", trial.emulator)
        assertEquals(source.emulatorId, trial.emulatorId)
        assertEquals(source.game, trial.game)
        assertEquals(source.scene, trial.scene)
        assertEquals(source.symptom, trial.symptom)
        assertEquals(source.trial, trial.trial)
        assertEquals("Better", source.result)
        assertEquals("Fast", source.original)
        assertNotEquals(trial.id, source.newTrial("New device", "Eden new").id)
    }
    @Test fun legacyDefaultsRemainAzaharAndUnknownScope() {
        val entry = GameCareEntry(game = "Legacy note")
        assertEquals("azahar", entry.emulatorId)
        assertEquals("Unknown", entry.scope)
        assertEquals("", entry.sourceId)
    }
}
