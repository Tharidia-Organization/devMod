package com.devmod.endurance;

import com.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Payload sent from server to client when a new personal record is achieved.
 * Triggers the "NEW RECORD!" banner overlay.
 */
public record RecordBannerPayload(String recordType, String recordValue) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "record_banner"));
    public static final Type<RecordBannerPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, RecordBannerPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf)),
            RecordBannerPayload::recordType,
            Objects.requireNonNull(StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf)),
            RecordBannerPayload::recordValue,
            RecordBannerPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
