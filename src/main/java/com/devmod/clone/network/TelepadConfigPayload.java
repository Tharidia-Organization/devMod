package com.devmod.clone.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.NetworkConstants;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

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
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final int MAX_TELEPAD_NAME_LENGTH = NetworkConstants.MAX_SMALL_STRING;

    public static final Type<TelepadConfigPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_config")));

    public static final StreamCodec<FriendlyByteBuf, TelepadConfigPayload> STREAM_CODEC =
        StreamCodec.of(TelepadConfigPayload::encode, TelepadConfigPayload::decode);

    private static void encode(FriendlyByteBuf buf, TelepadConfigPayload payload) {
        buf.writeBlockPos(Objects.requireNonNull(payload.pos));
        String safeName = NetworkConstants.truncate(payload.telepadName, MAX_TELEPAD_NAME_LENGTH);
        buf.writeUtf(safeName, MAX_TELEPAD_NAME_LENGTH);
    }

    private static TelepadConfigPayload decode(FriendlyByteBuf buf) {
        return new TelepadConfigPayload(
            buf.readBlockPos(),
            buf.readUtf(MAX_TELEPAD_NAME_LENGTH)
        );
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = 8; // BlockPos
        String safeName = NetworkConstants.truncate(telepadName, MAX_TELEPAD_NAME_LENGTH);
        size += PayloadSizeUtil.estimatedUtfSize(safeName);
        return PayloadSizeUtil.clampToInt(size);
    }
}
