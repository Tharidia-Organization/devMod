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
 * @param filterBitmask Bitmask of active filters
 * @param filterHighlightOnly Whether to show only filtered blocks
 * @param texturedMode Whether to render with real block textures
 * @param showEntities Whether to render entities in the hologram
 * @param fullEntityModels Whether to render full 3D entity models
 * @param maxEntityModels Maximum number of entities to render as 3D models
 * @param entityFilterBitmask Bitmask of active entity type filters
 * @param ySliceEnabled Whether Y-slice mode is enabled
 * @param ySliceLevel The Y level for the slice
 * @param ySliceThickness The thickness of the Y-slice
 */
public record HologramOpenScreenPayload(
    BlockPos pos,
    int scanSize,
    int blockSize,
    boolean rotationEnabled,
    boolean transparentMode,
    int filterBitmask,
    boolean filterHighlightOnly,
    boolean texturedMode,
    boolean showEntities,
    boolean fullEntityModels,
    int maxEntityModels,
    int entityFilterBitmask,
    boolean ySliceEnabled,
    int ySliceLevel,
    int ySliceThickness
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
        buf.writeVarInt(payload.filterBitmask);
        buf.writeBoolean(payload.filterHighlightOnly);
        buf.writeBoolean(payload.texturedMode);
        buf.writeBoolean(payload.showEntities);
        buf.writeBoolean(payload.fullEntityModels);
        buf.writeVarInt(payload.maxEntityModels);
        buf.writeVarInt(payload.entityFilterBitmask);
        buf.writeBoolean(payload.ySliceEnabled);
        buf.writeVarInt(payload.ySliceLevel);
        buf.writeVarInt(payload.ySliceThickness);
    }

    private static HologramOpenScreenPayload decode(FriendlyByteBuf buf) {
        return new HologramOpenScreenPayload(
            buf.readBlockPos(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readVarInt()
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
        size += PayloadSizeUtil.varIntSize(filterBitmask);
        size += 1; // filterHighlightOnly
        size += 1; // texturedMode
        size += 1; // showEntities
        size += 1; // fullEntityModels
        size += PayloadSizeUtil.varIntSize(maxEntityModels);
        size += PayloadSizeUtil.varIntSize(entityFilterBitmask);
        size += 1; // ySliceEnabled
        size += PayloadSizeUtil.varIntSize(ySliceLevel);
        size += PayloadSizeUtil.varIntSize(ySliceThickness);
        return PayloadSizeUtil.clampToInt(size);
    }
}
