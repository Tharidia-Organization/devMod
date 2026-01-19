package com.devmod.foundry.progression;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Foundry progression tiers that unlock features and materials.
 */
public enum FoundryTier {
    PRIMITIVE(0, "primitive", 1200, 3, 3),
    BASIC(1, "basic", 1200, 3, 3),
    IRON_AGE(2, "iron_age", 1600, 5, 5),
    ADVANCED(3, "advanced", 2000, 7, 7),
    NETHER(4, "nether", 3000, 9, 9),
    COSMIC(5, "cosmic", 4000, 11, 11);

    private final int level;
    private final String name;
    private final int maxTemperature;
    private final int maxSmelterySize;
    private final int maxSmelteryHeight;

    FoundryTier(int level, String name, int maxTemperature, int maxSmelterySize, int maxSmelteryHeight) {
        this.level = level;
        this.name = name;
        this.maxTemperature = maxTemperature;
        this.maxSmelterySize = maxSmelterySize;
        this.maxSmelteryHeight = maxSmelteryHeight;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }

    public int getMaxSmelterySize() {
        return maxSmelterySize;
    }

    public int getMaxSmelteryHeight() {
        return maxSmelteryHeight;
    }

    public MutableComponent getDisplayName() {
        return Component.translatable("tier.devmod.foundry." + name);
    }

    /**
     * Gets the next tier, or this if already max.
     */
    public FoundryTier next() {
        int nextLevel = Math.min(level + 1, COSMIC.level);
        return fromLevel(nextLevel);
    }

    /**
     * Checks if this tier allows a given temperature.
     */
    public boolean allowsTemperature(int temp) {
        return temp <= maxTemperature;
    }

    /**
     * Checks if this tier allows a given smeltery size.
     */
    public boolean allowsSmelterySize(int size) {
        return size <= maxSmelterySize;
    }

    /**
     * Checks if this tier allows a given smeltery height.
     */
    public boolean allowsSmelteryHeight(int height) {
        return height <= maxSmelteryHeight;
    }

    /**
     * Gets tier from level.
     */
    public static FoundryTier fromLevel(int level) {
        for (FoundryTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return PRIMITIVE;
    }

    /**
     * Gets tier from name.
     */
    public static FoundryTier fromName(String name) {
        for (FoundryTier tier : values()) {
            if (tier.name.equals(name)) {
                return tier;
            }
        }
        return PRIMITIVE;
    }
}
