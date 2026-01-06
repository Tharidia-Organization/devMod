package com.devmod.runtime.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record NexusLogSnapshotPayload(
    NexusLogType logType,
    List<String> lines,
    boolean truncated,
    String sourceLabel
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final int MAX_LINES = 180;
    private static final int MAX_LINE_LENGTH = 360;
    private static final int MAX_LABEL_LENGTH = 64;

    /**
     * Get lines as non-null list, capped at MAX_LINES.
     * Used by both encode() and estimatedSize() for consistent null handling.
     */
    private List<String> safeLines() {
        if (lines == null) {
            return List.of();
        }
        return lines.size() <= MAX_LINES ? lines : lines.subList(0, MAX_LINES);
    }

    /**
     * Get label as non-null string.
     * Used by both encode() and estimatedSize() for consistent null handling.
     */
    private String safeLabel() {
        return sourceLabel == null ? "" : sourceLabel;
    }

    public static final Type<NexusLogSnapshotPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "nexus_log_snapshot"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NexusLogSnapshotPayload> STREAM_CODEC = StreamCodec.of(
        NexusLogSnapshotPayload::encode,
        NexusLogSnapshotPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, NexusLogSnapshotPayload payload) {
        buf.writeVarInt(Objects.requireNonNull(payload.logType(), "logType").ordinal());
        List<String> lines = payload.safeLines();
        buf.writeVarInt(lines.size());
        for (String line : lines) {
            // Double wrap needed: requireNonNullElse returns @PolyNull, writeUtf needs @Nonnull
            String safeLine = Objects.requireNonNull(Objects.requireNonNullElse(line, ""), "line");
            buf.writeUtf(safeLine, MAX_LINE_LENGTH);
        }
        buf.writeBoolean(payload.truncated());
        buf.writeUtf(Objects.requireNonNull(payload.safeLabel(), "label"), MAX_LABEL_LENGTH);
    }

    private static NexusLogSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        NexusLogType type = NexusLogType.fromOrdinal(ordinal);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_LINES) {
            count = 0;
        }
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(buf.readUtf(MAX_LINE_LENGTH));
        }
        boolean truncated = buf.readBoolean();
        String sourceLabel = buf.readUtf(MAX_LABEL_LENGTH);
        return new NexusLogSnapshotPayload(type, lines, truncated, sourceLabel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += varIntSize(logType.ordinal());
        List<String> safeLinesList = safeLines();
        size += varIntSize(safeLinesList.size());
        for (String line : safeLinesList) {
            size += estimatedUtfSize(line);
        }
        size += 1; // truncated boolean
        size += estimatedUtfSize(safeLabel());
        return size;
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
