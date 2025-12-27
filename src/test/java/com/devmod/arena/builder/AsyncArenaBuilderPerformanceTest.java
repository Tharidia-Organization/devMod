package com.devmod.arena.builder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.telemetry.ArenaTelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncArenaBuilderPerformanceTest {

    @Test
    void pausesBuildWhenMsptIsSeverelyHigh() {
        CountingBlockPlacer blockPlacer = new CountingBlockPlacer();
        AsyncArenaBuilder builder = new AsyncArenaBuilder(
            new ArenaTelemetry(),
            blockPlacer,
            () -> 100.0
        );

        ArenaTemplate template = ArenaTemplate.defaultTemplate();
        builder.submitBuildAsync(UUID.randomUUID(), template, 0, 64, 0).isDone();

        builder.onTick();

        assertEquals(0, blockPlacer.getPlacedCount());
    }

    private static class CountingBlockPlacer implements ArenaBuilder.BlockPlacer {
        private final AtomicInteger placed = new AtomicInteger();

        @Override
        public int placeBlock(int x, int y, int z, String material) {
            placed.incrementAndGet();
            return 0;
        }

        int getPlacedCount() {
            return placed.get();
        }
    }
}
