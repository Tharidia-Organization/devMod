package com.devmod.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShieldImpactPayload(
    int entityId,
    double impactX,
    double impactY,
    double impactZ,
    float damage,
    boolean wasDeflection  // true if a projectile was deflected
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<ShieldImpactPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "shield_impact"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldImpactPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            buffer.writeVarInt(val.entityId());
            buffer.writeDouble(val.impactX());
            buffer.writeDouble(val.impactY());
            buffer.writeDouble(val.impactZ());
            buffer.writeFloat(val.damage());
            buffer.writeBoolean(val.wasDeflection());
        },
        buffer -> new ShieldImpactPayload(
            buffer.readVarInt(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readFloat(),
            buffer.readBoolean()
        )
    );

    @Override
    @Nonnull
    public Type<ShieldImpactPayload> type() {
        return Objects.requireNonNull(TYPE, "payload type");
    }

    @Override
    public int estimatedSize() {
        int size = varIntSize(entityId);
        size += 8 * 3; // impactX/Y/Z
        size += 4; // damage
        size += 1; // wasDeflection
        return size;
    }

    /**
     * Creates an impact payload for damage blocked.
     */
    public static ShieldImpactPayload damageBlocked(int entityId, double x, double y, double z, float damage) {
        return new ShieldImpactPayload(entityId, x, y, z, damage, false);
    }

    /**
     * Creates an impact payload for projectile deflection.
     */
    public static ShieldImpactPayload projectileDeflected(int entityId, double x, double y, double z, float damage) {
        return new ShieldImpactPayload(entityId, x, y, z, damage, true);
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
