package com.devmod.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server → Client: the hive and flower a bee has remembered.
 * <p>
 * Carries only the part the client cannot see for itself: {@code hivePos} and
 * {@code savedFlowerPos} are plain server-side fields that only reach NBT. Nectar
 * ({@code DATA_FLAGS_ID}) and anger ({@code DATA_REMAINING_ANGER_TIME}) are synced, so the
 * renderer reads those from {@code mc.level} instead.
 * <p>
 * The bee itself is resolved client-side by id, so the lines follow its interpolated position.
 */
public record BeesPayload(List<BeeInfo> bees) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum bees per payload to prevent DoS via unbounded allocation */
    private static final int MAX_BEES = 128;

    public static final CustomPacketPayload.Type<BeesPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_bees")));

    public static final StreamCodec<FriendlyByteBuf, BeesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BeesPayload decode(@Nonnull FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), MAX_BEES);
            if (count < 0) count = 0;
            List<BeeInfo> bees = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                bees.add(new BeeInfo(
                    buf.readVarInt(),                        // entityId
                    buf.readNullable(b -> b.readBlockPos()), // hivePos
                    buf.readNullable(b -> b.readBlockPos())  // flowerPos
                ));
            }
            return new BeesPayload(bees);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull BeesPayload payload) {
            buf.writeVarInt(payload.bees.size());
            for (BeeInfo bee : payload.bees) {
                buf.writeVarInt(bee.entityId);
                buf.writeNullable(bee.hivePos, (b, pos) -> b.writeBlockPos(pos));
                buf.writeNullable(bee.flowerPos, (b, pos) -> b.writeBlockPos(pos));
            }
        }
    };

    /** Caps the sender so a crowded apiary cannot exceed what the codec will decode. */
    public static int maxBees() {
        return MAX_BEES;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(bees.size());
        for (BeeInfo bee : bees) {
            size += PayloadSizeUtil.varIntSize(bee.entityId);
            size += 1 + (bee.hivePos != null ? 8L : 0L);   // presence flag + packed long
            size += 1 + (bee.flowerPos != null ? 8L : 0L);
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    public record BeeInfo(
        int entityId,
        @Nullable BlockPos hivePos,
        @Nullable BlockPos flowerPos
    ) {}
}
