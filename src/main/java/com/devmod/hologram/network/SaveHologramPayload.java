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
import com.devmod.hologram.data.HologramOptions;
import com.devmod.hologram.data.HologramStyle;
import com.devmod.hologram.runtime.HologramNaming;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Client to Server payload for saving hologram changes.
 * Uses optimistic locking via expectedRevision.
 */
public record SaveHologramPayload(
    @Nonnull UUID hologramId,
    @Nonnull List<String> lines,
    @Nonnull HologramStyle style,
    @Nonnull HologramOptions options,
    int expectedRevision
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram_save"));
    public static final Type<SaveHologramPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SaveHologramPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SaveHologramPayload decode(FriendlyByteBuf buf) {
            UUID hologramId = buf.readUUID();
            int lineCount = buf.readVarInt();
            if (lineCount < 0 || lineCount > HologramNaming.MAX_LINES) {
                throw new IllegalArgumentException("Invalid hologram line count: " + lineCount);
            }
            List<String> lines = new ArrayList<>(lineCount);
            for (int i = 0; i < lineCount; i++) {
                lines.add(buf.readUtf(HologramNaming.MAX_LINE_LENGTH));
            }
            HologramStyle style = HologramStyle.STREAM_CODEC.decode(buf);
            HologramOptions options = HologramOptions.STREAM_CODEC.decode(buf);
            int expectedRevision = buf.readVarInt();
            return new SaveHologramPayload(hologramId, lines, style, options, expectedRevision);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SaveHologramPayload payload) {
            buf.writeUUID(payload.hologramId);
            int lineCount = Math.min(payload.lines.size(), HologramNaming.MAX_LINES);
            buf.writeVarInt(lineCount);
            int written = 0;
            for (String line : payload.lines) {
                if (written >= lineCount) {
                    break;
                }
                buf.writeUtf(HologramNaming.truncateLine(line), HologramNaming.MAX_LINE_LENGTH);
                written++;
            }
            HologramStyle.STREAM_CODEC.encode(buf, payload.style);
            HologramOptions.STREAM_CODEC.encode(buf, payload.options);
            buf.writeVarInt(payload.expectedRevision);
        }
    };

    public SaveHologramPayload {
        Objects.requireNonNull(hologramId, "hologramId");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(options, "options");
        if (lines.size() > HologramNaming.MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, HologramNaming.MAX_LINES));
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
        size += PayloadSizeUtil.varIntSize(lines.size());
        for (String line : lines) {
            size += PayloadSizeUtil.estimatedUtfSize(line);
        }
        size += HologramPayloadSizing.estimateStyleSize(style);
        size += HologramPayloadSizing.estimateOptionsSize(options);
        size += PayloadSizeUtil.varIntSize(expectedRevision);
        return PayloadSizeUtil.clampToInt(size);
    }
}
