package com.devmod.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server → Client: the blocks that received a neighbour update since the last flush.
 * <p>
 * Unlike the other debug payloads this is not a periodic snapshot of world state the client
 * could otherwise not see - it is a stream of events that exist only for the instant
 * {@code BlockStateBase.handleNeighborChanged} dispatches them. {@code NativeDebugSender}
 * collects them and ships one batch per update interval, so the positions here share a single
 * {@code gameTime} (the tick the batch was flushed) rather than one stamp each: within a 5-tick
 * window the difference is below what a fading marker can show anyway.
 * <p>
 * Positions are already deduplicated by the sender, so a block hit repeatedly inside one window
 * appears once.
 */
public record BlockUpdatesPayload(long gameTime, List<BlockPos> positions)
        implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum positions per payload to prevent DoS via unbounded allocation */
    private static final int MAX_POSITIONS = 256;

    public static final CustomPacketPayload.Type<BlockUpdatesPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_block_updates")));

    public static final StreamCodec<FriendlyByteBuf, BlockUpdatesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlockUpdatesPayload decode(@Nonnull FriendlyByteBuf buf) {
            long gameTime = buf.readVarLong();
            int count = Math.min(buf.readVarInt(), MAX_POSITIONS);
            if (count < 0) count = 0;
            List<BlockPos> positions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                positions.add(buf.readBlockPos());
            }
            return new BlockUpdatesPayload(gameTime, positions);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull BlockUpdatesPayload payload) {
            buf.writeVarLong(payload.gameTime);
            buf.writeVarInt(payload.positions.size());
            for (BlockPos pos : payload.positions) {
                buf.writeBlockPos(pos);
            }
        }
    };

    /** Caps the sender's pending set so a busy contraption cannot exceed what the codec decodes. */
    public static int maxPositions() {
        return MAX_POSITIONS;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varLongSize(gameTime);
        size += PayloadSizeUtil.varIntSize(positions.size());
        size += positions.size() * 8L; // packed long per position
        return PayloadSizeUtil.clampToInt(size);
    }
}
