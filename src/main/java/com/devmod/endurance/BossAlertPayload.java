package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

public record BossAlertPayload(long alertDurationMs, String bossType)
        implements CustomPacketPayload, PayloadValidation.SizedPayload {

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

    @Override
    public int estimatedSize() {
        int size = varLongSize(alertDurationMs);
        size += estimatedUtfSize(bossType);
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

    private static int varLongSize(long value) {
        long v = value;
        int size = 1;
        while ((v & ~0x7FL) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
