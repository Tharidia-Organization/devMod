package com.frenkvs.devmod.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.PathfindingDebugPayload;
import net.minecraft.network.protocol.common.custom.GoalDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiAddedDebugPayload;
import net.minecraft.network.protocol.common.custom.RaidsDebugPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Uses Minecraft's NATIVE debug payload system.
 * These are the same packets that Mojang uses internally for debugging.
 * The client already has renderers for these - we just need to send the packets.
 */
@SuppressWarnings("unused") // Native debug sending is temporarily disabled; keep code for future use
public class NativeDebugSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeDebugSender.class);

    public static final NativeDebugSender INSTANCE = new NativeDebugSender();

    private static final int SEARCH_RADIUS = 64;

    private int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 5;

    private NativeDebugSender() {}

    /**
     * Tick - sends debug packets to players who have features enabled.
     *
     * NOTE: Native debug packet sending is temporarily disabled because:
     * 1. The DebugRendererMixin that would render these packets has mixin issues
     * 2. Without the mixin, clients cannot render the debug visualizations anyway
     * 3. The PathfindingDebugPayload causes IndexOutOfBoundsException on some paths
     *
     * To re-enable: fix DebugRendererMixin or use a different rendering approach.
     */
    public void tick(ServerLevel level) {
        // TEMPORARILY DISABLED - see note above
        // The native debug system requires the DebugRendererMixin to work
        // which has issues with field shadowing in NeoForge 1.21.1

        /*
        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL) return;
        tickCounter = 0;

        for (ServerPlayer player : level.players()) {
            Set<DebugFeature> features = DebugManager.INSTANCE.getEnabledFeatures(player);
            if (features.isEmpty()) continue;

            try {
                if (features.contains(DebugFeature.ENTITY_PATHING)) {
                    sendPathfindingDebug(player, level);
                }

                if (features.contains(DebugFeature.ENTITY_GOALS)) {
                    sendGoalsDebug(player, level);
                }

                if (features.contains(DebugFeature.POI)) {
                    sendPOIDebug(player, level);
                }

                if (features.contains(DebugFeature.RAIDS)) {
                    sendRaidsDebug(player, level);
                }
            } catch (Exception e) {
                LOGGER.warn("Error sending debug packets to {}: {}",
                    player.getName().getString(), e.getMessage());
            }
        }
        */
    }

    /**
     * Send pathfinding debug using Minecraft's native PathfindingDebugPayload.
     */
    private void sendPathfindingDebug(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(SEARCH_RADIUS));

        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            PathNavigation nav = mob.getNavigation();
            Path path = nav.getPath();

            if (path != null && !path.isDone()) {
                // Use Minecraft's native debug packet sender
                // DebugPackets.sendPathFindingPacket(level, mob, path, nav.getMaxDistanceToWaypoint());

                // Or create and send the payload directly
                PathfindingDebugPayload payload = new PathfindingDebugPayload(
                    mob.getId(),
                    path,
                    nav.getMaxDistanceToWaypoint()
                );

                player.connection.send(new ClientboundCustomPayloadPacket(payload));
            }
        }
    }

    /**
     * Send goals debug using Minecraft's native GoalDebugPayload.
     */
    private void sendGoalsDebug(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(SEARCH_RADIUS));

        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            List<GoalDebugPayload.DebugGoal> goals = new ArrayList<>();

            // Extract goals from goalSelector
            for (WrappedGoal wrappedGoal : mob.goalSelector.getAvailableGoals()) {
                goals.add(new GoalDebugPayload.DebugGoal(
                    wrappedGoal.getPriority(),
                    wrappedGoal.isRunning(),
                    Objects.requireNonNull(wrappedGoal.getGoal().getClass().getSimpleName())
                ));
            }

            // Extract target goals
            for (WrappedGoal wrappedGoal : mob.targetSelector.getAvailableGoals()) {
                goals.add(new GoalDebugPayload.DebugGoal(
                    wrappedGoal.getPriority(),
                    wrappedGoal.isRunning(),
                    "[T] " + Objects.requireNonNull(wrappedGoal.getGoal().getClass().getSimpleName())
                ));
            }

            if (!goals.isEmpty()) {
                GoalDebugPayload payload = new GoalDebugPayload(
                    mob.getId(),
                    Objects.requireNonNull(mob.blockPosition()),
                    goals
                );

                player.connection.send(new ClientboundCustomPayloadPacket(payload));
            }
        }
    }

    /**
     * Send POI debug using Minecraft's native PoiAddedDebugPayload.
     */
    private void sendPOIDebug(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        PoiManager poiManager = level.getPoiManager();

        poiManager.getInRange(
            holder -> true,
            playerPos,
            48,
            PoiManager.Occupancy.ANY
        ).forEach(record -> {
            PoiAddedDebugPayload payload = new PoiAddedDebugPayload(
                Objects.requireNonNull(record.getPos()),
                Objects.requireNonNull(record.getPoiType().getRegisteredName()),
                0 // Free tickets - simplified
            );

            player.connection.send(new ClientboundCustomPayloadPacket(payload));
        });
    }

    /**
     * Send raids debug using Minecraft's native RaidsDebugPayload.
     */
    private void sendRaidsDebug(ServerPlayer player, ServerLevel level) {
        Raids raids = level.getRaids();
        if (raids == null) return;

        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        Raid nearestRaid = raids.getNearbyRaid(playerPos, 128);

        if (nearestRaid != null) {
            BlockPos center = Objects.requireNonNull(nearestRaid.getCenter());
            List<BlockPos> raidCenters = Objects.requireNonNull(List.of(center));

            RaidsDebugPayload payload = new RaidsDebugPayload(raidCenters);
            player.connection.send(new ClientboundCustomPayloadPacket(payload));
        }
    }
}
