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
import net.minecraft.world.level.pathfinder.Path;

import com.devmod.debug.DebugFeature;
import com.devmod.debug.DebugManager;

@Mixin(DebugPackets.class)
public class DebugPacketsMixin {

    /**
     * Send pathfinding debug packets to players with the feature enabled.
     */
    @Inject(method = "sendPathFindingPacket", at = @At("HEAD"), cancellable = true)
    private static void devmod_sendPathFindingPacket(Level level, Mob mob, Path path, float maxDistanceToWaypoint, CallbackInfo ci) {
        Objects.requireNonNull(ci, "CallbackInfo required by Mixin");
        // Cancel vanilla method completely - PathfindingDebugPayload has serialization issues
        // that cause IndexOutOfBoundsException on client-side deserialization.
        // The Path.createFromStream() reads more bytes than were written for some paths.
        // TODO: Implement custom pathfinding debug visualization that doesn't use vanilla packets
        ci.cancel();
    }

    /**
     * Send goal selector debug packets.
     */
    @Inject(method = "sendGoalSelector", at = @At("HEAD"))
    private static void devmod_sendGoalSelector(Level level, Mob mob, GoalSelector goalSelector, CallbackInfo ci) {
        Objects.requireNonNull(ci, "CallbackInfo required by Mixin");
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
    @Inject(method = "sendPoiRemovedPacket", at = @At("HEAD"))
    private static void devmod_sendPoiRemovedPacket(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        Objects.requireNonNull(ci, "CallbackInfo required by Mixin");
        PoiRemovedDebugPayload payload = new PoiRemovedDebugPayload(Objects.requireNonNull(pos, "pos"));
        sendToPlayers(level, payload, DebugFeature.POI);
    }

    /**
     * Send raids debug packets.
     */
    @Inject(method = "sendRaids", at = @At("HEAD"))
    private static void devmod_sendRaids(ServerLevel level, Collection<Raid> raids, CallbackInfo ci) {
        Objects.requireNonNull(ci, "CallbackInfo required by Mixin");
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
        Objects.requireNonNull(ci, "CallbackInfo required by Mixin");
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
