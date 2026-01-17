package com.devmod.hologram.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Server to Client payload for syncing hologram updates to nearby clients.
 * Sent after a hologram is modified to update other players' views.
 */
public record HologramSyncPayload(
    @Nonnull UUID hologramId,
    int revision,
    @Nonnull List<String> lines
) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram_sync"));
    public static final Type<HologramSyncPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, HologramSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HologramSyncPayload decode(FriendlyByteBuf buf) {
            UUID hologramId = buf.readUUID();
            int revision = buf.readVarInt();
            int lineCount = buf.readVarInt();
            List<String> lines = new ArrayList<>(lineCount);
            for (int i = 0; i < lineCount; i++) {
                lines.add(buf.readUtf());
            }
            return new HologramSyncPayload(hologramId, revision, lines);
        }

        @Override
        public void encode(FriendlyByteBuf buf, HologramSyncPayload payload) {
            buf.writeUUID(payload.hologramId);
            buf.writeVarInt(payload.revision);
            buf.writeVarInt(payload.lines.size());
            for (String line : payload.lines) {
                buf.writeUtf(line);
            }
        }
    };

    public HologramSyncPayload {
        Objects.requireNonNull(hologramId, "hologramId");
        Objects.requireNonNull(lines, "lines");
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
