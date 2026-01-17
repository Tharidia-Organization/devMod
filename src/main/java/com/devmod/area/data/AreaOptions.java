package com.devmod.area.data;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Construction options for an area.
 *
 * @param hasWalls       Whether to generate walls
 * @param hasCeiling     Whether to generate a ceiling
 * @param hasFloor       Whether to generate a floor
 * @param wallStyle      Style of wall construction
 * @param spawnPortals   Whether to spawn portal connection points
 * @param allowBuilding  Whether players can build in this area
 * @param linkedZoneId   Optional linked zone ID for special areas
 */
public record AreaOptions(
    boolean hasWalls,
    boolean hasCeiling,
    boolean hasFloor,
    WallStyle wallStyle,
    boolean spawnPortals,
    boolean allowBuilding,
    @Nullable String linkedZoneId
) {
    public static final AreaOptions DEFAULT = new AreaOptions(
        true, true, true, WallStyle.SOLID, false, false, null
    );

    public static final AreaOptions OPEN = new AreaOptions(
        false, false, true, WallStyle.SOLID, false, true, null
    );

    public static final Codec<AreaOptions> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("hasWalls").forGetter(AreaOptions::hasWalls),
            Codec.BOOL.fieldOf("hasCeiling").forGetter(AreaOptions::hasCeiling),
            Codec.BOOL.fieldOf("hasFloor").forGetter(AreaOptions::hasFloor),
            WallStyle.CODEC.fieldOf("wallStyle").forGetter(AreaOptions::wallStyle),
            Codec.BOOL.fieldOf("spawnPortals").forGetter(AreaOptions::spawnPortals),
            Codec.BOOL.fieldOf("allowBuilding").forGetter(AreaOptions::allowBuilding),
            Codec.STRING.optionalFieldOf("linkedZoneId").forGetter(o -> Optional.ofNullable(o.linkedZoneId()))
        ).apply(instance, (walls, ceiling, floor, style, portals, building, zone) ->
            new AreaOptions(walls, ceiling, floor, style, portals, building, zone.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, AreaOptions> STREAM_CODEC = StreamCodec.of(
        (buf, options) -> {
            ByteBufCodecs.BOOL.encode(buf, options.hasWalls);
            ByteBufCodecs.BOOL.encode(buf, options.hasCeiling);
            ByteBufCodecs.BOOL.encode(buf, options.hasFloor);
            ByteBufCodecs.VAR_INT.encode(buf, options.wallStyle.ordinal());
            ByteBufCodecs.BOOL.encode(buf, options.spawnPortals);
            ByteBufCodecs.BOOL.encode(buf, options.allowBuilding);
            ByteBufCodecs.BOOL.encode(buf, options.linkedZoneId != null);
            if (options.linkedZoneId != null) {
                ByteBufCodecs.STRING_UTF8.encode(buf, options.linkedZoneId);
            }
        },
        buf -> {
            boolean hasWalls = ByteBufCodecs.BOOL.decode(buf);
            boolean hasCeiling = ByteBufCodecs.BOOL.decode(buf);
            boolean hasFloor = ByteBufCodecs.BOOL.decode(buf);
            WallStyle wallStyle = WallStyle.values()[Math.min(ByteBufCodecs.VAR_INT.decode(buf), WallStyle.values().length - 1)];
            boolean spawnPortals = ByteBufCodecs.BOOL.decode(buf);
            boolean allowBuilding = ByteBufCodecs.BOOL.decode(buf);
            String linkedZoneId = ByteBufCodecs.BOOL.decode(buf) ? ByteBufCodecs.STRING_UTF8.decode(buf) : null;
            return new AreaOptions(hasWalls, hasCeiling, hasFloor, wallStyle, spawnPortals, allowBuilding, linkedZoneId);
        }
    );

    public AreaOptions withWalls(boolean newHasWalls) {
        return new AreaOptions(newHasWalls, hasCeiling, hasFloor, wallStyle, spawnPortals, allowBuilding, linkedZoneId);
    }

    public AreaOptions withCeiling(boolean newHasCeiling) {
        return new AreaOptions(hasWalls, newHasCeiling, hasFloor, wallStyle, spawnPortals, allowBuilding, linkedZoneId);
    }

    public AreaOptions withFloor(boolean newHasFloor) {
        return new AreaOptions(hasWalls, hasCeiling, newHasFloor, wallStyle, spawnPortals, allowBuilding, linkedZoneId);
    }

    public AreaOptions withWallStyle(WallStyle newStyle) {
        return new AreaOptions(hasWalls, hasCeiling, hasFloor, newStyle, spawnPortals, allowBuilding, linkedZoneId);
    }

    public AreaOptions withAllowBuilding(boolean newAllowBuilding) {
        return new AreaOptions(hasWalls, hasCeiling, hasFloor, wallStyle, spawnPortals, newAllowBuilding, linkedZoneId);
    }

    public AreaOptions withSpawnPortals(boolean newSpawnPortals) {
        return new AreaOptions(hasWalls, hasCeiling, hasFloor, wallStyle, newSpawnPortals, allowBuilding, linkedZoneId);
    }

    public AreaOptions withLinkedZone(@Nullable String newLinkedZoneId) {
        return new AreaOptions(hasWalls, hasCeiling, hasFloor, wallStyle, spawnPortals, allowBuilding, newLinkedZoneId);
    }

    /**
     * Wall construction styles.
     */
    public enum WallStyle implements StringRepresentable {
        /**
         * Solid walls with no openings.
         */
        SOLID("solid"),

        /**
         * Walls with window openings.
         */
        WINDOWED("windowed"),

        /**
         * Open corners with solid edges.
         */
        OPEN_CORNERS("open_corners"),

        /**
         * Only corner pillars, no walls.
         */
        PILLARS_ONLY("pillars_only");

        public static final Codec<WallStyle> CODEC = StringRepresentable.fromEnum(WallStyle::values);

        private final String name;

        WallStyle(String name) {
            this.name = name;
        }

        @Override
        @Nonnull
        public String getSerializedName() {
            return Objects.requireNonNull(name);
        }
    }
}
