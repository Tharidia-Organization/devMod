package com.devmod.foundry.quality;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Represents molten metal with quality properties.
 * Tracks purity, oxidation, and temperature history.
 */
public class MoltenMetal {
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_PURITY = "Purity";
    private static final String TAG_OXIDATION = "Oxidation";
    private static final String TAG_PEAK_TEMP = "PeakTemp";
    private static final String TAG_QUALITY = "Quality";

    private FluidStack fluidStack;
    private float purity;
    private int oxidationTicks;
    private float peakTemperature;
    private MaterialQuality quality;

    // Oxidation constants
    public static final int OXIDATION_THRESHOLD = 6000; // 5 minutes at 20 ticks/sec
    public static final float MAX_OXIDATION_PENALTY = 0.3f;

    // Batching for performance - only process every N ticks
    private static final int TICK_BATCH_INTERVAL = 20; // Process every second (20 ticks)
    private int tickCounter = 0;

    public MoltenMetal(FluidStack fluidStack, float basePurity) {
        this.fluidStack = fluidStack.copy();
        this.purity = Math.max(0.1f, Math.min(1.0f, basePurity));
        this.oxidationTicks = 0;
        this.peakTemperature = 0;
        this.quality = MaterialQuality.STANDARD;
    }

    /**
     * Called every tick while in tank.
     * Uses batching for performance - oxidation updates are batched every TICK_BATCH_INTERVAL ticks.
     */
    public void tick(float currentTemp, boolean isSealed) {
        // Track peak temperature (always updated - cheap operation)
        if (currentTemp > peakTemperature) {
            peakTemperature = currentTemp;
        }

        // Increment tick counter
        tickCounter++;

        // Only process oxidation changes on batch intervals for performance
        if (tickCounter < TICK_BATCH_INTERVAL) {
            return;
        }

        // Reset counter and apply batched changes
        tickCounter = 0;

        // Oxidation increases while hot and exposed (batched - add interval amount)
        if (!isSealed && currentTemp > 500) {
            oxidationTicks += TICK_BATCH_INTERVAL;
        } else if (isSealed) {
            // Sealed tanks prevent oxidation
        } else if (currentTemp < 200) {
            // Very slow oxidation recovery when cool (roughly 10% chance per batch)
            if (oxidationTicks > 0) {
                // Average of ~2 ticks recovery per batch (20 ticks * 0.1 chance)
                oxidationTicks = Math.max(0, oxidationTicks - 2);
            }
        }
    }

    /**
     * Gets effective purity accounting for oxidation.
     */
    public float getEffectivePurity() {
        float oxidationPenalty = Math.min(MAX_OXIDATION_PENALTY,
                (float) oxidationTicks / OXIDATION_THRESHOLD * MAX_OXIDATION_PENALTY);
        return Math.max(0.1f, purity - oxidationPenalty);
    }

    /**
     * Applies flux to improve purity.
     *
     * @param fluxPower The strength of the flux (0.0 - 0.3 typical)
     */
    public void applyFlux(float fluxPower) {
        purity = Math.min(1.0f, purity + fluxPower);
        oxidationTicks = Math.max(0, oxidationTicks - 1200); // Reset some oxidation
    }

    /**
     * Applies impurity from ore quality or processing.
     *
     * @param impurityAmount Amount of impurity to add (0.0 - 0.5 typical)
     */
    public void addImpurity(float impurityAmount) {
        purity = Math.max(0.1f, purity - impurityAmount);
    }

    /**
     * Calculates and caches quality based on current state.
     */
    public void calculateQuality(float optimalTempLow, float optimalTempHigh, boolean hadIncidents) {
        float tempAccuracy = QualityCalculator.calculateTempAccuracy(
                peakTemperature, optimalTempLow, optimalTempHigh);
        this.quality = QualityCalculator.calculateMeltingQuality(
                getEffectivePurity(), tempAccuracy, hadIncidents);
    }

    /**
     * Merges another molten metal into this one.
     * Used when combining fluids of same type.
     */
    public void merge(MoltenMetal other) {
        if (!FluidStack.isSameFluidSameComponents(this.fluidStack, other.fluidStack)) {
            return;
        }

        int totalAmount = this.fluidStack.getAmount() + other.fluidStack.getAmount();
        float thisRatio = (float) this.fluidStack.getAmount() / totalAmount;
        float otherRatio = (float) other.fluidStack.getAmount() / totalAmount;

        // Weighted average of properties
        this.purity = (this.purity * thisRatio) + (other.purity * otherRatio);
        this.oxidationTicks = (int) ((this.oxidationTicks * thisRatio) + (other.oxidationTicks * otherRatio));
        this.peakTemperature = Math.max(this.peakTemperature, other.peakTemperature);

        // Update amount
        this.fluidStack.setAmount(totalAmount);

        // Recalculate quality (will need temp params from recipe)
        // For now, take worse quality
        if (other.quality.getTier() < this.quality.getTier()) {
            this.quality = other.quality;
        }
    }

    /**
     * Creates a copy of this molten metal with a specific amount.
     */
    public MoltenMetal copyWithAmount(int amount) {
        int clamped = Math.max(0, Math.min(amount, fluidStack.getAmount()));
        MoltenMetal copy = new MoltenMetal(new FluidStack(fluidStack.getFluid(), clamped), purity);
        copy.oxidationTicks = oxidationTicks;
        copy.peakTemperature = peakTemperature;
        copy.quality = quality;
        return copy;
    }

    /**
     * Splits off a portion of this molten metal.
     *
     * @param amount Amount to split off
     * @return New MoltenMetal with the split portion, or null if invalid
     */
    public MoltenMetal split(int amount) {
        if (amount <= 0 || amount > fluidStack.getAmount()) {
            return null;
        }

        MoltenMetal split = new MoltenMetal(
                new FluidStack(fluidStack.getFluid(), amount),
                this.purity
        );
        split.oxidationTicks = this.oxidationTicks;
        split.peakTemperature = this.peakTemperature;
        split.quality = this.quality;

        this.fluidStack.shrink(amount);
        return split;
    }

    // Getters

    public FluidStack getFluidStack() {
        return fluidStack;
    }

    public int getAmount() {
        return fluidStack.getAmount();
    }

    public float getPurity() {
        return purity;
    }

    public int getOxidationTicks() {
        return oxidationTicks;
    }

    public float getPeakTemperature() {
        return peakTemperature;
    }

    public MaterialQuality getQuality() {
        return quality;
    }

    public void setQuality(MaterialQuality quality) {
        if (quality != null) {
            this.quality = quality;
        }
    }

    public void setPurity(float purity) {
        this.purity = Math.max(0.1f, Math.min(1.0f, purity));
    }

    public void setOxidationTicks(int oxidationTicks) {
        this.oxidationTicks = Math.max(0, oxidationTicks);
    }

    public void setPeakTemperature(float peakTemperature) {
        this.peakTemperature = Math.max(0f, peakTemperature);
    }

    public boolean isEmpty() {
        return fluidStack.isEmpty();
    }

    // Serialization

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag fluidTag = new CompoundTag();
        fluidStack.save(registries, fluidTag);
        tag.put(TAG_FLUID, fluidTag);
        tag.putFloat(TAG_PURITY, purity);
        tag.putInt(TAG_OXIDATION, oxidationTicks);
        tag.putFloat(TAG_PEAK_TEMP, peakTemperature);
        tag.putString(TAG_QUALITY, quality.getSerializedName());
        return tag;
    }

    public static MoltenMetal load(HolderLookup.Provider registries, CompoundTag tag) {
        FluidStack fluid = FluidStack.parseOptional(registries, tag.getCompound(TAG_FLUID));
        if (fluid.isEmpty()) {
            return null;
        }

        MoltenMetal metal = new MoltenMetal(fluid, tag.getFloat(TAG_PURITY));
        metal.oxidationTicks = tag.getInt(TAG_OXIDATION);
        metal.peakTemperature = tag.getFloat(TAG_PEAK_TEMP);
        metal.quality = MaterialQuality.fromName(tag.getString(TAG_QUALITY));
        return metal;
    }
}
