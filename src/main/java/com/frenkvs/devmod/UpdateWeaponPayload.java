package com.frenkvs.devmod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public record UpdateWeaponPayload(
        boolean isGlobal,
        float head,
        float body,
        float legs,
        float pen,
        float bonus,
        String name
) implements CustomPacketPayload {

    public static final Type<UpdateWeaponPayload> TYPE = new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "update_weapon")));

    // FIX: We use StreamCodec.of(...) instead of composite(...)
    // This manual method supports infinite parameters.
    public static final StreamCodec<ByteBuf, UpdateWeaponPayload> STREAM_CODEC = StreamCodec.of(
            // 1. ENCODER (Scrittura: dal tuo PC al cavo di rete)
            (buffer, value) -> {
                ByteBufCodecs.BOOL.encode(buffer, value.isGlobal());
                ByteBufCodecs.FLOAT.encode(buffer, value.head());
                ByteBufCodecs.FLOAT.encode(buffer, value.body());
                ByteBufCodecs.FLOAT.encode(buffer, value.legs());
                ByteBufCodecs.FLOAT.encode(buffer, value.pen());
                ByteBufCodecs.FLOAT.encode(buffer, value.bonus());
                ByteBufCodecs.STRING_UTF8.encode(buffer, Objects.requireNonNull(value.name()));
            },
            // 2. DECODER (Lettura: dal cavo di rete al PC)
            (buffer) -> new UpdateWeaponPayload(
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer))
            )
    );

    public UpdateWeaponPayload {
        name = name == null ? "" : name;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
