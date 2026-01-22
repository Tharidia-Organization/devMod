package com.devmod.hologram.network;

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
 * Payload to open the hologram configuration screen on the client.
 * Sent from server to client when player right-clicks the projector.
 *
 * @param pos Block position of the projector
 * @param scanSize Current scan size setting
 * @param blockSize Current block size setting
 * @param rotationEnabled Current rotation setting
 * @param transparentMode Current transparency setting
 */
public record HologramOpenScreenPayload(
    BlockPos pos,
    int scanSize,
    int blockSize,
    boolean rotationEnabled,
    boolean transparentMode
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<HologramOpenScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram_open_screen"));

    public static final StreamCodec<FriendlyByteBuf, HologramOpenScreenPayload> STREAM_CODEC =
        StreamCodec.of(HologramOpenScreenPayload::encode, HologramOpenScreenPayload::decode);

    private static void encode(FriendlyByteBuf buf, HologramOpenScreenPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.scanSize);
        buf.writeVarInt(payload.blockSize);
        buf.writeBoolean(payload.rotationEnabled);
        buf.writeBoolean(payload.transparentMode);
    }

    private static HologramOpenScreenPayload decode(FriendlyByteBuf buf) {
        return new HologramOpenScreenPayload(
            buf.readBlockPos(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readBoolean()
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
        size += PayloadSizeUtil.varIntSize(scanSize);
        size += PayloadSizeUtil.varIntSize(blockSize);
        size += 1; // rotationEnabled
        size += 1; // transparentMode
        return PayloadSizeUtil.clampToInt(size);
    }
}
