package com.devmod.foundry.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.foundry.block.entity.FoundryTankBlockEntity;

/**
 * Foundry tank block (visual tank; storage delegated to controller).
 */
public class FoundryTankBlock extends Block implements EntityBlock {
    public FoundryTankBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(4.0f, 6.0f)
            .sound(Objects.requireNonNull(SoundType.GLASS))
            .requiresCorrectToolForDrops()
            .noOcclusion());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FoundryTankBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ) {
        return null;
    }
}
