package com.devmod.hologram.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Client to Server payload for deleting a hologram.
 */
public record DeleteHologramPayload(
    @Nonnull UUID hologramId
) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram_delete"));
    public static final Type<DeleteHologramPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, DeleteHologramPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DeleteHologramPayload decode(FriendlyByteBuf buf) {
            return new DeleteHologramPayload(buf.readUUID());
        }

        @Override
        public void encode(FriendlyByteBuf buf, DeleteHologramPayload payload) {
            buf.writeUUID(payload.hologramId);
        }
    };

    public DeleteHologramPayload {
        Objects.requireNonNull(hologramId, "hologramId");
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
