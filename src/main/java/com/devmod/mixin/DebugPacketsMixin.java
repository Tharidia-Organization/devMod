package com.devmod.mixin;

import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.network.protocol.common.custom.GoalDebugPayload;
import net.minecraft.network.protocol.common.custom.NeighborUpdatesDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiRemovedDebugPayload;
import net.minecraft.network.protocol.common.custom.RaidsDebugPayload;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import com.devmod.debug.DebugFeature;
import com.devmod.debug.DebugManager;
import com.devmod.debug.EntityPathingPayload;

@Mixin(DebugPackets.class)
@SuppressWarnings({"UnusedMethod", "UnusedVariable"}) // Mixin methods are invoked via bytecode injection
public class DebugPacketsMixin {

    /**
     * Send pathfinding debug packets to players with the feature enabled.
     * Uses custom EntityPathingPayload instead of vanilla PathfindingDebugPayload
     * which has serialization issues causing IndexOutOfBoundsException.
     */
    @Inject(method = "sendPathFindingPacket", at = @At("HEAD"), cancellable = true)
    private static void devmod_sendPathFindingPacket(Level level, Mob mob, Path path, float maxDistanceToWaypoint, CallbackInfo ci) {
        // Cancel vanilla method - use our custom payload instead
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
     * Send goal selector debug packets.
     */
    @Inject(method = "sendGoalSelector", at = @At("HEAD"), cancellable = true)
    private static void devmod_sendGoalSelector(Level level, Mob mob, GoalSelector goalSelector, CallbackInfo ci) {
        ci.cancel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        List<GoalDebugPayload.DebugGoal> goals = new ArrayList<>();
        for (WrappedGoal wrappedGoal : goalSelector.getAvailableGoals()) {
            goals.add(new GoalDebugPayload.DebugGoal(
                wrappedGoal.getPriority(),
                wrappedGoal.isRunning(),
                Objects.requireNonNull(wrappedGoal.getGoal().getClass().getSimpleName(), "goalName")
            ));
        }

        if (!goals.isEmpty()) {
            GoalDebugPayload payload = new GoalDebugPayload(
                mob.getId(),
                Objects.requireNonNull(mob.blockPosition(), "mobPosition"),
                goals
            );
            sendToPlayers(serverLevel, payload, DebugFeature.ENTITY_GOALS);
        }
    }

    /**
     * Send POI removed debug packets.
     */
    @Inject(method = "sendPoiRemovedPacket", at = @At("HEAD"), cancellable = true)
    private static void devmod_sendPoiRemovedPacket(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        ci.cancel();
        PoiRemovedDebugPayload payload = new PoiRemovedDebugPayload(Objects.requireNonNull(pos, "pos"));
        sendToPlayers(level, payload, DebugFeature.POI);
    }

    /**
     * Send raids debug packets.
     */
    @Inject(method = "sendRaids", at = @At("HEAD"), cancellable = true)
    private static void devmod_sendRaids(ServerLevel level, Collection<Raid> raids, CallbackInfo ci) {
        ci.cancel();
        List<BlockPos> raidCenters = new ArrayList<>();
        for (Raid raid : raids) {
            if (raid.isActive()) {
                raidCenters.add(raid.getCenter());
            }
        }

        if (!raidCenters.isEmpty()) {
            RaidsDebugPayload payload = new RaidsDebugPayload(raidCenters);
            sendToPlayers(level, payload, DebugFeature.RAIDS);
        }
    }

    /**
     * Send neighbor updates debug packets.
     */
    @Inject(method = "sendNeighborsUpdatePacket", at = @At("HEAD"), cancellable = true)
    private static void devmod_sendNeighborsUpdatePacket(Level level, BlockPos pos, CallbackInfo ci) {
        ci.cancel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        NeighborUpdatesDebugPayload payload = new NeighborUpdatesDebugPayload(
            serverLevel.getGameTime(),
            Objects.requireNonNull(pos, "pos")
        );
        sendToPlayers(serverLevel, payload, DebugFeature.BLOCK_UPDATES);
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
