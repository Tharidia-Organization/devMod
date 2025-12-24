package com.devmod.debug;

import com.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Payload for POI (Points of Interest) debug data (server to client).
 * Shows beds, workstations, bells, beehives, etc.
 */
public record POIPayload(List<POIInfo> pois) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<POIPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_poi")));

    public static final StreamCodec<FriendlyByteBuf, POIPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public POIPayload decode(@Nonnull FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<POIInfo> pois = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                pois.add(new POIInfo(
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), // x, y, z
                    Objects.requireNonNull(buf.readUtf()),    // type
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

    public record POIInfo(
        int x, int y, int z,
        String type,
        int freeTickets,
        int maxTickets
    ) {}
}
