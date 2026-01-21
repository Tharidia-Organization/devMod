package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.FoundryBlocks;
import com.devmod.foundry.client.model.FoundryModelProperties;
import com.devmod.foundry.client.model.FoundryTankModelLoader;

/**
 * Tank block entity for foundry (visual, links to controller).
 */
public class FoundryTankBlockEntity extends FoundryComponentBlockEntity {
    private static final String TAG_DISPLAY_FLUID = "DisplayFluid";
    private static final String TAG_DISPLAY_AMOUNT = "DisplayAmount";
    private static final String TAG_DISPLAY_CAPACITY = "DisplayCapacity";

    private FluidStack displayFluid = FluidStack.EMPTY;
    private int displayAmount = 0;
    private int displayCapacity = 0;

    public FoundryTankBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_TANK.get()), pos, state);
    }

    public void tickServer() {
        Level level = Objects.requireNonNull(getLevel());
        if (level.isClientSide) {
            return;
        }
        FoundryControllerBlockEntity controller = getController(level);
        if (controller == null || !controller.isFormed()) {
            updateDisplay(FluidStack.EMPTY, 0, 0);
            return;
        }

        boolean fuelTank = getBlockState().is(Objects.requireNonNull(FoundryBlocks.FOUNDRY_FUEL_TANK.get()));
        FluidStack fluid = fuelTank ? controller.getFuelFluid() : controller.getPrimaryMoltenFluid();
        int amount = fuelTank ? controller.getFuelAmount() : controller.getMoltenAmount();
        int capacity = fuelTank ? controller.getFuelCapacity() : controller.getMoltenCapacity();
        if (amount <= 0 || fluid.isEmpty()) {
            fluid = FluidStack.EMPTY;
        }
        updateDisplay(fluid, amount, capacity);
    }

    public FluidStack getDisplayFluid() {
        return displayFluid.copy();
    }

    public int getDisplayAmount() {
        return displayAmount;
    }

    public int getDisplayCapacity() {
        return displayCapacity;
    }

    private void updateDisplay(FluidStack fluid, int amount, int capacity) {
        boolean changed = amount != displayAmount || capacity != displayCapacity
            || !FluidStack.isSameFluidSameComponents(displayFluid, fluid);
        if (!changed) {
            return;
        }
        displayFluid = fluid.copy();
        displayAmount = amount;
        displayCapacity = capacity;
        sync();
        requestModelDataUpdate();
    }

    private void sync() {
        setChanged();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    @Nonnull
    public ModelData getModelData() {
        if (displayFluid.isEmpty() || displayCapacity <= 0) {
            return ModelData.EMPTY;
        }
        // Create fluid stack with display amount for rendering
        FluidStack renderFluid = displayFluid.copyWithAmount(displayAmount);
        return ModelData.builder()
            .with(FoundryTankModelLoader.FLUID_STACK, renderFluid)
            .with(FoundryTankModelLoader.TANK_CAPACITY, displayCapacity)
            .with(FoundryModelProperties.FLUID_STACK, renderFluid)
            .with(FoundryModelProperties.TANK_CAPACITY, displayCapacity)
            .build();
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!displayFluid.isEmpty()) {
            CompoundTag fluidTag = new CompoundTag();
            displayFluid.save(registries, fluidTag);
            tag.put(TAG_DISPLAY_FLUID, fluidTag);
        }
        tag.putInt(TAG_DISPLAY_AMOUNT, displayAmount);
        tag.putInt(TAG_DISPLAY_CAPACITY, displayCapacity);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_DISPLAY_FLUID)) {
            displayFluid = FluidStack.parseOptional(registries, tag.getCompound(TAG_DISPLAY_FLUID));
        } else {
            displayFluid = FluidStack.EMPTY;
        }
        displayAmount = tag.getInt(TAG_DISPLAY_AMOUNT);
        displayCapacity = tag.getInt(TAG_DISPLAY_CAPACITY);
    }
}
