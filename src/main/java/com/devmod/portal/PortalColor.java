package com.devmod.portal;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * 16 portal color variants following Minecraft dye colors.
 * Each color can only link with portals of the same color by default.
 */
public enum PortalColor implements StringRepresentable {
    WHITE("white", 0xF9FFFE),
    ORANGE("orange", 0xF9801D),
    MAGENTA("magenta", 0xC74EBD),
    LIGHT_BLUE("light_blue", 0x3AB3DA),
    YELLOW("yellow", 0xFED83D),
    LIME("lime", 0x80C71F),
    PINK("pink", 0xF38BAA),
    GRAY("gray", 0x474F52),
    LIGHT_GRAY("light_gray", 0x9D9D97),
    CYAN("cyan", 0x169C9C),
    PURPLE("purple", 0x8932B8),
    BLUE("blue", 0x3C44AA),
    BROWN("brown", 0x835432),
    GREEN("green", 0x5E7C16),
    RED("red", 0xB02E26),
    BLACK("black", 0x1D1D21);

    public static final Codec<PortalColor> CODEC = StringRepresentable.fromEnum(PortalColor::values);

    /**
     * StreamCodec for network serialization using ordinal index.
     */
    public static final StreamCodec<FriendlyByteBuf, PortalColor> STREAM_CODEC = StreamCodec.of(
        (buf, color) -> buf.writeVarInt(color.getIndex()),
        buf -> byIndex(buf.readVarInt())
    );

    private final String id;
    private final int color;

    PortalColor(String id, int color) {
        this.id = id;
        this.color = color;
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
     * Returns the portal color by index (for network serialization).
     */
    public static PortalColor byIndex(int index) {
        PortalColor[] values = values();
        if (index < 0 || index >= values.length) {
            return BLUE; // default
        }
        return values[index];
    }

    /**
     * Returns the index of this color (for network serialization).
     */
    public int getIndex() {
        return ordinal();
    }
}
