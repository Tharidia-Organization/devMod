package com.devmod.portal;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.portal.block.CustomPortalBlock;
import com.devmod.portal.block.RuneBlock;

/**
 * Portal module block registrations.
 * Includes the portal block and 5 rune blocks.
 */
public final class PortalBlocks {
    private PortalBlocks() {}

    // ========================================================================
    // Custom Portal Block
    // ========================================================================

    /**
     * The custom portal block that fills portal frames.
     * Supports 16 colors and axis orientation.
     */
    public static final DeferredHolder<Block, CustomPortalBlock> CUSTOM_PORTAL = DevMod.BLOCKS.register(
        "custom_portal",
        () -> new CustomPortalBlock(BlockBehaviour.Properties.of()
            .noCollission()
            .noOcclusion()
            .strength(-1.0F, 3600000.0F)  // Unbreakable except by frame destruction
            .sound(SoundType.GLASS)
            .lightLevel(state -> 11)
            .noLootTable())
    );

    // ========================================================================
    // Rune Blocks (5 types)
    // ========================================================================

    private static final Map<RuneType, DeferredHolder<Block, RuneBlock>> RUNE_BLOCKS = new EnumMap<>(RuneType.class);

    static {
        for (RuneType rune : RuneType.values()) {
            String name = "rune_" + rune.getSerializedName();
            RUNE_BLOCKS.put(rune, DevMod.BLOCKS.register(name,
                () -> new RuneBlock(rune, BlockBehaviour.Properties.of()
                    .strength(1.5F, 6.0F)  // Same as stone
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)
                    .noOcclusion())));
        }
    }

    /**
     * Gets the rune block for the specified type.
     */
    @Nullable
    public static DeferredHolder<Block, RuneBlock> getRuneBlock(@Nonnull RuneType type) {
        return RUNE_BLOCKS.get(type);
    }

    /**
     * Gets all rune blocks.
     */
    @Nonnull
    public static Map<RuneType, DeferredHolder<Block, RuneBlock>> getRuneBlocks() {
        return RUNE_BLOCKS;
    }

    // Convenience accessors
    public static final DeferredHolder<Block, RuneBlock> RUNE_HASTE = RUNE_BLOCKS.get(RuneType.HASTE);
    public static final DeferredHolder<Block, RuneBlock> RUNE_GATE = RUNE_BLOCKS.get(RuneType.GATE);
    public static final DeferredHolder<Block, RuneBlock> RUNE_ENHANCER = RUNE_BLOCKS.get(RuneType.ENHANCER);
    public static final DeferredHolder<Block, RuneBlock> RUNE_STRONG_ENHANCER = RUNE_BLOCKS.get(RuneType.STRONG_ENHANCER);
    public static final DeferredHolder<Block, RuneBlock> RUNE_INFINITY = RUNE_BLOCKS.get(RuneType.INFINITY);

    /**
     * Called during mod initialization to ensure blocks are registered.
     */
    public static void init() {
        // Static init ensures blocks are registered
        DevMod.LOGGER.debug("[Portal] Portal blocks initialized: 1 portal, {} runes", RUNE_BLOCKS.size());
    }
}
