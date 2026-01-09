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
import com.devmod.portal.PortalColor;

/**
 * Network payload for sending portal destination preview from server to client.
 * Contains information about where the portal leads for display in the UI.
 *
 * @param portalPos the position of the portal that was queried
 * @param destinationName display name of the destination (e.g., "Combat Zone" or player base name)
 * @param dimensionName display name of the dimension (e.g., "Nexus" or "Overworld")
 * @param distance distance in blocks to destination (-1 if cross-dimension or unknown)
 * @param portalColorIndex the index of the portal color
 * @param isFixedDestination true if this is a fixed destination portal (zone portal), false if linked
 */
public record PortalPreviewPayload(
    @Nonnull BlockPos portalPos,
    @Nonnull String destinationName,
    @Nonnull String dimensionName,
    int distance,
    int portalColorIndex,
    boolean isFixedDestination
) implements CustomPacketPayload {

    public static final Type<PortalPreviewPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "portal_preview")
    );

    public static final StreamCodec<ByteBuf, PortalPreviewPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG.map(BlockPos::of, BlockPos::asLong), PortalPreviewPayload::portalPos,
        ByteBufCodecs.STRING_UTF8, PortalPreviewPayload::destinationName,
        ByteBufCodecs.STRING_UTF8, PortalPreviewPayload::dimensionName,
        ByteBufCodecs.VAR_INT, PortalPreviewPayload::distance,
        ByteBufCodecs.VAR_INT, PortalPreviewPayload::portalColorIndex,
        ByteBufCodecs.BOOL, PortalPreviewPayload::isFixedDestination,
        PortalPreviewPayload::new
    );

    public PortalPreviewPayload {
        Objects.requireNonNull(portalPos, "portalPos");
        Objects.requireNonNull(destinationName, "destinationName");
        Objects.requireNonNull(dimensionName, "dimensionName");
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

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
     * Returns true if distance is known and valid.
     */
    public boolean hasDistance() {
        return distance >= 0;
    }

    // === Factory Methods ===

    /**
     * Creates a preview payload for a fixed destination portal (Nexus zone).
     */
    public static PortalPreviewPayload forFixedDestination(
            @Nonnull BlockPos portalPos,
            @Nonnull String zoneName,
            @Nonnull String dimensionName,
            @Nonnull PortalColor color) {
        return new PortalPreviewPayload(
            portalPos,
            zoneName,
            dimensionName,
            -1, // No distance for fixed destinations within same dimension
            color.getIndex(),
            true
        );
    }

    /**
     * Creates a preview payload for a linked portal.
     */
    public static PortalPreviewPayload forLinkedPortal(
            @Nonnull BlockPos portalPos,
            @Nonnull String ownerName,
            @Nonnull String dimensionName,
            int distance,
            @Nonnull PortalColor color) {
        return new PortalPreviewPayload(
            portalPos,
            ownerName,
            dimensionName,
            distance,
            color.getIndex(),
            false
        );
    }
}
