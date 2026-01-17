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
import com.devmod.transport.TransportMode;
import com.devmod.transport.TransportNodeType;

/**
 * Server → Client payload to open the transport configuration GUI.
 * Sent when player shift-clicks on a transport node.
 *
 * <p>Channel ID: 210 (TRANSPORT_CONFIG_OPEN)
 */
public record TransportConfigOpenPayload(
    @Nonnull BlockPos nodePos,
    int nodeTypeIndex,
    int modeIndex,
    int colorIndex,
    @Nonnull String networkName,
    @Nonnull String displayName,
    int chargeTime,
    int cooldownTime,
    boolean hasLinkedNode,
    int frameCount
) implements CustomPacketPayload {

    public static final Type<TransportConfigOpenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "210"));

    public static final StreamCodec<ByteBuf, TransportConfigOpenPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            BlockPos.STREAM_CODEC.encode(buf, payload.nodePos);
            ByteBufCodecs.VAR_INT.encode(buf, payload.nodeTypeIndex);
            ByteBufCodecs.VAR_INT.encode(buf, payload.modeIndex);
            ByteBufCodecs.VAR_INT.encode(buf, payload.colorIndex);
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.networkName);
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.displayName);
            ByteBufCodecs.VAR_INT.encode(buf, payload.chargeTime);
            ByteBufCodecs.VAR_INT.encode(buf, payload.cooldownTime);
            ByteBufCodecs.BOOL.encode(buf, payload.hasLinkedNode);
            ByteBufCodecs.VAR_INT.encode(buf, payload.frameCount);
        },
        buf -> new TransportConfigOpenPayload(
            BlockPos.STREAM_CODEC.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf)
        )
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Returns the transport node type enum value.
     */
    @Nonnull
    public TransportNodeType getNodeType() {
        return TransportNodeType.byIndex(nodeTypeIndex);
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
}
