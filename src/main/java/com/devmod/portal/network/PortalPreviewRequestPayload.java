package com.devmod.portal.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Network payload for requesting portal destination preview from client to server.
 * Sent when a player is looking at a portal block and wants to see where it leads.
 *
 * @param portalPos the position of the portal block the player is looking at
 */
public record PortalPreviewRequestPayload(
    @Nonnull BlockPos portalPos
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<PortalPreviewRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "portal_preview_request")
    );

    public static final StreamCodec<ByteBuf, PortalPreviewRequestPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG.map(BlockPos::of, BlockPos::asLong), PortalPreviewRequestPayload::portalPos,
        PortalPreviewRequestPayload::new
    );

    public PortalPreviewRequestPayload {
        Objects.requireNonNull(portalPos, "portalPos");
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        return PayloadSizeUtil.varLongSize(portalPos.asLong());
    }
}
