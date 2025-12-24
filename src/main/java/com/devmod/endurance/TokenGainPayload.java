package com.devmod.endurance;

import com.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Payload sent from server to client when tokens are gained.
 * Triggers the floating "+X Tokens" animation.
 */
public record TokenGainPayload(int amount) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "token_gain"));
    public static final Type<TokenGainPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, TokenGainPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TokenGainPayload decode(@Nonnull FriendlyByteBuf buf) {
            return new TokenGainPayload(buf.readInt());
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull TokenGainPayload payload) {
            buf.writeInt(payload.amount());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
