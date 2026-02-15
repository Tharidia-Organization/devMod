package com.devmod.endurance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;

import com.devmod.DevMod;
import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.TemplateSpawnValidator;
import com.devmod.arena.spawn.SpawnOccupancyTracker;

/**
 * Resolves spawn positions, manages spawn pools, and validates spawn slots
 * for endurance wave mob spawning.
 * Extracted from WaveManager to isolate position-selection logic.
 */
final class WaveSpawnPositionResolver {

    static final WaveSpawnPositionResolver INSTANCE = new WaveSpawnPositionResolver();

    private WaveSpawnPositionResolver() {}

    /**
     * Build a SpawnContext from an arena and its handle.
     * Returns null if the arena/handle is missing spawn positions.
     */
    @Nullable
    WaveManager.SpawnContext buildSpawnContext(ArenaContext arena, ArenaHandle handle) {
        if (arena == null || handle == null || handle.mobSpawnPositions() == null || handle.mobSpawnPositions().isEmpty()) {
            return null;
        }
        List<BlockPos> positions = new ArrayList<>(handle.mobSpawnPositions().size());
        for (ArenaHandle.BlockPos pos : handle.mobSpawnPositions()) {
            positions.add(new BlockPos(pos.x(), pos.y(), pos.z()));
        }

        ArenaTemplate template = null;
        TemplateSpawnValidator runtimeValidator = null;
        Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap = Collections.emptyMap();
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        if (registry != null) {
            template = registry.get(handle.templateId()).orElse(null);
            if (template != null) {
                slotMap = buildMobSpawnSlotMap(template, handle);
                runtimeValidator = new TemplateSpawnValidator();
            }
        }

        WaveManager.SpawnPools pools = buildSpawnPools(positions, slotMap);
        return new WaveManager.SpawnContext(positions, slotMap, template, runtimeValidator, pools);
    }

    /**
     * Build spawn pools from positions and their slot map, categorizing by tag.
     */
    WaveManager.SpawnPools buildSpawnPools(List<BlockPos> positions, Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap) {
        List<BlockPos> melee = new ArrayList<>();
        List<BlockPos> ranged = new ArrayList<>();
        List<BlockPos> corner = new ArrayList<>();
        for (BlockPos pos : positions) {
            ArenaTemplate.SpawnSlot slot = slotMap.get(pos);
            if (slot != null && slot.tags() != null) {
                if (slot.tags().contains("melee")) {
                    melee.add(pos);
                }
                if (slot.tags().contains("ranged")) {
                    ranged.add(pos);
                }
                if (slot.tags().contains("corner")) {
                    corner.add(pos);
                }
            }
        }
        return new WaveManager.SpawnPools(positions, melee, ranged, corner);
    }

    /**
     * Pick a validated spawn position from the candidate list, respecting occupancy
     * and runtime validation constraints.
     */
    @Nullable
    BlockPos pickValidatedSpawnPosition(
            List<BlockPos> positions,
            int startIndex,
            SpawnOccupancyTracker occupied,
            @Nullable TemplateSpawnValidator runtimeValidator,
            Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap,
            @Nullable ArenaTemplate template,
            ServerLevel level,
            boolean allowReuse) {
        int size = positions.size();
        for (int offset = 0; offset < size; offset++) {
            BlockPos pos = positions.get((startIndex + offset) % size);
            if (!allowReuse && occupied.isOccupied(pos)) {
                continue;
            }
            if (runtimeValidator != null && template != null && !slotMap.isEmpty()) {
                ArenaTemplate.SpawnSlot slot = slotMap.get(pos);
                if (slot == null) {
                    continue;
                }
                if (!runtimeValidator.validateAtRuntime(template.id(), slot, level, pos)) {
                    continue;
                }
            }
            if (!allowReuse) {
                occupied.markOccupied(pos);
            }
            return pos;
        }
        return null;
    }

    /**
     * Choose the best spawn pool for a mob based on the requested role and mob type.
     */
    List<BlockPos> chooseSpawnPool(WaveDirector.SpawnRole role, WaveManager.SpawnPools pools, Mob mob) {
        if (role == WaveDirector.SpawnRole.RANGED && !pools.ranged().isEmpty()) {
            return pools.ranged();
        }
        if (role == WaveDirector.SpawnRole.MELEE && !pools.melee().isEmpty()) {
            return pools.melee();
        }
        if (role == WaveDirector.SpawnRole.CORNER && !pools.corner().isEmpty()) {
            return pools.corner();
        }
        boolean isRanged = mob instanceof RangedAttackMob;
        if (isRanged && !pools.ranged().isEmpty()) {
            return pools.ranged();
        }
        if (!pools.melee().isEmpty()) {
            return pools.melee();
        }
        if (!pools.corner().isEmpty()) {
            return pools.corner();
        }
        return pools.all();
    }

    /**
     * Resolve the pool tag name for telemetry/logging purposes.
     */
    String resolvePoolTag(List<BlockPos> candidatePool, WaveManager.SpawnPools pools) {
        if (candidatePool == pools.ranged()) {
            return "ranged";
        }
        if (candidatePool == pools.melee()) {
            return "melee";
        }
        if (candidatePool == pools.corner()) {
            return "corner";
        }
        return "all";
    }

    /**
     * Map a SpawnAffix to its preferred SpawnRole.
     */
    WaveDirector.SpawnRole resolveSpawnRole(@Nullable SpawnAffix affix) {
        if (affix == null) {
            return WaveDirector.SpawnRole.ANY;
        }
        return switch (affix) {
            case SNIPER -> WaveDirector.SpawnRole.RANGED;
            case RUSH, BRUTE -> WaveDirector.SpawnRole.MELEE;
            case ELITE, OBJECTIVE_ELITE -> WaveDirector.SpawnRole.CORNER;
            default -> WaveDirector.SpawnRole.ANY;
        };
    }

    /**
     * Build a map of BlockPos to SpawnSlot from an arena template and handle.
     */
    Map<BlockPos, ArenaTemplate.SpawnSlot> buildMobSpawnSlotMap(
            ArenaTemplate template, ArenaHandle handle) {
        Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap = new HashMap<>();
        if (template.spawnSlots() == null) {
            return slotMap;
        }
        for (ArenaTemplate.SpawnSlot slot : template.spawnSlots()) {
            if (slot.tags() == null || !(slot.tags().contains("mob") || slot.tags().contains("boss"))) {
                continue;
            }
            int[] pos = slot.pos();
            if (pos == null || pos.length != 3) continue;
            int x = handle.originX() + pos[0];
            int y = resolveSpawnY(slot, template, handle.originY());
            int z = handle.originZ() + pos[2];
            slotMap.put(new BlockPos(x, y, z), slot);
        }
        return slotMap;
    }

    /**
     * Resolve the Y coordinate for a spawn slot based on its YMode.
     */
    int resolveSpawnY(ArenaTemplate.SpawnSlot slot, ArenaTemplate template, int originY) {
        int baseY = slot.pos() != null && slot.pos().length == 3 ? slot.pos()[1] : 0;
        int floorY = template.floor() != null ? template.floor().y() : originY;
        if (slot.yMode() == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            return floorY + baseY;
        }
        return baseY;
    }
}
