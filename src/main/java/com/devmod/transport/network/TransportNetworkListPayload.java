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
 * <p>Channel ID: 215 (TRANSPORT_NETWORK_LIST)
 */
public record TransportNetworkListPayload(
    String networkName,
    List<NetworkNodeInfo> nodes
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TransportNetworkListPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "215")));

    public static final StreamCodec<ByteBuf, TransportNetworkListPayload> STREAM_CODEC =
        StreamCodec.of(TransportNetworkListPayload::encode, TransportNetworkListPayload::decode);

    private static void encode(ByteBuf buf, TransportNetworkListPayload payload) {
        ByteBufCodecs.STRING_UTF8.encode(Objects.requireNonNull(buf), Objects.requireNonNull(payload.networkName));
        buf.writeShort(payload.nodes.size());
        for (NetworkNodeInfo node : payload.nodes) {
            encodeNode(buf, node);
        }
    }

    private static void encodeNode(ByteBuf buf, NetworkNodeInfo node) {
        UUIDUtil.STREAM_CODEC.encode(Objects.requireNonNull(buf), Objects.requireNonNull(node.nodeId));
        ByteBufCodecs.STRING_UTF8.encode(Objects.requireNonNull(buf), Objects.requireNonNull(node.displayName));
        ByteBufCodecs.STRING_UTF8.encode(Objects.requireNonNull(buf), Objects.requireNonNull(node.dimension));
        buf.writeInt(node.x);
        buf.writeInt(node.y);
        buf.writeInt(node.z);
        buf.writeByte(node.colorIndex);
        buf.writeBoolean(node.available);
    }

    private static TransportNetworkListPayload decode(ByteBuf buf) {
        String networkName = ByteBufCodecs.STRING_UTF8.decode(Objects.requireNonNull(buf));
        int count = buf.readShort();
        List<NetworkNodeInfo> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nodes.add(decodeNode(buf));
        }
        return new TransportNetworkListPayload(networkName, nodes);
    }

    private static NetworkNodeInfo decodeNode(ByteBuf buf) {
        UUID nodeId = UUIDUtil.STREAM_CODEC.decode(Objects.requireNonNull(buf));
        String displayName = ByteBufCodecs.STRING_UTF8.decode(Objects.requireNonNull(buf));
        String dimension = ByteBufCodecs.STRING_UTF8.decode(Objects.requireNonNull(buf));
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        int colorIndex = buf.readByte();
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
        long size = PayloadSizeUtil.estimatedUtfSize(networkName);
        size += 2; // node count (short)
        for (NetworkNodeInfo node : nodes) {
            size += 16; // UUID
            size += PayloadSizeUtil.estimatedUtfSize(node.displayName);
            size += PayloadSizeUtil.estimatedUtfSize(node.dimension);
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
