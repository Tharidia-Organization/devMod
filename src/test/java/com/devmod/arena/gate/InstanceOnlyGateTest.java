package com.devmod.arena.gate;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.telemetry.ArenaTelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceOnlyGateTest {

    @Test
    void blocksWhenInstanceOnlyFalseAndNotDebugAllowlisted() {
        String prevInstanceOnly = System.getProperty("devmod.arena.instanceOnly");
        String prevLegacyAllowed = System.getProperty("devmod.arena.allowLegacyOverworldArena");
        try {
            System.setProperty("devmod.arena.instanceOnly", "false");
            System.clearProperty("devmod.arena.allowLegacyOverworldArena");

            ArenaTemplateConfig.ConfigSnapshot snapshot = ArenaTemplateConfig.load().snapshot();
            InstanceOnlyGate gate = new InstanceOnlyGate(snapshot, new ArenaTelemetry());

            ResourceKey<Level> key = overworldKey();

            InstanceOnlyGate.Result result = gate.checkDimensionKey(key, "SomeCaller");
            assertEquals(InstanceOnlyGate.Result.BLOCKED, result);
        } finally {
            restoreProperty("devmod.arena.instanceOnly", prevInstanceOnly);
            restoreProperty("devmod.arena.allowLegacyOverworldArena", prevLegacyAllowed);
        }
    }

    @Test
    void allowsDebugCallerWhenLegacyAllowedAndInstanceOnlyFalse() {
        String prevInstanceOnly = System.getProperty("devmod.arena.instanceOnly");
        String prevLegacyAllowed = System.getProperty("devmod.arena.allowLegacyOverworldArena");
        try {
            System.setProperty("devmod.arena.instanceOnly", "false");
            System.setProperty("devmod.arena.allowLegacyOverworldArena", "true");

            ArenaTemplateConfig.ConfigSnapshot snapshot = ArenaTemplateConfig.load().snapshot();
            InstanceOnlyGate gate = new InstanceOnlyGate(snapshot, new ArenaTelemetry());

            ResourceKey<Level> key = overworldKey();

            InstanceOnlyGate.Result result = gate.checkDimensionKey(key, "QuickTestWizard");
            assertEquals(InstanceOnlyGate.Result.ALLOWED_DEBUG_ONLY, result);
        } finally {
            restoreProperty("devmod.arena.instanceOnly", prevInstanceOnly);
            restoreProperty("devmod.arena.allowLegacyOverworldArena", prevLegacyAllowed);
        }
    }

    private static ResourceKey<Level> overworldKey() {
        return ResourceKey.create(
            Objects.requireNonNull(Registries.DIMENSION, "dimensionRegistry"),
            Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), "overworldLocation")
        );
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
