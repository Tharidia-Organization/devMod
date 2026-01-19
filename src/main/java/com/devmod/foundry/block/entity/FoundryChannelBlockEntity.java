package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.FoundryBlocks;
import com.devmod.foundry.quality.FoundryFluidQuality;
import com.devmod.foundry.quality.MaterialQuality;

/**
 * Channel block entity that buffers and routes molten fluids.
 */
public class FoundryChannelBlockEntity extends FoundryComponentBlockEntity {
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_COOLDOWN = "Cooldown";
    private static final String TAG_FILTER = "Filter";
    private static final String TAG_VALVES = "Valves";

    private static final int MAX_BUFFER = 250;
    private static final int TRANSFER_AMOUNT = 50;
    private static final int TRANSFER_COOLDOWN_TICKS = 2;
    private static final int VALVE_ALL_OPEN = 0x3F;

    private FluidStack buffer = FluidStack.EMPTY;
    private int cooldown = 0;
    @Nullable
    private ResourceLocation filterFluidId = null;
    private int valveMask = VALVE_ALL_OPEN;

    public FoundryChannelBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_CHANNEL.get()), pos, state);
    }

    public void tickServer() {
        Level level = Objects.requireNonNull(getLevel());
        if (level.isClientSide) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (isDuct()) {
            int pulled = pullFromController(level);
            if (pulled > 0) {
                cooldown = TRANSFER_COOLDOWN_TICKS;
                return;
            }
        }

        if (buffer.isEmpty()) {
            return;
        }

        int moved = 0;
        moved += transferDown(level);
        moved += transferHorizontals(level);

        if (moved > 0) {
            cooldown = TRANSFER_COOLDOWN_TICKS;
        }
    }

    public int tryFillFromFaucet(FluidStack incoming) {
        return tryFillInternal(incoming, Direction.UP);
    }

    public int tryFillFromChannel(FluidStack incoming, Direction from) {
        return tryFillInternal(incoming, from);
    }

    private int tryFillFromController(FluidStack incoming) {
        return tryFillInternal(incoming, null);
    }

    private int tryFillInternal(FluidStack incoming, @Nullable Direction from) {
        if (incoming.isEmpty()) {
            return 0;
        }
        if (from != null && !isValveOpen(from)) {
            return 0;
        }
        if (!matchesFilter(incoming)) {
            return 0;
        }
        if (buffer.isEmpty()) {
            buffer = incoming.copy();
            buffer.setAmount(0);
        }
        if (buffer.getFluid() != incoming.getFluid()) {
            return 0;
        }
        int free = Math.max(0, MAX_BUFFER - buffer.getAmount());
        if (free <= 0) {
            return 0;
        }
        int accepted = Math.min(free, incoming.getAmount());
        FluidStack portion = incoming.copy();
        portion.setAmount(accepted);
        float mergedPurity = FoundryFluidQuality.mergePurity(buffer, portion);
        MaterialQuality mergedQuality = FoundryFluidQuality.mergeQuality(buffer, portion);
        int mergedOxidation = FoundryFluidQuality.mergeOxidationTicks(buffer, portion);
        float mergedPeakTemp = FoundryFluidQuality.mergePeakTemperature(buffer, portion);
        buffer.grow(accepted);
        FoundryFluidQuality.applyMoltenState(buffer, mergedQuality, mergedPurity, mergedOxidation, mergedPeakTemp);
        sync();
        return accepted;
    }

    private int pullFromController(Level level) {
        FoundryControllerBlockEntity controller = getController(level);
        if (controller == null || !controller.isFormed()) {
            return 0;
        }
        int free = Math.max(0, MAX_BUFFER - getBufferAmount());
        if (free <= 0) {
            return 0;
        }
        FluidStack available = selectControllerFluid(controller);
        if (available.isEmpty()) {
            return 0;
        }
        if (!buffer.isEmpty() && buffer.getFluid() != available.getFluid()) {
            return 0;
        }
        int toDrain = Math.min(TRANSFER_AMOUNT, Math.min(free, available.getAmount()));
        if (toDrain <= 0) {
            return 0;
        }
        FluidStack request = available.copy();
        request.setAmount(toDrain);
        if (!matchesFilter(request)) {
            return 0;
        }
        FluidStack drained = controller.drainMolten(request, true);
        if (drained.isEmpty()) {
            return 0;
        }
        int accepted = tryFillFromController(drained);
        if (accepted < drained.getAmount()) {
            FluidStack remainder = drained.copy();
            remainder.setAmount(drained.getAmount() - accepted);
            controller.fillMolten(remainder, true);
        }
        return accepted;
    }

    private FluidStack selectControllerFluid(FoundryControllerBlockEntity controller) {
        if (filterFluidId != null) {
            Fluid fluid = BuiltInRegistries.FLUID.get(filterFluidId);
            if (fluid != null && controller.getMoltenAmountForFluid(fluid) > 0) {
                FluidStack stack = new FluidStack(fluid, controller.getMoltenAmountForFluid(fluid));
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            return FluidStack.EMPTY;
        }
        return controller.getPrimaryMoltenFluid();
    }

    private boolean isDuct() {
        return getBlockState().is(Objects.requireNonNull(FoundryBlocks.FOUNDRY_DUCT.get()));
    }

    private int transferDown(Level level) {
        if (buffer.isEmpty()) {
            return 0;
        }
        if (!isValveOpen(Direction.DOWN)) {
            return 0;
        }
        BlockPos downPos = Objects.requireNonNull(worldPosition.below());
        BlockEntity targetBe = level.getBlockEntity(downPos);
        if (targetBe instanceof FoundryCastingBlockEntity casting) {
            FluidStack attempt = buffer.copy();
            attempt.setAmount(Math.min(TRANSFER_AMOUNT, buffer.getAmount()));
            int accepted = casting.tryFillFromFaucet(attempt);
            if (accepted > 0) {
                buffer.shrink(accepted);
                clearIfEmpty();
                sync();
            }
            return accepted;
        }
        if (targetBe instanceof FoundryChannelBlockEntity channel) {
            return transferToChannel(channel, Direction.DOWN);
        }
        return 0;
    }

    private int transferHorizontals(Level level) {
        if (buffer.isEmpty()) {
            return 0;
        }
        int moved = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!isValveOpen(dir)) {
                continue;
            }
            BlockEntity targetBe = level.getBlockEntity(Objects.requireNonNull(worldPosition.relative(Objects.requireNonNull(dir))));
            if (targetBe instanceof FoundryChannelBlockEntity channel) {
                moved += transferToChannel(channel, dir);
                if (buffer.isEmpty()) {
                    break;
                }
            }
        }
        return moved;
    }

    private int transferToChannel(FoundryChannelBlockEntity channel, Direction direction) {
        if (buffer.isEmpty()) {
            return 0;
        }
        int otherAmount = channel.bufferAmount();
        if (otherAmount >= buffer.getAmount()) {
            return 0;
        }
        FluidStack attempt = buffer.copy();
        attempt.setAmount(Math.min(TRANSFER_AMOUNT, buffer.getAmount()));
        int accepted = channel.tryFillFromChannel(attempt, direction.getOpposite());
        if (accepted > 0) {
            buffer.shrink(accepted);
            clearIfEmpty();
            sync();
        }
        return accepted;
    }

    private int bufferAmount() {
        return buffer.isEmpty() ? 0 : buffer.getAmount();
    }

    private void clearIfEmpty() {
        if (buffer.isEmpty() || buffer.getAmount() <= 0) {
            buffer = FluidStack.EMPTY;
        }
    }

    public int getMaxBuffer() {
        return MAX_BUFFER;
    }

    public int getBufferAmount() {
        return buffer.isEmpty() ? 0 : buffer.getAmount();
    }

    public FluidStack getBuffer() {
        return buffer.copy();
    }

    public boolean setFilter(@Nonnull Fluid fluid) {
        ResourceLocation id = Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(Objects.requireNonNull(fluid)));
        if (Objects.equals(filterFluidId, id)) {
            return false;
        }
        filterFluidId = id;
        sync();
        return true;
    }

    public boolean clearFilter() {
        if (filterFluidId == null) {
            return false;
        }
        filterFluidId = null;
        sync();
        return true;
    }

    public boolean toggleValve(Direction direction) {
        int bit = 1 << direction.get3DDataValue();
        if ((valveMask & bit) != 0) {
            valveMask &= ~bit;
            sync();
            return false;
        }
        valveMask |= bit;
        sync();
        return true;
    }

    public boolean isValveOpen(Direction direction) {
        int bit = 1 << direction.get3DDataValue();
        return (valveMask & bit) != 0;
    }

    private boolean matchesFilter(FluidStack incoming) {
        ResourceLocation filter = filterFluidId;
        if (filter == null) {
            return true;
        }
        ResourceLocation id = Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(Objects.requireNonNull(incoming.getFluid())));
        return filter.equals(id);
    }

    private void sync() {
        setChanged();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            BlockPos pos = Objects.requireNonNull(worldPosition);
            BlockState state = Objects.requireNonNull(getBlockState());
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!buffer.isEmpty()) {
            CompoundTag fluidTag = new CompoundTag();
            buffer.save(registries, fluidTag);
            tag.put(TAG_FLUID, fluidTag);
        }
        tag.putInt(TAG_COOLDOWN, cooldown);
        tag.putInt(TAG_VALVES, valveMask);
        ResourceLocation filter = filterFluidId;
        if (filter != null) {
            tag.putString(TAG_FILTER, Objects.requireNonNull(filter.toString()));
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_FLUID)) {
            buffer = FluidStack.parseOptional(registries, Objects.requireNonNull(tag.getCompound(TAG_FLUID)));
        } else {
            buffer = FluidStack.EMPTY;
        }
        cooldown = tag.getInt(TAG_COOLDOWN);
        valveMask = tag.contains(TAG_VALVES) ? tag.getInt(TAG_VALVES) : VALVE_ALL_OPEN;
        if (tag.contains(TAG_FILTER)) {
            filterFluidId = ResourceLocation.tryParse(Objects.requireNonNull(tag.getString(TAG_FILTER)));
        } else {
            filterFluidId = null;
        }
    }
}
