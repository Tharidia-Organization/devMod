package com.devmod.network;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static com.devmod.DevMod.MODID;

/**
 * Client-to-server payload used to acknowledge TesterModality value on login.
 * Server validates this value and denies access on mismatch.
 */
public record TesterModalityAckPayload(boolean clientEnabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TesterModalityAckPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(MODID, ChannelId.TESTER_MODALITY_ACK.asString())));

    public static final StreamCodec<RegistryFriendlyByteBuf, TesterModalityAckPayload> STREAM_CODEC =
        StreamCodec.of(TesterModalityAckPayload::encode, TesterModalityAckPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TesterModalityAckPayload payload) {
        buf.writeBoolean(payload.clientEnabled);
    }

    private static TesterModalityAckPayload decode(RegistryFriendlyByteBuf buf) {
        return new TesterModalityAckPayload(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
