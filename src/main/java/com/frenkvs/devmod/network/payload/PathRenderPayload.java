package com.frenkvs.devmod.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record PathRenderPayload(
        List<BlockPos> pathNodes,
        BlockPos endNode,
        BlockPos stuckPos,
        int mobId
) implements CustomPacketPayload {

    public static final Type<PathRenderPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "path_render"));

    // Codec Manuale (Più sicuro e compatibile)
    public static final StreamCodec<ByteBuf, PathRenderPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, val) -> {
                // 1. Scrivi la lista dei nodi
                buffer.writeInt(val.pathNodes.size());
                for (BlockPos pos : val.pathNodes) {
                    buffer.writeLong(pos.asLong());
                }

                // 2. Scrivi EndNode (può essere null)
                buffer.writeBoolean(val.endNode != null);
                if (val.endNode != null) buffer.writeLong(val.endNode.asLong());

                // 3. Scrivi StuckPos (può essere null)
                buffer.writeBoolean(val.stuckPos != null);
                if (val.stuckPos != null) buffer.writeLong(val.stuckPos.asLong());

                // 4. Mob ID
                buffer.writeInt(val.mobId);
            },
            (buffer) -> {
                // 1. Leggi Lista
                int size = buffer.readInt();
                List<BlockPos> nodes = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    nodes.add(BlockPos.of(buffer.readLong()));
                }

                // 2. Leggi EndNode
                BlockPos endNode = buffer.readBoolean() ? BlockPos.of(buffer.readLong()) : null;

                // 3. Leggi StuckPos
                BlockPos stuckPos = buffer.readBoolean() ? BlockPos.of(buffer.readLong()) : null;

                // 4. Mob ID
                int mobId = buffer.readInt();

                return new PathRenderPayload(nodes, endNode, stuckPos, mobId);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}