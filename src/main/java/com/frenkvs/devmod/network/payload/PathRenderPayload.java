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

    public static final CustomPacketPayload.Type<PathRenderPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "path_render"));

    public static final StreamCodec<ByteBuf, PathRenderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, BlockPos.STREAM_CODEC),
            PathRenderPayload::pathNodes,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional).map(
                    opt -> opt.orElse(null),
                    pos -> java.util.Optional.ofNullable(pos)
            ),
            PathRenderPayload::endNode,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional).map(
                    opt -> opt.orElse(null),
                    pos -> java.util.Optional.ofNullable(pos)
            ),
            PathRenderPayload::stuckPos,
            ByteBufCodecs.INT,
            PathRenderPayload::mobId,
            PathRenderPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
