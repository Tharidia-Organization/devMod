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
import com.devmod.hologram.runtime.HologramNaming;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server to Client payload for syncing hologram updates to nearby clients.
 * Sent after a hologram is modified to update other players' views.
 */
public record HologramSyncPayload(
    @Nonnull UUID hologramId,
    int revision,
    @Nonnull List<String> lines
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram_sync"));
    public static final Type<HologramSyncPayload> TYPE = new Type<>(ID);

    /** Maximum lines per hologram to prevent DoS via unbounded allocation */
    private static final int MAX_LINES = HologramNaming.MAX_LINES;
    /** Maximum characters per line */
    private static final int MAX_LINE_LENGTH = HologramNaming.MAX_LINE_LENGTH;

    public static final StreamCodec<FriendlyByteBuf, HologramSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HologramSyncPayload decode(FriendlyByteBuf buf) {
            UUID hologramId = buf.readUUID();
            int revision = buf.readVarInt();
            int lineCount = buf.readVarInt();
            if (lineCount < 0 || lineCount > MAX_LINES) {
                throw new IllegalArgumentException("Invalid hologram line count: " + lineCount);
            }
            List<String> lines = new ArrayList<>(lineCount);
            for (int i = 0; i < lineCount; i++) {
                lines.add(buf.readUtf(MAX_LINE_LENGTH));
            }
            return new HologramSyncPayload(hologramId, revision, lines);
        }

        @Override
        public void encode(FriendlyByteBuf buf, HologramSyncPayload payload) {
            buf.writeUUID(payload.hologramId);
            buf.writeVarInt(payload.revision);
            int lineCount = Math.min(payload.lines.size(), MAX_LINES);
            buf.writeVarInt(lineCount);
            int written = 0;
            for (String line : payload.lines) {
                if (written >= lineCount) {
                    break;
                }
                buf.writeUtf(HologramNaming.truncateLine(line), MAX_LINE_LENGTH);
                written++;
            }
        }
    };

    public HologramSyncPayload {
        Objects.requireNonNull(hologramId, "hologramId");
        Objects.requireNonNull(lines, "lines");
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
        }
        List<String> normalized = new ArrayList<>(lines.size());
        for (String line : lines) {
            normalized.add(HologramNaming.truncateLine(line));
        }
        lines = List.copyOf(normalized);
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = 16; // UUID
        size += PayloadSizeUtil.varIntSize(revision);
        size += PayloadSizeUtil.varIntSize(lines.size());
        for (String line : lines) {
            size += PayloadSizeUtil.estimatedUtfSize(line);
        }
        return PayloadSizeUtil.clampToInt(size);
    }
}
