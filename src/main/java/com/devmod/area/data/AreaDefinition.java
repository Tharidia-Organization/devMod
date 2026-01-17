package com.devmod.area.data;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Complete definition of an area including all configuration.
 *
 * @param id               Unique identifier for this area
 * @param name             Display name of the area
 * @param generationType   Type of generation (CUSTOM or BIOME)
 * @param shape            Geometric shape of the area
 * @param dimensions       Size dimensions of the area
 * @param centerPosition   Center position of the area
 * @param dimensionId      Dimension this area belongs to
 * @param palette          Material palette for CUSTOM generation
 * @param biomeConfig      Biome config for BIOME generation (null if CUSTOM)
 * @param options          Construction options
 * @param creatorUUID      UUID of the player who created this area (null for system-created)
 * @param createdAt        Timestamp when the area was created
 * @param updatedAt        Timestamp when the area was last updated
 * @param revision         Revision number for optimistic locking
 * @param isMainHub        True if this is the main hub area
 * @param linkedZoneId     The zone ID (string) this area is linked to (null if not linked)
 * @param customShapeNbt   NBT data for CUSTOM_NBT shape (null if not using custom shape)
 */
public record AreaDefinition(
    UUID id,
    String name,
    AreaGenerationType generationType,
    AreaShape shape,
    AreaDimensions dimensions,
    BlockPos centerPosition,
    ResourceLocation dimensionId,
    AreaPalette palette,
    @Nullable BiomeGenerationConfig biomeConfig,
    AreaOptions options,
    @Nullable UUID creatorUUID,
    long createdAt,
    long updatedAt,
    int revision,
    boolean isMainHub,
    @Nullable String linkedZoneId,
    @Nullable CompoundTag customShapeNbt
) {
    /**
     * Manual codec implementation for 17 fields (RecordCodecBuilder.group() only supports 16).
     */
    public static final Codec<AreaDefinition> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<AreaDefinition, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getMap(input).flatMap(map -> {
                try {
                    UUID id = getField(ops, map, "id", UUIDUtil.CODEC);
                    String name = getField(ops, map, "name", Codec.STRING);
                    AreaGenerationType genType = getField(ops, map, "generationType", AreaGenerationType.CODEC);
                    AreaShape shape = getField(ops, map, "shape", AreaShape.CODEC);
                    AreaDimensions dims = getField(ops, map, "dimensions", AreaDimensions.CODEC);
                    BlockPos center = getField(ops, map, "center", BlockPos.CODEC);
                    ResourceLocation dimension = getField(ops, map, "dimension", ResourceLocation.CODEC);
                    AreaPalette palette = getField(ops, map, "palette", AreaPalette.CODEC);
                    BiomeGenerationConfig biomeConfig = getOptionalField(ops, map, "biomeConfig", BiomeGenerationConfig.CODEC);
                    AreaOptions options = getField(ops, map, "options", AreaOptions.CODEC);
                    UUID creatorUUID = getOptionalField(ops, map, "creator", UUIDUtil.CODEC);
                    long createdAt = getField(ops, map, "createdAt", Codec.LONG);
                    long updatedAt = getField(ops, map, "updatedAt", Codec.LONG);
                    int revision = getField(ops, map, "revision", Codec.INT);
                    boolean isMainHub = getField(ops, map, "isMainHub", Codec.BOOL);
                    String linkedZoneId = getOptionalField(ops, map, "linkedZoneId", Codec.STRING);
                    CompoundTag customShapeNbt = getOptionalField(ops, map, "customShapeNbt", CompoundTag.CODEC);

                    AreaDefinition def = new AreaDefinition(id, name, genType, shape, dims, center, dimension,
                        palette, biomeConfig, options, creatorUUID, createdAt, updatedAt, revision, isMainHub,
                        linkedZoneId, customShapeNbt);
                    return DataResult.success(Pair.of(def, ops.empty()));
                } catch (Exception e) {
                    return DataResult.error(() -> "Failed to decode AreaDefinition: " + e.getMessage());
                }
            });
        }

        @Override
        public <T> DataResult<T> encode(AreaDefinition def, DynamicOps<T> ops, T prefix) {
            RecordBuilder<T> builder = ops.mapBuilder();
            builder.add("id", UUIDUtil.CODEC.encodeStart(ops, def.id()));
            builder.add("name", Codec.STRING.encodeStart(ops, def.name()));
            builder.add("generationType", AreaGenerationType.CODEC.encodeStart(ops, def.generationType()));
            builder.add("shape", AreaShape.CODEC.encodeStart(ops, def.shape()));
            builder.add("dimensions", AreaDimensions.CODEC.encodeStart(ops, def.dimensions()));
            builder.add("center", BlockPos.CODEC.encodeStart(ops, def.centerPosition()));
            builder.add("dimension", ResourceLocation.CODEC.encodeStart(ops, def.dimensionId()));
            builder.add("palette", AreaPalette.CODEC.encodeStart(ops, def.palette()));
            if (def.biomeConfig() != null) {
                builder.add("biomeConfig", BiomeGenerationConfig.CODEC.encodeStart(ops, def.biomeConfig()));
            }
            builder.add("options", AreaOptions.CODEC.encodeStart(ops, def.options()));
            if (def.creatorUUID() != null) {
                builder.add("creator", UUIDUtil.CODEC.encodeStart(ops, def.creatorUUID()));
            }
            builder.add("createdAt", Codec.LONG.encodeStart(ops, def.createdAt()));
            builder.add("updatedAt", Codec.LONG.encodeStart(ops, def.updatedAt()));
            builder.add("revision", Codec.INT.encodeStart(ops, def.revision()));
            builder.add("isMainHub", Codec.BOOL.encodeStart(ops, def.isMainHub()));
            if (def.linkedZoneId() != null) {
                builder.add("linkedZoneId", Codec.STRING.encodeStart(ops, def.linkedZoneId()));
            }
            if (def.customShapeNbt() != null) {
                builder.add("customShapeNbt", CompoundTag.CODEC.encodeStart(ops, def.customShapeNbt()));
            }
            return builder.build(prefix);
        }

        private <T, V> V getField(DynamicOps<T> ops, MapLike<T> map, String key, Codec<V> codec) {
            T value = map.get(key);
            if (value == null) {
                throw new IllegalStateException("Missing required field: " + key);
            }
            // HIGH-01 fix: Wrap codec decode with better error context
            try {
                return codec.decode(ops, value).getOrThrow().getFirst();
            } catch (Exception e) {
                throw new IllegalStateException("Malformed data for field '" + key + "': " + e.getMessage(), e);
            }
        }

        @Nullable
        private <T, V> V getOptionalField(DynamicOps<T> ops, MapLike<T> map, String key, Codec<V> codec) {
            T value = map.get(key);
            if (value == null) {
                return null;
            }
            return codec.decode(ops, value).result().map(Pair::getFirst).orElse(null);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, AreaDefinition> STREAM_CODEC = new StreamCodec<>() {
        @Override
        @Nonnull
        public AreaDefinition decode(@Nonnull RegistryFriendlyByteBuf buf) {
            UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            int genOrdinal = buf.readVarInt();
            AreaGenerationType[] genValues = AreaGenerationType.values();
            AreaGenerationType genType = genOrdinal >= 0 && genOrdinal < genValues.length
                ? genValues[genOrdinal]
                : AreaGenerationType.CUSTOM;
            int shapeOrdinal = buf.readVarInt();
            AreaShape[] shapeValues = AreaShape.values();
            AreaShape shape = shapeOrdinal >= 0 && shapeOrdinal < shapeValues.length
                ? shapeValues[shapeOrdinal]
                : AreaShape.RECTANGULAR;
            AreaDimensions dims = AreaDimensions.STREAM_CODEC.decode(buf);
            BlockPos center = BlockPos.STREAM_CODEC.decode(buf);
            ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buf);
            AreaPalette palette = AreaPalette.STREAM_CODEC.decode(buf);

            BiomeGenerationConfig biomeConfig = null;
            if (buf.readBoolean()) {
                biomeConfig = BiomeGenerationConfig.STREAM_CODEC.decode(buf);
            }

            AreaOptions options = AreaOptions.STREAM_CODEC.decode(buf);

            UUID creatorUUID = null;
            if (buf.readBoolean()) {
                creatorUUID = UUIDUtil.STREAM_CODEC.decode(buf);
            }

            long createdAt = buf.readLong();
            long updatedAt = buf.readLong();
            int revision = buf.readVarInt();
            boolean isMainHub = buf.readBoolean();

            String linkedZoneId = null;
            if (buf.readBoolean()) {
                linkedZoneId = ByteBufCodecs.STRING_UTF8.decode(buf);
            }

            CompoundTag customShapeNbt = null;
            if (buf.readBoolean()) {
                customShapeNbt = buf.readNbt();
            }

            return new AreaDefinition(id, name, genType, shape, dims, center, dimension, palette,
                biomeConfig, options, creatorUUID, createdAt, updatedAt, revision, isMainHub, linkedZoneId, customShapeNbt);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull AreaDefinition def) {
            UUIDUtil.STREAM_CODEC.encode(buf, Objects.requireNonNull(def.id()));
            ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(def.name()));
            buf.writeVarInt(def.generationType().ordinal());
            buf.writeVarInt(def.shape().ordinal());
            AreaDimensions.STREAM_CODEC.encode(buf, Objects.requireNonNull(def.dimensions()));
            BlockPos.STREAM_CODEC.encode(buf, Objects.requireNonNull(def.centerPosition()));
            ResourceLocation.STREAM_CODEC.encode(buf, Objects.requireNonNull(def.dimensionId()));
            AreaPalette.STREAM_CODEC.encode(buf, Objects.requireNonNull(def.palette()));

            BiomeGenerationConfig biomeConfig = def.biomeConfig();
            buf.writeBoolean(biomeConfig != null);
            if (biomeConfig != null) {
                BiomeGenerationConfig.STREAM_CODEC.encode(buf, biomeConfig);
            }

            AreaOptions.STREAM_CODEC.encode(buf, Objects.requireNonNull(def.options()));

            UUID creatorUUID = def.creatorUUID();
            buf.writeBoolean(creatorUUID != null);
            if (creatorUUID != null) {
                UUIDUtil.STREAM_CODEC.encode(buf, creatorUUID);
            }

            buf.writeLong(def.createdAt());
            buf.writeLong(def.updatedAt());
            buf.writeVarInt(def.revision());
            buf.writeBoolean(def.isMainHub());

            String linkedZoneId = def.linkedZoneId();
            buf.writeBoolean(linkedZoneId != null);
            if (linkedZoneId != null) {
                ByteBufCodecs.STRING_UTF8.encode(buf, linkedZoneId);
            }

            CompoundTag customShapeNbt = def.customShapeNbt();
            buf.writeBoolean(customShapeNbt != null);
            if (customShapeNbt != null) {
                buf.writeNbt(customShapeNbt);
            }
        }
    };

    /**
     * Returns true if this area uses biome-based generation.
     */
    public boolean isBiomeGeneration() {
        return generationType == AreaGenerationType.BIOME && biomeConfig != null;
    }

    /**
     * Returns true if this area uses custom palette-based generation.
     */
    public boolean isCustomGeneration() {
        return generationType == AreaGenerationType.CUSTOM;
    }

    /**
     * Creates a new AreaDefinition with an incremented revision and updated timestamp.
     */
    @Nonnull
    public AreaDefinition withUpdate() {
        return new AreaDefinition(id, name, generationType, shape, dimensions, centerPosition,
            dimensionId, palette, biomeConfig, options, creatorUUID, createdAt,
            System.currentTimeMillis(), revision + 1, isMainHub, linkedZoneId, customShapeNbt);
    }

    /**
     * Creates a new AreaDefinition with updated dimensions.
     */
    @Nonnull
    public AreaDefinition withDimensions(@Nonnull AreaDimensions newDimensions) {
        return new AreaDefinition(id, name, generationType, shape, newDimensions, centerPosition,
            dimensionId, palette, biomeConfig, options, creatorUUID, createdAt,
            System.currentTimeMillis(), revision + 1, isMainHub, linkedZoneId, customShapeNbt);
    }

    /**
     * Creates a new AreaDefinition with updated options.
     */
    @Nonnull
    public AreaDefinition withOptions(@Nonnull AreaOptions newOptions) {
        return new AreaDefinition(id, name, generationType, shape, dimensions, centerPosition,
            dimensionId, palette, biomeConfig, newOptions, creatorUUID, createdAt,
            System.currentTimeMillis(), revision + 1, isMainHub, linkedZoneId, customShapeNbt);
    }

    /**
     * Creates a new AreaDefinition with updated palette.
     */
    @Nonnull
    public AreaDefinition withPalette(@Nonnull AreaPalette newPalette) {
        return new AreaDefinition(id, name, generationType, shape, dimensions, centerPosition,
            dimensionId, newPalette, biomeConfig, options, creatorUUID, createdAt,
            System.currentTimeMillis(), revision + 1, isMainHub, linkedZoneId, customShapeNbt);
    }

    /**
     * Creates a new AreaDefinition with updated name.
     */
    @Nonnull
    public AreaDefinition withName(@Nonnull String newName) {
        return new AreaDefinition(id, newName, generationType, shape, dimensions, centerPosition,
            dimensionId, palette, biomeConfig, options, creatorUUID, createdAt,
            System.currentTimeMillis(), revision + 1, isMainHub, linkedZoneId, customShapeNbt);
    }

    /**
     * Creates a new AreaDefinition with updated linked zone.
     */
    @Nonnull
    public AreaDefinition withLinkedZone(@Nullable String zoneId) {
        return new AreaDefinition(id, name, generationType, shape, dimensions, centerPosition,
            dimensionId, palette, biomeConfig, options, creatorUUID, createdAt,
            System.currentTimeMillis(), revision + 1, isMainHub, zoneId, customShapeNbt);
    }

    /**
     * Creates a new AreaDefinition with updated custom shape NBT.
     */
    @Nonnull
    public AreaDefinition withCustomShapeNbt(@Nullable CompoundTag nbt) {
        return new AreaDefinition(id, name, generationType, shape, dimensions, centerPosition,
            dimensionId, palette, biomeConfig, options, creatorUUID, createdAt,
            System.currentTimeMillis(), revision + 1, isMainHub, linkedZoneId, nbt);
    }

    /**
     * Creates a new AreaDefinition with updated main hub status.
     */
    @Nonnull
    public AreaDefinition withMainHub(boolean mainHub) {
        return new AreaDefinition(id, name, generationType, shape, dimensions, centerPosition,
            dimensionId, palette, biomeConfig, options, creatorUUID, createdAt,
            System.currentTimeMillis(), revision + 1, mainHub, linkedZoneId, customShapeNbt);
    }

    /**
     * Creates a builder for a new AreaDefinition.
     */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AreaDefinition.
     */
    public static class Builder {
        private UUID id = UUID.randomUUID();
        private String name = "New Area";
        private AreaGenerationType generationType = AreaGenerationType.CUSTOM;
        private AreaShape shape = AreaShape.RECTANGULAR;
        private AreaDimensions dimensions = AreaDimensions.DEFAULT;
        private BlockPos centerPosition = BlockPos.ZERO;
        private ResourceLocation dimensionId = ResourceLocation.withDefaultNamespace("overworld");
        private AreaPalette palette = AreaPalette.empty("default");
        @Nullable private BiomeGenerationConfig biomeConfig = null;
        private AreaOptions options = AreaOptions.DEFAULT;
        @Nullable private UUID creatorUUID = null;
        private boolean isMainHub = false;
        @Nullable private String linkedZoneId = null;
        @Nullable private CompoundTag customShapeNbt = null;

        public Builder id(@Nonnull UUID id) {
            this.id = Objects.requireNonNull(id);
            return this;
        }

        public Builder name(@Nonnull String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Builder generationType(@Nonnull AreaGenerationType type) {
            this.generationType = Objects.requireNonNull(type);
            return this;
        }

        public Builder shape(@Nonnull AreaShape shape) {
            this.shape = Objects.requireNonNull(shape);
            return this;
        }

        public Builder dimensions(@Nonnull AreaDimensions dimensions) {
            this.dimensions = Objects.requireNonNull(dimensions);
            return this;
        }

        public Builder center(@Nonnull BlockPos center) {
            this.centerPosition = Objects.requireNonNull(center);
            return this;
        }

        public Builder dimension(@Nonnull ResourceLocation dimension) {
            this.dimensionId = Objects.requireNonNull(dimension);
            return this;
        }

        public Builder palette(@Nonnull AreaPalette palette) {
            this.palette = Objects.requireNonNull(palette);
            return this;
        }

        public Builder biomeConfig(@Nullable BiomeGenerationConfig config) {
            this.biomeConfig = config;
            return this;
        }

        public Builder options(@Nonnull AreaOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        public Builder creator(@Nullable UUID creator) {
            this.creatorUUID = creator;
            return this;
        }

        public Builder mainHub(boolean isMainHub) {
            this.isMainHub = isMainHub;
            return this;
        }

        public Builder linkedZone(@Nullable String zoneId) {
            this.linkedZoneId = zoneId;
            return this;
        }

        public Builder customShapeNbt(@Nullable CompoundTag nbt) {
            this.customShapeNbt = nbt;
            return this;
        }

        @Nonnull
        public AreaDefinition build() {
            long now = System.currentTimeMillis();
            return new AreaDefinition(
                id, name, generationType, shape, dimensions, centerPosition,
                dimensionId, palette, biomeConfig, options, creatorUUID,
                now, now, 0, isMainHub, linkedZoneId, customShapeNbt
            );
        }
    }
}
