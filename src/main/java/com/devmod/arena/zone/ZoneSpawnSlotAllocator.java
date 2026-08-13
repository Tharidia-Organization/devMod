package com.devmod.arena.zone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Allocates spawn slots to zones and provides spawn position generation.
 * Handles the mapping between mob types and their designated zones.
 */
public class ZoneSpawnSlotAllocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneSpawnSlotAllocator.class);

    private final ZoneLayout layout;
    private final int baseY;
    private final Random random;

    /** Maps mob IDs to their assigned zones */
    private final Map<ResourceLocation, ArenaZone> mobZoneAssignments;

    /** Tracks spawn counts per zone for balancing */
    private final Map<String, Integer> zoneSpawnCounts;

    public ZoneSpawnSlotAllocator(ZoneLayout layout, int baseY) {
        this(layout, baseY, new Random());
    }

    public ZoneSpawnSlotAllocator(ZoneLayout layout, int baseY, Random random) {
        this.layout = layout;
        this.baseY = baseY;
        this.random = random;
        this.mobZoneAssignments = new HashMap<>();
        this.zoneSpawnCounts = new HashMap<>();

        // Initialize zone assignments from layout
        initializeAssignments();
    }

    private void initializeAssignments() {
        for (ArenaZone zone : layout.zones()) {
            zoneSpawnCounts.put(zone.name(), 0);

            for (ResourceLocation mobId : zone.assignedMobs()) {
                mobZoneAssignments.put(mobId, zone);
            }
        }
    }

    /**
     * Gets the zone assigned to a mob type.
     * If no specific assignment exists, returns the best matching zone.
     */
    public ArenaZone getZoneForMob(ResourceLocation mobId) {
        // Check explicit assignment
        ArenaZone assigned = mobZoneAssignments.get(mobId);
        if (assigned != null) {
            return assigned;
        }

        // Find zone from layout
        return layout.getZoneForMob(mobId)
            .orElseGet(() -> selectLeastPopulatedZone());
    }

    /**
     * Allocates a spawn position for a mob within its designated zone.
     */
    public BlockPos allocateSpawnPosition(ResourceLocation mobId) {
        ArenaZone zone = getZoneForMob(mobId);
        BlockPos pos = zone.getRandomPosition(random, baseY);

        // Track spawn count (explicit to avoid null safety warnings with Integer::sum)
        String zoneName = zone.name();
        Integer currentCount = zoneSpawnCounts.get(zoneName);
        zoneSpawnCounts.put(zoneName, Integer.valueOf((currentCount != null ? currentCount.intValue() : 0) + 1));

        LOGGER.debug("Allocated spawn position for {} in zone {}: {}", mobId, zoneName, pos);
        return pos;
    }

    /**
     * Allocates spawn positions for multiple mobs, distributing them across zones.
     */
    public List<SpawnAllocation> allocateSpawnPositions(Map<ResourceLocation, Integer> mobCounts) {
        List<SpawnAllocation> allocations = new ArrayList<>();

        for (var entry : mobCounts.entrySet()) {
            ResourceLocation mobId = entry.getKey();
            int count = entry.getValue();

            ArenaZone zone = getZoneForMob(mobId);

            String zoneName = zone.name();
            for (int i = 0; i < count; i++) {
                BlockPos pos = zone.getRandomPosition(random, baseY);
                allocations.add(new SpawnAllocation(mobId, zoneName, pos));
                // Track spawn count (explicit to avoid null safety warnings with Integer::sum)
                Integer currentCount = zoneSpawnCounts.get(zoneName);
                zoneSpawnCounts.put(zoneName, Integer.valueOf((currentCount != null ? currentCount.intValue() : 0) + 1));
            }
        }

        return allocations;
    }

    /**
     * Pre-allocates spawn slots for a wave, ensuring even distribution.
     */
    public List<SpawnSlot> preAllocateSlots(List<ResourceLocation> mobIds, int slotsPerMob) {
        List<SpawnSlot> slots = new ArrayList<>();

        // Group mobs by zone
        Map<String, List<ResourceLocation>> mobsByZone = new HashMap<>();
        for (ResourceLocation mobId : mobIds) {
            ArenaZone zone = getZoneForMob(mobId);
            mobsByZone.computeIfAbsent(zone.name(), k -> new ArrayList<>()).add(mobId);
        }

        // Allocate slots for each zone
        for (var entry : mobsByZone.entrySet()) {
            String zoneName = entry.getKey();
            List<ResourceLocation> zoneMobs = entry.getValue();

            ArenaZone zone = layout.zones().stream()
                .filter(z -> z.name().equals(zoneName))
                .findFirst()
                .orElse(null);

            if (zone == null) continue;

            // Generate slots evenly distributed within zone
            List<BlockPos> positions = generateDistributedPositions(zone, zoneMobs.size() * slotsPerMob);

            int posIndex = 0;
            for (ResourceLocation mobId : zoneMobs) {
                for (int i = 0; i < slotsPerMob && posIndex < positions.size(); i++) {
                    slots.add(new SpawnSlot(mobId, zoneName, positions.get(posIndex++)));
                }
            }
        }

        return slots;
    }

    /**
     * Generates evenly distributed positions within a zone.
     */
    private List<BlockPos> generateDistributedPositions(ArenaZone zone, int count) {
        // Guard against division by zero when count is 0 or negative
        if (count <= 0) {
            return java.util.List.of();
        }

        List<BlockPos> positions = new ArrayList<>();

        // Use a grid-based distribution with jitter
        int gridSize = (int) Math.ceil(Math.sqrt(count));
        var bounds = zone.bounds();

        int stepX = Math.max(1, bounds.getWidth() / gridSize);
        int stepZ = Math.max(1, bounds.getDepth() / gridSize);

        for (int i = 0; i < count; i++) {
            int gridX = i % gridSize;
            int gridZ = i / gridSize;

            int baseX = bounds.x1() + gridX * stepX + stepX / 2;
            int baseZ = bounds.z1() + gridZ * stepZ + stepZ / 2;

            // Add jitter
            int jitterX = random.nextInt(Math.max(1, stepX / 2)) - stepX / 4;
            int jitterZ = random.nextInt(Math.max(1, stepZ / 2)) - stepZ / 4;

            BlockPos pos = new BlockPos(baseX + jitterX, baseY, baseZ + jitterZ);

            // Ensure position is within zone
            if (zone.contains(pos)) {
                positions.add(pos);
            } else {
                // Fallback to random position
                positions.add(zone.getRandomPosition(random, baseY));
            }
        }

        return java.util.List.copyOf(positions);
    }

    /**
     * Selects the zone with the fewest spawns for load balancing.
     */
    private ArenaZone selectLeastPopulatedZone() {
        // orElse would evaluate zones().get(0) on every call, which throws for an
        // empty layout instead of reporting the real problem.
        return layout.zones().stream()
            .min(Comparator.comparingInt(z -> zoneSpawnCounts.getOrDefault(z.name(), 0)))
            .orElseThrow(() -> new IllegalStateException("Zone layout contains no zones"));
    }

    /**
     * Assigns a mob to a specific zone (override).
     */
    public void assignMobToZone(ResourceLocation mobId, String zoneName) {
        layout.zones().stream()
            .filter(z -> z.name().equals(zoneName))
            .findFirst()
            .ifPresent(zone -> {
                mobZoneAssignments.put(mobId, zone);
                LOGGER.debug("Assigned {} to zone {}", mobId, zoneName);
            });
    }

    /**
     * Clears spawn counts for a new wave.
     */
    public void resetSpawnCounts() {
        zoneSpawnCounts.replaceAll((k, v) -> 0);
    }

    /**
     * Gets spawn statistics for each zone.
     */
    public Map<String, Integer> getSpawnCounts() {
        return Collections.unmodifiableMap(zoneSpawnCounts);
    }

    /**
     * Gets the layout this allocator is using.
     */
    public ZoneLayout getLayout() {
        return layout;
    }

    // ============== Data Classes ==============

    /**
     * Represents a single spawn allocation.
     */
    public record SpawnAllocation(
        ResourceLocation mobId,
        String zoneName,
        BlockPos position
    ) {}

    /**
     * Represents a pre-allocated spawn slot.
     */
    public record SpawnSlot(
        ResourceLocation mobId,
        String zoneName,
        BlockPos position
    ) {
        public SpawnAllocation toAllocation() {
            return new SpawnAllocation(mobId, zoneName, position);
        }
    }

    // ============== Factory Methods ==============

    /**
     * Creates an allocator for a single-zone layout.
     */
    public static ZoneSpawnSlotAllocator singleZone(int radius, int baseY) {
        ZoneLayout layout = ZoneLayout.single(radius, ZoneEnvironment.DEFAULT);
        return new ZoneSpawnSlotAllocator(layout, baseY);
    }

    /**
     * Creates an allocator from a planned layout.
     */
    public static ZoneSpawnSlotAllocator fromLayout(ZoneLayout layout, int baseY) {
        return new ZoneSpawnSlotAllocator(layout, baseY);
    }

    /**
     * Creates an allocator with automatic zone planning for the given mobs.
     */
    public static ZoneSpawnSlotAllocator forMobs(List<ResourceLocation> mobIds, int arenaRadius, int baseY) {
        ZoneLayoutPlanner planner = ZoneLayoutPlanner.create();
        ZoneLayout layout = planner.planLayout(mobIds, arenaRadius);
        return new ZoneSpawnSlotAllocator(layout, baseY);
    }
}
