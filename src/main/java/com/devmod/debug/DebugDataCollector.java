package com.devmod.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Server-side collector that gathers debug data from Minecraft's internal systems
 * and sends it to clients who have enabled the corresponding debug features.
 */
public class DebugDataCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugDataCollector.class);

    public static final DebugDataCollector INSTANCE = new DebugDataCollector();

    // Collection radius around players
    private static final int ENTITY_SEARCH_RADIUS = 64;
    private static final int POI_SEARCH_RADIUS = 48;

    // Throttle sending to avoid network spam (ticks between updates)
    private int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 5; // Every 5 ticks (250ms)

    private DebugDataCollector() {}

    /**
     * Tick method - called from server tick event.
     * Collects and sends debug data to subscribed players.
     */
    public void tick(ServerLevel level) {
        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL) return;
        tickCounter = 0;

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        for (ServerPlayer player : players) {
            Set<DebugFeature> enabledFeatures = DebugManager.INSTANCE.getEnabledFeatures(player);
            if (enabledFeatures.isEmpty()) continue;

            try {
                // Entity Pathing
                if (enabledFeatures.contains(DebugFeature.ENTITY_PATHING)) {
                    collectAndSendPathingData(player, level);
                }

                // Entity Goals
                if (enabledFeatures.contains(DebugFeature.ENTITY_GOALS)) {
                    collectAndSendGoalsData(player, level);
                }

                // POI
                if (enabledFeatures.contains(DebugFeature.POI)) {
                    collectAndSendPOIData(player, level);
                }

                // Raids
                if (enabledFeatures.contains(DebugFeature.RAIDS)) {
                    collectAndSendRaidsData(player, level);
                }
            } catch (Exception e) {
                LOGGER.warn("Error collecting debug data for player {}: {}",
                    player.getName().getString(), e.getMessage());
            }
        }
    }

    /**
     * Collect and send entity pathing data.
     * Uses the REAL path from mob.getNavigation().getPath()
     */
    private void collectAndSendPathingData(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(ENTITY_SEARCH_RADIUS));

        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            PathNavigation nav = mob.getNavigation();
            Path path = nav.getPath();

            if (path != null && !path.isDone()) {
                List<EntityPathingPayload.PathNode> nodes = new ArrayList<>();

                // Get all nodes from the path
                for (int i = 0; i < path.getNodeCount(); i++) {
                    Node node = path.getNode(i);
                    int nodeType = 0; // normal
                    if (i == path.getNextNodeIndex()) nodeType = 1; // current
                    if (i == path.getNodeCount() - 1) nodeType = 3; // target

                    nodes.add(new EntityPathingPayload.PathNode(
                        node.x + 0.5,
                        node.y + 0.1,
                        node.z + 0.5,
                        nodeType,
                        node.costMalus
                    ));
                }

                // Get target position safely
                double targetX, targetY, targetZ;
                BlockPos targetPos = path.getTarget();
                if (targetPos != null) {
                    BlockPos nonNullTarget = Objects.requireNonNull(targetPos);
                    targetX = nonNullTarget.getX() + 0.5;
                    targetY = nonNullTarget.getY();
                    targetZ = nonNullTarget.getZ() + 0.5;
                } else {
                    Node endNode = path.getEndNode();
                    if (endNode != null) {
                        targetX = endNode.x + 0.5;
                        targetY = endNode.y;
                        targetZ = endNode.z + 0.5;
                    } else {
                        // Fallback to mob's target entity position
                        var mobTarget = mob.getTarget();
                        if (mobTarget != null) {
                            targetX = mobTarget.getX();
                            targetY = mobTarget.getY();
                            targetZ = mobTarget.getZ();
                        } else {
                            continue; // Skip if no valid target
                        }
                    }
                }

                EntityPathingPayload payload = new EntityPathingPayload(
                    mob.getId(),
                    mob.getName().getString(),
                    nodes,
                    targetX,
                    targetY,
                    targetZ,
                    path.canReach(),
                    nav.getMaxDistanceToWaypoint()
                );

                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Collect and send entity AI goals data.
     * Shows both regular goals and target goals.
     */
    private void collectAndSendGoalsData(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(ENTITY_SEARCH_RADIUS));

        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            List<EntityGoalsPayload.GoalInfo> goals = extractGoals(mob.goalSelector);
            List<EntityGoalsPayload.GoalInfo> targetGoals = extractGoals(mob.targetSelector);

            if (!goals.isEmpty() || !targetGoals.isEmpty()) {
                EntityGoalsPayload payload = new EntityGoalsPayload(
                    mob.getId(),
                    mob.getName().getString(),
                    mob.getX(),
                    mob.getY(),
                    mob.getZ(),
                    goals,
                    targetGoals
                );

                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Extract goal information from a GoalSelector.
     */
    private List<EntityGoalsPayload.GoalInfo> extractGoals(GoalSelector selector) {
        List<EntityGoalsPayload.GoalInfo> result = new ArrayList<>();

        for (WrappedGoal wrappedGoal : selector.getAvailableGoals()) {
            Goal goal = wrappedGoal.getGoal();
            result.add(new EntityGoalsPayload.GoalInfo(
                wrappedGoal.getPriority(),
                wrappedGoal.isRunning(),
                goal.getClass().getSimpleName()
            ));
        }

        return result;
    }

    /**
     * Collect and send POI (Points of Interest) data.
     */
    private void collectAndSendPOIData(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        PoiManager poiManager = level.getPoiManager();

        List<POIPayload.POIInfo> pois = new ArrayList<>();

        // Get all POIs in range
        Stream<PoiRecord> poiStream = poiManager.getInRange(
            holder -> true, // All POI types
            playerPos,
            POI_SEARCH_RADIUS,
            PoiManager.Occupancy.ANY
        );

        poiStream.forEach(record -> {
            BlockPos pos = Objects.requireNonNull(record.getPos());
            String typeName = record.getPoiType().getRegisteredName();

            pois.add(new POIPayload.POIInfo(
                pos.getX(), pos.getY(), pos.getZ(),
                typeName,
                0, // Free tickets - deprecated in 1.21
                1  // Max tickets
            ));
        });

        if (!pois.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new POIPayload(pois));
        }
    }

    /**
     * Collect and send Raids data.
     */
    private void collectAndSendRaidsData(ServerPlayer player, ServerLevel level) {
        Raids raids = level.getRaids();
        if (raids == null) return;

        List<RaidsPayload.RaidInfo> raidInfos = new ArrayList<>();

        // Iterate through raid IDs to find active raids
        // In 1.21.1, we need to check raids near the player
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        Raid nearestRaid = raids.getNearbyRaid(playerPos, 128);

        if (nearestRaid != null && nearestRaid.isActive()) {
            BlockPos center = nearestRaid.getCenter();

            raidInfos.add(new RaidsPayload.RaidInfo(
                nearestRaid.getId(),
                center.getX() + 0.5,
                center.getY(),
                center.getZ() + 0.5,
                0, // Bad omen level - not directly accessible in 1.21.1
                nearestRaid.getGroupsSpawned(),
                nearestRaid.getNumGroups(Objects.requireNonNull(level.getDifficulty())),
                nearestRaid.isActive(),
                nearestRaid.isVictory()
            ));
        }

        if (!raidInfos.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new RaidsPayload(raidInfos));
        }
    }
}
