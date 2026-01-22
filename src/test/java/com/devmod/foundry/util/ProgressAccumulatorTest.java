package com.devmod.foundry.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressAccumulatorTest {

    @Test
    void accumulatesFractionalProgress() {
        ProgressAccumulator accumulator = new ProgressAccumulator();
        int total = 0;
        for (int i = 0; i < 4; i++) {
            total += accumulator.accumulate(0.25f);
        }
        assertEquals(1, total);
        assertEquals(0f, accumulator.getRemainder(), 1e-6f);
    }

    @Test
    void carriesRemainderAcrossTicks() {
        ProgressAccumulator accumulator = new ProgressAccumulator();
        int first = accumulator.accumulate(1.5f);
        int second = accumulator.accumulate(1.5f);
        assertEquals(1, first);
        assertEquals(2, second);
        assertEquals(0f, accumulator.getRemainder(), 1e-6f);
    }

    @Test
    void nonPositiveSpeedResetsRemainder() {
        ProgressAccumulator accumulator = new ProgressAccumulator();
        accumulator.setRemainder(0.6f);
        int delta = accumulator.accumulate(0f);
        assertEquals(0, delta);
        assertEquals(0f, accumulator.getRemainder(), 1e-6f);
    }

    @Test
    void setRemainderNormalizesValues() {
        ProgressAccumulator accumulator = new ProgressAccumulator();
        accumulator.setRemainder(1.75f);
        assertEquals(0.75f, accumulator.getRemainder(), 1e-6f);
        accumulator.setRemainder(-0.5f);
        assertEquals(0f, accumulator.getRemainder(), 1e-6f);
    }
}
