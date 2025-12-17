package com.frenkvs.devmod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ensures armorStatsComponent does not silently fallback when registry isn't bound
 * unless the explicit test flag is set.
 */
public class ArmorComponentsBindingTest {

    @Test
    void armorComponentThrowsWhenFallbackDisabled() {
        String previous = System.getProperty("devmod.allowFallbackComponents");
        System.setProperty("devmod.allowFallbackComponents", "false");
        try {
            assertThrows(IllegalStateException.class, ArmorComponents::armorStatsComponent);
        } finally {
            if (previous != null) {
                System.setProperty("devmod.allowFallbackComponents", previous);
            } else {
                System.clearProperty("devmod.allowFallbackComponents");
            }
        }
    }
}
