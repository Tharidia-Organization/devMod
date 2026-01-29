package com.devmod.transport.network;

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
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum length for network name. */
    public static final int MAX_NETWORK_NAME_LENGTH = TransportPayloadLimits.MAX_NETWORK_NAME_LENGTH;

    /** Maximum length for display name. */
    public static final int MAX_DISPLAY_NAME_LENGTH = TransportPayloadLimits.MAX_DISPLAY_NAME_LENGTH;

    public static final Type<TransportConfigSavePayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "transport_config_save")));

    public static final StreamCodec<ByteBuf, TransportConfigSavePayload> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(BlockPos.STREAM_CODEC), TransportConfigSavePayload::nodePos,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportConfigSavePayload::modeIndex,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportConfigSavePayload::colorIndex,
        Objects.requireNonNull(ByteBufCodecs.stringUtf8(MAX_NETWORK_NAME_LENGTH)), TransportConfigSavePayload::getSanitizedNetworkName,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportConfigSavePayload::selectionModeIndex,
        Objects.requireNonNull(ByteBufCodecs.stringUtf8(MAX_DISPLAY_NAME_LENGTH)), TransportConfigSavePayload::getSanitizedDisplayName,
        (pos, mode, color, network, selection, display) -> new TransportConfigSavePayload(
            Objects.requireNonNull(pos),
            Objects.requireNonNull(mode),
            Objects.requireNonNull(color),
            Objects.requireNonNull(network),
            Objects.requireNonNull(selection),
            Objects.requireNonNull(display)
        )
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varLongSize(nodePos.asLong());
        size += PayloadSizeUtil.varIntSize(modeIndex);
        size += PayloadSizeUtil.varIntSize(colorIndex);
        size += PayloadSizeUtil.estimatedUtfSize(getSanitizedNetworkName());
        size += PayloadSizeUtil.varIntSize(selectionModeIndex);
        size += PayloadSizeUtil.estimatedUtfSize(getSanitizedDisplayName());
        return PayloadSizeUtil.clampToInt(size);
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
        return TransportPayloadLimits.truncate(networkName, MAX_NETWORK_NAME_LENGTH);
    }

    /**
     * Validates and sanitizes the display name.
     *
     * @return sanitized display name (truncated to MAX_DISPLAY_NAME_LENGTH)
     */
    @Nonnull
    public String getSanitizedDisplayName() {
        return TransportPayloadLimits.truncate(displayName, MAX_DISPLAY_NAME_LENGTH);
    }
}
