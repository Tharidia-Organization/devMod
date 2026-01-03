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
        config.getResonance().duoWindowMs = 777;
        config.getContracts().maxContractsPerWave = 9;
        config.getTide().enabled = false;

        GameDesignConfig copy = config.copy();

        assertNotSame(config, copy);
        assertNotSame(config.getResonance(), copy.getResonance());
        assertNotSame(config.getContracts(), copy.getContracts());
        assertNotSame(config.getTide(), copy.getTide());

        assertEquals(777, copy.getResonance().duoWindowMs);
        assertEquals(9, copy.getContracts().maxContractsPerWave);
        assertEquals(false, copy.getTide().enabled);

        copy.getResonance().duoWindowMs = 100;
        copy.getContracts().maxContractsPerWave = 1;
        copy.getTide().enabled = true;

        assertEquals(777, config.getResonance().duoWindowMs);
        assertEquals(9, config.getContracts().maxContractsPerWave);
        assertEquals(false, config.getTide().enabled);
    }
}
