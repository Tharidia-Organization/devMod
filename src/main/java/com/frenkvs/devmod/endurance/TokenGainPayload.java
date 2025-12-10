package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from server to client when tokens are gained.
 * Triggers the floating "+X Tokens" animation.
 */
@SuppressWarnings({"null", "unused"})
public record TokenGainPayload(int amount) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "token_gain");
    public static final Type<TokenGainPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, TokenGainPayload> STREAM_CODEC =
        StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt),
            TokenGainPayload::amount,
            TokenGainPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
