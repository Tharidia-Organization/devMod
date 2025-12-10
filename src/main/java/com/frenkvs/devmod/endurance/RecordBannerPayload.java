package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from server to client when a new personal record is achieved.
 * Triggers the "NEW RECORD!" banner overlay.
 */
@SuppressWarnings({"null", "unused"})
public record RecordBannerPayload(String recordType, String recordValue) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "record_banner");
    public static final Type<RecordBannerPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RecordBannerPayload> STREAM_CODEC =
        StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf),
            RecordBannerPayload::recordType,
            StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf),
            RecordBannerPayload::recordValue,
            RecordBannerPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
