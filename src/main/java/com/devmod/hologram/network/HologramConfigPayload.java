package com.devmod.hologram.network;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Payload for configuring hologram projector settings.
 * Sent from client to server when player changes settings in the UI.
 *
 * @param pos Block position of the projector
 * @param scanSize Scan area size (16, 32, 48, or 64)
 * @param blockSize Display scale multiplier (1-4)
 * @param rotationEnabled Whether the hologram rotates
 * @param transparentMode Whether blocks are rendered semi-transparent
 * @param rescan Whether to trigger a mesh rebuild
 */
public record HologramConfigPayload(
    BlockPos pos,
    int scanSize,
    int blockSize,
    boolean rotationEnabled,
    boolean transparentMode,
    boolean rescan
) implements CustomPacketPayload {

    public static final Type<HologramConfigPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram_config"));

    public static final StreamCodec<FriendlyByteBuf, HologramConfigPayload> STREAM_CODEC =
        StreamCodec.of(HologramConfigPayload::encode, HologramConfigPayload::decode);

    private static void encode(FriendlyByteBuf buf, HologramConfigPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.scanSize);
        buf.writeVarInt(payload.blockSize);
        buf.writeBoolean(payload.rotationEnabled);
        buf.writeBoolean(payload.transparentMode);
        buf.writeBoolean(payload.rescan);
    }

    private static HologramConfigPayload decode(FriendlyByteBuf buf) {
        return new HologramConfigPayload(
            buf.readBlockPos(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
