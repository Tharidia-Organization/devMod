package com.devmod.runtime.network;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record NexusUiPayload(
    UiAction action
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public enum UiAction {
        TESTING_HUB,
        TELEMETRY_DASHBOARD,
        LOG_VIEWER
    }

    public static final Type<NexusUiPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "nexus_ui"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NexusUiPayload> STREAM_CODEC = StreamCodec.of(
        NexusUiPayload::encode,
        NexusUiPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, NexusUiPayload payload) {
        buf.writeVarInt(Objects.requireNonNull(payload.action(), "action").ordinal());
    }

    private static NexusUiPayload decode(RegistryFriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        UiAction[] values = UiAction.values();
        UiAction action = values[Math.min(Math.max(ordinal, 0), values.length - 1)];
        return new NexusUiPayload(action);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return varIntSize(action.ordinal());
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
