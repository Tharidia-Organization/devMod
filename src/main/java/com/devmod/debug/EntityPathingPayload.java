package com.devmod.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

public record EntityPathingPayload(
    int entityId,
    String entityName,
    List<PathNode> nodes,
    double targetX,
    double targetY,
    double targetZ,
    boolean canReach,
    float maxDistanceToWaypoint
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final CustomPacketPayload.Type<EntityPathingPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_pathing")));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityPathingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityPathingPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            String entityName = Objects.requireNonNull(buf.readUtf());
            int nodeCount = buf.readVarInt();
            List<PathNode> nodes = new ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) {
                nodes.add(new PathNode(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readFloat()
                ));
            }
            double targetX = buf.readDouble();
            double targetY = buf.readDouble();
            double targetZ = buf.readDouble();
            boolean canReach = buf.readBoolean();
            float maxDist = buf.readFloat();
            return new EntityPathingPayload(entityId, entityName, nodes, targetX, targetY, targetZ, canReach, maxDist);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull EntityPathingPayload payload) {
            buf.writeVarInt(payload.entityId);
            buf.writeUtf(Objects.requireNonNull(payload.entityName));
            buf.writeVarInt(payload.nodes.size());
            for (PathNode node : payload.nodes) {
                buf.writeDouble(node.x);
                buf.writeDouble(node.y);
                buf.writeDouble(node.z);
                buf.writeVarInt(node.nodeType);
                buf.writeFloat(node.costMalus);
            }
            buf.writeDouble(payload.targetX);
            buf.writeDouble(payload.targetY);
            buf.writeDouble(payload.targetZ);
            buf.writeBoolean(payload.canReach);
            buf.writeFloat(payload.maxDistanceToWaypoint);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(entityId);
        size += PayloadSizeUtil.estimatedUtfSize(entityName);
        size += PayloadSizeUtil.varIntSize(nodes.size());
        for (PathNode node : nodes) {
            size += 8L * 3; // x, y, z
            size += PayloadSizeUtil.varIntSize(node.nodeType);
            size += 4; // costMalus
        }
        size += 8L * 3; // targetX/Y/Z
        size += 1; // canReach
        size += 4; // maxDistanceToWaypoint
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * A single node in the path.
     */
    public record PathNode(
        double x,
        double y,
        double z,
        int nodeType, // 0=normal, 1=open, 2=closed, 3=target
        float costMalus
    ) {}

    /**
     * Create an empty/clear payload for an entity (removes debug rendering).
     */
    public static EntityPathingPayload clear(int entityId) {
        return new EntityPathingPayload(entityId, "", List.of(), 0, 0, 0, false, 0);
    }
}
