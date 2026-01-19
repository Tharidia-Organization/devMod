package com.devmod.foundry.risk;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.devmod.shared.SharedColorTokens;

/**
 * Risk levels for foundry operations.
 * Higher risk = higher reward but chance of incidents.
 */
public enum RiskLevel {
    SAFE(0, 1.0f, 0.0f, SharedColorTokens.Foundry.Risk.SAFE, "safe"),
    ELEVATED(1, 1.1f, 0.05f, SharedColorTokens.Foundry.Risk.ELEVATED, "elevated"),
    HIGH(2, 1.25f, 0.15f, SharedColorTokens.Foundry.Risk.HIGH, "high"),
    CRITICAL(3, 1.5f, 0.35f, SharedColorTokens.Foundry.Risk.CRITICAL, "critical");

    private final int level;
    private final float efficiencyMultiplier;
    private final float incidentChance;
    private final int color;
    private final String name;

    RiskLevel(int level, float efficiencyMultiplier, float incidentChance, int color, String name) {
        this.level = level;
        this.efficiencyMultiplier = efficiencyMultiplier;
        this.incidentChance = incidentChance;
        this.color = color;
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public float getEfficiencyMultiplier() {
        return efficiencyMultiplier;
    }

    public float getIncidentChance() {
        return incidentChance;
    }

    public int getColor() {
        return color;
    }

    public String getName() {
        return name;
    }

    public MutableComponent getDisplayName() {
        return Component.translatable("risk.devmod.foundry." + name);
    }

    public MutableComponent getColoredDisplayName() {
        return getDisplayName().withColor(color);
    }

    /**
     * Gets risk level from score (0-4+).
     */
    public static RiskLevel fromScore(int score) {
        if (score <= 0) return SAFE;
        if (score == 1) return ELEVATED;
        if (score == 2) return HIGH;
        return CRITICAL;
    }

    /**
     * Gets the next higher risk level.
     */
    public RiskLevel increase() {
        return switch (this) {
            case SAFE -> ELEVATED;
            case ELEVATED -> HIGH;
            case HIGH, CRITICAL -> CRITICAL;
        };
    }

    /**
     * Gets the next lower risk level.
     */
    public RiskLevel decrease() {
        return switch (this) {
            case CRITICAL -> HIGH;
            case HIGH -> ELEVATED;
            case ELEVATED, SAFE -> SAFE;
        };
    }
}
