package com.devmod.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import com.devmod.debug.DebugFeature;
import com.devmod.debug.DebugManager;
import com.devmod.debug.EntityPathingPayload;

/**
 * Hooks the vanilla debug-send call sites so the mod can push its own payloads from them.
 * <p>
 * Every {@code DebugPackets} send method is an empty stub in the 1.21.1 release jar, so nothing
 * here is suppressing traffic - the value is the call site itself, which fires the moment a path
 * is recomputed rather than on the next {@code NativeDebugSender} interval. The {@code ci.cancel()}
 * stays so behaviour is identical if a body ever reappears.
 * <p>
 * Neighbour updates are deliberately not taken from {@code sendNeighborsUpdatePacket}: vanilla
 * only reaches that from the base {@code BlockBehaviour.neighborChanged}, which most redstone
 * blocks never call. {@code BlockStateNeighborUpdateMixin} hooks the dispatch site instead, and
 * is the only recorder feeding {@code DebugFeature.BLOCK_UPDATES}.
 */
@Mixin(DebugPackets.class)
@SuppressWarnings({"UnusedMethod", "UnusedVariable"}) // Mixin methods are invoked via bytecode injection
public class DebugPacketsMixin {

    /**
     * Send pathfinding debug packets to players with the feature enabled.
     * Uses the mod's own EntityPathingPayload, drawn by {@code PathfindingDebugger}; vanilla's
     * PathfindingDebugPayload has no renderer left on this client.
     */
    @Inject(method = "sendPathFindingPacket", at = @At("HEAD"), cancellable = true)
    private static void devmod_sendPathFindingPacket(Level level, Mob mob, Path path, float maxDistanceToWaypoint, CallbackInfo ci) {
        ci.cancel();

        if (!(level instanceof ServerLevel serverLevel)) return;
        if (path == null || path.getNodeCount() == 0) return;

        // Build path nodes list
        List<EntityPathingPayload.PathNode> nodes = new ArrayList<>();
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (node != null) {
                // nodeType: 0=normal, 1=open (before current), 2=closed (already visited), 3=target
                int nodeType = 0;
                if (i < path.getNextNodeIndex()) {
                    nodeType = 2; // closed - already passed
                } else if (i == path.getNodeCount() - 1) {
                    nodeType = 3; // target
                }
                nodes.add(new EntityPathingPayload.PathNode(
                    node.x + 0.5, // center of block
                    node.y + 0.1, // slightly above ground
                    node.z + 0.5,
                    nodeType,
                    node.costMalus
                ));
            }
        }

        if (nodes.isEmpty()) return;

        // Get target position
        BlockPos target = path.getTarget();
        double targetX = target != null ? target.getX() + 0.5 : nodes.get(nodes.size() - 1).x();
        double targetY = target != null ? target.getY() + 0.1 : nodes.get(nodes.size() - 1).y();
        double targetZ = target != null ? target.getZ() + 0.5 : nodes.get(nodes.size() - 1).z();

        EntityPathingPayload payload = new EntityPathingPayload(
            mob.getId(),
            Objects.requireNonNull(mob.getName().getString()),
            nodes,
            targetX,
            targetY,
            targetZ,
            path.canReach(),
            maxDistanceToWaypoint
        );

        sendToPlayers(serverLevel, payload, DebugFeature.ENTITY_PATHING);
    }

    /**
     * Helper to send packets to players who have the feature enabled.
     */
    private static void sendToPlayers(ServerLevel level, CustomPacketPayload payload, DebugFeature feature) {
        for (ServerPlayer player : level.players()) {
            Set<DebugFeature> enabledFeatures = DebugManager.INSTANCE.getEnabledFeatures(player);
            if (enabledFeatures.contains(feature)) {
                player.connection.send(new ClientboundCustomPayloadPacket(Objects.requireNonNull(payload, "payload")));
            }
        }
    }
}
