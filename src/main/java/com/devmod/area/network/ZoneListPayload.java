package com.devmod.area.network;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Server -> Client: Zone list for ZoneSelectorWidget.
 * Contains list of zone summaries with basic info.
 */
public record ZoneListPayload(List<ZoneSummary> zones) implements CustomPacketPayload {

    /** Maximum zones to sync at once */
    public static final int MAX_ZONES = 500;

    public static final Type<ZoneListPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "zone_list")));

    /**
     * Summary of a zone for UI display.
     */
    public record ZoneSummary(
        String id,
        String displayName,
        int areaCount
    ) {
        public static final StreamCodec<FriendlyByteBuf, ZoneSummary> STREAM_CODEC =
            StreamCodec.composite(
                Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), ZoneSummary::id,
                Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), ZoneSummary::displayName,
                Objects.requireNonNull(ByteBufCodecs.VAR_INT), ZoneSummary::areaCount,
                ZoneSummary::create
            );

        /** Factory method to handle Integer -> int conversion for StreamCodec */
        private static ZoneSummary create(String id, String displayName, Integer areaCount) {
            return new ZoneSummary(id, displayName, areaCount != null ? areaCount.intValue() : 0);
        }
    }

    public static final StreamCodec<FriendlyByteBuf, ZoneListPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(ZoneSummary.STREAM_CODEC.apply(
                Objects.requireNonNull(ByteBufCodecs.list(MAX_ZONES)))),
            ZoneListPayload::zones,
            ZoneListPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
