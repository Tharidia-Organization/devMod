package com.devmod.network;

import java.util.Objects;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import io.netty.buffer.ByteBuf;

public record EquipMobPayload(
        int entityId,       // Monster ID
        String mainHand,    // Main Hand (Right)
        String offHand,     // Off Hand (Left)
        String head,        // Head
        String chest,       // Chest
        String legs,        // Legs
        String feet         // Feet
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    // Unique network identifier
    public static final Type<EquipMobPayload> TYPE = new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "equip_mob")));

    // Manual codec (needed because we have 7 variables, and the automatic limit is 6)
    public static final StreamCodec<ByteBuf, EquipMobPayload> STREAM_CODEC = StreamCodec.of(
            // 1. WRITE (Encoder)
            (buffer, value) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, value.entityId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, Objects.requireNonNull(value.mainHand()));
                ByteBufCodecs.STRING_UTF8.encode(buffer, Objects.requireNonNull(value.offHand()));
                ByteBufCodecs.STRING_UTF8.encode(buffer, Objects.requireNonNull(value.head()));
                ByteBufCodecs.STRING_UTF8.encode(buffer, Objects.requireNonNull(value.chest()));
                ByteBufCodecs.STRING_UTF8.encode(buffer, Objects.requireNonNull(value.legs()));
                ByteBufCodecs.STRING_UTF8.encode(buffer, Objects.requireNonNull(value.feet()));
            },
            // 2. READ (Decoder)
            (buffer) -> new EquipMobPayload(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                    Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                    Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                    Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                    Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                    Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer))
            )
    );

    public EquipMobPayload {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(head, "head");
        Objects.requireNonNull(chest, "chest");
        Objects.requireNonNull(legs, "legs");
        Objects.requireNonNull(feet, "feet");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        // 1 VarInt + 6 strings (~50 chars each avg) = ~310 bytes
        return 10 + mainHand.length() + offHand.length() + head.length() + chest.length() + legs.length() + feet.length();
    }
}
