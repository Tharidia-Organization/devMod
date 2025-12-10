package com.frenkvs.devmod.endurance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Manages arena creation, barriers, and cleanup for Endurance Quests.
 * Arenas are 64x64 blocks (4 chunks) with invisible barriers.
 */
@SuppressWarnings({"null", "unused"}) // Minecraft APIs lack null annotations
public class ArenaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaManager.class);

    // Default arena configuration
    public static final int DEFAULT_ARENA_SIZE = 64; // 4 chunks
    public static final int ARENA_HEIGHT = 32; // Barrier height
    public static final int FLOOR_OFFSET = -1; // Floor below player

    // Active arenas
    private final Map<UUID, Arena> activeArenas = new ConcurrentHashMap<>();

    // Arena by position (for quick lookup)
    private final Map<ServerLevel, List<Arena>> arenasByLevel = new ConcurrentHashMap<>();

    /**
     * Represents an arena instance.
     */
    public static class Arena {
        private final UUID id;
        private final ServerLevel level;
        private final BlockPos center;
        private final int size;
        private final AABB bounds;
        private final long createdAt;

        // Arena state
        private boolean barrierActive = true;
        private final Set<BlockPos> modifiedBlocks = new HashSet<>();

        public Arena(ServerLevel level, BlockPos center, int size) {
            this.id = UUID.randomUUID();
            this.level = level;
            this.center = center;
            this.size = size;
            this.createdAt = System.currentTimeMillis();

            // Calculate bounds
            int halfSize = size / 2;
            this.bounds = new AABB(
                center.getX() - halfSize, center.getY() - 5, center.getZ() - halfSize,
                center.getX() + halfSize, center.getY() + ARENA_HEIGHT, center.getZ() + halfSize
            );
        }

        public UUID getId() { return id; }
        public ServerLevel getLevel() { return level; }
        public BlockPos getCenter() { return center; }
        public int getSize() { return size; }
        public AABB getBounds() { return bounds; }
        public long getCreatedAt() { return createdAt; }
        public boolean isBarrierActive() { return barrierActive; }

        public void setBarrierActive(boolean active) {
            this.barrierActive = active;
        }

        public void addModifiedBlock(BlockPos pos) {
            modifiedBlocks.add(pos.immutable());
        }

        public Set<BlockPos> getModifiedBlocks() {
            return Collections.unmodifiableSet(modifiedBlocks);
        }

        /**
         * Check if a position is inside this arena.
         */
        public boolean contains(@Nonnull Vec3 pos) {
            return bounds.contains(Objects.requireNonNull(pos));
        }

        /**
         * Check if a position is inside this arena.
         */
        public boolean contains(BlockPos pos) {
            BlockPos safePos = Objects.requireNonNull(pos);
            return bounds.contains(safePos.getX(), safePos.getY(), safePos.getZ());
        }

        /**
         * Get a random spawn position within the arena.
         */
        public BlockPos getRandomSpawnPos(Random random) {
            int halfSize = size / 2 - 2; // Keep away from walls
            int x = center.getX() + random.nextInt(halfSize * 2) - halfSize;
            int z = center.getZ() + random.nextInt(halfSize * 2) - halfSize;
            return new BlockPos(x, center.getY(), z);
        }

        /**
         * Get spawn positions distributed around the arena.
         * Validates each position to ensure mobs can spawn there.
         */
        public List<BlockPos> getDistributedSpawnPositions(int count) {
            List<BlockPos> positions = new ArrayList<>();
            int halfSize = Math.max(1, size / 2 - 3); // Ensure at least 1 to avoid issues with tiny arenas

            // Distribute evenly in a grid pattern
            int gridSize = (int) Math.ceil(Math.sqrt(count));
            int spacing = Math.max(2, (halfSize * 2) / (gridSize + 1)); // Minimum 2 block spacing

            Random random = new Random();

            // Calculate arena boundaries for clamping (1 block inside walls)
            int minX = center.getX() - size / 2 + 1;
            int maxX = center.getX() + size / 2 - 1;
            int minZ = center.getZ() - size / 2 + 1;
            int maxZ = center.getZ() + size / 2 - 1;

            for (int i = 0; i < count; i++) {
                int gridX = i % gridSize;
                int gridZ = i / gridSize;

                int baseX = center.getX() - halfSize + spacing + (gridX * spacing);
                int baseZ = center.getZ() - halfSize + spacing + (gridZ * spacing);

                // Clamp to arena bounds to prevent spawns outside arena
                baseX = Math.max(minX, Math.min(maxX, baseX));
                baseZ = Math.max(minZ, Math.min(maxZ, baseZ));

                // Find a valid spawn position (with floor below and air above)
                BlockPos validPos = findValidSpawnPosition(baseX, baseZ, random);
                if (validPos != null) {
                    positions.add(validPos);
                }
            }

            // If we couldn't find enough valid positions, add more using random positions
            int attempts = 0;
            while (positions.size() < count && attempts < count * 3) {
                attempts++;
                int x = center.getX() + random.nextInt(Math.max(1, halfSize * 2)) - halfSize;
                int z = center.getZ() + random.nextInt(Math.max(1, halfSize * 2)) - halfSize;

                // Clamp to arena bounds
                x = Math.max(minX, Math.min(maxX, x));
                z = Math.max(minZ, Math.min(maxZ, z));

                BlockPos validPos = findValidSpawnPosition(x, z, random);
                if (validPos != null && !positions.contains(validPos)) {
                    positions.add(validPos);
                }
            }

            return positions;
        }

        /**
         * Find a valid spawn position at the given X/Z coordinates.
         * Returns null if no valid position can be found.
         */
        private BlockPos findValidSpawnPosition(int x, int z, Random random) {
            // Start from arena floor level and check upward
            int startY = center.getY();

            for (int yOffset = 0; yOffset < 5; yOffset++) {
                BlockPos checkPos = Objects.requireNonNull(new BlockPos(x, startY + yOffset, z));

                // Check if this position is valid for spawning:
                // - Block at feet level should be air
                // - Block at head level should be air
                // - Block below should be solid (not air)
                BlockPos below = Objects.requireNonNull(checkPos.below());
                BlockPos head = Objects.requireNonNull(checkPos.above());

                BlockState feetState = level.getBlockState(checkPos);
                BlockState headState = level.getBlockState(head);
                BlockState belowState = level.getBlockState(below);

                boolean feetClear = feetState.isAir() || feetState.getBlock() == Blocks.BARRIER;
                boolean headClear = headState.isAir() || headState.getBlock() == Blocks.BARRIER;
                boolean floorSolid = !belowState.isAir();

                if (feetClear && headClear && floorSolid) {
                    return checkPos;
                }
            }

            // Fallback: just use the arena floor level
            return new BlockPos(x, startY, z);
        }
    }

    /**
     * Create a new arena at the specified location.
     */
    public Arena createArena(ServerLevel level, BlockPos playerPos, int size) {
        // Find suitable center position (flat area)
        BlockPos center = Objects.requireNonNullElse(findSuitableCenter(level, playerPos, size), playerPos);

        Arena arena = new Arena(level, center, size);

        // Build the arena
        buildArenaFloor(arena);
        buildArenaBarriers(arena);

        // IMPORTANT: Clear all pre-existing mobs from arena area before registering
        clearPreExistingMobs(arena);

        // Register arena
        activeArenas.put(arena.getId(), arena);
        arenasByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(arena);

        LOGGER.info("[EnduranceQuest] Created arena {} at {} (size: {}x{})",
            arena.getId(), center, size, size);

        return arena;
    }

    /**
     * Remove all pre-existing mobs from the arena area.
     * Called when arena is first created to ensure a clean battlefield.
     */
    private void clearPreExistingMobs(Arena arena) {
        ServerLevel level = arena.getLevel();
        int removedCount = 0;

        // Get all mobs in arena bounds
        List<net.minecraft.world.entity.Entity> entities = level.getEntities(
            (net.minecraft.world.entity.Entity) null,
            Objects.requireNonNull(arena.getBounds()),
            entity -> entity instanceof net.minecraft.world.entity.Mob
        );

        for (net.minecraft.world.entity.Entity entity : entities) {
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                // Remove the mob (discard = no drops, no death event)
                mob.discard();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            LOGGER.info("[EnduranceQuest] Cleared {} pre-existing mobs from arena area", removedCount);
        }
    }

    /**
     * Find a suitable flat area for the arena.
     */
    private BlockPos findSuitableCenter(ServerLevel level, BlockPos start, int size) {
        // For simplicity, use a position slightly above ground at player location
        // In a real implementation, you'd scan for flat terrain

        // Find ground level
        BlockPos groundCheck = start;
        while (groundCheck.getY() > level.getMinBuildHeight() &&
               level.getBlockState(groundCheck).isAir()) {
            groundCheck = groundCheck.below();
        }

        // Position arena floor at ground level + 1
        return new BlockPos(start.getX(), groundCheck.getY() + 1, start.getZ());
    }

    /**
     * Build arena floor (stone brick platform).
     */
    private void buildArenaFloor(Arena arena) {
        ServerLevel level = arena.getLevel();
        BlockPos center = arena.getCenter();
        int halfSize = arena.getSize() / 2;

        BlockState floorBlock = Objects.requireNonNull(Blocks.STONE_BRICKS.defaultBlockState());

        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                BlockPos pos = Objects.requireNonNull(center.offset(x, FLOOR_OFFSET, z));
                level.setBlock(pos, floorBlock, 3);
                arena.addModifiedBlock(pos);
            }
        }
    }

    /**
     * Build invisible barriers around the arena.
     */
    private void buildArenaBarriers(Arena arena) {
        ServerLevel level = arena.getLevel();
        BlockPos center = arena.getCenter();
        int halfSize = arena.getSize() / 2;

        BlockState barrier = Objects.requireNonNull(Blocks.BARRIER.defaultBlockState());

        // Build walls
        for (int y = 0; y < ARENA_HEIGHT; y++) {
            for (int i = -halfSize; i <= halfSize; i++) {
                // North wall
                BlockPos north = Objects.requireNonNull(center.offset(i, y, -halfSize));
                level.setBlock(north, barrier, 3);
                arena.addModifiedBlock(north);

                // South wall
                BlockPos south = Objects.requireNonNull(center.offset(i, y, halfSize));
                level.setBlock(south, barrier, 3);
                arena.addModifiedBlock(south);

                // East wall
                BlockPos east = Objects.requireNonNull(center.offset(halfSize, y, i));
                level.setBlock(east, barrier, 3);
                arena.addModifiedBlock(east);

                // West wall
                BlockPos west = Objects.requireNonNull(center.offset(-halfSize, y, i));
                level.setBlock(west, barrier, 3);
                arena.addModifiedBlock(west);
            }
        }

        // Build ceiling
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                BlockPos pos = Objects.requireNonNull(center.offset(x, ARENA_HEIGHT, z));
                level.setBlock(pos, barrier, 3);
                arena.addModifiedBlock(pos);
            }
        }
    }

    /**
     * Destroy an arena and restore the area.
     */
    public void destroyArena(Arena arena) {
        if (arena == null) return;

        ServerLevel level = arena.getLevel();

        // Remove all modified blocks
        for (BlockPos pos : arena.getModifiedBlocks()) {
            BlockPos safePos = Objects.requireNonNull(pos);
            BlockState air = Objects.requireNonNull(Blocks.AIR.defaultBlockState());
            level.setBlock(safePos, air, 3);
        }

        // Unregister arena
        activeArenas.remove(arena.getId());
        List<Arena> levelArenas = arenasByLevel.get(level);
        if (levelArenas != null) {
            levelArenas.remove(arena);
        }

        LOGGER.info("[EnduranceQuest] Destroyed arena {}", arena.getId());
    }

    /**
     * Teleport a player to the center of an arena.
     */
    public void teleportToArena(ServerPlayer player, Arena arena) {
        BlockPos center = arena.getCenter();
        player.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
    }

    /**
     * Check if a position is inside any active arena.
     */
    public Optional<Arena> getArenaAt(ServerLevel level, Vec3 pos) {
        Vec3 safePos = Objects.requireNonNull(pos);
        List<Arena> levelArenas = arenasByLevel.get(level);
        if (levelArenas != null) {
            for (Arena arena : levelArenas) {
                if (arena.contains(safePos)) {
                    return Optional.of(arena);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Get arena by ID.
     */
    public Optional<Arena> getArena(UUID arenaId) {
        return Optional.ofNullable(activeArenas.get(arenaId));
    }

    /**
     * Get all active arenas.
     */
    public Collection<Arena> getAllArenas() {
        return Collections.unmodifiableCollection(activeArenas.values());
    }

    /**
     * Toggle barrier visibility for an arena.
     */
    public void setBarrierActive(Arena arena, boolean active) {
        arena.setBarrierActive(active);
        // In a real implementation, you might use structure void or similar
        // for "passable" barriers, or particle effects to show boundary
    }

    /**
     * Clean up all arenas (server shutdown).
     */
    public void cleanupAll() {
        for (Arena arena : new ArrayList<>(activeArenas.values())) {
            destroyArena(arena);
        }
        activeArenas.clear();
        arenasByLevel.clear();
    }
}
