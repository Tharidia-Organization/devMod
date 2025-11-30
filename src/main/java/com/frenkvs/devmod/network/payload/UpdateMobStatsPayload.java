package com.frenkvs.devmod.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateMobStatsPayload(
        boolean isGlobal, // <--- NUOVO CAMPO
        int entityId,
        double followRange,
        double damage,
        double maxHealth,
        double armor,
        double attackRange
) implements CustomPacketPayload {

    public static final Type<UpdateMobStatsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "update_stats"));

    public static final StreamCodec<ByteBuf, UpdateMobStatsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, val) -> {
                ByteBufCodecs.BOOL.encode(buffer, val.isGlobal()); // Scriviamo il booleano
                ByteBufCodecs.VAR_INT.encode(buffer, val.entityId());
                ByteBufCodecs.DOUBLE.encode(buffer, val.followRange());
                ByteBufCodecs.DOUBLE.encode(buffer, val.damage());
                ByteBufCodecs.DOUBLE.encode(buffer, val.maxHealth());
                ByteBufCodecs.DOUBLE.encode(buffer, val.armor());
                ByteBufCodecs.DOUBLE.encode(buffer, val.attackRange());
            },
            (buffer) -> new UpdateMobStatsPayload(
                    ByteBufCodecs.BOOL.decode(buffer), // Leggiamo il booleano
                    ByteBufCodecs.VAR_INT.decode(buffer),
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
}