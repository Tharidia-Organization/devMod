package com.devmod.config.gamedesign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class GameDesignConfigDirectTest {

    @Test
    @DisplayName("copy creates independent config with the same primitive values")
    void copyCreatesIndependentConfig() {
        GameDesignConfig config = new GameDesignConfig();
        config.resonance.duoWindowMs = 777;
        config.contracts.maxContractsPerWave = 9;
        config.tide.enabled = false;

        GameDesignConfig copy = config.copy();

        assertNotSame(config, copy);
        assertNotSame(config.resonance, copy.resonance);
        assertNotSame(config.contracts, copy.contracts);
        assertNotSame(config.tide, copy.tide);

        assertEquals(777, copy.resonance.duoWindowMs);
        assertEquals(9, copy.contracts.maxContractsPerWave);
        assertEquals(false, copy.tide.enabled);

        copy.resonance.duoWindowMs = 100;
        copy.contracts.maxContractsPerWave = 1;
        copy.tide.enabled = true;

        assertEquals(777, config.resonance.duoWindowMs);
        assertEquals(9, config.contracts.maxContractsPerWave);
        assertEquals(false, config.tide.enabled);
    }
}
