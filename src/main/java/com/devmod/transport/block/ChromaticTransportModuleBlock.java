package com.devmod.transport.block;

import javax.annotation.Nonnull;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import com.devmod.transport.TransportEnhancement;

/**
 * Chromatic module block that exposes the COLOR property.
 */
public class ChromaticTransportModuleBlock extends TransportModuleBlock {
    public ChromaticTransportModuleBlock(BlockBehaviour.Properties properties) {
        super(TransportEnhancement.CHROMATIC, properties);
    }

    @Override
    protected void createBlockStateDefinition(
        @Nonnull StateDefinition.Builder<Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }
}
