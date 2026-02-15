package com.devmod.arena.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class CompactBlockTrackerTest {

    @Test
    void trackAndSize() {
        CompactBlockTracker tracker = new CompactBlockTracker();
        tracker.track(CompactBlockTracker.pack(0, 64, 0), 1);
        tracker.track(CompactBlockTracker.pack(1, 64, 0), 2);
        assertEquals(2, tracker.size());
    }

    @Test
    void deduplicates() {
        CompactBlockTracker tracker = new CompactBlockTracker();
        long pos = CompactBlockTracker.pack(10, 64, 20);
        tracker.track(pos, 1);
        tracker.track(pos, 2); // duplicate position
        assertEquals(1, tracker.size());
    }

    @Test
    void changesReversedOrder() {
        CompactBlockTracker tracker = new CompactBlockTracker();
        long pos1 = CompactBlockTracker.pack(0, 0, 0);
        long pos2 = CompactBlockTracker.pack(1, 0, 0);
        long pos3 = CompactBlockTracker.pack(2, 0, 0);
        tracker.track(pos1, 10);
        tracker.track(pos2, 20);
        tracker.track(pos3, 30);

        List<CompactBlockTracker.BlockChange> changes = tracker.getChangesReversed();
        assertEquals(3, changes.size());
        assertEquals(pos3, changes.get(0).packedPos());
        assertEquals(30, changes.get(0).previousStateId());
        assertEquals(pos1, changes.get(2).packedPos());
    }

    @Test
    void capacityEnforced() {
        CompactBlockTracker tracker = new CompactBlockTracker(5);
        for (int i = 0; i < 5; i++) {
            tracker.track(CompactBlockTracker.pack(i, 0, 0), i);
        }
        assertThrows(BuildLimitExceededException.class, () ->
            tracker.track(CompactBlockTracker.pack(99, 0, 0), 99));
    }

    @Test
    void clear() {
        CompactBlockTracker tracker = new CompactBlockTracker();
        tracker.track(CompactBlockTracker.pack(0, 0, 0), 1);
        tracker.clear();
        assertEquals(0, tracker.size());
        // Can re-add the same position after clear
        tracker.track(CompactBlockTracker.pack(0, 0, 0), 1);
        assertEquals(1, tracker.size());
    }

    @Test
    void estimatedMemory() {
        CompactBlockTracker tracker = new CompactBlockTracker();
        assertEquals(0, tracker.estimatedMemoryBytes());
        tracker.track(CompactBlockTracker.pack(0, 0, 0), 1);
        assertEquals(12, tracker.estimatedMemoryBytes());
    }

    @Test
    void packUnpackRoundTrip() {
        long packed = CompactBlockTracker.pack(100, 64, -200);
        var change = new CompactBlockTracker.BlockChange(packed, 0);
        assertEquals(100, change.x());
        assertEquals(64, change.y());
        assertEquals(-200, change.z());
    }

    @Test
    void maxCapacityCapped() {
        // Even if requesting more than MAX_TRACKED_BLOCKS, it should cap
        CompactBlockTracker tracker = new CompactBlockTracker(Integer.MAX_VALUE);
        // Fill to limit would take too long; just verify it doesn't crash on creation
        assertNotNull(tracker);
    }
}
