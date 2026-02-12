package com.devmod.transport;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.google.common.base.Splitter;
import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.BossEvent;

import com.devmod.portal.PortalColor;

/**
 * Transport color variants following Minecraft dye colors.
 * This enum wraps and extends {@link PortalColor} for the unified transport system.
 *
 * <p>Each color variant maintains compatibility with the legacy portal system
 * while providing additional utilities for the new transport framework.
 *
 * <p>Colors are used for:
 * <ul>
 *   <li>Visual appearance of portals and Warp Cores</li>
 *   <li>Network separation (colored networks)</li>
 *   <li>Linking restrictions (same-color linking)</li>
 *   <li>Particle colors during effects</li>
 * </ul>
 */
public enum TransportColor implements StringRepresentable {
    WHITE("white", 0xF9FFFE, PortalColor.WHITE),
    ORANGE("orange", 0xF9801D, PortalColor.ORANGE),
    MAGENTA("magenta", 0xC74EBD, PortalColor.MAGENTA),
    LIGHT_BLUE("light_blue", 0x3AB3DA, PortalColor.LIGHT_BLUE),
    YELLOW("yellow", 0xFED83D, PortalColor.YELLOW),
    LIME("lime", 0x80C71F, PortalColor.LIME),
    PINK("pink", 0xF38BAA, PortalColor.PINK),
    GRAY("gray", 0x474F52, PortalColor.GRAY),
    LIGHT_GRAY("light_gray", 0x9D9D97, PortalColor.LIGHT_GRAY),
    CYAN("cyan", 0x169C9C, PortalColor.CYAN),
    PURPLE("purple", 0x8932B8, PortalColor.PURPLE),
    BLUE("blue", 0x3C44AA, PortalColor.BLUE),
    BROWN("brown", 0x835432, PortalColor.BROWN),
    GREEN("green", 0x5E7C16, PortalColor.GREEN),
    RED("red", 0xB02E26, PortalColor.RED),
    BLACK("black", 0x1D1D21, PortalColor.BLACK);

    public static final Codec<TransportColor> CODEC = StringRepresentable.fromEnum(TransportColor::values);

    /** Default color for Warp Cores (same as Telepad default). */
    public static final TransportColor DEFAULT = CYAN;

    private final String id;
    private final int color;
    private final PortalColor legacyColor;

    TransportColor(String id, int color, PortalColor legacyColor) {
        this.id = id;
        this.color = color;
        this.legacyColor = legacyColor;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(id);
    }

    /**
     * Returns the RGB color value for rendering.
     */
    public int getColor() {
        return color;
    }

    /**
     * Returns the color with full alpha (ARGB integer).
     */
    public int getColorWithAlpha() {
        return 0xFF000000 | color;
    }

    /**
     * Returns the red component (0-255).
     */
    public int getRed() {
        return (color >> 16) & 0xFF;
    }

    /**
     * Returns the green component (0-255).
     */
    public int getGreen() {
        return (color >> 8) & 0xFF;
    }

    /**
     * Returns the blue component (0-255).
     */
    public int getBlue() {
        return color & 0xFF;
    }

    /**
     * Returns the red component as float (0.0-1.0).
     */
    public float getRedF() {
        return getRed() / 255.0f;
    }

    /**
     * Returns the green component as float (0.0-1.0).
     */
    public float getGreenF() {
        return getGreen() / 255.0f;
    }

    /**
     * Returns the blue component as float (0.0-1.0).
     */
    public float getBlueF() {
        return getBlue() / 255.0f;
    }

    /**
     * Returns the legacy PortalColor for backward compatibility.
     */
    @Nonnull
    public PortalColor toLegacy() {
        return Objects.requireNonNull(legacyColor);
    }

    /**
     * Returns the index of this color (for network serialization).
     */
    public int getIndex() {
        return ordinal();
    }

    /**
     * Returns the RGB color value for rendering (alias for getColor()).
     */
    public int getColorValue() {
        return color;
    }

    /**
     * Returns a human-readable display name (capitalized form).
     */
    @Nonnull
    public String getDisplayName() {
        // Convert snake_case to Title Case
        StringBuilder sb = new StringBuilder();
        for (String part : Splitter.on('_').split(Objects.requireNonNull(id))) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return Objects.requireNonNull(sb.toString());
    }

    /**
     * Returns the transport color by index (for network serialization).
     */
    @Nonnull
    public static TransportColor byIndex(int index) {
        TransportColor[] values = values();
        if (index < 0 || index >= values.length) {
            return Objects.requireNonNull(DEFAULT);
        }
        return Objects.requireNonNull(values[index]);
    }

    /**
     * Returns the transport color by index (alias for byIndex).
     */
    @Nonnull
    public static TransportColor fromIndex(int index) {
        return byIndex(index);
    }

    /**
     * Returns the transport color by serialized name.
     */
    @Nonnull
    public static TransportColor byName(String name) {
        for (TransportColor color : values()) {
            if (color.id.equals(name)) {
                return color;
            }
        }
        return Objects.requireNonNull(DEFAULT);
    }

    /**
     * Converts from legacy PortalColor to TransportColor.
     */
    @Nonnull
    public static TransportColor fromLegacy(@Nonnull PortalColor legacy) {
        for (TransportColor color : values()) {
            if (color.legacyColor == legacy) {
                return color;
            }
        }
        return Objects.requireNonNull(DEFAULT);
    }

    /**
     * Converts this transport color to the corresponding BossBar color.
     * Centralizes the mapping previously duplicated in CountdownManager and RiftGateManager.
     */
    @Nonnull
    public BossEvent.BossBarColor toBossBarColor() {
        return switch (this) {
            case WHITE -> BossEvent.BossBarColor.WHITE;
            case PINK -> BossEvent.BossBarColor.PINK;
            case RED -> BossEvent.BossBarColor.RED;
            case YELLOW -> BossEvent.BossBarColor.YELLOW;
            case GREEN, LIME -> BossEvent.BossBarColor.GREEN;
            case BLUE, LIGHT_BLUE, CYAN -> BossEvent.BossBarColor.BLUE;
            case PURPLE, MAGENTA -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.WHITE;
        };
    }

    /**
     * Returns the next color in the cycle (for UI color picker).
     */
    @Nonnull
    public TransportColor next() {
        TransportColor[] values = values();
        return Objects.requireNonNull(values[(ordinal() + 1) % values.length]);
    }

    /**
     * Returns the previous color in the cycle (for UI color picker).
     */
    @Nonnull
    public TransportColor previous() {
        TransportColor[] values = values();
        return Objects.requireNonNull(values[(ordinal() - 1 + values.length) % values.length]);
    }
}
