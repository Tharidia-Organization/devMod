package com.devmod.clone.network;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
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
        buf.writeUtf(payload.telepadName);
    }

    private static TelepadOpenScreenPayload decode(FriendlyByteBuf buf) {
        return new TelepadOpenScreenPayload(
            buf.readBlockPos(),
            buf.readUtf()
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
        size += PayloadSizeUtil.estimatedUtfSize(telepadName);
        return PayloadSizeUtil.clampToInt(size);
    }
}
