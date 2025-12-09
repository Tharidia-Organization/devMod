package com.frenkvs.devmod.mixin;

import com.frenkvs.devmod.debug.DebugFeature;
import com.frenkvs.devmod.debug.DebugManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.GoalDebugPayload;
import net.minecraft.network.protocol.common.custom.NeighborUpdatesDebugPayload;
import net.minecraft.network.protocol.common.custom.PathfindingDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiRemovedDebugPayload;
import net.minecraft.network.protocol.common.custom.RaidsDebugPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Mixin into Minecraft's DebugPackets class to actually send debug packets.
 * In release builds, these methods are empty - this mixin makes them work.
 * Similar to what DebugUtils mod does.
 */
@Mixin(DebugPackets.class)
public class DebugPacketsMixin {

    /**
     * Send pathfinding debug packets to players with the feature enabled.
     */
    @Inject(method = "sendPathFindingPacket", at = @At("HEAD"))
    private static void devmod_sendPathFindingPacket(Level level, Mob mob, Path path, float maxDistanceToWaypoint, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || path == null) return;

        PathfindingDebugPayload payload = new PathfindingDebugPayload(
            mob.getId(),
            path,
            maxDistanceToWaypoint
        );

        sendToPlayers(serverLevel, payload, DebugFeature.ENTITY_PATHING);
    }

    /**
     * Send goal selector debug packets.
     */
    @Inject(method = "sendGoalSelector", at = @At("HEAD"))
    private static void devmod_sendGoalSelector(Level level, Mob mob, GoalSelector goalSelector, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        List<GoalDebugPayload.DebugGoal> goals = new ArrayList<>();
        for (WrappedGoal wrappedGoal : goalSelector.getAvailableGoals()) {
            goals.add(new GoalDebugPayload.DebugGoal(
                wrappedGoal.getPriority(),
                wrappedGoal.isRunning(),
                wrappedGoal.getGoal().getClass().getSimpleName()
            ));
        }

        if (!goals.isEmpty()) {
            GoalDebugPayload payload = new GoalDebugPayload(
                mob.getId(),
                mob.blockPosition(),
                goals
            );
            sendToPlayers(serverLevel, payload, DebugFeature.ENTITY_GOALS);
        }
    }

    /**
     * Send POI removed debug packets.
     */
    @Inject(method = "sendPoiRemovedPacket", at = @At("HEAD"))
    private static void devmod_sendPoiRemovedPacket(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        PoiRemovedDebugPayload payload = new PoiRemovedDebugPayload(pos);
        sendToPlayers(level, payload, DebugFeature.POI);
    }

    /**
     * Send raids debug packets.
     */
    @Inject(method = "sendRaids", at = @At("HEAD"))
    private static void devmod_sendRaids(ServerLevel level, Collection<Raid> raids, CallbackInfo ci) {
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
    @Inject(method = "sendNeighborsUpdatePacket", at = @At("HEAD"))
    private static void devmod_sendNeighborsUpdatePacket(Level level, BlockPos pos, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        NeighborUpdatesDebugPayload payload = new NeighborUpdatesDebugPayload(
            serverLevel.getGameTime(),
            pos
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
                player.connection.send(new ClientboundCustomPayloadPacket(payload));
            }
        }
    }
}
