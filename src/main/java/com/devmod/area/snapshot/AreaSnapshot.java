package com.devmod.area.snapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaShape;

/**
 * Metadata record for an area snapshot.
 * The actual block data is stored in a separate file under world/devmod/snapshots/.
 *
 * @param id              Unique identifier for this snapshot
 * @param areaId          The area this snapshot belongs to
 * @param areaName        Area name at time of capture
 * @param description     User-provided description for the snapshot
 * @param creatorId       UUID of player who created snapshot (null for server)
 * @param creatorName     Name of player who created snapshot
 * @param createdAt       Timestamp when snapshot was created
 * @param blockCount      Number of non-air blocks captured
 * @param fileSizeBytes   File size on disk after compression
 * @param dimensions      Area dimensions at time of capture
 * @param center          Center position at time of capture
 * @param shape           Area shape at time of capture (HIGH-04 fix)
 * @param dimensionKey    Dimension key where snapshot was captured (HIGH-05 fix)
 * @param customShapeNbt  Custom shape NBT data if shape is CUSTOM (HIGH-04 fix)
 */
public record AreaSnapshot(
    UUID id,
    UUID areaId,
    String areaName,
    String description,
    @Nullable UUID creatorId,
    @Nullable String creatorName,
    long createdAt,
    int blockCount,
    long fileSizeBytes,
    AreaDimensions dimensions,
    BlockPos center,
    AreaShape shape,
    ResourceKey<Level> dimensionKey,
    @Nullable CompoundTag customShapeNbt
) {
    /** Maximum description length */
    public static final int MAX_DESCRIPTION_LENGTH = 128;
    /** M-04 fix: Maximum creator name length (consistent with Minecraft player name limits) */
    public static final int MAX_CREATOR_NAME_LENGTH = 64;

    /**
     * Compact constructor to validate and normalize inputs.
     */
    public AreaSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(areaId, "areaId");
        Objects.requireNonNull(areaName, "areaName");
        Objects.requireNonNull(dimensions, "dimensions");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(dimensionKey, "dimensionKey");

        // Truncate description if too long
        if (description == null) {
            description = "";
        } else if (description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        }

        // M-04 fix: Normalize creatorName similarly to description
        if (creatorName != null && creatorName.length() > MAX_CREATOR_NAME_LENGTH) {
            creatorName = creatorName.substring(0, MAX_CREATOR_NAME_LENGTH);
        }

        // Ensure non-negative values
        if (blockCount < 0) blockCount = 0;
        if (fileSizeBytes < 0) fileSizeBytes = 0;
    }

    public static final Codec<AreaSnapshot> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(AreaSnapshot::id),
            UUIDUtil.CODEC.fieldOf("areaId").forGetter(AreaSnapshot::areaId),
            Codec.STRING.fieldOf("areaName").forGetter(AreaSnapshot::areaName),
            Codec.STRING.optionalFieldOf("description", "").forGetter(AreaSnapshot::description),
            UUIDUtil.CODEC.optionalFieldOf("creatorId")
                .forGetter(s -> Optional.ofNullable(s.creatorId())),
            Codec.STRING.optionalFieldOf("creatorName", "")
                .forGetter(s -> s.creatorName() != null ? s.creatorName() : ""),
            Codec.LONG.fieldOf("createdAt").forGetter(AreaSnapshot::createdAt),
            Codec.INT.fieldOf("blockCount").forGetter(AreaSnapshot::blockCount),
            Codec.LONG.fieldOf("fileSizeBytes").forGetter(AreaSnapshot::fileSizeBytes),
            AreaDimensions.CODEC.fieldOf("dimensions").forGetter(AreaSnapshot::dimensions),
            BlockPos.CODEC.fieldOf("center").forGetter(AreaSnapshot::center),
            // HIGH-04 fix: Store shape for correct restore
            AreaShape.CODEC.optionalFieldOf("shape", AreaShape.RECTANGULAR).forGetter(AreaSnapshot::shape),
            // HIGH-05 fix: Store dimension key for verification
            Level.RESOURCE_KEY_CODEC.optionalFieldOf("dimensionKey", Level.OVERWORLD).forGetter(AreaSnapshot::dimensionKey),
            // HIGH-04 fix: Store custom shape NBT if applicable
            CompoundTag.CODEC.optionalFieldOf("customShapeNbt")
                .forGetter(s -> Optional.ofNullable(s.customShapeNbt()))
        ).apply(instance, (id, areaId, name, desc, creatorOpt, creatorName, time, blocks, size, dims, center, shape, dimKey, customNbtOpt) ->
            new AreaSnapshot(id, areaId, name, desc,
                creatorOpt.orElse(null),
                creatorName.isEmpty() ? null : creatorName,
                time, blocks, size, dims, center,
                shape, dimKey, customNbtOpt.orElse(null)))
    );

    /**
     * Gets the snapshot file path relative to world folder.
     * Format: devmod/snapshots/{areaId}/{snapshotId}.nbt
     */
    @Nonnull
    public String getFilePath() {
        return "devmod/snapshots/" + areaId.toString() + "/" + id.toString() + ".nbt";
    }

    /**
     * Gets the directory path for this area's snapshots.
     */
    @Nonnull
    public String getDirectoryPath() {
        return "devmod/snapshots/" + areaId.toString();
    }

    /**
     * Creates a copy with updated file size.
     * Used after writing the file to update the metadata.
     */
    @Nonnull
    public AreaSnapshot withFileSize(long newSize) {
        return new AreaSnapshot(
            id, areaId, areaName, description, creatorId, creatorName,
            createdAt, blockCount, newSize, dimensions, center,
            shape, dimensionKey, customShapeNbt
        );
    }

    /**
     * Creates a copy with updated block count.
     */
    @Nonnull
    public AreaSnapshot withBlockCount(int newCount) {
        return new AreaSnapshot(
            id, areaId, areaName, description, creatorId, creatorName,
            createdAt, newCount, fileSizeBytes, dimensions, center,
            shape, dimensionKey, customShapeNbt
        );
    }

    /**
     * Formats the file size as a human-readable string.
     */
    @Nonnull
    public String getFormattedFileSize() {
        if (fileSizeBytes < 1024) {
            return fileSizeBytes + " B";
        } else if (fileSizeBytes < 1024 * 1024) {
            return Objects.requireNonNull(String.format("%.1f KB", fileSizeBytes / 1024.0));
        } else {
            return Objects.requireNonNull(String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024.0)));
        }
    }

    /**
     * Gets the age of this snapshot in milliseconds.
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - createdAt;
    }
}
