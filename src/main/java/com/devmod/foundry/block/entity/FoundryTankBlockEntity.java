package com.devmod.foundry.block.entity;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.foundry.FoundryBlockEntities;

/**
 * Tank block entity for foundry (visual, links to controller).
 */
public class FoundryTankBlockEntity extends FoundryComponentBlockEntity {
    public FoundryTankBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_TANK.get()), pos, state);
    }
}
