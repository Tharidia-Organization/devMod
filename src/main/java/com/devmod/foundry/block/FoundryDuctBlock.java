package com.devmod.foundry.block;

import javax.annotation.Nonnull;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Foundry duct block variant for channel routing.
 * Adds IN_STRUCTURE state for visual feedback when part of a multiblock.
 */
public final class FoundryDuctBlock extends FoundryChannelBlock {
    public static final BooleanProperty IN_STRUCTURE = BooleanProperty.create("in_structure");

    @SuppressWarnings("this-escape")
    public FoundryDuctBlock() {
        super();
        registerDefaultState(defaultBlockState().setValue(IN_STRUCTURE, false));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(IN_STRUCTURE);
    }
}
