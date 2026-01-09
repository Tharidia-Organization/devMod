package com.devmod.clone;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.clone.block.ImprinterBlock;
import com.devmod.clone.block.NeurocellBlock;
import com.devmod.clone.block.NeurocellLBlock;
import com.devmod.clone.block.NeurolinkBlock;
import com.devmod.clone.block.ReformerBlock;
import com.devmod.clone.block.TelepadBlock;

/**
 * Clone module block registrations.
 * Registers all clone-related blocks.
 */
public final class CloneBlocks {
    private CloneBlocks() {}

    /**
     * The telepad block.
     * Teleportation pad with charging mechanic.
     */
    public static final DeferredHolder<Block, TelepadBlock> TELEPAD = DevMod.BLOCKS.register(
        "telepad",
        () -> new TelepadBlock(BlockBehaviour.Properties.of()
            .strength(2.0F, 6.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> 5)
            .noOcclusion())
    );

    /**
     * The imprinter block.
     * Automatic entity scanner that fills bioscanners.
     */
    public static final DeferredHolder<Block, ImprinterBlock> IMPRINTER = DevMod.BLOCKS.register(
        "imprinter",
        ImprinterBlock::new
    );

    /**
     * The neurocell block.
     * Cloning chamber that processes bioscan data.
     */
    public static final DeferredHolder<Block, NeurocellBlock> NEUROCELL = DevMod.BLOCKS.register(
        "neurocell",
        NeurocellBlock::new
    );

    /**
     * The neurolink block.
     * Connection cables between clone system blocks.
     */
    public static final DeferredHolder<Block, NeurolinkBlock> NEUROLINK = DevMod.BLOCKS.register(
        "neurolink",
        NeurolinkBlock::new
    );

    /**
     * The reformer block.
     * Spawns cloned entities from processed data.
     */
    public static final DeferredHolder<Block, ReformerBlock> REFORMER = DevMod.BLOCKS.register(
        "reformer",
        ReformerBlock::new
    );

    /**
     * The large neurocell block (3x3x3).
     * Cloning chamber for larger entities.
     */
    public static final DeferredHolder<Block, NeurocellLBlock> NEUROCELL_L = DevMod.BLOCKS.register(
        "neurocell_l",
        NeurocellLBlock::new
    );

    /**
     * Called during mod initialization to ensure blocks are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Clone] Clone blocks initialized");
    }
}
