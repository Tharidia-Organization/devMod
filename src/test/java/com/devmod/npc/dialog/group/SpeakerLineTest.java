package com.devmod.npc.dialog.group;

import org.junit.jupiter.api.Test;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test per verificare il fix del null-check in SpeakerLine.
 */
class SpeakerLineTest {

    @Test
    void testValidCreation() {
        // Test creazione valida
        SpeakerLine line = SpeakerLine.npc(Objects.requireNonNull(UUID.randomUUID()), "Hello!");
        assertNotNull(line);
        assertFalse(line.isPlayer());
        assertFalse(line.isNarrator());
        assertTrue(line.isNpc());
    }

    @Test
    void testNullSpeakerIdThrows() {
        // PRIMA DEL FIX: Avrebbe creato l'oggetto, NPE in isPlayer()
        // DOPO IL FIX: NPE immediata nel costruttore
        NullPointerException ex = assertThrows(
            NullPointerException.class,
            () -> new SpeakerLine(null, "text", null, null)
        );
        assertEquals("speakerId cannot be null", ex.getMessage());
    }

    @Test
    void testNullTextThrows() {
        NullPointerException ex = assertThrows(
            NullPointerException.class,
            () -> new SpeakerLine(Objects.requireNonNull(UUID.randomUUID()), null, null, null)
        );
        assertEquals("text cannot be null", ex.getMessage());
    }

    @Test
    void testSpecialSpeakers() {
        SpeakerLine player = SpeakerLine.player("I speak");
        SpeakerLine narrator = SpeakerLine.narrator("Narration");

        assertTrue(player.isPlayer());
        assertFalse(player.isNarrator());

        assertFalse(narrator.isPlayer());
        assertTrue(narrator.isNarrator());
    }
}
