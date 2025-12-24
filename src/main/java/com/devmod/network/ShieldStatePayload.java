package com.devmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Network payload for synchronizing shield state between client and server.
 *
 * <p>Used to sync:</p>
 * <ul>
 *   <li>Shield active/inactive state</li>
 *   <li>Shield strength (for visual feedback)</li>
 *   <li>Shattered state and regeneration progress</li>
 * </ul>
 */
public record ShieldStatePayload(
    int entityId,
    boolean isActive,
    float strength,      // 0.0 - 1.0
    boolean isShattered,
    float regenProgress  // 0.0 - 1.0 (only relevant if shattered)
) implements CustomPacketPayload {

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
}
