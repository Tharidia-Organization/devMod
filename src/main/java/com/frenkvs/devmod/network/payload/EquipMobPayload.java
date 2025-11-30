package com.frenkvs.devmod.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Questo "pacchetto" trasporta i nomi degli oggetti che vuoi mettere al mob
public record EquipMobPayload(
        int entityId,       // ID del mostro
        String mainHand,    // Mano Destra
        String offHand,     // Mano Sinistra
        String head,        // Testa
        String chest,       // Petto
        String legs,        // Gambe
        String feet         // Piedi
) implements CustomPacketPayload {

    // Identificativo unico per la rete
    public static final Type<EquipMobPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "equip_mob"));

    // Codec manuale (necessario perché abbiamo 7 variabili, e il limite automatico è 6)
    public static final StreamCodec<ByteBuf, EquipMobPayload> STREAM_CODEC = StreamCodec.of(
            // 1. SCRITTURA (Encoder)
            (buffer, value) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, value.entityId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.mainHand());
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.offHand());
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.head());
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.chest());
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.legs());
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.feet());
            },
            // 2. LETTURA (Decoder)
            (buffer) -> new EquipMobPayload(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}