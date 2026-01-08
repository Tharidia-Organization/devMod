package com.devmod.portal;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Rune types that can be placed on portal frames as blocks.
 * Each rune provides a unique enhancement to portal behavior.
 *
 * <p>Runes are placeable blocks that must be adjacent to a portal frame block.
 * Their effects apply to all portals using that frame.
 */
public enum RuneType implements StringRepresentable {
    /**
     * Haste Rune: Allows instant teleportation.
     * Normal portals have a delay; with Haste it's instant.
     */
    HASTE("haste", 0x55FFFF, 0, true, false),

    /**
     * Gate Rune: Enables cross-dimension portal linking.
     * Without this rune, portals can only link within the same dimension.
     */
    GATE("gate", 0xAA00AA, 0, false, true),

    /**
     * Enhancer Rune: Increases portal linking range by 100 blocks.
     * Default range is 100 blocks; with Enhancer it's 200.
     */
    ENHANCER("enhancer", 0xFFAA00, 100, false, false),

    /**
     * Strong Enhancer Rune: Increases portal linking range by 500 blocks.
     * Crafted from Enhancer Runes; provides greater range boost.
     */
    STRONG_ENHANCER("strong_enhancer", 0xFF5500, 500, false, false),

    /**
     * Infinity Rune: Provides unlimited portal linking range.
     * Portals can link across any distance within the dimension.
     */
    INFINITY("infinity", 0x00FF00, Integer.MAX_VALUE, false, false);

    public static final Codec<RuneType> CODEC = StringRepresentable.fromEnum(RuneType::values);

    private final String id;
    private final int glowColor;
    private final int rangeBonus;
    private final boolean instantTeleport;
    private final boolean crossDimension;

    RuneType(String id, int glowColor, int rangeBonus, boolean instantTeleport, boolean crossDimension) {
        this.id = id;
        this.glowColor = glowColor;
        this.rangeBonus = rangeBonus;
        this.instantTeleport = instantTeleport;
        this.crossDimension = crossDimension;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return id;
    }

    /**
     * Returns the glow color for rendering the rune block.
     */
    public int getGlowColor() {
        return glowColor;
    }

    /**
     * Returns the range bonus this rune provides for portal linking.
     * Returns 0 for runes that don't affect range.
     * Returns Integer.MAX_VALUE for unlimited range.
     */
    public int getRangeBonus() {
        return rangeBonus;
    }

    /**
     * Returns true if this rune allows instant teleportation (no delay).
     */
    public boolean allowsInstantTeleport() {
        return instantTeleport;
    }

    /**
     * Returns true if this rune allows cross-dimension portal linking.
     */
    public boolean allowsCrossDimension() {
        return crossDimension;
    }

    /**
     * Returns the rune type by index (for network serialization).
     * Returns null for invalid indices.
     */
    @Nullable
    public static RuneType byIndex(int index) {
        RuneType[] values = values();
        if (index < 0 || index >= values.length) {
            return null;
        }
        return values[index];
    }

    /**
     * Returns the index of this rune (for network serialization).
     */
    public int getIndex() {
        return ordinal();
    }
}
