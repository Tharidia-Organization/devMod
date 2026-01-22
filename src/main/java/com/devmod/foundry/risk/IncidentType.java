package com.devmod.foundry.risk;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Types of incidents that can occur in the foundry.
 */
public enum IncidentType {
    /**
     * Molten metal splashes out, causing damage and losing some fluid.
     */
    SPLATTER("splatter", 1, true, false, false),

    /**
     * Structure brick cracks from thermal stress.
     */
    THERMAL_CRACK("thermal_crack", 1, false, true, false),

    /**
     * Rapid oxidation burst reduces purity.
     */
    OXIDATION_BURST("oxidation_burst", 1, false, false, true),

    /**
     * Fuel flare causes area damage.
     */
    FUEL_FLARE("fuel_flare", 2, true, false, false),

    /**
     * Structural failure - multiblock partially breaks.
     */
    STRUCTURE_FAIL("structure_fail", 3, false, true, false),

    /**
     * Containment breach - fluid leaks out.
     */
    CONTAINMENT_BREACH("containment_breach", 3, true, true, true);

    private final String name;
    private final int severity; // 1 = minor, 2 = moderate, 3 = major
    private final boolean causesDamage;
    private final boolean damagesStructure;
    private final boolean affectsPurity;

    IncidentType(String name, int severity, boolean causesDamage, boolean damagesStructure, boolean affectsPurity) {
        this.name = name;
        this.severity = severity;
        this.causesDamage = causesDamage;
        this.damagesStructure = damagesStructure;
        this.affectsPurity = affectsPurity;
    }

    public String getName() {
        return name;
    }

    public int getSeverity() {
        return severity;
    }

    public boolean causesDamage() {
        return causesDamage;
    }

    public boolean damagesStructure() {
        return damagesStructure;
    }

    public boolean affectsPurity() {
        return affectsPurity;
    }

    public MutableComponent getDisplayName() {
        return Component.translatable("incident.devmod.foundry." + name);
    }

    public MutableComponent getDescription() {
        return Component.translatable("incident.devmod.foundry." + name + ".desc");
    }

    /**
     * Gets a weighted random incident based on risk level.
     */
    @Nullable
    public static IncidentType getRandomForRisk(RiskLevel risk, java.util.Random random) {
        return switch (risk) {
            case SAFE -> null; // No incidents at safe level
            case ELEVATED -> {
                // Only minor incidents
                float roll = random.nextFloat();
                if (roll < 0.4f) yield SPLATTER;
                if (roll < 0.7f) yield THERMAL_CRACK;
                yield OXIDATION_BURST;
            }
            case HIGH -> {
                // Minor and moderate
                float roll = random.nextFloat();
                if (roll < 0.25f) yield SPLATTER;
                if (roll < 0.45f) yield THERMAL_CRACK;
                if (roll < 0.65f) yield OXIDATION_BURST;
                if (roll < 0.85f) yield FUEL_FLARE;
                yield THERMAL_CRACK;
            }
            case CRITICAL -> {
                // All incidents possible
                float roll = random.nextFloat();
                if (roll < 0.15f) yield SPLATTER;
                if (roll < 0.25f) yield THERMAL_CRACK;
                if (roll < 0.35f) yield OXIDATION_BURST;
                if (roll < 0.50f) yield FUEL_FLARE;
                if (roll < 0.75f) yield STRUCTURE_FAIL;
                yield CONTAINMENT_BREACH;
            }
        };
    }
}
