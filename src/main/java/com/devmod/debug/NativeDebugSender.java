package com.devmod.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

public class NativeDebugSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeDebugSender.class);

    public static final NativeDebugSender INSTANCE = new NativeDebugSender();

    private static final int SEARCH_RADIUS = 64;
    private static final int POI_RADIUS = 48;
    private static final int RAID_RADIUS = 128;
    /** Chunk radius around the player scanned for structure starts; mirrors the renderer's box. */
    private static final int STRUCTURE_CHUNK_RADIUS = 2;

    /** Only neighbour updates this close to a watching player are worth a marker. */
    private static final int BLOCK_UPDATE_RADIUS = 64;

    // Per-dimension: a single shared counter aliases with the number of ticking levels, so only
    // one arbitrary dimension would receive packets per interval.
    private final Map<ResourceKey<Level>, Integer> tickCounters = new HashMap<>();
    private static final int UPDATE_INTERVAL = 5;

    // Per-dimension: neighbour updates seen since the last flush, in the order they happened.
    // Written by recordBlockUpdate from the neighbour-update dispatch site and drained by tick,
    // both of which only ever run on the server thread, so this needs no synchronisation.
    private final Map<ResourceKey<Level>, Set<BlockPos>> pendingBlockUpdates = new HashMap<>();

    // recordBlockUpdate sits on a path that runs thousands of times per tick, so the "is anyone
    // watching" answer is cached here rather than recomputed per call - scanning the manager's
    // map would allocate a view and an iterator every neighbour update. Refreshed once per
    // flush, so enabling the feature can take up to one interval to start collecting.
    private volatile boolean blockUpdatesWatched;

    private NativeDebugSender() {}

    /**
     * Tick - sends debug packets to players who have features enabled.
     *
     * NOTE: Pathfinding debug uses DebugPackets with a mixin that swaps in a safe payload.
     */
    public void tick(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        int counter = tickCounters.getOrDefault(dimension, 0) + 1;
        if (counter < UPDATE_INTERVAL) {
            tickCounters.put(dimension, counter);
            return;
        }
        tickCounters.put(dimension, 0);
        blockUpdatesWatched = DebugManager.INSTANCE.anyPlayerHasFeature(DebugFeature.BLOCK_UPDATES);

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

                if (features.contains(DebugFeature.STRUCTURE_GENERATIONS)) {
                    sendStructuresDebug(player, level);
                }

                if (features.contains(DebugFeature.POI)) {
                    sendPOIDebug(player, level);
                }

                if (features.contains(DebugFeature.RAIDS)) {
                    sendRaidsDebug(player, level);
                }

                if (features.contains(DebugFeature.ENTITY_BRAINS)) {
                    sendBrainsDebug(player, level);
                }

                if (features.contains(DebugFeature.BEES)) {
                    sendBeesDebug(player, level);
                }

                if (features.contains(DebugFeature.BLOCK_UPDATES)) {
                    sendBlockUpdatesDebug(player, level);
                }
            } catch (Exception e) {
                LOGGER.warn("Error sending debug packets to {}: {}",
                    player.getName().getString(), e.getMessage());
            }
        }

        // Outside the player loop and outside the try: the window closes even if nobody is left
        // to receive it, so a disabled feature or a failed send cannot leave positions behind.
        pendingBlockUpdates.remove(dimension);
    }

    /**
     * Record one neighbour update for the next flush.
     * <p>
     * Called from {@code BlockStateNeighborUpdateMixin} at the dispatch site, which fires once
     * per neighbour per update - orders of magnitude too often to turn into a packet each.
     * Positions are deduplicated into a set, so a block hammered by a redstone clock costs one
     * entry per window rather than dozens, and the whole batch goes out on the normal interval.
     */
    public void recordBlockUpdate(ServerLevel level, BlockPos pos) {
        if (!blockUpdatesWatched) return;

        Set<BlockPos> pending = pendingBlockUpdates.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>());
        // Tested before the player scan: once the window is full the scan cannot change the
        // outcome, and a saturated window is exactly when this path runs hottest.
        if (pending.size() >= BlockUpdatesPayload.maxPositions()) return;

        for (ServerPlayer player : level.players()) {
            if (DebugManager.INSTANCE.isEnabled(player, DebugFeature.BLOCK_UPDATES)
                && player.blockPosition().distSqr(pos) <= (double) BLOCK_UPDATE_RADIUS * BLOCK_UPDATE_RADIUS) {
                pending.add(pos.immutable());
                return;
            }
        }
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
                EntityPathingPayload payload = buildPathingPayload(mob, path, nav.getMaxDistanceToWaypoint());
                if (payload != null) {
                    player.connection.send(new ClientboundCustomPayloadPacket(payload));
                }
            }
        }
    }

    @Nullable
    private EntityPathingPayload buildPathingPayload(Mob mob, Path path, float maxDistanceToWaypoint) {
        if (path == null || path.getNodeCount() == 0) return null;
        List<EntityPathingPayload.PathNode> nodes = new ArrayList<>();
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (node != null) {
                int nodeType = 0;
                if (i < path.getNextNodeIndex()) {
                    nodeType = 2; // closed
                } else if (i == path.getNodeCount() - 1) {
                    nodeType = 3; // target
                }
                nodes.add(new EntityPathingPayload.PathNode(
                    node.x + 0.5,
                    node.y + 0.1,
                    node.z + 0.5,
                    nodeType,
                    node.costMalus
                ));
            }
        }

        if (nodes.isEmpty()) return null;
        BlockPos target = path.getTarget();
        double targetX = target != null ? target.getX() + 0.5 : nodes.get(nodes.size() - 1).x();
        double targetY = target != null ? target.getY() + 0.1 : nodes.get(nodes.size() - 1).y();
        double targetZ = target != null ? target.getZ() + 0.5 : nodes.get(nodes.size() - 1).z();

        return new EntityPathingPayload(
            mob.getId(),
            Objects.requireNonNull(mob.getName().getString()),
            nodes,
            targetX,
            targetY,
            targetZ,
            path.canReach(),
            maxDistanceToWaypoint
        );
    }

    /**
     * Send the goal and target selectors of every nearby mob. Both selectors are server-only
     * state, as is each goal's running flag, so the client renderer cannot gather these itself;
     * the mob's position it reads from {@code mc.level}.
     */
    private void sendGoalsDebug(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(SEARCH_RADIUS));

        List<EntityGoalsPayload.MobGoals> mobs = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            List<EntityGoalsPayload.GoalInfo> goals = new ArrayList<>();
            collectGoals(mob.goalSelector, false, goals);
            collectGoals(mob.targetSelector, true, goals);
            if (goals.isEmpty()) continue;

            mobs.add(new EntityGoalsPayload.MobGoals(mob.getId(), goals));
            if (mobs.size() >= EntityGoalsPayload.maxMobs()) break;
        }

        player.connection.send(new ClientboundCustomPayloadPacket(new EntityGoalsPayload(mobs)));
    }

    private static void collectGoals(GoalSelector selector, boolean targetSelector,
                                     List<EntityGoalsPayload.GoalInfo> out) {
        for (WrappedGoal wrappedGoal : selector.getAvailableGoals()) {
            if (out.size() >= EntityGoalsPayload.maxGoalsPerMob()) return;
            out.add(new EntityGoalsPayload.GoalInfo(
                wrappedGoal.getPriority(),
                wrappedGoal.isRunning(),
                targetSelector,
                Objects.requireNonNull(wrappedGoal.getGoal().getClass().getSimpleName())
            ));
        }
    }

    /**
     * Send structure bounding boxes around the player. Structure starts exist only in the
     * server chunk map, so the client renderer cannot gather these itself.
     */
    private void sendStructuresDebug(ServerPlayer player, ServerLevel level) {
        SectionPos sectionPos = SectionPos.of(Objects.requireNonNull(player.blockPosition()));
        List<StructuresPayload.StructureBox> boxes = new ArrayList<>();

        outer:
        for (int dx = -STRUCTURE_CHUNK_RADIUS; dx <= STRUCTURE_CHUNK_RADIUS; dx++) {
            for (int dz = -STRUCTURE_CHUNK_RADIUS; dz <= STRUCTURE_CHUNK_RADIUS; dz++) {
                ChunkAccess chunk = level.getChunk(sectionPos.x() + dx, sectionPos.z() + dz,
                    Objects.requireNonNull(ChunkStatus.FULL), false);
                if (chunk == null) continue;

                for (StructureStart start : chunk.getAllStarts().values()) {
                    if (start == null) continue;

                    BoundingBox bb = start.getBoundingBox();
                    boxes.add(new StructuresPayload.StructureBox(
                        bb.minX(), bb.minY(), bb.minZ(),
                        bb.maxX(), bb.maxY(), bb.maxZ()
                    ));

                    if (boxes.size() >= StructuresPayload.maxBoxes()) break outer;
                }
            }
        }

        player.connection.send(new ClientboundCustomPayloadPacket(new StructuresPayload(boxes)));
    }

    /**
     * Send POI records around the player.
     */
    private void sendPOIDebug(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        PoiManager poiManager = level.getPoiManager();

        List<POIPayload.POIInfo> pois = poiManager.getInRange(
            holder -> true,
            playerPos,
            POI_RADIUS,
            PoiManager.Occupancy.ANY
        ).limit(POIPayload.maxPois()).map(record -> {
            BlockPos pos = Objects.requireNonNull(record.getPos());
            return new POIPayload.POIInfo(
                pos.getX(), pos.getY(), pos.getZ(),
                Objects.requireNonNull(record.getPoiType().getRegisteredName()),
                record.getFreeTickets(),
                0 // Max tickets is not exposed by PoiRecord
            );
        }).toList();

        player.connection.send(new ClientboundCustomPayloadPacket(new POIPayload(pois)));
    }

    /**
     * Send the target of every nearby mob. {@code Mob.getTarget()} is server-only state, so the
     * client renderer cannot draw the target lines without it; everything else that renderer
     * needs (position, aggressive flag) is synced entity data.
     */
    private void sendBrainsDebug(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(SEARCH_RADIUS));

        List<BrainsPayload.TargetLink> targets = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            LivingEntity target = mob.getTarget();
            if (target == null) continue;

            targets.add(new BrainsPayload.TargetLink(mob.getId(), target.getId()));
            if (targets.size() >= BrainsPayload.maxTargets()) break;
        }

        player.connection.send(new ClientboundCustomPayloadPacket(new BrainsPayload(targets)));
    }

    /**
     * Send the hive and flower each nearby bee remembers. Both are server-only fields; nectar and
     * anger are synced, so the client renderer reads those itself.
     */
    private void sendBeesDebug(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(SEARCH_RADIUS));

        List<BeesPayload.BeeInfo> bees = new ArrayList<>();
        for (Bee bee : level.getEntitiesOfClass(Bee.class, searchBox)) {
            BlockPos hivePos = bee.getHivePos();
            BlockPos flowerPos = bee.getSavedFlowerPos();
            if (hivePos == null && flowerPos == null) continue;

            bees.add(new BeesPayload.BeeInfo(bee.getId(), hivePos, flowerPos));
            if (bees.size() >= BeesPayload.maxBees()) break;
        }

        player.connection.send(new ClientboundCustomPayloadPacket(new BeesPayload(bees)));
    }

    /**
     * Send the neighbour updates collected since the last flush, if any. Unlike the other
     * senders this gathers nothing itself: {@link #recordBlockUpdate} has already filled the
     * window, because the events exist only for the instant vanilla reports them.
     */
    private void sendBlockUpdatesDebug(ServerPlayer player, ServerLevel level) {
        Set<BlockPos> pending = pendingBlockUpdates.get(level.dimension());
        if (pending == null || pending.isEmpty()) return;

        player.connection.send(new ClientboundCustomPayloadPacket(
            new BlockUpdatesPayload(level.getGameTime(), List.copyOf(pending))));
    }

    /**
     * Send the nearby raid, if any.
     */
    private void sendRaidsDebug(ServerPlayer player, ServerLevel level) {
        Raids raids = level.getRaids();

        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        Raid nearestRaid = raids.getNearbyRaid(playerPos, RAID_RADIUS);

        List<RaidsPayload.RaidInfo> raidInfos = new ArrayList<>(1);
        if (nearestRaid != null) {
            BlockPos center = Objects.requireNonNull(nearestRaid.getCenter());
            raidInfos.add(new RaidsPayload.RaidInfo(
                nearestRaid.getId(),
                center.getX(), center.getY(), center.getZ(),
                nearestRaid.getRaidOmenLevel(),
                nearestRaid.getGroupsSpawned(),
                nearestRaid.getNumGroups(Objects.requireNonNull(level.getDifficulty())),
                nearestRaid.isActive(),
                nearestRaid.isVictory()
            ));
        }

        player.connection.send(new ClientboundCustomPayloadPacket(new RaidsPayload(raidInfos)));
    }
}
