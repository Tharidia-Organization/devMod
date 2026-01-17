package com.devmod.area.data;

import java.util.Objects;

import io.netty.buffer.ByteBuf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Dimensions for an area including size constraints.
 *
 * <p>HIGH-03 fix: All shapes now use both width and length dimensions.
 * For circular/hexagonal/octagonal shapes, different width and length values
 * create elliptical variants (e.g., a circle with width=30 and length=20
 * creates an ellipse stretched along the X axis).
 *
 * @param width   X axis dimension (used by all shapes, including circular for ellipse support)
 * @param length  Z axis dimension (used by all shapes, including circular for ellipse support)
 * @param height  Y axis dimension
 * @param floorY  Base Y level for the floor
 */
public record AreaDimensions(
    int width,
    int length,
    int height,
    int floorY
) {
    public static final int MIN_SIZE = 8;
    public static final int MAX_SIZE = 256;
    public static final int MIN_HEIGHT = 4;
    public static final int MAX_HEIGHT = 128;
    /** Minimum floor Y level (vanilla 1.18+ bottom) */
    public static final int MIN_FLOOR_Y = -64;
    /** Maximum floor Y level (vanilla 1.18+ top, accounting for height) */
    public static final int MAX_FLOOR_Y = 320;
    public static final int DEFAULT_WIDTH = 32;
    public static final int DEFAULT_LENGTH = 32;
    public static final int DEFAULT_HEIGHT = 16;
    public static final int DEFAULT_FLOOR_Y = 64;

    public static final AreaDimensions DEFAULT = new AreaDimensions(
        DEFAULT_WIDTH, DEFAULT_LENGTH, DEFAULT_HEIGHT, DEFAULT_FLOOR_Y
    );

    public static final Codec<AreaDimensions> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.intRange(MIN_SIZE, MAX_SIZE).fieldOf("width").forGetter(AreaDimensions::width),
            Codec.intRange(MIN_SIZE, MAX_SIZE).fieldOf("length").forGetter(AreaDimensions::length),
            Codec.intRange(MIN_HEIGHT, MAX_HEIGHT).fieldOf("height").forGetter(AreaDimensions::height),
            // C-09 fix: Add bounds to floorY to prevent overflow in calculations
            Codec.intRange(MIN_FLOOR_Y, MAX_FLOOR_Y).fieldOf("floorY").forGetter(AreaDimensions::floorY)
        ).apply(instance, (w, l, h, f) -> new AreaDimensions(
            Objects.requireNonNull(w),
            Objects.requireNonNull(l),
            Objects.requireNonNull(h),
            Objects.requireNonNull(f)
        ))
    );

    public static final StreamCodec<ByteBuf, AreaDimensions> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), AreaDimensions::width,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), AreaDimensions::length,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), AreaDimensions::height,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), AreaDimensions::floorY,
        (w, l, h, f) -> new AreaDimensions(
            Objects.requireNonNull(w),
            Objects.requireNonNull(l),
            Objects.requireNonNull(h),
            Objects.requireNonNull(f)
        )
    );

    /**
     * Creates dimensions clamped to valid ranges.
     */
    public static AreaDimensions clamped(int width, int length, int height, int floorY) {
        return new AreaDimensions(
            Math.clamp(width, MIN_SIZE, MAX_SIZE),
            Math.clamp(length, MIN_SIZE, MAX_SIZE),
            Math.clamp(height, MIN_HEIGHT, MAX_HEIGHT),
            Math.clamp(floorY, MIN_FLOOR_Y, MAX_FLOOR_Y)  // C-09 fix: also clamp floorY
        );
    }

    public AreaDimensions withWidth(int newWidth) {
        return new AreaDimensions(Math.clamp(newWidth, MIN_SIZE, MAX_SIZE), length, height, floorY);
    }

    public AreaDimensions withLength(int newLength) {
        return new AreaDimensions(width, Math.clamp(newLength, MIN_SIZE, MAX_SIZE), height, floorY);
    }

    public AreaDimensions withHeight(int newHeight) {
        return new AreaDimensions(width, length, Math.clamp(newHeight, MIN_HEIGHT, MAX_HEIGHT), floorY);
    }

    public AreaDimensions withFloorY(int newFloorY) {
        // MED-01 fix: Clamp floorY like other with* methods
        return new AreaDimensions(width, length, height, Math.clamp(newFloorY, MIN_FLOOR_Y, MAX_FLOOR_Y));
    }

    /**
     * Returns the total volume in blocks.
     */
    public long volume() {
        return (long) width * length * height;
    }

    /**
     * Returns the floor area in blocks.
     */
    public int floorArea() {
        return width * length;
    }
}
