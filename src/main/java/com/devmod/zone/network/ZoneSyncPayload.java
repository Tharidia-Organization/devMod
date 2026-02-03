package com.devmod.zone.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.zone.data.ZoneBounds;

/**
 * Server -> Client payload to sync zone data.
 * Used on dimension entry or when zones are modified.
 * Sends lightweight summaries, not full definitions.
 */
public record ZoneSyncPayload(
    List<ZoneSummary> zones,
    long registryVersion
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "zone_sync")
    );
    public static final Type<ZoneSyncPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneSyncPayload> STREAM_CODEC =
        StreamCodec.of(ZoneSyncPayload::encode, ZoneSyncPayload::decode);

    /**
     * Lightweight zone summary for client-side rendering and tracking.
     */
    public record ZoneSummary(
        @Nonnull UUID id,
        @Nonnull String zoneId,
        @Nonnull String displayName,
        @Nonnull ZoneBounds bounds,
        int color,
        int priority,
        boolean hasTeleport
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ZoneSummary> STREAM_CODEC =
            StreamCodec.of(ZoneSummary::encode, ZoneSummary::decode);

        private static void encode(RegistryFriendlyByteBuf buf, ZoneSummary summary) {
            buf.writeUUID(summary.id);
            buf.writeUtf(summary.zoneId, 32);
            buf.writeUtf(summary.displayName, 64);
            ZoneBounds.STREAM_CODEC.encode(buf, summary.bounds);
            buf.writeInt(summary.color);
            buf.writeVarInt(summary.priority);
            buf.writeBoolean(summary.hasTeleport);
        }

        private static ZoneSummary decode(RegistryFriendlyByteBuf buf) {
            return new ZoneSummary(
                Objects.requireNonNull(buf.readUUID()),
                Objects.requireNonNull(buf.readUtf(32)),
                Objects.requireNonNull(buf.readUtf(64)),
                Objects.requireNonNull(ZoneBounds.STREAM_CODEC.decode(buf)),
                buf.readInt(),
                buf.readVarInt(),
                buf.readBoolean()
            );
        }
    }

    private static void encode(RegistryFriendlyByteBuf buf, ZoneSyncPayload payload) {
        buf.writeVarInt(payload.zones.size());
        for (ZoneSummary summary : payload.zones) {
            ZoneSummary.STREAM_CODEC.encode(buf, Objects.requireNonNull(summary));
        }
        buf.writeLong(payload.registryVersion);
    }

    /** Maximum zones to sync per payload to prevent DoS via unbounded allocation */
    private static final int MAX_ZONES = 500;

    private static ZoneSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_ZONES);
        if (count < 0) count = 0; // Guard against negative values
        List<ZoneSummary> zones = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            zones.add(ZoneSummary.STREAM_CODEC.decode(buf));
        }
        long registryVersion = buf.readLong();
        return new ZoneSyncPayload(zones, registryVersion);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = PayloadSizeUtil.varIntSize(zones.size()) + 8;
        for (ZoneSummary summary : zones) {
            size += 16; // UUID
            size += PayloadSizeUtil.estimatedUtfSize(summary.zoneId());
            size += PayloadSizeUtil.estimatedUtfSize(summary.displayName());
            size += 24; // ZoneBounds (6 ints)
            size += 4; // color
            size += PayloadSizeUtil.varIntSize(summary.priority());
            size += 1; // hasTeleport
        }
        return size;
    }

    /**
     * Creates an empty sync payload (for clearing client cache).
     */
    public static ZoneSyncPayload empty() {
        return new ZoneSyncPayload(List.of(), 0);
    }
}
