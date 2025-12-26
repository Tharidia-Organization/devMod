package com.devmod.endurance;

import java.util.Objects;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

public record BossAlertPayload(long alertDurationMs, String bossType) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "boss_alert"));
    public static final Type<BossAlertPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, BossAlertPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(ByteBufCodecs.VAR_LONG),
            BossAlertPayload::alertDurationMs,
            Objects.requireNonNull(StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf)),
            BossAlertPayload::bossType,
            (duration, boss) -> new BossAlertPayload(duration, Objects.requireNonNull(boss))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
