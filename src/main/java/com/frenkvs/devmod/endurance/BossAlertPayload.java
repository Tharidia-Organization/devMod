package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from server to client to alert about incoming boss wave.
 * Triggers visual + audio alert 3 seconds before boss spawn.
 */
public record BossAlertPayload(long alertDurationMs, String bossType) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "boss_alert");
    public static final Type<BossAlertPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, BossAlertPayload> STREAM_CODEC =
        StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeLong, FriendlyByteBuf::readLong),
            BossAlertPayload::alertDurationMs,
            StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf),
            BossAlertPayload::bossType,
            BossAlertPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
