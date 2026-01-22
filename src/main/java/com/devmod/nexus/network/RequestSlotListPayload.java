package com.devmod.nexus.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server: Request slot list for Nexus UI.
 * Empty payload - server responds with SlotListPayload.
 */
public record RequestSlotListPayload() implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<RequestSlotListPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nexus_request_slot_list")));

    public static final StreamCodec<FriendlyByteBuf, RequestSlotListPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestSlotListPayload());

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        return 0;
    }
}
