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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
        "nexus_dummy_far_west",
        "nexus_dummy_west",
        "nexus_dummy_center",
        "nexus_dummy_east",
        "nexus_dummy_far_east"
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
        placeItemWorkshopChests(level, origin);
        placeCollisionLabDummies(level, origin);

        // Decorate all zones with themed furniture, equipment, and interactive blocks
        NexusZoneDecorator.INSTANCE.decorateAllZones(level);

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
        Optional<ZoneSlot> combatSlot = slotRegistry.getSlot("combat_lab");
        if (combatSlot.isPresent()) {
            ZoneSlot slot = combatSlot.get();
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

        Optional<ZoneDefinition> combatZone = ZoneResolver.INSTANCE.resolveByNameOrAlias(server, "combat_lab");
        if (combatZone.isPresent()) {
            ZoneDefinition zone = combatZone.get();
            BlockPos spawn = zone.getAbsoluteSpawn(origin);
            if (spawn == null) {
                spawn = zone.bounds().floorCenter();
            }
            return defaultDummyPositions(spawn);
        }

        BlockPos center = origin.offset(legacySpawnOffset("combat_lab"));
        return defaultDummyPositions(center);
    }

    @Nonnull
    private static List<BlockPos> defaultDummyPositions(@Nonnull BlockPos center) {
        int floorY = center.getY();
        int centerX = center.getX();
        int dummyZ = center.getZ() - 8;
        return List.of(
            new BlockPos(centerX - 8, floorY + 1, dummyZ),
            new BlockPos(centerX - 4, floorY + 1, dummyZ),
            new BlockPos(centerX, floorY + 1, dummyZ),
            new BlockPos(centerX + 4, floorY + 1, dummyZ),
            new BlockPos(centerX + 8, floorY + 1, dummyZ)
        );
    }

    /**
     * Place equipment chests in the item_workshop zone.
     */
    private void placeItemWorkshopChests(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        var server = level.getServer();
        if (server == null) return;

        Optional<ZoneSlot> slot = ZoneSlotRegistry.get(server).getSlot("item_workshop");
        if (slot.isEmpty()) return;

        BlockPos center = slot.get().bounds().floorCenter();
        BlockState chestState = Blocks.CHEST.defaultBlockState()
            .setValue(ChestBlock.FACING, Direction.SOUTH);

        // Weapons chest
        BlockPos weaponPos = center.offset(-3, 1, -3);
        level.setBlock(weaponPos, chestState, 3);
        if (level.getBlockEntity(weaponPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_SWORD));
            chest.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
            chest.setItem(2, new ItemStack(Items.BOW));
            chest.setItem(3, new ItemStack(Items.CROSSBOW));
            chest.setItem(4, new ItemStack(Items.TRIDENT));
        }

        // Armor chest
        BlockPos armorPos = center.offset(3, 1, -3);
        level.setBlock(armorPos, chestState, 3);
        if (level.getBlockEntity(armorPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_HELMET));
            chest.setItem(1, new ItemStack(Items.IRON_CHESTPLATE));
            chest.setItem(2, new ItemStack(Items.IRON_LEGGINGS));
            chest.setItem(3, new ItemStack(Items.IRON_BOOTS));
            chest.setItem(4, new ItemStack(Items.SHIELD));
            chest.setItem(9, new ItemStack(Items.DIAMOND_HELMET));
            chest.setItem(10, new ItemStack(Items.DIAMOND_CHESTPLATE));
            chest.setItem(11, new ItemStack(Items.DIAMOND_LEGGINGS));
            chest.setItem(12, new ItemStack(Items.DIAMOND_BOOTS));
        }

        LOGGER.debug("[Nexus] Placed item_workshop chests at {}", center);
    }

    /**
     * Spawn training dummies in the collision_lab zone for hitbox testing.
     */
    private void placeCollisionLabDummies(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        var server = level.getServer();
        if (server == null) return;

        Optional<ZoneSlot> slot = ZoneSlotRegistry.get(server).getSlot("collision_lab");
        if (slot.isEmpty()) return;

        BlockPos center = slot.get().bounds().floorCenter();
        int y = center.getY() + 1;

        // Spread dummies for hitbox visualization testing
        String[] ids = {"collision_dummy_1", "collision_dummy_2", "collision_dummy_3"};
        BlockPos[] positions = {
            new BlockPos(center.getX() - 6, y, center.getZ()),
            new BlockPos(center.getX(), y, center.getZ()),
            new BlockPos(center.getX() + 6, y, center.getZ())
        };

        for (int i = 0; i < ids.length; i++) {
            spawnDummy(level, positions[i], ids[i]);
        }

        LOGGER.debug("[Nexus] Placed collision_lab dummies at {}", center);
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
