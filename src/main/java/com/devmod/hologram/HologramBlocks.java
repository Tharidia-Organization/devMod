package com.devmod.hologram;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.hologram.block.HologramProjectorBlock;

/**
 * Hologram module block registrations.
 * Registers the hologram projector block.
 */
public final class HologramBlocks {
    private HologramBlocks() {}

    /**
     * The hologram projector block.
     * Projects a 3D miniature hologram of the surrounding terrain.
     */
    public static final DeferredHolder<Block, HologramProjectorBlock> HOLOGRAM_PROJECTOR = DevMod.BLOCKS.register(
        "hologram_projector",
        () -> new HologramProjectorBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .strength(2.0F, 6.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> 7)
            .noOcclusion())
    );

    /**
     * Called during mod initialization to ensure blocks are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Hologram] Hologram blocks initialized");
    }
}
