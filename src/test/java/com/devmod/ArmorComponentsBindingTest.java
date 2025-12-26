package com.devmod;

import org.junit.jupiter.api.Test;

import com.devmod.components.ArmorComponents;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
