package com.frenkvs.devmod.debug;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Payload for entity pathing debug data (server to client).
 * Contains the actual path nodes calculated by the server.
 */
public record EntityPathingPayload(
    int entityId,
    String entityName,
    List<PathNode> nodes,
    double targetX,
    double targetY,
    double targetZ,
    boolean canReach,
    float maxDistanceToWaypoint
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntityPathingPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_pathing")));

    public static final StreamCodec<FriendlyByteBuf, EntityPathingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityPathingPayload decode(@Nonnull FriendlyByteBuf buf) {
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
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull EntityPathingPayload payload) {
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
