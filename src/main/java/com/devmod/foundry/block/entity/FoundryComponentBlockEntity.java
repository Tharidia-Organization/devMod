package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.foundry.FoundryBlockEntities;

/**
 * Base class for foundry component block entities linked to a controller.
 */
public abstract class FoundryComponentBlockEntity extends BlockEntity {
    private static final String TAG_CONTROLLER = "ControllerPos";

    @Nullable
    private BlockPos controllerPos;

    protected FoundryComponentBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_DRAIN.get()), pos, state);
    }

    protected FoundryComponentBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(type), pos, state);
    }

    public void setControllerPos(@Nullable BlockPos pos) {
        controllerPos = pos;
        setChanged();
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Nullable
    public FoundryControllerBlockEntity getController(Level level) {
        BlockPos pos = controllerPos;
        if (pos == null) {
            return null;
        }
        var be = level.getBlockEntity(pos);
        return be instanceof FoundryControllerBlockEntity controller ? controller : null;
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putLong(TAG_CONTROLLER, controllerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_CONTROLLER)) {
            controllerPos = BlockPos.of(tag.getLong(TAG_CONTROLLER));
        } else {
            controllerPos = null;
        }
    }
}
