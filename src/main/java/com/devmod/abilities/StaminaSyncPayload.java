package com.devmod.abilities;

import java.util.Objects;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

public record StaminaSyncPayload(float currentStamina, float maxStamina)
        implements CustomPacketPayload, PayloadValidation.SizedPayload {

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

    @Override
    public int estimatedSize() {
        return 8;
    }
}
