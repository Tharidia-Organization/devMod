package com.devmod.zone;

import java.util.Objects;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.zone.block.ZoneMarkerBlock;

/**
 * Block registrations for the Zone Marker module.
 */
public final class ZoneBlocks {
    private ZoneBlocks() {}

    /**
     * Zone Marker block - marks zone bounds and opens zone editor.
     * Placeable by OP 2+ to define zone boundaries.
     */
    public static final DeferredHolder<Block, ZoneMarkerBlock> ZONE_MARKER =
        DevMod.BLOCKS.register("zone_marker",
            () -> new ZoneMarkerBlock(BlockBehaviour.Properties.of()
                .strength(2.0F, 6.0F)
                .sound(Objects.requireNonNull(SoundType.AMETHYST))
                .lightLevel(state -> 5)
                .noOcclusion())
        );

    /**
     * Initialize block registrations.
     * Called during module initialization to trigger static field loading.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Zone] Zone blocks initialized");
    }
}
