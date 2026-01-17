package com.devmod.area;

import java.util.Objects;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.area.block.AreaEditorBlock;
import com.devmod.area.block.NexusEditorCentralBlock;

/**
 * Block registrations for the Area module.
 */
public final class AreaBlocks {
    private AreaBlocks() {}

    /**
     * Area Editor block - placeable by OP 2+ to create and configure areas.
     */
    public static final DeferredHolder<Block, AreaEditorBlock> AREA_EDITOR =
        DevMod.BLOCKS.register("area_editor",
            () -> new AreaEditorBlock(BlockBehaviour.Properties.of()
                .strength(2.0F, 6.0F)
                .sound(Objects.requireNonNull(SoundType.METAL))
                .lightLevel(state -> 7)
                .noOcclusion())
        );

    /**
     * Nexus Editor Central block - spawns automatically in Nexus, indestructible.
     */
    public static final DeferredHolder<Block, NexusEditorCentralBlock> NEXUS_EDITOR_CENTRAL =
        DevMod.BLOCKS.register("nexus_editor_central",
            () -> new NexusEditorCentralBlock(BlockBehaviour.Properties.of()
                .strength(-1.0F, 3600000.0F)
                .sound(Objects.requireNonNull(SoundType.AMETHYST))
                .lightLevel(state -> 15)
                .noOcclusion()
                .noLootTable())
        );

    /**
     * Initialize block registrations.
     * Called during module initialization to trigger static field loading.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Area] Area blocks initialized");
    }
}
