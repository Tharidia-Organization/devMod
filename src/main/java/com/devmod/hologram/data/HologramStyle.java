package com.devmod.hologram.data;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Visual styling for a hologram.
 * Contains colors, scale, billboard mode, and rendering options.
 */
public record HologramStyle(
    int textColor,
    int backgroundColor,
    float scale,
    float lineSpacing,
    HologramBillboard billboard,
    boolean shadow,
    boolean seeThrough,
    int glowColor
) {
    /**
     * Default style for new holograms.
     */
    public static final HologramStyle DEFAULT = new HologramStyle(
        0xFFFFFF,    // White text
        0x40000000,  // Semi-transparent black background
        1.0f,        // Normal scale
        1.0f,        // Normal line spacing
        HologramBillboard.CENTER,
        true,        // Shadow enabled
        false,       // Not see-through
        0            // No glow
    );

    public static final Codec<HologramStyle> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("textColor").forGetter(HologramStyle::textColor),
            Codec.INT.fieldOf("backgroundColor").forGetter(HologramStyle::backgroundColor),
            Codec.FLOAT.fieldOf("scale").forGetter(HologramStyle::scale),
            Codec.FLOAT.fieldOf("lineSpacing").forGetter(HologramStyle::lineSpacing),
            HologramBillboard.CODEC.fieldOf("billboard").forGetter(HologramStyle::billboard),
            Codec.BOOL.fieldOf("shadow").forGetter(HologramStyle::shadow),
            Codec.BOOL.fieldOf("seeThrough").forGetter(HologramStyle::seeThrough),
            Codec.INT.fieldOf("glowColor").forGetter(HologramStyle::glowColor)
        ).apply(instance, HologramStyle::new)
    );

    public static final StreamCodec<FriendlyByteBuf, HologramStyle> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HologramStyle decode(FriendlyByteBuf buf) {
            return new HologramStyle(
                buf.readInt(),
                buf.readInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readEnum(HologramBillboard.class),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, HologramStyle style) {
            buf.writeInt(style.textColor);
            buf.writeInt(style.backgroundColor);
            buf.writeFloat(style.scale);
            buf.writeFloat(style.lineSpacing);
            buf.writeEnum(style.billboard);
            buf.writeBoolean(style.shadow);
            buf.writeBoolean(style.seeThrough);
            buf.writeInt(style.glowColor);
        }
    };

    /**
     * Validates style values are within acceptable ranges.
     */
    public HologramStyle {
        Objects.requireNonNull(billboard, "billboard");
        if (scale < 0.1f || scale > 4.0f) {
            scale = Math.max(0.1f, Math.min(4.0f, scale));
        }
        if (lineSpacing < 0.5f || lineSpacing > 3.0f) {
            lineSpacing = Math.max(0.5f, Math.min(3.0f, lineSpacing));
        }
    }

    /**
     * Returns whether glow is enabled.
     */
    public boolean hasGlow() {
        return glowColor != 0;
    }

    /**
     * Creates a builder initialized with this style's values.
     */
    public Builder toBuilder() {
        return new Builder()
            .textColor(textColor)
            .backgroundColor(backgroundColor)
            .scale(scale)
            .lineSpacing(lineSpacing)
            .billboard(billboard)
            .shadow(shadow)
            .seeThrough(seeThrough)
            .glowColor(glowColor);
    }

    /**
     * Builder for HologramStyle.
     */
    public static class Builder {
        private int textColor = 0xFFFFFF;
        private int backgroundColor = 0x40000000;
        private float scale = 1.0f;
        private float lineSpacing = 1.0f;
        private HologramBillboard billboard = HologramBillboard.CENTER;
        private boolean shadow = true;
        private boolean seeThrough = false;
        private int glowColor = 0;

        public Builder textColor(int color) { this.textColor = color; return this; }
        public Builder backgroundColor(int color) { this.backgroundColor = color; return this; }
        public Builder scale(float scale) { this.scale = scale; return this; }
        public Builder lineSpacing(float spacing) { this.lineSpacing = spacing; return this; }
        public Builder billboard(HologramBillboard billboard) { this.billboard = billboard; return this; }
        public Builder shadow(boolean shadow) { this.shadow = shadow; return this; }
        public Builder seeThrough(boolean seeThrough) { this.seeThrough = seeThrough; return this; }
        public Builder glowColor(int color) { this.glowColor = color; return this; }

        public HologramStyle build() {
            return new HologramStyle(textColor, backgroundColor, scale, lineSpacing, billboard, shadow, seeThrough, glowColor);
        }
    }
}
