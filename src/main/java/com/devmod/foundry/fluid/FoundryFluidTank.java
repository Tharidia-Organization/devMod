package com.devmod.foundry.fluid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Simple multi-fluid tank for foundry molten storage.
 */
public class FoundryFluidTank {
    private static final String TAG_FLUIDS = "Fluids";
    private static final String TAG_CAPACITY = "Capacity";

    private int capacity;
    private final List<FluidStack> fluids = new ArrayList<>();

    public FoundryFluidTank(int capacity) {
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getUsed() {
        int total = 0;
        for (FluidStack stack : fluids) {
            total += stack.getAmount();
        }
        return total;
    }

    public int getFree() {
        return Math.max(0, capacity - getUsed());
    }

    public boolean isEmpty() {
        return fluids.isEmpty() || getUsed() == 0;
    }

    public List<FluidStack> getFluids() {
        List<FluidStack> copy = new ArrayList<>();
        for (FluidStack stack : fluids) {
            copy.add(stack.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    public int fill(FluidStack input, boolean simulate) {
        if (input.isEmpty()) {
            return 0;
        }
        int free = getFree();
        if (free <= 0) {
            return 0;
        }
        int toFill = Math.min(free, input.getAmount());
        if (!simulate) {
            addFluidInternal(new FluidStack(input.getFluid(), toFill));
        }
        return toFill;
    }

    public FluidStack drain(int amount, boolean simulate) {
        if (fluids.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack stack = fluids.get(0);
        int drained = Math.min(amount, stack.getAmount());
        FluidStack result = new FluidStack(stack.getFluid(), drained);
        if (!simulate) {
            stack.shrink(drained);
            if (stack.isEmpty()) {
                fluids.remove(0);
            }
        }
        return result;
    }

    public FluidStack drain(FluidStack request, boolean simulate) {
        if (request.isEmpty()) {
            return FluidStack.EMPTY;
        }
        for (int i = 0; i < fluids.size(); i++) {
            FluidStack stack = fluids.get(i);
            if (FluidStack.isSameFluidSameComponents(stack, request)) {
                int drained = Math.min(request.getAmount(), stack.getAmount());
                FluidStack result = new FluidStack(stack.getFluid(), drained);
                if (!simulate) {
                    stack.shrink(drained);
                    if (stack.isEmpty()) {
                        fluids.remove(i);
                    }
                }
                return result;
            }
        }
        return FluidStack.EMPTY;
    }

    public boolean contains(FluidStack required) {
        if (required.isEmpty()) {
            return false;
        }
        for (FluidStack stack : fluids) {
            if (FluidStack.isSameFluidSameComponents(stack, required) && stack.getAmount() >= required.getAmount()) {
                return true;
            }
        }
        return false;
    }

    private void addFluidInternal(FluidStack toAdd) {
        for (FluidStack stack : fluids) {
            if (FluidStack.isSameFluidSameComponents(stack, toAdd)) {
                stack.grow(toAdd.getAmount());
                return;
            }
        }
        fluids.add(toAdd);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (FluidStack stack : fluids) {
            CompoundTag stackTag = new CompoundTag();
            stack.save(registries, stackTag);
            list.add(stackTag);
        }
        tag.put(TAG_FLUIDS, list);
        tag.putInt(TAG_CAPACITY, capacity);
        return tag;
    }

    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        fluids.clear();
        capacity = tag.getInt(TAG_CAPACITY);
        if (tag.contains(TAG_FLUIDS)) {
            ListTag list = tag.getList(TAG_FLUIDS, Tag.TAG_COMPOUND);
            for (Tag entry : list) {
                if (entry instanceof CompoundTag fluidTag) {
                    FluidStack stack = FluidStack.parseOptional(registries, fluidTag);
                    if (!stack.isEmpty()) {
                        fluids.add(stack);
                    }
                }
            }
        }
    }
}
