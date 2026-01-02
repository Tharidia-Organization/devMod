package com.devmod.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShieldShatterPayload(
    int entityId,
    double centerX,
    double centerY,
    double centerZ,
    float finalDamage  // The damage that broke the shield
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

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

    @Override
    public int estimatedSize() {
        int size = varIntSize(entityId);
        size += 8 * 3; // centerX/Y/Z
        size += 4; // finalDamage
        return size;
    }

    /**
     * Creates a shatter payload at the entity's position.
     */
    public static ShieldShatterPayload at(int entityId, double x, double y, double z, float damage) {
        return new ShieldShatterPayload(entityId, x, y, z, damage);
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
