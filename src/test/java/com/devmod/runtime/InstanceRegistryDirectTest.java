package com.devmod.runtime;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceRegistryDirectTest {

    @BeforeEach
    void resetRegistry() {
        InstanceRegistry.INSTANCE.clear();
    }

    @AfterEach
    void cleanupRegistry() {
        InstanceRegistry.INSTANCE.clear();
    }

    @Test
    @DisplayName("Registry maps players to instances")
    void registryMapsPlayersToInstances() {
        UUID ownerId = UUID.randomUUID();
        InstanceData instance = InstanceRegistry.INSTANCE.createInstance("arena_default", ownerId);
        UUID instanceId = instance.getInstanceId();

        UUID playerId = UUID.randomUUID();
        InstanceRegistry.INSTANCE.mapPlayer(playerId, instanceId);

        Optional<InstanceData> mapped = InstanceRegistry.INSTANCE.getPlayerInstance(playerId);
        assertTrue(mapped.isPresent());
        assertEquals(instanceId, mapped.get().getInstanceId());
    }

    @Test
    @DisplayName("Registry dimension lookups round-trip")
    void registryDimensionLookupsRoundTrip() {
        UUID ownerId = UUID.randomUUID();
        InstanceData instance = InstanceRegistry.INSTANCE.createInstance("arena_default", ownerId);
        UUID instanceId = instance.getInstanceId();

        ResourceKey<Level> dimensionKey = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.parse("devmod:instance_test")
        );
        InstanceRegistry.INSTANCE.setDimensionKey(instanceId, dimensionKey);

        Optional<InstanceData> resolved = InstanceRegistry.INSTANCE.getInstanceByDimension(dimensionKey);
        assertTrue(resolved.isPresent());
        assertEquals(instanceId, resolved.get().getInstanceId());
    }
}
