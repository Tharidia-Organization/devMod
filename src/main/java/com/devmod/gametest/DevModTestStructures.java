package com.devmod.gametest;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import com.devmod.DevMod;

/**
 * Provides test structure templates for DevMod GameTests.
 *
 * Structures are defined programmatically to avoid NBT file management.
 * Each structure is a simple empty platform with stone floor.
 *
 * Available templates:
 * - empty_3x3: 3x3x3 test area (for unit tests)
 * - empty_5x5: 5x5x5 test area (for entity tests)
 * - combat_arena: 7x5x7 arena for combat tests
 */

@EventBusSubscriber(modid = DevMod.MODID)
public class DevModTestStructures {

    // Template sizes (width x height x depth)
    public static final int EMPTY_3X3_SIZE = 3;
    public static final int EMPTY_5X5_SIZE = 5;
    public static final int COMBAT_ARENA_WIDTH = 7;
    public static final int COMBAT_ARENA_HEIGHT = 5;
    public static final int COMBAT_ARENA_DEPTH = 7;

    /**
     * Registers GameTests with the NeoForge system.
     */
    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        event.register(DevModGameTests.class);
        DevMod.LOGGER.info("[DevMod] Registered {} GameTests", 15);
    }

    /**
     * Creates an empty test structure at runtime.
     * The structure has:
     * - Stone floor at Y=0
     * - Air blocks above
     * - Barrier blocks around the edges (optional, controlled by addBarriers)
     *
     * @param level The server level
     * @param pos The position to build at
     * @param sizeX Width
     * @param sizeY Height
     * @param sizeZ Depth
     * @param addBarriers Whether to add barrier walls
     */
    public static void buildEmptyStructure(ServerLevel level, BlockPos pos,
                                           int sizeX, int sizeY, int sizeZ,
                                           boolean addBarriers) {
        // Build stone floor
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                level.setBlock(Objects.requireNonNull(pos.offset(x, 0, z)), Objects.requireNonNull(Blocks.STONE.defaultBlockState()), 3);
            }
        }

        // Clear air above
        for (int x = 0; x < sizeX; x++) {
            for (int y = 1; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    level.setBlock(Objects.requireNonNull(pos.offset(x, y, z)), Objects.requireNonNull(Blocks.AIR.defaultBlockState()), 3);
                }
            }
        }

        // Add barrier walls if requested
        if (addBarriers) {
            // North and South walls
            for (int x = 0; x < sizeX; x++) {
                for (int y = 1; y < sizeY; y++) {
                    level.setBlock(Objects.requireNonNull(pos.offset(x, y, 0)), Objects.requireNonNull(Blocks.BARRIER.defaultBlockState()), 3);
                    level.setBlock(Objects.requireNonNull(pos.offset(x, y, sizeZ - 1)), Objects.requireNonNull(Blocks.BARRIER.defaultBlockState()), 3);
                }
            }
            // East and West walls
            for (int z = 1; z < sizeZ - 1; z++) {
                for (int y = 1; y < sizeY; y++) {
                    level.setBlock(Objects.requireNonNull(pos.offset(0, y, z)), Objects.requireNonNull(Blocks.BARRIER.defaultBlockState()), 3);
                    level.setBlock(Objects.requireNonNull(pos.offset(sizeX - 1, y, z)), Objects.requireNonNull(Blocks.BARRIER.defaultBlockState()), 3);
                }
            }
        }
    }

    /**
     * Returns the structure size for a template name.
     * Used by GameTestHelper to set up test bounds.
     */
    public static BlockPos getStructureSize(String templateName) {
        return switch (templateName) {
            case "empty_3x3" -> new BlockPos(EMPTY_3X3_SIZE, EMPTY_3X3_SIZE, EMPTY_3X3_SIZE);
            case "empty_5x5" -> new BlockPos(EMPTY_5X5_SIZE, EMPTY_5X5_SIZE, EMPTY_5X5_SIZE);
            case "combat_arena" -> new BlockPos(COMBAT_ARENA_WIDTH, COMBAT_ARENA_HEIGHT, COMBAT_ARENA_DEPTH);
            default -> new BlockPos(3, 3, 3); // Default fallback
        };
    }
}
