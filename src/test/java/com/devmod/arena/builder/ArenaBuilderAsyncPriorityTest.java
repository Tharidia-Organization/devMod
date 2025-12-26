package com.devmod.arena.builder;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.InstanceSettingsValidator;
import com.devmod.arena.telemetry.ArenaTelemetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaBuilderAsyncPriorityTest {

    @Test
    void asyncPriorityIsBlockedBySyncBuilder() {
        ArenaTemplate base = ArenaTemplate.defaultTemplate();
        ArenaTemplate template = withBuildPriority(base, ArenaTemplate.BuildSettings.Priority.ASYNC);

        ArenaBuilder.BlockPlacer blockPlacer = (x, y, z, material) -> 0;
        ArenaBuilder.EntitySpawner entitySpawner = (x, y, z, entityType) -> UUID.randomUUID();
        ChunkLoadingManager chunkManager = new ChunkLoadingManager(
            (chunkX, chunkZ) -> { },
            (chunkX, chunkZ) -> true,
            new ChunkLoadingManager.TicketManager() {
                @Override
                public void addTicket(int chunkX, int chunkZ) {}

                @Override
                public void removeTicket(int chunkX, int chunkZ) {}
            }
        );

        ArenaBuilder builder = new ArenaBuilder(
            new ArenaTelemetry(),
            blockPlacer,
            entitySpawner,
            chunkManager,
            null,
            InstanceSettingsValidator.InstanceLimits.defaults()
        );

        ArenaBuilder.BuildResult result = builder.build(template, 0, 64, 0);

        assertFalse(result.success());
        @Nonnull String errorMessage = Objects.requireNonNull(result.errorMessage(), "errorMessage");
        assertNotNull(errorMessage);
        assertTrue(errorMessage.contains("ASYNC"));
    }

    private static ArenaTemplate withBuildPriority(ArenaTemplate base, ArenaTemplate.BuildSettings.Priority priority) {
        ArenaTemplate.BuildSettings buildSettings = new ArenaTemplate.BuildSettings(
            priority,
            base.buildSettings().buildOrder()
        );
        return new ArenaTemplate(
            base.id(),
            base.extendsTemplate(),
            base.version(),
            base.schemaVersion(),
            base.breakingChange(),
            base.deprecated(),
            base.replacementVersion(),
            base.minParentVersion(),
            base.templateType(),
            base.origin(),
            base.size(),
            base.sizeX(),
            base.sizeZ(),
            base.floor(),
            base.walls(),
            base.ceiling(),
            base.underfloor(),
            base.palette(),
            base.biome(),
            base.lighting(),
            base.spawnSlots(),
            base.playerSpawnOffset(),
            base.mobSpawnStrategy(),
            base.forbiddenZones(),
            base.hazards(),
            base.environment(),
            base.compat(),
            base.instanceSettings(),
            base.structureNbt(),
            base.limits(),
            buildSettings,
            base.tags()
        );
    }
}
