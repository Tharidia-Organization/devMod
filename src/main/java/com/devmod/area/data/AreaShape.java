package com.devmod.area.data;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import com.google.errorprone.annotations.InlineMe;
import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
/**
 * Defines the geometric shape of an area.
 */
public enum AreaShape implements StringRepresentable {
    /**
     * Standard rectangular shape.
     */
    RECTANGULAR("rectangular"),

    /**
     * Circular/elliptical shape.
     * Uses width for X-axis diameter and length for Z-axis diameter.
     * Set width == length for a perfect circle.
     */
    CIRCULAR("circular"),

    /**
     * Hexagonal shape.
     * Uses width for X-axis scale and length for Z-axis scale.
     * Set width == length for a regular hexagon.
     */
    HEXAGONAL("hexagonal"),

    /**
     * Octagonal shape.
     * Uses width for X-axis scale and length for Z-axis scale.
     * Set width == length for a regular octagon.
     */
    OCTAGONAL("octagonal"),

    /**
     * Custom shape loaded from NBT structure.
     */
    CUSTOM_NBT("custom_nbt"),

    /**
     * Path/spline shape for race tracks and corridors.
     * Uses waypoints stored in NBT to define the path centerline.
     * Width determines the corridor width around the path.
     * Length is ignored (path length is determined by waypoints).
     *
     * <p>UX-09 fix: Added to support race track and corridor use cases.
     */
    PATH("path");

    public static final Codec<AreaShape> CODEC = StringRepresentable.fromEnum(AreaShape::values);

    /**
     * M-02 fix: Safe decoder that returns RECTANGULAR for invalid ordinals instead of silent clamping.
     * Logs a warning for unknown values (likely from newer version).
     */
    public static final StreamCodec<ByteBuf, AreaShape> STREAM_CODEC = StreamCodec.of(
        (buf, shape) -> ByteBufCodecs.VAR_INT.encode(buf, shape.ordinal()),
        buf -> {
            int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
            AreaShape[] vals = values();
            if (ordinal < 0 || ordinal >= vals.length) {
                // M-02 fix: Log warning for unknown ordinal, return safe default
                org.slf4j.LoggerFactory.getLogger(AreaShape.class)
                    .warn("[AreaShape] Unknown ordinal {} (max: {}), defaulting to RECTANGULAR. Data may be from a newer version.",
                        ordinal, vals.length - 1);
                return RECTANGULAR;
            }
            return vals[ordinal];
        }
    );

    private final String name;

    AreaShape(String name) {
        this.name = name;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    /**
     * Returns true if this shape uses both width and length dimensions.
     *
     * <p>HIGH-03 fix: ALL shapes now use both width (X axis) and length (Z axis)
     * to support elliptical variants. CIRCULAR with different width/length creates
     * an ellipse, HEXAGONAL/OCTAGONAL are similarly scaled independently on each axis.
     *
     * @return Always true for all current shapes
     * @deprecated This method is misleading as all shapes now use both dimensions.
     *             Check shape-specific documentation instead.
     */
    @Deprecated
    @InlineMe(replacement = "true")
    public final boolean usesBothDimensions() {
        // HIGH-03 fix: All shapes now use both width and length
        return true;
    }

    /**
     * Returns true if this shape uses only width (as diameter/size).
     *
     * <p>HIGH-03 fix: This method is now deprecated. All shapes use both width and
     * length dimensions to support non-square/elliptical variants.
     *
     * @return Always false (all shapes use both dimensions now)
     * @deprecated This method is misleading as all shapes now use both dimensions.
     *             For symmetrical shapes, set width == length.
     */
    @Deprecated
    @InlineMe(replacement = "false")
    public final boolean usesWidthOnly() {
        // HIGH-03 fix: All shapes now use both width and length
        return false;
    }
}
