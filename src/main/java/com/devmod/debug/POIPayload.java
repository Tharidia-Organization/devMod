package com.devmod.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

public record POIPayload(List<POIInfo> pois) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum POIs per payload to prevent DoS via unbounded allocation */
    private static final int MAX_POIS = 500;
    /** Maximum type string length */
    private static final int MAX_TYPE_LENGTH = 128;

    public static final CustomPacketPayload.Type<POIPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_poi")));

    public static final StreamCodec<FriendlyByteBuf, POIPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public POIPayload decode(@Nonnull FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), MAX_POIS);
            if (count < 0) count = 0;
            List<POIInfo> pois = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                pois.add(new POIInfo(
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), // x, y, z
                    Objects.requireNonNull(buf.readUtf(MAX_TYPE_LENGTH)),    // type
                    buf.readVarInt(), // freeTickets
                    buf.readVarInt()  // maxTickets
                ));
            }
            return new POIPayload(pois);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull POIPayload payload) {
            buf.writeVarInt(payload.pois.size());
            for (POIInfo poi : payload.pois) {
                buf.writeVarInt(poi.x);
                buf.writeVarInt(poi.y);
                buf.writeVarInt(poi.z);
                buf.writeUtf(Objects.requireNonNull(poi.type));
                buf.writeVarInt(poi.freeTickets);
                buf.writeVarInt(poi.maxTickets);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(pois.size());
        for (POIInfo poi : pois) {
            size += PayloadSizeUtil.varIntSize(poi.x);
            size += PayloadSizeUtil.varIntSize(poi.y);
            size += PayloadSizeUtil.varIntSize(poi.z);
            size += PayloadSizeUtil.estimatedUtfSize(poi.type);
            size += PayloadSizeUtil.varIntSize(poi.freeTickets);
            size += PayloadSizeUtil.varIntSize(poi.maxTickets);
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    public record POIInfo(
        int x, int y, int z,
        String type,
        int freeTickets,
        int maxTickets
    ) {}
}
