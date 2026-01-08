package com.devmod.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable data structure representing a custom portal's state.
 * Includes color, linking information, dimension, position, and rune enhancement.
 */
public record PortalData(
    @Nonnull UUID id,
    @Nonnull PortalColor color,
    @Nullable UUID linkedId,
    @Nullable ResourceLocation dim,
    @Nullable BlockPos pos,
    @Nullable RuneType runeType,
    int frameBlockCount,
    @Nullable UUID creatorId
) {
    private static final String TAG_ID = "id";
    private static final String TAG_COLOR = "color";
    private static final String TAG_LINKED_ID = "linked_id";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS_X = "pos_x";
    private static final String TAG_POS_Y = "pos_y";
    private static final String TAG_POS_Z = "pos_z";
    private static final String TAG_RUNE = "rune";
    private static final String TAG_FRAME_COUNT = "frame_count";
    private static final String TAG_CREATOR_ID = "creator_id";

    /**
     * Creates a new unlinked portal with the given color.
     */
    public static PortalData create(@Nonnull PortalColor color) {
        return new PortalData(
            UUID.randomUUID(),
            Objects.requireNonNull(color, "color"),
            null, null, null, null, 0, null
        );
    }

    /**
     * Creates a new portal with full configuration.
     */
    public static PortalData create(
        @Nonnull PortalColor color,
        @Nullable ResourceLocation dimension,
        @Nullable BlockPos position,
        int frameBlockCount
    ) {
        return new PortalData(
            UUID.randomUUID(),
            Objects.requireNonNull(color, "color"),
            null, dimension, position, null, frameBlockCount, null
        );
    }

    /**
     * Creates a new unlinked portal with the given color and creator.
     */
    public static PortalData createWithCreator(@Nonnull PortalColor color, @Nullable UUID creator) {
        return new PortalData(
            UUID.randomUUID(),
            Objects.requireNonNull(color, "color"),
            null, null, null, null, 0, creator
        );
    }

    /**
     * Returns true if this portal is linked to another portal.
     */
    public boolean isLinked() {
        return linkedId != null;
    }

    /**
     * Returns the linked portal ID as Optional.
     */
    public Optional<UUID> linkedPortalId() {
        return Optional.ofNullable(linkedId);
    }

    /**
     * Returns the dimension as Optional.
     */
    public Optional<ResourceLocation> dimension() {
        return Optional.ofNullable(dim);
    }

    /**
     * Returns the position as Optional.
     */
    public Optional<BlockPos> position() {
        return Optional.ofNullable(pos);
    }

    /**
     * Returns the rune enhancement as Optional.
     */
    public Optional<RuneType> rune() {
        return Optional.ofNullable(runeType);
    }

    /**
     * Creates a new PortalData linked to the given portal ID.
     */
    public PortalData linkTo(@Nonnull UUID targetId) {
        return new PortalData(id, color, Objects.requireNonNull(targetId), dim, pos, runeType, frameBlockCount, creatorId);
    }

    /**
     * Creates a new unlinked PortalData.
     */
    public PortalData unlink() {
        return new PortalData(id, color, null, dim, pos, runeType, frameBlockCount, creatorId);
    }

    /**
     * Creates a new PortalData with the given dimension.
     */
    public PortalData withDimension(@Nullable ResourceLocation dimension) {
        return new PortalData(id, color, linkedId, dimension, pos, runeType, frameBlockCount, creatorId);
    }

    /**
     * Creates a new PortalData with the given position.
     */
    public PortalData withPosition(@Nullable BlockPos position) {
        return new PortalData(id, color, linkedId, dim, position, runeType, frameBlockCount, creatorId);
    }

    /**
     * Creates a new PortalData with the given rune.
     */
    public PortalData withRune(@Nullable RuneType rune) {
        return new PortalData(id, color, linkedId, dim, pos, rune, frameBlockCount, creatorId);
    }

    /**
     * Creates a new PortalData with the given frame block count.
     */
    public PortalData withFrameCount(int count) {
        return new PortalData(id, color, linkedId, dim, pos, runeType, count, creatorId);
    }

    /**
     * Creates a new PortalData with the given creator.
     */
    public PortalData withCreator(@Nullable UUID creator) {
        return new PortalData(id, color, linkedId, dim, pos, runeType, frameBlockCount, creator);
    }

    /**
     * Returns the creator as Optional.
     */
    public Optional<UUID> creator() {
        return Optional.ofNullable(creatorId);
    }

    /**
     * Returns true if this portal has a creator set.
     */
    public boolean hasCreator() {
        return creatorId != null;
    }

    /**
     * Returns true if this portal is owned by the given player UUID.
     */
    public boolean isOwnedBy(@Nonnull UUID playerId) {
        return creatorId != null && creatorId.equals(playerId);
    }

    /**
     * Returns true if this portal is in a different dimension than the other.
     */
    public boolean isInterDimensional(@Nonnull PortalData other) {
        if (dim == null || other.dim == null) {
            return false;
        }
        return !dim.equals(other.dim);
    }

    /**
     * Serializes this portal data to NBT.
     */
    @Nonnull
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putInt(TAG_COLOR, color.getIndex());

        if (linkedId != null) {
            tag.putUUID(TAG_LINKED_ID, linkedId);
        }

        if (dim != null) {
            tag.putString(TAG_DIMENSION, dim.toString());
        }

        if (pos != null) {
            tag.putInt(TAG_POS_X, pos.getX());
            tag.putInt(TAG_POS_Y, pos.getY());
            tag.putInt(TAG_POS_Z, pos.getZ());
        }

        if (runeType != null) {
            tag.putInt(TAG_RUNE, runeType.getIndex());
        }

        tag.putInt(TAG_FRAME_COUNT, frameBlockCount);

        if (creatorId != null) {
            tag.putUUID(TAG_CREATOR_ID, creatorId);
        }

        return tag;
    }

    /**
     * Deserializes portal data from NBT.
     */
    @Nonnull
    public static PortalData load(@Nonnull CompoundTag tag) {
        UUID id = tag.getUUID(TAG_ID);
        PortalColor color = PortalColor.byIndex(tag.getInt(TAG_COLOR));

        UUID linkedId = tag.contains(TAG_LINKED_ID) ? tag.getUUID(TAG_LINKED_ID) : null;

        ResourceLocation dimension = null;
        if (tag.contains(TAG_DIMENSION)) {
            dimension = ResourceLocation.parse(tag.getString(TAG_DIMENSION));
        }

        BlockPos position = null;
        if (tag.contains(TAG_POS_X)) {
            position = new BlockPos(
                tag.getInt(TAG_POS_X),
                tag.getInt(TAG_POS_Y),
                tag.getInt(TAG_POS_Z)
            );
        }

        RuneType rune = null;
        if (tag.contains(TAG_RUNE)) {
            rune = RuneType.byIndex(tag.getInt(TAG_RUNE));
        }

        int frameCount = tag.getInt(TAG_FRAME_COUNT);

        UUID creator = tag.contains(TAG_CREATOR_ID) ? tag.getUUID(TAG_CREATOR_ID) : null;

        return new PortalData(id, color, linkedId, dimension, position, rune, frameCount, creator);
    }
}
