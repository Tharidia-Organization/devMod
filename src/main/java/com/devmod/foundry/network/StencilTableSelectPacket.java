package com.devmod.foundry.network;

import java.util.Objects;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Network packet for selecting a pattern in the Stencil Table.
 * Sent from client to server when a pattern is selected.
 */
public record StencilTableSelectPacket(
    ResourceLocation patternId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<StencilTableSelectPacket> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "stencil_table_select"))
    );

    public static final StreamCodec<ByteBuf, StencilTableSelectPacket> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ResourceLocation.STREAM_CODEC), StencilTableSelectPacket::patternId,
        StencilTableSelectPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return patternId.toString().length() + 4;
    }
}
