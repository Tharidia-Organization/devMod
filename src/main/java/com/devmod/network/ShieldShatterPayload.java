package com.devmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Network payload for synchronizing shield shatter events.
 *
 * <p>Sent from server to nearby clients when a shield is broken,
 * triggering the shatter visual effect (fragments flying apart).</p>
 */
public record ShieldShatterPayload(
    int entityId,
    double centerX,
    double centerY,
    double centerZ,
    float finalDamage  // The damage that broke the shield
) implements CustomPacketPayload {

    public static final Type<ShieldShatterPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "shield_shatter"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldShatterPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            buffer.writeVarInt(val.entityId());
            buffer.writeDouble(val.centerX());
            buffer.writeDouble(val.centerY());
            buffer.writeDouble(val.centerZ());
            buffer.writeFloat(val.finalDamage());
        },
        buffer -> new ShieldShatterPayload(
            buffer.readVarInt(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readFloat()
        )
    );

    @Override
    @Nonnull
    public Type<ShieldShatterPayload> type() {
        return Objects.requireNonNull(TYPE, "payload type");
    }

    /**
     * Creates a shatter payload at the entity's position.
     */
    public static ShieldShatterPayload at(int entityId, double x, double y, double z, float damage) {
        return new ShieldShatterPayload(entityId, x, y, z, damage);
    }
}
