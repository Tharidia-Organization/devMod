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
 * Payload for saving or loading hologram configuration presets.
 * Sent from client to server when player clicks preset buttons in the UI.
 *
 * <p>The hologram projector supports 5 preset slots (0-4) for quick configuration switching.
 * Each preset stores all configurable settings: scan size, block size, rotation, transparency,
 * textured mode, filters, entity settings, and Y-slice settings.
 *
 * @param pos Block position of the projector
 * @param slot Preset slot number (0-4)
 * @param isSave True to save current settings to slot, false to load from slot
 */
public record HologramPresetPayload(
    BlockPos pos,
    int slot,
    boolean isSave
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum number of preset slots available. */
    public static final int MAX_PRESETS = 5;

    public static final Type<HologramPresetPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram_preset"));

    public static final StreamCodec<FriendlyByteBuf, HologramPresetPayload> STREAM_CODEC =
        StreamCodec.of(HologramPresetPayload::encode, HologramPresetPayload::decode);

    private static void encode(FriendlyByteBuf buf, HologramPresetPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.slot);
        buf.writeBoolean(payload.isSave);
    }

    private static HologramPresetPayload decode(FriendlyByteBuf buf) {
        return new HologramPresetPayload(
            buf.readBlockPos(),
            buf.readVarInt(),
            buf.readBoolean()
        );
    }

    /**
     * Validates the slot number is within bounds.
     *
     * @return true if the slot is valid (0 to MAX_PRESETS-1)
     */
    public boolean isValidSlot() {
        return slot >= 0 && slot < MAX_PRESETS;
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = 8; // BlockPos
        size += PayloadSizeUtil.varIntSize(slot);
        size += 1; // isSave
        return PayloadSizeUtil.clampToInt(size);
    }
}
