package com.devmod.config.gamedesign;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GameDesignConfigManagerDirectTest {

    private final GameDesignConfigManager manager = GameDesignConfigManager.INSTANCE;

    @AfterEach
    void resetManagerState() {
        manager.clearInstanceOverrides();
        manager.resetToDefaults();
    }

    @Test
    @DisplayName("getEffectiveConfig returns global when no overrides are present")
    void returnsGlobalWhenNoOverride() {
        manager.resetToDefaults();
        manager.clearInstanceOverrides();

        GameDesignConfig global = manager.getGlobalConfig();
        GameDesignConfig effective = manager.getEffectiveConfig(UUID.randomUUID());

        assertSame(global, effective);
    }

    @Test
    @DisplayName("instance overrides apply without mutating global config")
    void overridesApplyWithoutMutatingGlobal() {
        manager.resetToDefaults();
        manager.clearInstanceOverrides();

        GameDesignConfig global = manager.getGlobalConfig();
        long originalWindow = global.getResonance().duoWindowMs;

        UUID instanceId = UUID.randomUUID();
        InstanceOverride override = new InstanceOverride("test").setDuoWindowMs(900);
        manager.setInstanceOverride(instanceId, override);

        GameDesignConfig effective = manager.getEffectiveConfig(instanceId);

        assertNotSame(global, effective);
        assertEquals(900, effective.getResonance().duoWindowMs);
        assertEquals(originalWindow, global.getResonance().duoWindowMs);
    }
}
