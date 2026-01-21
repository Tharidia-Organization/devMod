package com.devmod.debug.block;

import java.util.Objects;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;

/**
 * Debug module block registrations.
 * Provides development and testing tools.
 */
public final class DebugBlocks {
    private DebugBlocks() {}

    /**
     * The entity scanner block.
     * Scans nearby entities and displays their attributes, equipment, and AI goals.
     */
    public static final DeferredHolder<Block, EntityScannerBlock> ENTITY_SCANNER = DevMod.BLOCKS.register(
        "entity_scanner",
        () -> new EntityScannerBlock(BlockBehaviour.Properties.of()
            .mapColor(Objects.requireNonNull(MapColor.COLOR_GREEN))
            .strength(2.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(state -> 5)
            .noOcclusion())
    );

    /**
     * Called during mod initialization to ensure blocks are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Debug] Debug blocks initialized");
    }
}
