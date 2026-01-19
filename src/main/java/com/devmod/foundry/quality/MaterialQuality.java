package com.devmod.foundry.quality;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

import com.devmod.shared.SharedColorTokens;

/**
 * Represents the quality tier of a material or tool.
 * Quality affects final stats and is determined by crafting conditions.
 */
public enum MaterialQuality implements StringRepresentable {
    CRUDE("crude", 0.7f, SharedColorTokens.Foundry.Quality.CRUDE, 0),
    STANDARD("standard", 1.0f, SharedColorTokens.Foundry.Quality.STANDARD, 1),
    REFINED("refined", 1.15f, SharedColorTokens.Foundry.Quality.REFINED, 2),
    PRISTINE("pristine", 1.3f, SharedColorTokens.Foundry.Quality.PRISTINE, 3),
    MASTERWORK("masterwork", 1.5f, SharedColorTokens.Foundry.Quality.MASTERWORK, 4);

    public static final Codec<MaterialQuality> CODEC = StringRepresentable.fromEnum(MaterialQuality::values);

    private final String name;
    private final float statMultiplier;
    private final int color;
    private final int tier;

    MaterialQuality(String name, float statMultiplier, int color, int tier) {
        this.name = name;
        this.statMultiplier = statMultiplier;
        this.color = color;
        this.tier = tier;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return name;
    }

    public float getStatMultiplier() {
        return statMultiplier;
    }

    public int getColor() {
        return color;
    }

    public int getTier() {
        return tier;
    }

    public MutableComponent getDisplayName() {
        return Component.translatable("quality.devmod.foundry." + name);
    }

    public MutableComponent getColoredDisplayName() {
        return getDisplayName().withColor(color);
    }

    /**
     * Gets quality from a numeric tier value.
     */
    public static MaterialQuality fromTier(int tier) {
        for (MaterialQuality quality : values()) {
            if (quality.tier == tier) {
                return quality;
            }
        }
        return STANDARD;
    }

    /**
     * Gets quality from serialized name.
     */
    public static MaterialQuality fromName(String name) {
        for (MaterialQuality quality : values()) {
            if (quality.name.equals(name)) {
                return quality;
            }
        }
        return STANDARD;
    }

    /**
     * Checks if this quality is at least as good as another.
     */
    public boolean isAtLeast(MaterialQuality other) {
        return this.tier >= other.tier;
    }

    /**
     * Gets the next quality tier, or this if already max.
     */
    public MaterialQuality upgrade() {
        int nextTier = Math.min(tier + 1, MASTERWORK.tier);
        return fromTier(nextTier);
    }

    /**
     * Gets the previous quality tier, or this if already min.
     */
    public MaterialQuality downgrade() {
        int prevTier = Math.max(tier - 1, CRUDE.tier);
        return fromTier(prevTier);
    }
}
