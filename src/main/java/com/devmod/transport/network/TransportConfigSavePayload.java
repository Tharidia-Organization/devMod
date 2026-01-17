package com.devmod.transport.network;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData.NetworkSelectionMode;
import com.devmod.transport.TransportMode;

/**
 * Client → Server payload to save transport node configuration.
 * Sent when player saves changes in the configuration GUI.
 *
 * <p>Channel ID: 211 (TRANSPORT_CONFIG_SAVE)
 */
public record TransportConfigSavePayload(
    @Nonnull BlockPos nodePos,
    int modeIndex,
    int colorIndex,
    @Nonnull String networkName,
    int selectionModeIndex,
    @Nonnull String displayName
) implements CustomPacketPayload {

    /** Maximum length for network name. */
    public static final int MAX_NETWORK_NAME_LENGTH = 64;

    /** Maximum length for display name. */
    public static final int MAX_DISPLAY_NAME_LENGTH = 48;

    public static final Type<TransportConfigSavePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "211"));

    public static final StreamCodec<ByteBuf, TransportConfigSavePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, TransportConfigSavePayload::nodePos,
        ByteBufCodecs.VAR_INT, TransportConfigSavePayload::modeIndex,
        ByteBufCodecs.VAR_INT, TransportConfigSavePayload::colorIndex,
        ByteBufCodecs.STRING_UTF8, TransportConfigSavePayload::networkName,
        ByteBufCodecs.VAR_INT, TransportConfigSavePayload::selectionModeIndex,
        ByteBufCodecs.STRING_UTF8, TransportConfigSavePayload::displayName,
        TransportConfigSavePayload::new
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Returns the transport mode enum value.
     */
    @Nonnull
    public TransportMode getMode() {
        return TransportMode.byIndex(modeIndex);
    }

    /**
     * Returns the transport color enum value.
     */
    @Nonnull
    public TransportColor getColor() {
        return TransportColor.byIndex(colorIndex);
    }

    /**
     * Returns the network selection mode enum value.
     */
    @Nonnull
    public NetworkSelectionMode getSelectionMode() {
        return NetworkSelectionMode.byIndex(selectionModeIndex);
    }

    /**
     * Validates and sanitizes the network name.
     *
     * @return sanitized network name (truncated to MAX_NETWORK_NAME_LENGTH)
     */
    @Nonnull
    public String getSanitizedNetworkName() {
        if (networkName.length() > MAX_NETWORK_NAME_LENGTH) {
            return networkName.substring(0, MAX_NETWORK_NAME_LENGTH);
        }
        return networkName;
    }

    /**
     * Validates and sanitizes the display name.
     *
     * @return sanitized display name (truncated to MAX_DISPLAY_NAME_LENGTH)
     */
    @Nonnull
    public String getSanitizedDisplayName() {
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            return displayName.substring(0, MAX_DISPLAY_NAME_LENGTH);
        }
        return displayName;
    }
}
