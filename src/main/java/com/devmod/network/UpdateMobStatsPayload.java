package com.devmod.network;

import java.util.Objects;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import io.netty.buffer.ByteBuf;

public record UpdateMobStatsPayload(
        boolean isGlobal,
        int entityId,
        double followRange,
        double damage,
        double maxHealth,
        double armor,
        double attackRange,
        double speed,
        double knockbackResist
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<UpdateMobStatsPayload> TYPE = new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "update_stats")));

    public static final StreamCodec<ByteBuf, UpdateMobStatsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, val) -> {
                ByteBufCodecs.BOOL.encode(buffer, val.isGlobal());
                ByteBufCodecs.VAR_INT.encode(buffer, val.entityId());
                ByteBufCodecs.DOUBLE.encode(buffer, val.followRange());
                ByteBufCodecs.DOUBLE.encode(buffer, val.damage());
                ByteBufCodecs.DOUBLE.encode(buffer, val.maxHealth());
                ByteBufCodecs.DOUBLE.encode(buffer, val.armor());
                ByteBufCodecs.DOUBLE.encode(buffer, val.attackRange());
                ByteBufCodecs.DOUBLE.encode(buffer, val.speed());
                ByteBufCodecs.DOUBLE.encode(buffer, val.knockbackResist());
            },
            (buffer) -> new UpdateMobStatsPayload(
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        // 1 bool + 1 VarInt + 7 doubles = ~60 bytes
        return 60;
    }
}
