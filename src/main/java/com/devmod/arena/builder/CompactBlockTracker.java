package com.devmod.arena.builder;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
public class CompactBlockTracker {

    // DD8: Hard cap at 150k blocks
    public static final int MAX_TRACKED_BLOCKS = 150_000;

    // DD8: Using fastutil for memory efficiency and dynamic resizing
    private final LongArrayList positions;
    private final IntArrayList previousStateIds;
    private final LongOpenHashSet seenPositions;
    private final int maxCapacity;

    public CompactBlockTracker() {
        this(MAX_TRACKED_BLOCKS);
    }

    public CompactBlockTracker(int capacity) {
        this.maxCapacity = Math.min(capacity, MAX_TRACKED_BLOCKS);
        // DD8: Start with reasonable initial capacity, grow as needed
        int initialCapacity = Math.min(1000, maxCapacity);
        this.positions = new LongArrayList(initialCapacity);
        this.previousStateIds = new IntArrayList(initialCapacity);
        this.seenPositions = new LongOpenHashSet(initialCapacity);
    }

    /**
     * Tracks a block change.
     *
     * @param packedPos BlockPos packed as long
     * @param previousStateId Numeric ID of the previous block state
     * @throws BuildLimitExceededException if limit exceeded
     */
    public void track(long packedPos, int previousStateId) {
        if (seenPositions.contains(packedPos)) {
            return;
        }
        if (positions.size() >= maxCapacity) {
            throw new BuildLimitExceededException(
                BuildLimitExceededException.LimitType.BLOCKS,
                positions.size() + 1,
                MAX_TRACKED_BLOCKS
            );
        }
        // DD8: LongArrayList.add() handles dynamic resizing
        seenPositions.add(packedPos);
        positions.add(packedPos);
        previousStateIds.add(previousStateId);
    }

    /**
     * Returns tracked changes for rollback (in reverse order).
     */
    public List<BlockChange> getChangesReversed() {
        int currentSize = positions.size();
        List<BlockChange> changes = new ArrayList<>(currentSize);
        for (int i = currentSize - 1; i >= 0; i--) {
            changes.add(new BlockChange(positions.getLong(i), previousStateIds.getInt(i)));
        }
        return changes;
    }

    /**
     * Returns the number of tracked blocks.
     */
    public int size() {
        return positions.size();
    }

    /**
     * Clears all tracked changes.
     */
    public void clear() {
        positions.clear();
        previousStateIds.clear();
        seenPositions.clear();
    }

    /**
     * Returns estimated memory usage in bytes.
     */
    public long estimatedMemoryBytes() {
        // 8 bytes per long + 4 bytes per int = 12 bytes per entry
        return (long) positions.size() * 12L;
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
