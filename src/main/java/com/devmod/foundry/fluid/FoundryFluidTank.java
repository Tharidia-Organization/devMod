package com.devmod.foundry.fluid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.devmod.foundry.quality.FoundryFluidQuality;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.quality.MoltenMetal;

/**
 * Multi-fluid tank for foundry molten storage.
 * Implements IFluidHandler for compatibility with pipes and other fluid transport mods.
 */
public class FoundryFluidTank implements IFluidHandler {
    private static final String TAG_FLUIDS = "Fluids";
    private static final String TAG_CAPACITY = "Capacity";

    private int capacity;
    private final List<FluidStack> fluids = new ArrayList<>();

    // Animation fields for smooth client-side rendering
    private float renderFillRatio = 0f;
    private float targetFillRatio = 0f;
    private static final float INTERPOLATION_SPEED = 0.15f;

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

    public int getAmountForFluid(Fluid fluid) {
        int total = 0;
        for (FluidStack stack : fluids) {
            if (stack.getFluid() == fluid) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public int getTotalAmountForFluids(Collection<Fluid> targetFluids) {
        int total = 0;
        for (Fluid fluid : targetFluids) {
            total += getAmountForFluid(fluid);
        }
        return total;
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

    // ==================== IFluidHandler Implementation ====================

    @Override
    public int getTanks() {
        // Report at least 1 tank, or the number of different fluids stored
        return Math.max(1, fluids.size());
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= fluids.size()) {
            return FluidStack.EMPTY;
        }
        return fluids.get(tank).copy();
    }

    @Override
    public int getTankCapacity(int tank) {
        return capacity;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        // Accept any fluid - the foundry can handle multiple fluid types
        return !stack.isEmpty();
    }

    @Override
    public int fill(@Nonnull FluidStack resource, @Nonnull FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        int free = getFree();
        if (free <= 0) {
            return 0;
        }
        int toFill = Math.min(free, resource.getAmount());
        if (action.execute()) {
            FluidStack toAdd = resource.copy();
            toAdd.setAmount(toFill);
            addFluidInternal(toAdd);
            updateTargetFillRatio();
        }
        return toFill;
    }

    @Override
    @Nonnull
    public FluidStack drain(@Nonnull FluidStack resource, @Nonnull FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        for (int i = 0; i < fluids.size(); i++) {
            FluidStack stack = fluids.get(i);
            if (stack.getFluid() == resource.getFluid()) {
                int drained = Math.min(resource.getAmount(), stack.getAmount());
                FluidStack result = stack.copy();
                result.setAmount(drained);
                if (action.execute()) {
                    stack.shrink(drained);
                    if (stack.isEmpty()) {
                        fluids.remove(i);
                    }
                    updateTargetFillRatio();
                }
                return result;
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    @Nonnull
    public FluidStack drain(int maxDrain, @Nonnull FluidAction action) {
        if (fluids.isEmpty() || maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack stack = fluids.get(0);
        int drained = Math.min(maxDrain, stack.getAmount());
        FluidStack result = stack.copy();
        result.setAmount(drained);
        if (action.execute()) {
            stack.shrink(drained);
            if (stack.isEmpty()) {
                fluids.remove(0);
            }
            updateTargetFillRatio();
        }
        return result;
    }

    // ==================== Legacy Methods (for internal use) ====================

    /**
     * @deprecated Use {@link #fill(FluidStack, FluidAction)} instead
     */
    @Deprecated
    public int fill(FluidStack input, boolean simulate) {
        return fill(input, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
    }

    /**
     * @deprecated Use {@link #drain(int, FluidAction)} instead
     */
    @Deprecated
    public FluidStack drain(int amount, boolean simulate) {
        return drain(amount, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
    }

    /**
     * @deprecated Use {@link #drain(FluidStack, FluidAction)} instead
     */
    @Deprecated
    public FluidStack drain(FluidStack request, boolean simulate) {
        return drain(request, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
    }

    public boolean contains(FluidStack required) {
        if (required.isEmpty()) {
            return false;
        }
        for (FluidStack stack : fluids) {
            if (stack.getFluid() == required.getFluid() && stack.getAmount() >= required.getAmount()) {
                return true;
            }
        }
        return false;
    }

    private void addFluidInternal(FluidStack toAdd) {
        for (FluidStack stack : fluids) {
            if (stack.getFluid() == toAdd.getFluid()) {
                float mergedPurity = FoundryFluidQuality.mergePurity(stack, toAdd);
                MaterialQuality mergedQuality = FoundryFluidQuality.mergeQuality(stack, toAdd);
                int mergedOxidation = FoundryFluidQuality.mergeOxidationTicks(stack, toAdd);
                float mergedPeakTemp = FoundryFluidQuality.mergePeakTemperature(stack, toAdd);
                stack.grow(toAdd.getAmount());
                FoundryFluidQuality.applyMoltenState(stack, mergedQuality, mergedPurity, mergedOxidation, mergedPeakTemp);
                return;
            }
        }
        fluids.add(toAdd);
    }

    public MaterialQuality getLowestQualityForFluid(net.minecraft.world.level.material.Fluid fluid) {
        MaterialQuality lowest = null;
        for (FluidStack stack : fluids) {
            if (stack.getFluid() != fluid) {
                continue;
            }
            MaterialQuality quality = FoundryFluidQuality.getQuality(stack);
            if (lowest == null || quality.getTier() < lowest.getTier()) {
                lowest = quality;
            }
        }
        return lowest != null ? lowest : MaterialQuality.STANDARD;
    }

    public MaterialQuality getLowestQuality() {
        MaterialQuality lowest = null;
        for (FluidStack stack : fluids) {
            MaterialQuality quality = FoundryFluidQuality.getQuality(stack);
            if (lowest == null || quality.getTier() < lowest.getTier()) {
                lowest = quality;
            }
        }
        return lowest != null ? lowest : MaterialQuality.STANDARD;
    }

    public float getLowestPurityForFluid(net.minecraft.world.level.material.Fluid fluid) {
        float lowest = 1.0f;
        boolean found = false;
        for (FluidStack stack : fluids) {
            if (stack.getFluid() != fluid) {
                continue;
            }
            float purity = FoundryFluidQuality.getPurity(stack);
            if (!found || purity < lowest) {
                lowest = purity;
                found = true;
            }
        }
        return found ? lowest : 1.0f;
    }

    public float getLowestPurity() {
        float lowest = 1.0f;
        boolean found = false;
        for (FluidStack stack : fluids) {
            float purity = FoundryFluidQuality.getPurity(stack);
            if (!found || purity < lowest) {
                lowest = purity;
                found = true;
            }
        }
        return found ? lowest : 1.0f;
    }

    public int getHighestOxidationTicks() {
        int highest = 0;
        for (FluidStack stack : fluids) {
            int oxidation = FoundryFluidQuality.getOxidationTicks(stack);
            if (oxidation > highest) {
                highest = oxidation;
            }
        }
        return highest;
    }

    public float getHighestOxidationPercent() {
        if (fluids.isEmpty()) {
            return 0.0f;
        }
        int highest = getHighestOxidationTicks();
        return Math.min(1.0f, highest / (float) MoltenMetal.OXIDATION_THRESHOLD);
    }

    public void tick(float temperature, boolean sealed) {
        for (FluidStack stack : fluids) {
            MoltenMetal metal = FoundryFluidQuality.toMoltenMetal(stack);
            if (metal == null) {
                continue;
            }
            metal.tick(temperature, sealed);
            FoundryFluidQuality.applyMoltenMetal(stack, metal);
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        System.out.println("[FoundryFluidTank] save() called, fluids.size()=" + fluids.size() + ", totalUsed=" + getUsed());
        for (FluidStack stack : fluids) {
            // In NeoForge 1.21.1, FluidStack.save() returns a new tag, doesn't populate the provided one
            CompoundTag stackTag = (CompoundTag) stack.save(registries);
            System.out.println("[FoundryFluidTank] Saving fluid: " + stack.getFluid() + " x" + stack.getAmount() + " -> " + stackTag);
            list.add(stackTag);
        }
        tag.put(TAG_FLUIDS, list);
        tag.putInt(TAG_CAPACITY, capacity);
        System.out.println("[FoundryFluidTank] Saved tag list size: " + list.size());
        return tag;
    }

    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        fluids.clear();
        capacity = tag.getInt(TAG_CAPACITY);
        System.out.println("[FoundryFluidTank] load() called, capacity=" + capacity + ", hasFluids=" + tag.contains(TAG_FLUIDS));
        if (tag.contains(TAG_FLUIDS)) {
            ListTag list = tag.getList(TAG_FLUIDS, Tag.TAG_COMPOUND);
            System.out.println("[FoundryFluidTank] Fluid list size: " + list.size());
            for (Tag entry : list) {
                if (entry instanceof CompoundTag fluidTag) {
                    System.out.println("[FoundryFluidTank] Parsing fluid tag: " + fluidTag);
                    FluidStack stack = FluidStack.parseOptional(registries, fluidTag);
                    System.out.println("[FoundryFluidTank] Parsed stack: " + (stack.isEmpty() ? "EMPTY" : stack.getFluid() + " x" + stack.getAmount()));
                    if (!stack.isEmpty()) {
                        fluids.add(stack);
                    }
                }
            }
        }
        System.out.println("[FoundryFluidTank] After load: totalUsed=" + getUsed());
        // Update target fill ratio after loading
        updateTargetFillRatio();
    }

    // ==================== Animation Methods (Client-side) ====================

    /**
     * Updates the target fill ratio based on current fluid levels.
     * Call this when fluid contents change.
     */
    public void updateTargetFillRatio() {
        targetFillRatio = capacity > 0 ? (float) getUsed() / capacity : 0f;
    }

    /**
     * Tick the client-side animation interpolation.
     * Call this every client tick to smoothly animate fluid level changes.
     */
    public void tickClient() {
        if (Math.abs(renderFillRatio - targetFillRatio) < 0.001f) {
            renderFillRatio = targetFillRatio;
            return;
        }
        float delta = targetFillRatio - renderFillRatio;
        renderFillRatio += delta * INTERPOLATION_SPEED;
    }

    /**
     * Gets the interpolated fill ratio for rendering.
     * @param partialTick Partial tick for smooth animation
     * @return Fill ratio between 0.0 and 1.0, smoothly interpolated
     */
    public float getRenderFillRatio(float partialTick) {
        if (Math.abs(renderFillRatio - targetFillRatio) < 0.001f) {
            return targetFillRatio;
        }
        // Interpolate between current render position and target
        float delta = targetFillRatio - renderFillRatio;
        return renderFillRatio + delta * INTERPOLATION_SPEED * partialTick;
    }

    /**
     * Gets the actual (non-animated) fill ratio.
     * @return Fill ratio between 0.0 and 1.0
     */
    public float getFillRatio() {
        return capacity > 0 ? (float) getUsed() / capacity : 0f;
    }

    /**
     * Instantly sets the render fill ratio to the current level (no animation).
     * Useful when first loading or teleporting.
     */
    public void snapRenderToTarget() {
        updateTargetFillRatio();
        renderFillRatio = targetFillRatio;
    }
}
