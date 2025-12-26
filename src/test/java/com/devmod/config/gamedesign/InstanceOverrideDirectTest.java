package com.devmod.config.gamedesign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InstanceOverrideDirectTest {

    @Test
    @DisplayName("applyTo updates only the overridden fields")
    void applyToUpdatesOverrides() {
        GameDesignConfig base = new GameDesignConfig();
        InstanceOverride override = new InstanceOverride("test")
            .setResonanceEnabled(false)
            .setDuoWindowMs(900)
            .setMaxContractsPerWave(4)
            .setProjectileDeflectionChance(0.42f)
            .setTideEnabled(false)
            .setSSSWaveTide(-25);

        GameDesignConfig result = override.applyTo(base.copy());

        assertFalse(result.resonance.enabled);
        assertEquals(900, result.resonance.duoWindowMs);
        assertEquals(4, result.contracts.maxContractsPerWave);
        assertEquals(0.42f, result.nemesis.projectileDeflectionChance, 0.0001f);
        assertFalse(result.tide.enabled);
        assertEquals(-25, result.tide.sssWave);
    }

    @Test
    @DisplayName("fromPreset applies hard mode defaults")
    void presetHardAppliesExpectedValues() {
        InstanceOverride override = InstanceOverride.fromPreset("hard");
        GameDesignConfig result = override.applyTo(new GameDesignConfig().copy());

        assertEquals("Hard Mode", override.getInstanceName());
        assertEquals(300, result.resonance.duoWindowMs);
        assertEquals(200, result.resonance.trinityWindowMs);
        assertEquals(100, result.resonance.apocalypseWindowMs);
        assertEquals(3, result.tide.playerDeath);
        assertEquals(0.50f, result.nemesis.projectileDeflectionChance, 0.0001f);
    }
}
