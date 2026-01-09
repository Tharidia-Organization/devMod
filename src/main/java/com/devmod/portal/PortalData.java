package com.devmod.portal;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable data structure representing a custom portal's state.
 * Includes color, linking information, dimension, position, and rune enhancement.
 *
 * <p>Supports three teleport modes:
 * <ul>
 *   <li><b>Linked portals:</b> UUID-to-UUID bidirectional linking (linkedId)</li>
 *   <li><b>Fixed destination:</b> Hardcoded unidirectional teleport (fixedDestDim + fixedDestPos)</li>
 *   <li><b>Named network:</b> Teleport to random portal in same network (networkName)</li>
 * </ul>
 */
public record PortalData(
    @Nonnull UUID id,
    @Nonnull PortalColor color,
    @Nullable UUID linkedId,
    @Nullable ResourceLocation dim,
    @Nullable BlockPos pos,
    @Nullable RuneType runeType,
    int frameBlockCount,
    @Nullable UUID creatorId,
    @Nullable ResourceLocation fixedDestDim,
    @Nullable BlockPos fixedDestPos,
    @Nullable String networkName
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
    private static final String TAG_FIXED_DEST_DIM = "fixed_dest_dim";
    private static final String TAG_FIXED_DEST_X = "fixed_dest_x";
    private static final String TAG_FIXED_DEST_Y = "fixed_dest_y";
    private static final String TAG_FIXED_DEST_Z = "fixed_dest_z";
    private static final String TAG_NETWORK = "network";

    /**
     * Creates a new unlinked portal with the given color.
     */
    public static PortalData create(@Nonnull PortalColor color) {
        return new PortalData(
            UUID.randomUUID(),
            Objects.requireNonNull(color, "color"),
            null, null, null, null, 0, null, null, null, null
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
            null, dimension, position, null, frameBlockCount, null, null, null, null
        );
    }

    /**
     * Creates a new unlinked portal with the given color and creator.
     */
    public static PortalData createWithCreator(@Nonnull PortalColor color, @Nullable UUID creator) {
        return new PortalData(
            UUID.randomUUID(),
            Objects.requireNonNull(color, "color"),
            null, null, null, null, 0, creator, null, null, null
        );
    }

    /**
     * Creates a portal with a fixed destination (unidirectional teleport).
     * Used for Nexus zone portals that teleport to a hardcoded position.
     *
     * @param color The portal color
     * @param sourceDim The dimension where this portal is located
     * @param sourcePos The position of this portal
     * @param destDim The destination dimension
     * @param destPos The destination position
     * @return A new PortalData with fixed destination
     */
    public static PortalData createWithFixedDestination(
        @Nonnull PortalColor color,
        @Nonnull ResourceLocation sourceDim,
        @Nonnull BlockPos sourcePos,
        @Nonnull ResourceLocation destDim,
        @Nonnull BlockPos destPos
    ) {
        return new PortalData(
            UUID.randomUUID(),
            Objects.requireNonNull(color, "color"),
            null,
            Objects.requireNonNull(sourceDim, "sourceDim"),
            Objects.requireNonNull(sourcePos, "sourcePos"),
            null, 0, null,
            Objects.requireNonNull(destDim, "destDim"),
            Objects.requireNonNull(destPos, "destPos"),
            null
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
     * Note: Linking clears any fixed destination and network.
     */
    public PortalData linkTo(@Nonnull UUID targetId) {
        return new PortalData(id, color, Objects.requireNonNull(targetId), dim, pos, runeType, frameBlockCount, creatorId, null, null, null);
    }

    /**
     * Creates a new unlinked PortalData.
     */
    public PortalData unlink() {
        return new PortalData(id, color, null, dim, pos, runeType, frameBlockCount, creatorId, fixedDestDim, fixedDestPos, networkName);
    }

    /**
     * Creates a new PortalData with the given dimension.
     */
    public PortalData withDimension(@Nullable ResourceLocation dimension) {
        return new PortalData(id, color, linkedId, dimension, pos, runeType, frameBlockCount, creatorId, fixedDestDim, fixedDestPos, networkName);
    }

    /**
     * Creates a new PortalData with the given position.
     */
    public PortalData withPosition(@Nullable BlockPos position) {
        return new PortalData(id, color, linkedId, dim, position, runeType, frameBlockCount, creatorId, fixedDestDim, fixedDestPos, networkName);
    }

    /**
     * Creates a new PortalData with the given rune.
     */
    public PortalData withRune(@Nullable RuneType rune) {
        return new PortalData(id, color, linkedId, dim, pos, rune, frameBlockCount, creatorId, fixedDestDim, fixedDestPos, networkName);
    }

    /**
     * Creates a new PortalData with the given frame block count.
     */
    public PortalData withFrameCount(int count) {
        return new PortalData(id, color, linkedId, dim, pos, runeType, count, creatorId, fixedDestDim, fixedDestPos, networkName);
    }

    /**
     * Creates a new PortalData with the given creator.
     */
    public PortalData withCreator(@Nullable UUID creator) {
        return new PortalData(id, color, linkedId, dim, pos, runeType, frameBlockCount, creator, fixedDestDim, fixedDestPos, networkName);
    }

    /**
     * Creates a new PortalData with the given fixed destination.
     * Note: Setting a fixed destination clears any linked portal and network.
     */
    public PortalData withFixedDestination(@Nonnull ResourceLocation destDim, @Nonnull BlockPos destPos) {
        return new PortalData(id, color, null, dim, pos, runeType, frameBlockCount, creatorId,
            Objects.requireNonNull(destDim), Objects.requireNonNull(destPos), null);
    }

    /**
     * Creates a new PortalData without a fixed destination.
     */
    public PortalData clearFixedDestination() {
        return new PortalData(id, color, linkedId, dim, pos, runeType, frameBlockCount, creatorId, null, null, networkName);
    }

    /**
     * Creates a new PortalData with the given network name.
     * Note: Setting a network clears any linked portal and fixed destination.
     */
    public PortalData withNetwork(@Nullable String network) {
        return new PortalData(id, color, null, dim, pos, runeType, frameBlockCount, creatorId, null, null, network);
    }

    /**
     * Returns the network name as Optional.
     */
    public Optional<String> network() {
        return Optional.ofNullable(networkName);
    }

    /**
     * Returns true if this portal belongs to a named network.
     */
    public boolean hasNetwork() {
        return networkName != null && !networkName.isEmpty();
    }

    /**
     * Returns true if this portal is in the same network as another.
     */
    public boolean isInSameNetwork(@Nonnull PortalData other) {
        if (!hasNetwork() || !other.hasNetwork()) {
            return false;
        }
        String localNetwork = networkName;
        String otherNetwork = other.networkName;
        return localNetwork != null && localNetwork.equals(otherNetwork);
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
     * Returns true if this portal has a fixed destination (unidirectional teleport).
     */
    public boolean hasFixedDestination() {
        return fixedDestDim != null && fixedDestPos != null;
    }

    /**
     * Returns the fixed destination position as Optional.
     */
    public Optional<BlockPos> fixedDestination() {
        return Optional.ofNullable(fixedDestPos);
    }

    /**
     * Returns the fixed destination dimension as Optional.
     */
    public Optional<ResourceLocation> fixedDestinationDimension() {
        return Optional.ofNullable(fixedDestDim);
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

        if (fixedDestDim != null) {
            tag.putString(TAG_FIXED_DEST_DIM, fixedDestDim.toString());
        }

        if (fixedDestPos != null) {
            tag.putInt(TAG_FIXED_DEST_X, fixedDestPos.getX());
            tag.putInt(TAG_FIXED_DEST_Y, fixedDestPos.getY());
            tag.putInt(TAG_FIXED_DEST_Z, fixedDestPos.getZ());
        }

        if (networkName != null) {
            tag.putString(TAG_NETWORK, networkName);
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

        ResourceLocation fixedDestDim = null;
        if (tag.contains(TAG_FIXED_DEST_DIM)) {
            fixedDestDim = ResourceLocation.parse(tag.getString(TAG_FIXED_DEST_DIM));
        }

        BlockPos fixedDestPos = null;
        if (tag.contains(TAG_FIXED_DEST_X)) {
            fixedDestPos = new BlockPos(
                tag.getInt(TAG_FIXED_DEST_X),
                tag.getInt(TAG_FIXED_DEST_Y),
                tag.getInt(TAG_FIXED_DEST_Z)
            );
        }

        String networkName = tag.contains(TAG_NETWORK) ? tag.getString(TAG_NETWORK) : null;

        return new PortalData(id, color, linkedId, dimension, position, rune, frameCount, creator, fixedDestDim, fixedDestPos, networkName);
    }
}
