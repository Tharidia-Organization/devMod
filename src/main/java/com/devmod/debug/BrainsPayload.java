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

/**
 * Server → Client: which mob is currently targeting which entity.
 * <p>
 * Carries only the part the client cannot see for itself: {@code Mob.getTarget()} is a plain
 * server-side field and is never synced. The aggressive/passive state is synced through
 * {@code DATA_MOB_FLAGS_ID}, so the renderer reads that from {@code mc.level} instead.
 * <p>
 * Both ids are resolved client-side, so the line follows the entities' interpolated positions
 * rather than the position they had when the packet was built.
 */
public record BrainsPayload(List<TargetLink> targets) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum links per payload to prevent DoS via unbounded allocation */
    private static final int MAX_TARGETS = 128;

    public static final CustomPacketPayload.Type<BrainsPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_brains")));

    public static final StreamCodec<FriendlyByteBuf, BrainsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrainsPayload decode(@Nonnull FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), MAX_TARGETS);
            if (count < 0) count = 0;
            List<TargetLink> targets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                targets.add(new TargetLink(
                    buf.readVarInt(), // entityId
                    buf.readVarInt()  // targetId
                ));
            }
            return new BrainsPayload(targets);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull BrainsPayload payload) {
            buf.writeVarInt(payload.targets.size());
            for (TargetLink link : payload.targets) {
                buf.writeVarInt(link.entityId);
                buf.writeVarInt(link.targetId);
            }
        }
    };

    /** Caps the sender so a crowded search box cannot exceed what the codec will decode. */
    public static int maxTargets() {
        return MAX_TARGETS;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(targets.size());
        for (TargetLink link : targets) {
            size += PayloadSizeUtil.varIntSize(link.entityId);
            size += PayloadSizeUtil.varIntSize(link.targetId);
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    public record TargetLink(
        int entityId,
        int targetId
    ) {}
}
