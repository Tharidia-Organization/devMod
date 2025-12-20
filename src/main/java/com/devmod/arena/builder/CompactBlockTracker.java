package com.devmod.arena.builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory-efficient block change tracker using packed long positions (DD8).
 *
 * <p>Memory budget:
 * <ul>
 *   <li>50,000 blocks = ~1.2MB</li>
 *   <li>100,000 blocks = ~2.4MB</li>
 *   <li>150,000 blocks = ~3.6MB (hard cap)</li>
 * </ul>
 *
 * <p>Uses BlockPos.asLong() format: (x << 38) | (y << 26) | (z << 0)
 */
public class CompactBlockTracker {

    // DD8: Hard cap at 150k blocks
    public static final int MAX_TRACKED_BLOCKS = 150_000;

    // Packed positions (BlockPos.asLong format)
    private final long[] positions;
    // Block state IDs (registry numeric ID)
    private final int[] previousStateIds;
    // Current size
    private int size = 0;

    public CompactBlockTracker() {
        this(MAX_TRACKED_BLOCKS);
    }

    public CompactBlockTracker(int capacity) {
        this.positions = new long[Math.min(capacity, MAX_TRACKED_BLOCKS)];
        this.previousStateIds = new int[Math.min(capacity, MAX_TRACKED_BLOCKS)];
    }

    /**
     * Tracks a block change.
     *
     * @param packedPos BlockPos packed as long
     * @param previousStateId Numeric ID of the previous block state
     * @throws BuildLimitExceededException if limit exceeded
     */
    public void track(long packedPos, int previousStateId) {
        if (size >= positions.length) {
            throw new BuildLimitExceededException(
                BuildLimitExceededException.LimitType.BLOCKS,
                size + 1,
                MAX_TRACKED_BLOCKS
            );
        }
        positions[size] = packedPos;
        previousStateIds[size] = previousStateId;
        size++;
    }

    /**
     * Returns tracked changes for rollback (in reverse order).
     */
    public List<BlockChange> getChangesReversed() {
        List<BlockChange> changes = new ArrayList<>(size);
        for (int i = size - 1; i >= 0; i--) {
            changes.add(new BlockChange(positions[i], previousStateIds[i]));
        }
        return changes;
    }

    /**
     * Returns the number of tracked blocks.
     */
    public int size() {
        return size;
    }

    /**
     * Clears all tracked changes.
     */
    public void clear() {
        size = 0;
    }

    /**
     * Returns estimated memory usage in bytes.
     */
    public long estimatedMemoryBytes() {
        // 8 bytes per long + 4 bytes per int = 12 bytes per entry
        return (long) size * 12L;
    }

    /**
     * Represents a single block change.
     */
    public record BlockChange(long packedPos, int previousStateId) {
        /**
         * Unpacks X coordinate from packed position.
         */
        public int x() {
            return (int) (packedPos >> 38);
        }

        /**
         * Unpacks Y coordinate from packed position.
         */
        public int y() {
            return (int) ((packedPos >> 26) & 0xFFF);
        }

        /**
         * Unpacks Z coordinate from packed position.
         */
        public int z() {
            return (int) (packedPos << 38 >> 38);
        }
    }

    /**
     * Packs coordinates into a long (same format as BlockPos.asLong).
     */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) << 26 | ((long) z & 0x3FFFFFF);
    }
}
