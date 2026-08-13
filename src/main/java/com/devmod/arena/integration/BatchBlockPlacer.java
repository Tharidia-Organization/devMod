package com.devmod.arena.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import com.devmod.arena.builder.ArenaBuilder;

/**
 * P1-007: Batched block placement for improved arena build performance.
 *
 * Optimizations:
 * <ul>
 *   <li>Material caching to avoid repeated registry lookups</li>
 *   <li>Mutable BlockPos reuse to reduce allocations</li>
 *   <li>Batch flushing to chunk sections for locality</li>
 *   <li>Deferred lighting updates until batch completion</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * BatchBlockPlacer placer = new BatchBlockPlacer(level, 1000);
 * // Queue many blocks...
 * placer.placeBlock(x, y, z, "minecraft:stone");
 * // Flush remaining at end
 * placer.flush();
 * </pre>
 */
public class BatchBlockPlacer implements ArenaBuilder.BlockPlacer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchBlockPlacer.class);

    /** Block update flags: no neighbor updates (2), no block updates (16), no observer notify (64) */
    private static final int PLACEMENT_FLAGS = 2 | 16 | 64;

    /** Default batch size before auto-flush */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final ServerLevel level;
    private final int batchSize;
    private final Map<Long, BlockState> originalStates;
    private final Map<String, BlockState> materialCache;
    private final List<QueuedBlock> batchQueue;
    private final BlockPos.MutableBlockPos mutablePos;

    // Statistics
    private long totalPlaced = 0;
    private long batchFlushes = 0;
    private long cacheHits = 0;
    private long cacheMisses = 0;

    /**
     * A queued block awaiting placement.
     */
    private record QueuedBlock(int x, int y, int z, BlockState state, long packedPos, int originalStateId) {}

    /**
     * Creates a batch placer with default batch size.
     */
    public BatchBlockPlacer(ServerLevel level) {
        this(level, DEFAULT_BATCH_SIZE);
    }

    /**
     * Creates a batch placer with specified batch size.
     *
     * @param level The server level
     * @param batchSize Number of blocks to queue before auto-flush
     */
    public BatchBlockPlacer(ServerLevel level, int batchSize) {
        this.level = Objects.requireNonNull(level, "level");
        this.batchSize = Math.max(100, batchSize);
        this.originalStates = new ConcurrentHashMap<>();
        this.materialCache = new HashMap<>(64);
        this.batchQueue = new ArrayList<>(this.batchSize);
        this.mutablePos = new BlockPos.MutableBlockPos();

        // Pre-cache common materials
        precacheCommonMaterials();
    }

    private void precacheCommonMaterials() {
        String[] common = {
            "minecraft:air", "minecraft:stone", "minecraft:dirt",
            "minecraft:cobblestone", "minecraft:bedrock", "minecraft:obsidian",
            "minecraft:glass", "minecraft:barrier", "minecraft:void_air",
            "minecraft:glowstone", "minecraft:sea_lantern", "minecraft:torch",
            "minecraft:water", "minecraft:lava", "minecraft:iron_block",
            "minecraft:gold_block", "minecraft:diamond_block"
        };

        for (String material : common) {
            BlockState state = resolveBlockState(material);
            if (state != null) {
                materialCache.put(material, state);
            }
        }
    }

    @Override
    public int placeBlock(int x, int y, int z, String material) {
        // Resolve material with caching
        BlockState newState = getCachedBlockState(material);
        if (newState == null) {
            LOGGER.warn("Unknown material '{}', using air", material);
            newState = Blocks.AIR.defaultBlockState();
        }

        // Capture original state
        mutablePos.set(x, y, z);
        long packedPos = mutablePos.asLong();

        // Use immutable() + requireNonNull() to satisfy @Nonnull parameter requirement
        BlockState originalState = level.getBlockState(Objects.requireNonNull(mutablePos.immutable()));
        originalStates.putIfAbsent(packedPos, originalState);
        int originalStateId = Block.getId(originalState);

        // Queue the block
        batchQueue.add(new QueuedBlock(x, y, z, newState, packedPos, originalStateId));
        totalPlaced++;

        // Auto-flush when batch is full
        if (batchQueue.size() >= batchSize) {
            flush();
        }

        return originalStateId;
    }

    /**
     * Flushes all queued blocks to the world.
     * Call this when done placing blocks or when you need immediate updates.
     */
    public void flush() {
        if (batchQueue.isEmpty()) {
            return;
        }

        batchFlushes++;
        int count = batchQueue.size();

        // Group by chunk section for locality
        Map<Long, List<QueuedBlock>> bySection = groupBySection(batchQueue);

        // Process each section
        for (Map.Entry<Long, List<QueuedBlock>> entry : bySection.entrySet()) {
            flushSection(entry.getValue());
        }

        batchQueue.clear();

        if (LOGGER.isDebugEnabled() && batchFlushes % 10 == 0) {
            LOGGER.debug("[BatchBlockPlacer] Flushed {} blocks (total: {}, flushes: {})",
                count, totalPlaced, batchFlushes);
        }
    }

    private Map<Long, List<QueuedBlock>> groupBySection(List<QueuedBlock> blocks) {
        Map<Long, List<QueuedBlock>> bySection = new HashMap<>();

        for (QueuedBlock block : blocks) {
            // Calculate section coordinates
            int sectionX = SectionPos.blockToSectionCoord(block.x());
            int sectionY = SectionPos.blockToSectionCoord(block.y());
            int sectionZ = SectionPos.blockToSectionCoord(block.z());
            long sectionKey = SectionPos.asLong(sectionX, sectionY, sectionZ);

            bySection.computeIfAbsent(sectionKey, k -> new ArrayList<>()).add(block);
        }

        return bySection;
    }

    private void flushSection(List<QueuedBlock> blocks) {
        if (blocks.isEmpty()) return;

        // Get chunk for this section
        QueuedBlock first = blocks.get(0);
        int chunkX = SectionPos.blockToSectionCoord(first.x());
        int chunkZ = SectionPos.blockToSectionCoord(first.z());

        LevelChunk chunk = level.getChunk(chunkX, chunkZ);

        for (QueuedBlock block : blocks) {
            mutablePos.set(block.x(), block.y(), block.z());
            BlockState blockState = Objects.requireNonNull(block.state());
            BlockPos immutablePos = Objects.requireNonNull(mutablePos.immutable());

            // Direct section access for better performance. States carrying a block
            // entity must go through level.setBlock: a raw section write never creates
            // the BlockEntity, leaving the chunk inconsistent.
            int sectionIndex = chunk.getSectionIndex(block.y());
            if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()
                || blockState.hasBlockEntity()) {
                level.setBlock(immutablePos, blockState, PLACEMENT_FLAGS);
                continue;
            }

            LevelChunkSection section = chunk.getSection(sectionIndex);

            // Calculate local coordinates within section
            int localX = block.x() & 15;
            int localY = block.y() & 15;
            int localZ = block.z() & 15;

            BlockState oldState = section.setBlockState(localX, localY, localZ, blockState, false);

            // oldState may be null for first-time placement
            if (oldState != null && oldState.hasBlockEntity()) {
                chunk.removeBlockEntity(immutablePos);
            }

            // The raw section write bypasses Level.setBlock, so lighting and client
            // sync have to be requested explicitly.
            level.getChunkSource().getLightEngine().checkBlock(immutablePos);
            level.getChunkSource().blockChanged(immutablePos);
        }

        // Mark chunk dirty for saving
        chunk.setUnsaved(true);
    }

    @Override
    public boolean revertBlock(long packedPos, int stateId) {
        // BlockPos.of() returns non-null, but we use requireNonNull for @Nonnull compliance
        BlockPos pos = Objects.requireNonNull(BlockPos.of(packedPos));

        // Try cached original state first
        BlockState originalState = originalStates.remove(packedPos);
        if (originalState == null) {
            originalState = Block.stateById(stateId);
        }

        return level.setBlock(pos, Objects.requireNonNull(originalState), PLACEMENT_FLAGS);
    }

    /**
     * Gets a cached block state for a material, resolving if not cached.
     */
    @Nullable
    private BlockState getCachedBlockState(String material) {
        BlockState cached = materialCache.get(material);
        if (cached != null) {
            cacheHits++;
            return cached;
        }

        cacheMisses++;
        BlockState resolved = resolveBlockState(material);
        if (resolved != null) {
            // Cache for future use (limit cache size)
            if (materialCache.size() < 256) {
                materialCache.put(material, resolved);
            }
        }
        return resolved;
    }

    @Nullable
    private BlockState resolveBlockState(String material) {
        if (material == null || material.isEmpty()) {
            return null;
        }

        String fullName = material.contains(":") ? material : "minecraft:" + material;
        ResourceLocation loc = ResourceLocation.tryParse(fullName);
        if (loc == null) {
            return null;
        }

        Block block = BuiltInRegistries.BLOCK.get(loc);
        if (block == Blocks.AIR && !fullName.equals("minecraft:air")) {
            if (!BuiltInRegistries.BLOCK.containsKey(loc)) {
                return null;
            }
        }

        return block.defaultBlockState();
    }

    /**
     * Clears the original state cache.
     */
    public void clearCache() {
        originalStates.clear();
    }

    /**
     * Returns the number of tracked original states.
     */
    public int getTrackedStateCount() {
        return originalStates.size();
    }

    /**
     * Returns the server level.
     */
    public ServerLevel getLevel() {
        return level;
    }

    /**
     * Returns the server level (alias for MinecraftBlockPlacer API compatibility).
     */
    public ServerLevel level() {
        return level;
    }

    /**
     * Returns placement statistics.
     */
    public PlacementStats getStats() {
        return new PlacementStats(totalPlaced, batchFlushes, cacheHits, cacheMisses);
    }

    /**
     * Placement statistics record.
     */
    public record PlacementStats(
        long totalPlaced,
        long batchFlushes,
        long cacheHits,
        long cacheMisses
    ) {
        public double cacheHitRate() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }
    }
}
