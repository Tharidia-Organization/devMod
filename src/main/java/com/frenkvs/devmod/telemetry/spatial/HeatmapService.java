package com.frenkvs.devmod.telemetry.spatial;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Centralized service for spatial heatmap management.
 *
 * Manages the following heatmaps:
 * - Stuck: Positions where entities get stuck
 * - Aggro Drop: Positions where mobs lose aggro
 * - Kiting: Positions where mobs get kited/turned
 * - Death: Death positions
 * - Movement: Player movement positions
 * - Camping: Positions where players camp
 * - Choke Points: Positions where players quit
 * - Invisible Collisions: Invisible collisions
 * - Parkour Falls: Parkour falls
 */
public class HeatmapService {
    public static final HeatmapService INSTANCE = new HeatmapService();

    // Heatmap by type
    private final Map<String, Map<BlockPos, Integer>> stuckHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> aggroDropHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> kitingHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> deathHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> movementHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> campingHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> chokePointHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> invisibleCollisionHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> parkourFallHeatmap = new ConcurrentHashMap<>();

    private HeatmapService() {}

    // ============================================
    // RECORD METHODS
    // ============================================

    public void recordStuck(String room, BlockPos pos) {
        increment(stuckHeatmap, room, pos);
    }

    public void recordAggroDrop(String room, BlockPos pos) {
        increment(aggroDropHeatmap, room, pos);
    }

    public void recordKiting(String room, BlockPos pos) {
        increment(kitingHeatmap, room, pos);
    }

    public void recordDeath(String room, BlockPos pos) {
        increment(deathHeatmap, room, pos);
    }

    public void recordMovement(String room, BlockPos pos) {
        increment(movementHeatmap, room, pos);
    }

    public void recordCamping(String room, BlockPos pos) {
        increment(campingHeatmap, room, pos);
    }

    public void recordChokePoint(String room, BlockPos pos) {
        increment(chokePointHeatmap, room, pos);
    }

    public void recordInvisibleCollision(String room, BlockPos pos) {
        increment(invisibleCollisionHeatmap, room, pos);
    }

    public void recordParkourFall(String room, BlockPos pos) {
        increment(parkourFallHeatmap, room, pos);
    }

    // ============================================
    // GETTER METHODS (read-only views)
    // ============================================

    public Map<String, Map<BlockPos, Integer>> getStuckHeatmap() {
        return Collections.unmodifiableMap(stuckHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getAggroDropHeatmap() {
        return Collections.unmodifiableMap(aggroDropHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getKitingHeatmap() {
        return Collections.unmodifiableMap(kitingHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getDeathHeatmap() {
        return Collections.unmodifiableMap(deathHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getMovementHeatmap() {
        return Collections.unmodifiableMap(movementHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getCampingHeatmap() {
        return Collections.unmodifiableMap(campingHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getChokePointHeatmap() {
        return Collections.unmodifiableMap(chokePointHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getInvisibleCollisionHeatmap() {
        return Collections.unmodifiableMap(invisibleCollisionHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getParkourFallHeatmap() {
        return Collections.unmodifiableMap(parkourFallHeatmap);
    }

    // ============================================
    // EXPORT METHODS
    // ============================================

    /**
     * Esporta una heatmap usando il consumer fornito.
     * @param heatmap La heatmap da esportare
     * @param lineConsumer Consumer che riceve (room, line) per ogni entry
     */
    public void exportHeatmap(Map<String, Map<BlockPos, Integer>> heatmap,
                              BiConsumer<String, String> lineConsumer) {
        heatmap.forEach((room, posMap) -> {
            posMap.forEach((pos, count) -> {
                String line = String.format(
                    "{\"room\":\"%s\",\"x\":%d,\"y\":%d,\"z\":%d,\"count\":%d}",
                    escapeJson(room), pos.getX(), pos.getY(), pos.getZ(), count
                );
                lineConsumer.accept(room, line);
            });
        });
    }

    public void exportStuckHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(stuckHeatmap, lineConsumer);
    }

    public void exportAggroDropHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(aggroDropHeatmap, lineConsumer);
    }

    public void exportKitingHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(kitingHeatmap, lineConsumer);
    }

    public void exportDeathHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(deathHeatmap, lineConsumer);
    }

    public void exportMovementHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(movementHeatmap, lineConsumer);
    }

    public void exportCampingHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(campingHeatmap, lineConsumer);
    }

    public void exportChokePointHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(chokePointHeatmap, lineConsumer);
    }

    public void exportInvisibleCollisionHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(invisibleCollisionHeatmap, lineConsumer);
    }

    public void exportParkourFallHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(parkourFallHeatmap, lineConsumer);
    }

    // ============================================
    // CLEAR METHODS
    // ============================================

    public void clearAll() {
        stuckHeatmap.clear();
        aggroDropHeatmap.clear();
        kitingHeatmap.clear();
        deathHeatmap.clear();
        movementHeatmap.clear();
        campingHeatmap.clear();
        chokePointHeatmap.clear();
        invisibleCollisionHeatmap.clear();
        parkourFallHeatmap.clear();
    }

    public void clearRoom(String room) {
        stuckHeatmap.remove(room);
        aggroDropHeatmap.remove(room);
        kitingHeatmap.remove(room);
        deathHeatmap.remove(room);
        movementHeatmap.remove(room);
        campingHeatmap.remove(room);
        chokePointHeatmap.remove(room);
        invisibleCollisionHeatmap.remove(room);
        parkourFallHeatmap.remove(room);
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    private void increment(Map<String, Map<BlockPos, Integer>> heatmap, String room, BlockPos pos) {
        heatmap.computeIfAbsent(room, k -> new ConcurrentHashMap<>())
               .merge(pos, 1, (a, b) -> a + b);
    }

    private String escapeJson(String s) {
        return com.frenkvs.devmod.telemetry.TelemetryJson.escape(s);
    }

    /**
     * Ottiene statistiche aggregate per una room.
     */
    public HeatmapStats getStatsForRoom(String room) {
        int stuckCount = countEntries(stuckHeatmap.get(room));
        int aggroDropCount = countEntries(aggroDropHeatmap.get(room));
        int kitingCount = countEntries(kitingHeatmap.get(room));
        int deathCount = countEntries(deathHeatmap.get(room));
        int movementCount = countEntries(movementHeatmap.get(room));
        int campingCount = countEntries(campingHeatmap.get(room));
        int chokePointCount = countEntries(chokePointHeatmap.get(room));
        int invisibleCollisionCount = countEntries(invisibleCollisionHeatmap.get(room));
        int parkourFallCount = countEntries(parkourFallHeatmap.get(room));

        return new HeatmapStats(room, stuckCount, aggroDropCount, kitingCount,
                               deathCount, movementCount, campingCount,
                               chokePointCount, invisibleCollisionCount, parkourFallCount);
    }

    private int countEntries(Map<BlockPos, Integer> posMap) {
        if (posMap == null) return 0;
        return posMap.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Record per le statistiche aggregate di una room.
     */
    public record HeatmapStats(
        String room,
        int stuckEvents,
        int aggroDropEvents,
        int kitingEvents,
        int deathEvents,
        int movementSamples,
        int campingEvents,
        int chokePointEvents,
        int invisibleCollisionEvents,
        int parkourFallEvents
    ) {
        @Override
        public String toString() {
            return String.format(
                "Room: %s | Stuck: %d | AggroDrop: %d | Kiting: %d | Deaths: %d | Movement: %d | Camping: %d | ChokePoints: %d | InvisCollisions: %d | ParkourFalls: %d",
                room, stuckEvents, aggroDropEvents, kitingEvents, deathEvents, movementSamples, campingEvents,
                chokePointEvents, invisibleCollisionEvents, parkourFallEvents
            );
        }
    }
}
