package com.frenkvs.devmod.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

// Questo pacchetto trasporta una lista di coordinate (X, Y, Z)
public record MarkerPayload(List<Vec3> positions) implements CustomPacketPayload {

    public static final Type<MarkerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "marker_sync"));

    public static final StreamCodec<ByteBuf, MarkerPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                // Scrittura: Prima scriviamo QUANTI sono, poi le coordinate di ognuno
                buffer.writeInt(payload.positions.size());
                for (Vec3 pos : payload.positions) {
                    buffer.writeDouble(pos.x);
                    buffer.writeDouble(pos.y);
                    buffer.writeDouble(pos.z);
                }
            },
            (buffer) -> {
                // Lettura: Leggiamo quanti sono e ricostruiamo la lista
                int count = buffer.readInt();
                List<Vec3> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
                }
                return new MarkerPayload(list);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}