package com.devmod.nexus.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server -> Client: Hub status update.
 * Sent when hub state changes or on client request.
 */
public record HubStatusPayload(
    boolean initialized,
    int totalSlots,
    int linkedSlots,
    int emptySlots
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<HubStatusPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nexus_hub_status")));

    public static final StreamCodec<FriendlyByteBuf, HubStatusPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.initialized);
            buf.writeVarInt(payload.totalSlots);
            buf.writeVarInt(payload.linkedSlots);
            buf.writeVarInt(payload.emptySlots);
        },
        buf -> new HubStatusPayload(
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt()
        )
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = 1; // initialized
        size += PayloadSizeUtil.varIntSize(totalSlots);
        size += PayloadSizeUtil.varIntSize(linkedSlots);
        size += PayloadSizeUtil.varIntSize(emptySlots);
        return PayloadSizeUtil.clampToInt(size);
    }
}
