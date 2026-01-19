package com.devmod.foundry.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.devmod.shared.SharedColorTokens;

/**
 * Tier levels for foundry patterns.
 * Higher tier patterns last longer and produce better quality parts.
 */
public enum PatternTier {
    BASIC("basic", 32, 0.8f, SharedColorTokens.Foundry.PatternTier.BASIC),
    STANDARD("standard", 64, 1.0f, SharedColorTokens.Foundry.PatternTier.STANDARD),
    QUALITY("quality", 128, 1.1f, SharedColorTokens.Foundry.PatternTier.QUALITY),
    MASTER("master", 96, 1.2f, SharedColorTokens.Foundry.PatternTier.MASTER),
    ANCIENT("ancient", 256, 1.3f, SharedColorTokens.Foundry.PatternTier.ANCIENT);

    private final String name;
    private final int baseDurability;
    private final float qualityBonus;
    private final int color;

    PatternTier(String name, int baseDurability, float qualityBonus, int color) {
        this.name = name;
        this.baseDurability = baseDurability;
        this.qualityBonus = qualityBonus;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public int getBaseDurability() {
        return baseDurability;
    }

    public float getQualityBonus() {
        return qualityBonus;
    }

    public int getColor() {
        return color;
    }

    public MutableComponent getDisplayName() {
        return Component.translatable("pattern.devmod.tier." + name);
    }

    public MutableComponent getColoredDisplayName() {
        return getDisplayName().withColor(color);
    }

    /**
     * Master patterns improve with use instead of degrading.
     */
    public boolean improveWithUse() {
        return this == MASTER;
    }

    /**
     * Ancient patterns don't wear on certain materials.
     */
    public boolean isImmuneToWear(String materialHardness) {
        return this == ANCIENT && !"hard".equals(materialHardness);
    }

    public static PatternTier fromName(String name) {
        for (PatternTier tier : values()) {
            if (tier.name.equals(name)) {
                return tier;
            }
        }
        return STANDARD;
    }

    public PatternTier upgrade() {
        return switch (this) {
            case BASIC -> STANDARD;
            case STANDARD -> QUALITY;
            case QUALITY -> MASTER;
            case MASTER, ANCIENT -> this;
        };
    }
}
