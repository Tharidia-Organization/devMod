package com.devmod.foundry.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * NBT helpers for foundry data structures.
 */
public final class FoundryNbtHelper {
    private FoundryNbtHelper() {}

    public static void putFluidStack(CompoundTag target, String key, HolderLookup.Provider registries, FluidStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        target.put(key, saveFluidStack(registries, stack));
    }

    public static CompoundTag saveFluidStack(HolderLookup.Provider registries, FluidStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        Tag encoded = stack.save(registries, new CompoundTag());
        if (encoded instanceof CompoundTag compound) {
            return compound;
        }
        return new CompoundTag();
    }

    public static FluidStack readFluidStack(CompoundTag source, String key, HolderLookup.Provider registries) {
        if (!source.contains(key)) {
            return FluidStack.EMPTY;
        }
        return FluidStack.parseOptional(registries, source.getCompound(key));
    }
}
