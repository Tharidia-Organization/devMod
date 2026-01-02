package com.devmod.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Network payload for syncing shield state between server and clients.
 * Sent server-to-client when shield status changes (block, shatter, regen).
 *
 * <p><b>CRITICAL:</b> The record field order MUST match the encode/decode order in STREAM_CODEC.
 * If fields are reordered, added, or removed, update the codec accordingly.</p>
 *
 * <p>Field order: entityId, isActive, strength, isShattered, regenProgress</p>
 */
public record ShieldStatePayload(
    int entityId,
    boolean isActive,
    float strength,      // 0.0 - 1.0
    boolean isShattered,
    float regenProgress  // 0.0 - 1.0 (only relevant if shattered)
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<ShieldStatePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "shield_state"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldStatePayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            buffer.writeVarInt(val.entityId());
            ByteBufCodecs.BOOL.encode(buffer, val.isActive());
            buffer.writeFloat(val.strength());
            ByteBufCodecs.BOOL.encode(buffer, val.isShattered());
            buffer.writeFloat(val.regenProgress());
        },
        buffer -> new ShieldStatePayload(
            buffer.readVarInt(),
            ByteBufCodecs.BOOL.decode(buffer),
            buffer.readFloat(),
            ByteBufCodecs.BOOL.decode(buffer),
            buffer.readFloat()
        )
    );

    @Override
    @Nonnull
    public Type<ShieldStatePayload> type() {
        return Objects.requireNonNull(TYPE, "payload type");
    }

    @Override
    public int estimatedSize() {
        int size = varIntSize(entityId);
        size += 1; // isActive
        size += 4; // strength
        size += 1; // isShattered
        size += 4; // regenProgress
        return size;
    }

    /**
     * Creates a payload for an active, healthy shield.
     */
    public static ShieldStatePayload active(int entityId, float strength) {
        return new ShieldStatePayload(entityId, true, strength, false, 0.0f);
    }

    /**
     * Creates a payload for a shattered shield.
     */
    public static ShieldStatePayload shattered(int entityId, float regenProgress) {
        return new ShieldStatePayload(entityId, false, 0.0f, true, regenProgress);
    }

    /**
     * Creates a payload for an inactive shield (no shield equipped).
     */
    public static ShieldStatePayload inactive(int entityId) {
        return new ShieldStatePayload(entityId, false, 0.0f, false, 0.0f);
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
