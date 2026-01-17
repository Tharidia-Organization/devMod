package com.devmod.zone.data;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable bounding box for zones.
 * Represents an axis-aligned rectangular region in 3D space.
 */
public record ZoneBounds(
    int minX, int maxX,
    int minY, int maxY,
    int minZ, int maxZ
) {
    // ========================================================================
    // Codecs
    // ========================================================================

    public static final Codec<ZoneBounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("minX").forGetter(ZoneBounds::minX),
        Codec.INT.fieldOf("maxX").forGetter(ZoneBounds::maxX),
        Codec.INT.fieldOf("minY").forGetter(ZoneBounds::minY),
        Codec.INT.fieldOf("maxY").forGetter(ZoneBounds::maxY),
        Codec.INT.fieldOf("minZ").forGetter(ZoneBounds::minZ),
        Codec.INT.fieldOf("maxZ").forGetter(ZoneBounds::maxZ)
    ).apply(instance, (a, b, c, d, e, f) ->
        new ZoneBounds(a.intValue(), b.intValue(), c.intValue(), d.intValue(), e.intValue(), f.intValue())));

    public static final StreamCodec<FriendlyByteBuf, ZoneBounds> STREAM_CODEC = StreamCodec.of(
        (buf, bounds) -> {
            buf.writeInt(bounds.minX);
            buf.writeInt(bounds.maxX);
            buf.writeInt(bounds.minY);
            buf.writeInt(bounds.maxY);
            buf.writeInt(bounds.minZ);
            buf.writeInt(bounds.maxZ);
        },
        buf -> new ZoneBounds(
            buf.readInt(), buf.readInt(),
            buf.readInt(), buf.readInt(),
            buf.readInt(), buf.readInt()
        )
    );

    // ========================================================================
    // Factory Methods
    // ========================================================================

    /**
     * Creates bounds from two corner positions (marker pair).
     * Automatically determines min/max values.
     */
    @Nonnull
    public static ZoneBounds fromCorners(@Nonnull BlockPos a, @Nonnull BlockPos b) {
        return new ZoneBounds(
            Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()), Math.max(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ()), Math.max(a.getZ(), b.getZ())
        );
    }

    /**
     * Creates bounds from center position and half-sizes.
     */
    @Nonnull
    public static ZoneBounds fromCenterAndSize(@Nonnull BlockPos center, int halfX, int halfY, int halfZ) {
        return new ZoneBounds(
            center.getX() - halfX, center.getX() + halfX,
            center.getY() - halfY, center.getY() + halfY,
            center.getZ() - halfZ, center.getZ() + halfZ
        );
    }

    /**
     * Creates small square bounds (for special zones like AFK alcove).
     */
    @Nonnull
    public static ZoneBounds small(int centerX, int centerZ, int floorY, int size, int height) {
        int half = size / 2;
        return new ZoneBounds(
            centerX - half, centerX + half,
            floorY, floorY + height,
            centerZ - half, centerZ + half
        );
    }

    /**
     * Creates bounds relative to an origin with offsets.
     */
    @Nonnull
    public static ZoneBounds relative(@Nonnull BlockPos origin,
            int offsetMinX, int offsetMaxX,
            int offsetMinY, int offsetMaxY,
            int offsetMinZ, int offsetMaxZ) {
        return new ZoneBounds(
            origin.getX() + offsetMinX, origin.getX() + offsetMaxX,
            origin.getY() + offsetMinY, origin.getY() + offsetMaxY,
            origin.getZ() + offsetMinZ, origin.getZ() + offsetMaxZ
        );
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Checks if a position is inside this bounds (inclusive).
     */
    public boolean contains(@Nonnull BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX
            && pos.getY() >= minY && pos.getY() <= maxY
            && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    /**
     * Checks if this bounds overlaps with another.
     */
    public boolean overlaps(@Nonnull ZoneBounds other) {
        return !(maxX < other.minX || minX > other.maxX
              || maxZ < other.minZ || minZ > other.maxZ
              || maxY < other.minY || minY > other.maxY);
    }

    /**
     * Gets the center position of this bounds.
     */
    @Nonnull
    public BlockPos center() {
        return new BlockPos(
            (minX + maxX) / 2,
            (minY + maxY) / 2,
            (minZ + maxZ) / 2
        );
    }

    /**
     * Gets the floor center (center at minY).
     */
    @Nonnull
    public BlockPos floorCenter() {
        return new BlockPos(
            (minX + maxX) / 2,
            minY,
            (minZ + maxZ) / 2
        );
    }

    /**
     * Gets the width (X dimension).
     */
    public int width() {
        return maxX - minX + 1;
    }

    /**
     * Gets the length (Z dimension).
     */
    public int length() {
        return maxZ - minZ + 1;
    }

    /**
     * Gets the height (Y dimension).
     */
    public int height() {
        return maxY - minY + 1;
    }

    /**
     * Gets the volume in blocks.
     */
    public int volume() {
        return width() * height() * length();
    }

    /**
     * Gets the floor area (width * length).
     */
    public int floorArea() {
        return width() * length();
    }

    // ========================================================================
    // Transformation Methods
    // ========================================================================

    /**
     * Creates expanded bounds by the given amount in all directions.
     */
    @Nonnull
    public ZoneBounds expand(int amount) {
        return new ZoneBounds(
            minX - amount, maxX + amount,
            minY - amount, maxY + amount,
            minZ - amount, maxZ + amount
        );
    }

    /**
     * Creates bounds shifted by the given offset.
     */
    @Nonnull
    public ZoneBounds offset(@Nonnull BlockPos offset) {
        return new ZoneBounds(
            minX + offset.getX(), maxX + offset.getX(),
            minY + offset.getY(), maxY + offset.getY(),
            minZ + offset.getZ(), maxZ + offset.getZ()
        );
    }

    /**
     * Creates bounds with a new Y range.
     */
    @Nonnull
    public ZoneBounds withYRange(int newMinY, int newMaxY) {
        return new ZoneBounds(minX, maxX, newMinY, newMaxY, minZ, maxZ);
    }

    // ========================================================================
    // Validation
    // ========================================================================

    /** Maximum allowed coordinate value (Minecraft world border limit) */
    public static final int MAX_COORDINATE = 30_000_000;

    /** Minimum allowed coordinate value */
    public static final int MIN_COORDINATE = -30_000_000;

    /** Maximum allowed zone dimension (blocks per axis) */
    public static final int MAX_ZONE_SIZE = 1000;

    /** Maximum allowed Y coordinate */
    public static final int MAX_Y = 320;

    /** Minimum allowed Y coordinate */
    public static final int MIN_Y = -64;

    /**
     * Checks if the bounds are valid (min <= max for all axes).
     */
    public boolean isValid() {
        return minX <= maxX && minY <= maxY && minZ <= maxZ;
    }

    /**
     * Checks if bounds are within safe world limits.
     */
    public boolean isWithinWorldLimits() {
        return minX >= MIN_COORDINATE && maxX <= MAX_COORDINATE
            && minZ >= MIN_COORDINATE && maxZ <= MAX_COORDINATE
            && minY >= MIN_Y && maxY <= MAX_Y;
    }

    /**
     * Checks if zone size is reasonable (not too large).
     */
    public boolean isSizeReasonable() {
        return width() <= MAX_ZONE_SIZE
            && height() <= MAX_ZONE_SIZE
            && length() <= MAX_ZONE_SIZE;
    }

    /**
     * Full validation: valid, within limits, reasonable size.
     */
    public boolean isFullyValid() {
        return isValid() && isWithinWorldLimits() && isSizeReasonable();
    }

    /**
     * Returns a clamped version that fits within world limits.
     */
    @Nonnull
    public ZoneBounds clamped() {
        return new ZoneBounds(
            Math.max(MIN_COORDINATE, Math.min(MAX_COORDINATE, minX)),
            Math.max(MIN_COORDINATE, Math.min(MAX_COORDINATE, maxX)),
            Math.max(MIN_Y, Math.min(MAX_Y, minY)),
            Math.max(MIN_Y, Math.min(MAX_Y, maxY)),
            Math.max(MIN_COORDINATE, Math.min(MAX_COORDINATE, minZ)),
            Math.max(MIN_COORDINATE, Math.min(MAX_COORDINATE, maxZ))
        );
    }

    /**
     * Returns a normalized version where min <= max is guaranteed.
     */
    @Nonnull
    public ZoneBounds normalized() {
        return new ZoneBounds(
            Math.min(minX, maxX), Math.max(minX, maxX),
            Math.min(minY, maxY), Math.max(minY, maxY),
            Math.min(minZ, maxZ), Math.max(minZ, maxZ)
        );
    }
}
