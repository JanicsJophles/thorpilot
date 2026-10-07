package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class LibrarySearchTest {
    @Test fun handoffHandlesAccentsPunctuationAndReleaseSeparators() {
        assertTrue(LibrarySearch.matches("Pokémon: Let's Go, Eevee!", "Pokemon_Lets_Go_Eevee_USA.nsp switch"))
        assertTrue(LibrarySearch.matches("Ocarina 3D", "0004000000033500 The Legend of Zelda Ocarina of Time 3D"))
        assertTrue(LibrarySearch.matches("Pokemon Lets Go Eevee", "Pokémon: Let’s Go, Eevee!"))
        assertFalse(LibrarySearch.matches("Eevee", "Pokemon Let's Go Pikachu"))
    }
    @Test fun searchSupportsMultipleWordsPlatformsAndNonLatinTitles() {
        assertTrue(LibrarySearch.matches("persona psp", "Persona 2 psp"))
        assertFalse(LibrarySearch.matches("persona psp", "Persona 2 psx"))
        assertTrue(LibrarySearch.matches("ポケット", "ポケットモンスター"))
        assertTrue(LibrarySearch.matches("  ", "Any game"))
    }
}
