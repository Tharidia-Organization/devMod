package com.frenkvs.devmod.abilities;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Payload for syncing stamina data from server to client.
 */
public record StaminaSyncPayload(float currentStamina, float maxStamina) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StaminaSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "stamina_sync")));

    public static final StreamCodec<FriendlyByteBuf, StaminaSyncPayload> STREAM_CODEC =
        StreamCodec.of(StaminaSyncPayload::write, StaminaSyncPayload::read);

    private static StaminaSyncPayload read(FriendlyByteBuf buf) {
        return new StaminaSyncPayload(buf.readFloat(), buf.readFloat());
    }

    private static void write(FriendlyByteBuf buf, StaminaSyncPayload payload) {
        buf.writeFloat(payload.currentStamina);
        buf.writeFloat(payload.maxStamina);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
