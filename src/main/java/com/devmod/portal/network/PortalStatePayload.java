package com.devmod.portal.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;
import com.devmod.portal.PortalColor;

/**
 * Network payload for syncing portal teleportation state from server to client.
 * Sent when a player enters, exits, or is standing in a custom portal.
 *
 * <p>Used by {@link com.devmod.portal.client.PortalTeleportOverlay} to render
 * the teleportation progress overlay on the client.
 *
 * @param inPortal true if the player is currently in a portal
 * @param portalColorIndex the index of the portal color (see {@link PortalColor#getIndex()})
 * @param ticksInPortal number of ticks the player has been standing in the portal
 * @param requiredDelay number of ticks required before teleportation (0 for instant)
 */
public record PortalStatePayload(
    boolean inPortal,
    int portalColorIndex,
    int ticksInPortal,
    int requiredDelay
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<PortalStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "portal_state")
    );

    public static final StreamCodec<ByteBuf, PortalStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, PortalStatePayload::inPortal,
        ByteBufCodecs.VAR_INT, PortalStatePayload::portalColorIndex,
        ByteBufCodecs.VAR_INT, PortalStatePayload::ticksInPortal,
        ByteBufCodecs.VAR_INT, PortalStatePayload::requiredDelay,
        PortalStatePayload::new
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = 1; // inPortal
        size += PayloadSizeUtil.varIntSize(portalColorIndex);
        size += PayloadSizeUtil.varIntSize(ticksInPortal);
        size += PayloadSizeUtil.varIntSize(requiredDelay);
        return PayloadSizeUtil.clampToInt(size);
    }

    // === Factory Methods ===

    /**
     * Creates a payload for when a player enters or is standing in a portal.
     *
     * @param color the portal color
     * @param ticksInPortal current ticks in portal
     * @param requiredDelay delay before teleportation
     * @return the payload
     */
    public static PortalStatePayload enterPortal(
            @Nonnull PortalColor color,
            int ticksInPortal,
            int requiredDelay) {
        return new PortalStatePayload(
            true,
            Objects.requireNonNull(color).getIndex(),
            ticksInPortal,
            requiredDelay
        );
    }

    /**
     * Creates a payload for when a player exits a portal.
     *
     * @return the payload
     */
    public static PortalStatePayload exitPortal() {
        return new PortalStatePayload(false, 0, 0, 0);
    }

    // === Helper Methods ===

    /**
     * Returns the portal color from the index.
     *
     * @return the portal color, or BLUE if invalid
     */
    @Nonnull
    public PortalColor getColor() {
        return PortalColor.byIndex(portalColorIndex);
    }

    /**
     * Calculates the teleportation progress (0.0 to 1.0).
     *
     * @return progress percentage where 1.0 means teleportation is imminent
     */
    public float getProgress() {
        if (requiredDelay <= 0) {
            return 1.0f;
        }
        return Math.min(1.0f, (float) ticksInPortal / requiredDelay);
    }
}
