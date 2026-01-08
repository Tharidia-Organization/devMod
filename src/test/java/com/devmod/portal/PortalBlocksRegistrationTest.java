package com.devmod.portal;

import com.devmod.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for portal blocks registration.
 * Verifies all portal and rune blocks are properly registered.
 */
class PortalBlocksRegistrationTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("Custom portal block is registered")
    void customPortalBlockRegistered() {
        assertNotNull(PortalBlocks.CUSTOM_PORTAL,
            "CUSTOM_PORTAL should be registered");
        // Note: .get() requires full registry initialization, tested in-game
        assertEquals("devmod:custom_portal", PortalBlocks.CUSTOM_PORTAL.getId().toString(),
            "CUSTOM_PORTAL should have correct registry name");
    }

    @Test
    @DisplayName("All 5 rune blocks are registered")
    void allRuneBlocksRegistered() {
        assertNotNull(PortalBlocks.RUNE_HASTE, "RUNE_HASTE should be registered");
        assertNotNull(PortalBlocks.RUNE_GATE, "RUNE_GATE should be registered");
        assertNotNull(PortalBlocks.RUNE_ENHANCER, "RUNE_ENHANCER should be registered");
        assertNotNull(PortalBlocks.RUNE_STRONG_ENHANCER, "RUNE_STRONG_ENHANCER should be registered");
        assertNotNull(PortalBlocks.RUNE_INFINITY, "RUNE_INFINITY should be registered");
    }

    @Test
    @DisplayName("Rune blocks can be retrieved by RuneType")
    void runeBlocksRetrievableByType() {
        for (RuneType rune : RuneType.values()) {
            assertNotNull(PortalBlocks.getRuneBlock(rune),
                "Rune block for " + rune.name() + " should be retrievable");
        }
    }

    @Test
    @DisplayName("Rune blocks have correct registry names")
    void runeBlocksHaveCorrectRegistryNames() {
        // Note: .get() requires full registry initialization, tested in-game
        // Here we verify registry names are correct
        assertEquals("devmod:rune_haste", PortalBlocks.RUNE_HASTE.getId().toString());
        assertEquals("devmod:rune_gate", PortalBlocks.RUNE_GATE.getId().toString());
        assertEquals("devmod:rune_enhancer", PortalBlocks.RUNE_ENHANCER.getId().toString());
        assertEquals("devmod:rune_strong_enhancer", PortalBlocks.RUNE_STRONG_ENHANCER.getId().toString());
        assertEquals("devmod:rune_infinity", PortalBlocks.RUNE_INFINITY.getId().toString());
    }
}
