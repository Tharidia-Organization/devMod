package com.devmod.foundry.quality;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Utility for storing quality/purity metadata on molten FluidStacks.
 */
public final class FoundryFluidQuality {
    private static final String TAG_ROOT = "FoundryQuality";
    private static final String TAG_QUALITY = "Quality";
    private static final String TAG_PURITY = "Purity";
    private static final String TAG_OXIDATION = "Oxidation";
    private static final String TAG_PEAK_TEMP = "PeakTemp";

    private FoundryFluidQuality() {}

    public static void applyQuality(FluidStack stack, MaterialQuality quality, float purity) {
        applyMoltenState(stack, quality, purity, getOxidationTicks(stack), getPeakTemperature(stack));
    }

    public static void applyMoltenState(
        FluidStack stack,
        MaterialQuality quality,
        float purity,
        int oxidationTicks,
        float peakTemperature
    ) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = existing.copyTag();
        CompoundTag root = tag.contains(TAG_ROOT) ? tag.getCompound(TAG_ROOT) : new CompoundTag();
        root.putString(TAG_QUALITY, quality.getSerializedName());
        root.putFloat(TAG_PURITY, clampPurity(purity));
        root.putInt(TAG_OXIDATION, Math.max(0, oxidationTicks));
        root.putFloat(TAG_PEAK_TEMP, Math.max(0f, peakTemperature));
        tag.put(TAG_ROOT, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static MaterialQuality getQuality(FluidStack stack) {
        CompoundTag root = getRoot(stack);
        if (root == null || !root.contains(TAG_QUALITY)) {
            return MaterialQuality.STANDARD;
        }
        return MaterialQuality.fromName(root.getString(TAG_QUALITY));
    }

    public static float getPurity(FluidStack stack) {
        float basePurity = getStoredPurity(stack);
        int oxidationTicks = getOxidationTicks(stack);
        float penalty = Math.min(MoltenMetal.MAX_OXIDATION_PENALTY,
            (float) oxidationTicks / MoltenMetal.OXIDATION_THRESHOLD * MoltenMetal.MAX_OXIDATION_PENALTY);
        return clampPurity(basePurity - penalty);
    }

    public static float getStoredPurity(FluidStack stack) {
        CompoundTag root = getRoot(stack);
        if (root == null || !root.contains(TAG_PURITY)) {
            return 1.0f;
        }
        return clampPurity(root.getFloat(TAG_PURITY));
    }

    public static int getOxidationTicks(FluidStack stack) {
        CompoundTag root = getRoot(stack);
        if (root == null || !root.contains(TAG_OXIDATION)) {
            return 0;
        }
        return Math.max(0, root.getInt(TAG_OXIDATION));
    }

    public static float getPeakTemperature(FluidStack stack) {
        CompoundTag root = getRoot(stack);
        if (root == null || !root.contains(TAG_PEAK_TEMP)) {
            return 0f;
        }
        return Math.max(0f, root.getFloat(TAG_PEAK_TEMP));
    }

    public static MaterialQuality mergeQuality(FluidStack base, FluidStack incoming) {
        MaterialQuality a = getQuality(base);
        MaterialQuality b = getQuality(incoming);
        return a.getTier() <= b.getTier() ? a : b;
    }

    public static float mergePurity(FluidStack base, FluidStack incoming) {
        int total = base.getAmount() + incoming.getAmount();
        if (total <= 0) {
            return 1.0f;
        }
        float a = getStoredPurity(base);
        float b = getStoredPurity(incoming);
        return clampPurity(((a * base.getAmount()) + (b * incoming.getAmount())) / total);
    }

    public static int mergeOxidationTicks(FluidStack base, FluidStack incoming) {
        int total = base.getAmount() + incoming.getAmount();
        if (total <= 0) {
            return 0;
        }
        int a = getOxidationTicks(base);
        int b = getOxidationTicks(incoming);
        return Math.max(0, Math.round(((a * base.getAmount()) + (b * incoming.getAmount())) / (float) total));
    }

    public static float mergePeakTemperature(FluidStack base, FluidStack incoming) {
        return Math.max(getPeakTemperature(base), getPeakTemperature(incoming));
    }

    @Nullable
    public static MoltenMetal toMoltenMetal(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        MoltenMetal metal = new MoltenMetal(stack.copy(), getStoredPurity(stack));
        metal.setQuality(getQuality(stack));
        metal.setOxidationTicks(getOxidationTicks(stack));
        metal.setPeakTemperature(getPeakTemperature(stack));
        return metal;
    }

    public static void applyMoltenMetal(FluidStack stack, MoltenMetal metal) {
        if (stack.isEmpty() || metal == null) {
            return;
        }
        applyMoltenState(
            stack,
            metal.getQuality(),
            metal.getPurity(),
            metal.getOxidationTicks(),
            metal.getPeakTemperature()
        );
    }

    public static int getPurityTintColor(FluidStack stack, int baseColor) {
        return applyPurityTint(baseColor, getPurity(stack));
    }

    public static int applyPurityTint(int baseColor, float purity) {
        float clamped = clampPurity(purity);
        float factor = 0.35f + (0.65f * clamped);

        int alpha = (baseColor >> 24) & 0xFF;
        if (alpha == 0) {
            alpha = 0xFF;
        }
        int red = Math.min(0xFF, Math.round(((baseColor >> 16) & 0xFF) * factor));
        int green = Math.min(0xFF, Math.round(((baseColor >> 8) & 0xFF) * factor));
        int blue = Math.min(0xFF, Math.round((baseColor & 0xFF) * factor));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    @Nullable
    private static CompoundTag getRoot(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_ROOT)) {
            return null;
        }
        return tag.getCompound(TAG_ROOT);
    }

    private static float clampPurity(float purity) {
        return Math.max(0.1f, Math.min(1.0f, purity));
    }
}
