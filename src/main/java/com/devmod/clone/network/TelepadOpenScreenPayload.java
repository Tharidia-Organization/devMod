package com.devmod.clone.network;

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
 * Payload to open the telepad configuration screen on the client.
 * Sent from server to client when player right-clicks the telepad.
 *
 * @param pos Block position of the telepad
 * @param telepadName Current name setting
 */
public record TelepadOpenScreenPayload(
    BlockPos pos,
    String telepadName
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TelepadOpenScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_open_screen"));

    public static final StreamCodec<FriendlyByteBuf, TelepadOpenScreenPayload> STREAM_CODEC =
        StreamCodec.of(TelepadOpenScreenPayload::encode, TelepadOpenScreenPayload::decode);

    private static void encode(FriendlyByteBuf buf, TelepadOpenScreenPayload payload) {
        buf.writeBlockPos(payload.pos);
        String safeName = NetworkConstants.truncate(payload.telepadName, TelepadConfigPayload.MAX_TELEPAD_NAME_LENGTH);
        buf.writeUtf(safeName, TelepadConfigPayload.MAX_TELEPAD_NAME_LENGTH);
    }

    private static TelepadOpenScreenPayload decode(FriendlyByteBuf buf) {
        return new TelepadOpenScreenPayload(
            buf.readBlockPos(),
            buf.readUtf(TelepadConfigPayload.MAX_TELEPAD_NAME_LENGTH)
        );
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = 8; // BlockPos
        String safeName = NetworkConstants.truncate(telepadName, TelepadConfigPayload.MAX_TELEPAD_NAME_LENGTH);
        size += PayloadSizeUtil.estimatedUtfSize(safeName);
        return PayloadSizeUtil.clampToInt(size);
    }
}
