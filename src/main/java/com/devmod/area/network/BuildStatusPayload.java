package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Server -> Client payload to notify about build status changes.
 * Used for UI updates when builds start, pause, resume, or complete.
 */
public record BuildStatusPayload(
    UUID areaId,
    Status status,
    int progressPercent
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /**
     * Build status types.
     */
    public enum Status implements StringRepresentable {
        STARTED("started"),
        IN_PROGRESS("in_progress"),
        PAUSED("paused"),
        RESUMED("resumed"),
        COMPLETED("completed"),
        FAILED("failed"),
        QUEUED("queued");

        private final String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        @Nonnull
        public String getSerializedName() {
            return Objects.requireNonNull(name);
        }

        public static Status byOrdinal(int ordinal) {
            Status[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FAILED;
        }
    }

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "build_status")
    );
    public static final Type<BuildStatusPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, BuildStatusPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), BuildStatusPayload::areaId,
            Objects.requireNonNull(ByteBufCodecs.VAR_INT.map(
                (Integer i) -> Status.byOrdinal(i.intValue()),
                Enum::ordinal
            )), BuildStatusPayload::status,
            Objects.requireNonNull(ByteBufCodecs.VAR_INT), BuildStatusPayload::progressPercent,
            (UUID uuid, Status status, Integer progress) -> new BuildStatusPayload(uuid, status, progress.intValue())
        );

    public BuildStatusPayload {
        Objects.requireNonNull(areaId, "areaId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (progressPercent < 0) progressPercent = 0;
        if (progressPercent > 100) progressPercent = 100;
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        return 16 + 1 + 1; // UUID + status ordinal (varint) + progress (varint)
    }

    // Factory methods for common statuses

    public static BuildStatusPayload started(UUID areaId) {
        return new BuildStatusPayload(areaId, Status.STARTED, 0);
    }

    public static BuildStatusPayload progress(UUID areaId, int percent) {
        return new BuildStatusPayload(areaId, Status.IN_PROGRESS, percent);
    }

    public static BuildStatusPayload paused(UUID areaId, int percent) {
        return new BuildStatusPayload(areaId, Status.PAUSED, percent);
    }

    public static BuildStatusPayload resumed(UUID areaId, int percent) {
        return new BuildStatusPayload(areaId, Status.RESUMED, percent);
    }

    public static BuildStatusPayload completed(UUID areaId) {
        return new BuildStatusPayload(areaId, Status.COMPLETED, 100);
    }

    public static BuildStatusPayload failed(UUID areaId) {
        return new BuildStatusPayload(areaId, Status.FAILED, 0);
    }

    public static BuildStatusPayload queued(UUID areaId, int queuePosition) {
        return new BuildStatusPayload(areaId, Status.QUEUED, queuePosition);
    }
}
