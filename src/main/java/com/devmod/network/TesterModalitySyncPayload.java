package com.devmod.network;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static com.devmod.DevMod.MODID;

/**
 * Server-to-client payload that syncs the TesterModality config value.
 * Sent on player login, before any other sync payloads.
 * The client handler disconnects on mismatch.
 */
public record TesterModalitySyncPayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TesterModalitySyncPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(MODID, ChannelId.TESTER_MODALITY_SYNC.asString())));

    public static final StreamCodec<RegistryFriendlyByteBuf, TesterModalitySyncPayload> STREAM_CODEC =
        StreamCodec.of(TesterModalitySyncPayload::encode, TesterModalitySyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TesterModalitySyncPayload payload) {
        buf.writeBoolean(payload.enabled);
    }

    private static TesterModalitySyncPayload decode(RegistryFriendlyByteBuf buf) {
        return new TesterModalitySyncPayload(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
