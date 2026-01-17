package com.devmod.area.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Server -> Client: Snapshot list for an area.
 * Contains list of snapshot summaries for UI display.
 */
public record SnapshotListPayload(
    UUID areaId,
    List<SnapshotSummary> snapshots
) implements CustomPacketPayload {

    /** Maximum snapshots to sync at once (matches AreaSnapshotRegistry.MAX_SNAPSHOTS_PER_AREA) */
    public static final int MAX_SNAPSHOTS = 10;

    public static final Type<SnapshotListPayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "snapshot_list")));

    /**
     * Summary of a snapshot for UI display.
     *
     * @param id           Snapshot UUID
     * @param description  User description
     * @param creatorName  Name of player who created it
     * @param createdAt    Creation timestamp
     * @param blockCount   Number of blocks captured
     * @param fileSizeBytes File size on disk
     */
    public record SnapshotSummary(
        UUID id,
        String description,
        @Nullable String creatorName,
        long createdAt,
        int blockCount,
        long fileSizeBytes
    ) {
        public static final StreamCodec<FriendlyByteBuf, SnapshotSummary> STREAM_CODEC =
            StreamCodec.composite(
                Objects.requireNonNull(UUIDUtil.STREAM_CODEC), SnapshotSummary::id,
                Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), SnapshotSummary::description,
                Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), s -> s.creatorName != null ? s.creatorName : "",
                Objects.requireNonNull(ByteBufCodecs.VAR_LONG), SnapshotSummary::createdAt,
                Objects.requireNonNull(ByteBufCodecs.VAR_INT), SnapshotSummary::blockCount,
                Objects.requireNonNull(ByteBufCodecs.VAR_LONG), SnapshotSummary::fileSizeBytes,
                SnapshotSummary::create
            );

        /** Factory method to handle nullables and Long/Integer conversion */
        private static SnapshotSummary create(UUID id, String description, String creatorName,
                                               Long createdAt, Integer blockCount, Long fileSizeBytes) {
            return new SnapshotSummary(
                id,
                description,
                creatorName.isEmpty() ? null : creatorName,
                createdAt != null ? createdAt.longValue() : 0L,
                blockCount != null ? blockCount.intValue() : 0,
                fileSizeBytes != null ? fileSizeBytes.longValue() : 0L
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
    }

    public static final StreamCodec<FriendlyByteBuf, SnapshotListPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), SnapshotListPayload::areaId,
            Objects.requireNonNull(SnapshotSummary.STREAM_CODEC.apply(
                Objects.requireNonNull(ByteBufCodecs.list(MAX_SNAPSHOTS)))),
            SnapshotListPayload::snapshots,
            SnapshotListPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
