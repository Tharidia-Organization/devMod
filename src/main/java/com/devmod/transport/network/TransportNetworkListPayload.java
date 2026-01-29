package com.devmod.transport.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server → Client payload to sync network list for selection GUI.
 * Contains information about available transport nodes in a network.
 *
 * <p>Channel: transport_network_list
 */
public record TransportNetworkListPayload(
    String networkName,
    List<NetworkNodeInfo> nodes
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TransportNetworkListPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "transport_network_list")));

    public static final StreamCodec<ByteBuf, TransportNetworkListPayload> STREAM_CODEC =
        StreamCodec.of(TransportNetworkListPayload::encode, TransportNetworkListPayload::decode);

    private static void encode(ByteBuf buf, TransportNetworkListPayload payload) {
        ByteBufCodecs.stringUtf8(TransportPayloadLimits.MAX_NETWORK_NAME_LENGTH).encode(
            Objects.requireNonNull(buf),
            TransportPayloadLimits.truncate(payload.networkName, TransportPayloadLimits.MAX_NETWORK_NAME_LENGTH)
        );
        int nodeCount = TransportPayloadLimits.clampCount(payload.nodes.size(), TransportPayloadLimits.MAX_NODES);
        buf.writeShort(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            NetworkNodeInfo node = payload.nodes.get(i);
            encodeNode(buf, node);
        }
    }

    private static void encodeNode(ByteBuf buf, NetworkNodeInfo node) {
        UUIDUtil.STREAM_CODEC.encode(Objects.requireNonNull(buf), Objects.requireNonNull(node.nodeId));
        ByteBufCodecs.stringUtf8(TransportPayloadLimits.MAX_DISPLAY_NAME_LENGTH).encode(
            Objects.requireNonNull(buf),
            TransportPayloadLimits.truncate(node.displayName, TransportPayloadLimits.MAX_DISPLAY_NAME_LENGTH)
        );
        ByteBufCodecs.stringUtf8(TransportPayloadLimits.MAX_DIMENSION_LENGTH).encode(
            Objects.requireNonNull(buf),
            TransportPayloadLimits.truncate(node.dimension, TransportPayloadLimits.MAX_DIMENSION_LENGTH)
        );
        buf.writeInt(node.x);
        buf.writeInt(node.y);
        buf.writeInt(node.z);
        buf.writeByte(node.colorIndex);
        buf.writeBoolean(node.available);
    }

    private static TransportNetworkListPayload decode(ByteBuf buf) {
        String networkName = ByteBufCodecs.stringUtf8(TransportPayloadLimits.MAX_NETWORK_NAME_LENGTH)
            .decode(Objects.requireNonNull(buf));
        int rawCount = buf.readShort() & 0xFFFF; // unsigned short
        int safeCount = TransportPayloadLimits.clampCount(rawCount, TransportPayloadLimits.MAX_NODES);
        List<NetworkNodeInfo> nodes = new ArrayList<>(safeCount);
        for (int i = 0; i < rawCount; i++) {
            NetworkNodeInfo node = decodeNode(buf);
            if (i < safeCount) {
                nodes.add(node);
            }
        }
        return new TransportNetworkListPayload(networkName, nodes);
    }

    private static NetworkNodeInfo decodeNode(ByteBuf buf) {
        UUID nodeId = UUIDUtil.STREAM_CODEC.decode(Objects.requireNonNull(buf));
        String displayName = ByteBufCodecs.stringUtf8(TransportPayloadLimits.MAX_DISPLAY_NAME_LENGTH)
            .decode(Objects.requireNonNull(buf));
        String dimension = ByteBufCodecs.stringUtf8(TransportPayloadLimits.MAX_DIMENSION_LENGTH)
            .decode(Objects.requireNonNull(buf));
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        int colorIndex = buf.readUnsignedByte();
        boolean available = buf.readBoolean();
        return new NetworkNodeInfo(nodeId, displayName, dimension, x, y, z, colorIndex, available);
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        int nodeCount = TransportPayloadLimits.clampCount(nodes.size(), TransportPayloadLimits.MAX_NODES);
        long size = PayloadSizeUtil.estimatedUtfSize(
            TransportPayloadLimits.truncateNullable(networkName, TransportPayloadLimits.MAX_NETWORK_NAME_LENGTH)
        );
        size += 2; // node count (short)
        for (int i = 0; i < nodeCount; i++) {
            NetworkNodeInfo node = nodes.get(i);
            size += 16; // UUID
            size += PayloadSizeUtil.estimatedUtfSize(
                TransportPayloadLimits.truncateNullable(node.displayName, TransportPayloadLimits.MAX_DISPLAY_NAME_LENGTH)
            );
            size += PayloadSizeUtil.estimatedUtfSize(
                TransportPayloadLimits.truncateNullable(node.dimension, TransportPayloadLimits.MAX_DIMENSION_LENGTH)
            );
            size += 4L * 3; // x/y/z
            size += 1; // colorIndex
            size += 1; // available
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * Returns the number of available nodes.
     */
    public int availableCount() {
        return (int) nodes.stream().filter(n -> n.available).count();
    }

    /**
     * Information about a single network node.
     */
    public record NetworkNodeInfo(
        UUID nodeId,
        String displayName,
        String dimension,
        int x,
        int y,
        int z,
        int colorIndex,
        boolean available
    ) {
        /**
         * Returns the position as BlockPos.
         */
        @Nonnull
        public BlockPos getPosition() {
            return new BlockPos(x, y, z);
        }

        /**
         * Returns a formatted coordinate string.
         */
        @Nonnull
        public String getCoordinatesString() {
            return Objects.requireNonNull(String.format("%d, %d, %d", x, y, z));
        }
    }
}
