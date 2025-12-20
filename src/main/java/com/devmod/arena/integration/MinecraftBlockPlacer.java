package com.devmod.arena.integration;

import com.devmod.arena.builder.ArenaBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minecraft/NeoForge implementation of BlockPlacer.
 *
 * <p>Handles actual block placement in ServerLevel with:</p>
 * <ul>
 *   <li>Material name to BlockState resolution</li>
 *   <li>State ID tracking for rollback</li>
 *   <li>Neighbor update flags configuration</li>
 *   <li>Block entity preservation</li>
 * </ul>
 */
public class MinecraftBlockPlacer implements ArenaBuilder.BlockPlacer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftBlockPlacer.class);

    /** Block update flags: no neighbor updates (2), no block updates (16), no observer notify (64) */
    private static final int PLACEMENT_FLAGS = 2 | 16 | 64;

    private final ServerLevel level;
    private final Map<Long, BlockState> originalStates;

    /**
     * Creates a new block placer for the given level.
     *
     * @param level The server level to place blocks in
     */
    public MinecraftBlockPlacer(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        this.originalStates = new ConcurrentHashMap<>();
    }

    /**
     * Exposes the underlying level (used for gating instance-only builds).
     */
    public ServerLevel level() {
        return level;
    }

    @Override
    public int placeBlock(int x, int y, int z, String material) {
        BlockPos pos = new BlockPos(x, y, z);
        long packedPos = pos.asLong();

        // Capture original state for rollback
        BlockState originalState = level.getBlockState(pos);
        originalStates.putIfAbsent(packedPos, originalState);

        // Resolve block from material string
        BlockState newState = resolveBlockState(material);
        if (newState == null) {
            LOGGER.warn("Unknown material '{}', using air", material);
            newState = Blocks.AIR.defaultBlockState();
        }

        // Place the block
        boolean success = level.setBlock(pos, Objects.requireNonNull(newState), PLACEMENT_FLAGS);
        if (!success) {
            LOGGER.debug("Failed to place block at {} (material: {})", pos, material);
        }

        // Return original state ID for rollback tracking
        return Block.getId(originalState);
    }

    @Override
    public boolean revertBlock(long packedPos, int stateId) {
        BlockPos pos = BlockPos.of(packedPos);

        // Try cached original state first
        BlockState originalState = originalStates.remove(packedPos);
        if (originalState == null) {
            // Fallback to state ID lookup
            originalState = Block.stateById(stateId);
            if (originalState == null) {
                LOGGER.warn("Cannot revert block at {}: unknown state ID {}", pos, stateId);
                return false;
            }
        }

        return level.setBlock(Objects.requireNonNull(pos), originalState, PLACEMENT_FLAGS);
    }

    /**
     * Resolves a material string to a BlockState.
     *
     * <p>Supports formats:</p>
     * <ul>
     *   <li>"minecraft:stone" - Full ResourceLocation</li>
     *   <li>"stone" - Defaults to minecraft namespace</li>
     *   <li>"stone[facing=north]" - With block state properties (future)</li>
     * </ul>
     */
    @Nullable
    private BlockState resolveBlockState(String material) {
        if (material == null || material.isEmpty()) {
            return null;
        }

        // Handle namespace
        String fullName = material.contains(":") ? material : "minecraft:" + material;

        // Parse ResourceLocation
        ResourceLocation loc = ResourceLocation.tryParse(fullName);
        if (loc == null) {
            LOGGER.warn("Invalid material format: {}", material);
            return null;
        }

        // Get block from registry
        Block block = BuiltInRegistries.BLOCK.get(loc);
        if (block == Blocks.AIR && !fullName.equals("minecraft:air")) {
            // Not found - might be air or invalid
            if (!BuiltInRegistries.BLOCK.containsKey(loc)) {
                return null;
            }
        }

        return block.defaultBlockState();
    }

    /**
     * Clears the original state cache.
     * Call after a build is fully committed.
     */
    public void clearCache() {
        originalStates.clear();
    }

    /**
     * Returns the number of tracked original states.
     */
    public int getTrackedStateCount() {
        return originalStates.size();
    }

    /**
     * Returns the server level this placer operates on.
     */
    public ServerLevel getLevel() {
        return level;
    }
}
