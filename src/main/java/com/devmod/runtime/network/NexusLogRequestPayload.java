package com.devmod.runtime.network;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record NexusLogRequestPayload(
    NexusLogType logType
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<NexusLogRequestPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "nexus_log_request"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NexusLogRequestPayload> STREAM_CODEC = StreamCodec.of(
        NexusLogRequestPayload::encode,
        NexusLogRequestPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, NexusLogRequestPayload payload) {
        buf.writeVarInt(Objects.requireNonNull(payload.logType(), "logType").ordinal());
    }

    private static NexusLogRequestPayload decode(RegistryFriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        return new NexusLogRequestPayload(NexusLogType.fromOrdinal(ordinal));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return varIntSize(logType.ordinal());
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
