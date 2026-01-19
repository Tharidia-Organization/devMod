package com.devmod.clone.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Payload for configuring telepad settings.
 * Sent from client to server when player changes settings in the UI.
 *
 * @param pos Block position of the telepad
 * @param telepadName New name for the telepad network
 */
public record TelepadConfigPayload(
    BlockPos pos,
    String telepadName
) implements CustomPacketPayload {

    public static final Type<TelepadConfigPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_config")));

    public static final StreamCodec<FriendlyByteBuf, TelepadConfigPayload> STREAM_CODEC =
        StreamCodec.of(TelepadConfigPayload::encode, TelepadConfigPayload::decode);

    private static void encode(FriendlyByteBuf buf, TelepadConfigPayload payload) {
        buf.writeBlockPos(Objects.requireNonNull(payload.pos));
        buf.writeUtf(Objects.requireNonNull(payload.telepadName));
    }

    private static TelepadConfigPayload decode(FriendlyByteBuf buf) {
        return new TelepadConfigPayload(
            buf.readBlockPos(),
            buf.readUtf()
        );
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
