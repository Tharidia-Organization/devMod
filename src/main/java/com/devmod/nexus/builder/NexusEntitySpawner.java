package com.devmod.nexus.builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat;
import com.devmod.nexus.data.ZoneSlot;
import com.devmod.nexus.data.ZoneSlotRegistry;
import com.devmod.nexus.runtime.NexusHubManager;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZonePresets;
import com.devmod.zone.runtime.ZoneResolver;

/**
 * Handles entity spawning for the Nexus hub.
 * Extracted from NexusHubBuilder to preserve entity spawning functionality.
 *
 * <p>Spawns:
 * <ul>
 *   <li>Combat training dummies in the combat zone</li>
 *   <li>Future: NPCs via NeurocellNpc item system</li>
 * </ul>
 */
public final class NexusEntitySpawner {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusEntitySpawner.class);

    /** Singleton instance. */
    public static final NexusEntitySpawner INSTANCE = new NexusEntitySpawner();

    /** Legacy spawn offsets from zone presets. */
    private static final Map<String, BlockPos> LEGACY_SPAWN_OFFSETS = loadLegacySpawnOffsets();

    private static final String[] DEFAULT_DUMMY_IDS = {
        "nexus_dummy_west",
        "nexus_dummy_center",
        "nexus_dummy_east"
    };

    private NexusEntitySpawner() {}

    /**
     * Spawn all entities after hub build completes.
     * Called from NexusDimensionManager after build finalization.
     *
     * @param level the server level
     * @param origin the hub origin position
     */
    public void postBuildEntities(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        var server = level.getServer();
        if (server != null) {
            NexusHubManager.INSTANCE.initialize(server);
        }

        spawnCombatDummies(level, origin);

        // NOTE: NexusAvatarManager.spawn() removed - hub now starts empty.
        // Use NeurocellNpc item to spawn NPCs with the new NPC system.

        LOGGER.debug("[Nexus] Post-build entities spawned at {}", origin);
    }

    /**
     * Spawn combat training dummies in the combat zone.
     *
     * @param level the server level
     * @param origin the hub origin position
     */
    private void spawnCombatDummies(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        List<BlockPos> positions = resolveCombatDummyPositions(level, origin);
        if (positions.isEmpty()) {
            LOGGER.debug("[Nexus] No combat dummy spawn positions available");
            return;
        }

        boolean useDefaultIds = positions.size() == DEFAULT_DUMMY_IDS.length;
        for (int i = 0; i < positions.size(); i++) {
            String id = useDefaultIds
                ? DEFAULT_DUMMY_IDS[i]
                : "nexus_dummy_" + (i + 1);
            spawnDummy(level, positions.get(i), Objects.requireNonNull(id));
        }

        LOGGER.debug("[Nexus] Spawned {} combat dummies", positions.size());
    }

    /**
     * Spawn a single training dummy at the given position.
     *
     * @param level the server level
     * @param pos the spawn position
     * @param id the dummy identifier for tracking
     */
    private void spawnDummy(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull String id) {
        DummmmmmyCompat.removeDummy(level, id);
        DummmmmmyCompat.spawnDummy(level, pos, id);
    }

    @Nonnull
    private List<BlockPos> resolveCombatDummyPositions(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        var server = level.getServer();
        if (server == null) {
            return List.of();
        }

        ZoneSlotRegistry slotRegistry = ZoneSlotRegistry.get(server);
        Optional<ZoneSlot> tutorialSlot = slotRegistry.getSlot("tutorial");
        if (tutorialSlot.isPresent()) {
            ZoneSlot slot = tutorialSlot.get();
            if (!slot.permissions().canSpawnMobs()) {
                LOGGER.debug("[Nexus] Slot '{}' disallows mob spawning; skipping dummies", slot.slotId());
                return List.of();
            }
            List<BlockPos> npcSpawns = slot.getAbsoluteNpcSpawnPositions();
            if (!npcSpawns.isEmpty()) {
                return npcSpawns;
            }
            return defaultDummyPositions(slot.bounds().floorCenter());
        }

        Optional<ZoneDefinition> combatZone = ZoneResolver.INSTANCE.resolveByNameOrAlias(server, "combat");
        if (combatZone.isPresent()) {
            ZoneDefinition zone = combatZone.get();
            BlockPos spawn = zone.getAbsoluteSpawn(origin);
            if (spawn == null) {
                spawn = zone.bounds().floorCenter();
            }
            return defaultDummyPositions(spawn);
        }

        BlockPos center = origin.offset(legacySpawnOffset("combat"));
        return defaultDummyPositions(center);
    }

    @Nonnull
    private static List<BlockPos> defaultDummyPositions(@Nonnull BlockPos center) {
        int floorY = center.getY();
        int centerX = center.getX();
        int dummyZ = center.getZ() - 8;
        return List.of(
            new BlockPos(centerX - 4, floorY + 1, dummyZ),
            new BlockPos(centerX, floorY + 1, dummyZ),
            new BlockPos(centerX + 4, floorY + 1, dummyZ)
        );
    }

    /**
     * Load legacy spawn offsets from zone presets.
     * Used for backward compatibility with zone-based positioning.
     */
    private static Map<String, BlockPos> loadLegacySpawnOffsets() {
        Map<String, BlockPos> offsets = new HashMap<>();
        for (ZoneDefinition zone : ZonePresets.createLegacyZones(Objects.requireNonNull(BlockPos.ZERO))) {
            BlockPos spawnOffset = zone.spawnOffset();
            if (spawnOffset != null) {
                offsets.put(zone.zoneId(), spawnOffset);
            }
        }
        return offsets;
    }

    /**
     * Get the legacy spawn offset for a zone.
     *
     * @param zoneId the zone identifier
     * @return the spawn offset, or ZERO if not found
     */
    @Nonnull
    private static BlockPos legacySpawnOffset(@Nonnull String zoneId) {
        BlockPos offset = LEGACY_SPAWN_OFFSETS.get(zoneId);
        if (offset == null) {
            LOGGER.warn("[NexusEntitySpawner] Missing legacy spawn offset for zone '{}'", zoneId);
            return Objects.requireNonNull(BlockPos.ZERO);
        }
        return offset;
    }
}
