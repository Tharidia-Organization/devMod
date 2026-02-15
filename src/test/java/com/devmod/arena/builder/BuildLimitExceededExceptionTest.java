package com.devmod.arena.builder;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BuildLimitExceededExceptionTest {

    @Test
    void simpleConstructor() {
        BuildLimitExceededException ex = new BuildLimitExceededException("test message");
        assertEquals("test message", ex.getMessage());
        assertEquals(-1, ex.getAttempted());
        assertEquals(-1, ex.getLimit());
        assertEquals(BuildLimitExceededException.LimitType.BLOCKS, ex.getType());
    }

    @Test
    void typedConstructor() {
        BuildLimitExceededException ex = new BuildLimitExceededException(
            BuildLimitExceededException.LimitType.BLOCKS, 200_000, 150_000);
        assertEquals(200_000, ex.getAttempted());
        assertEquals(150_000, ex.getLimit());
        assertEquals(BuildLimitExceededException.LimitType.BLOCKS, ex.getType());
        assertTrue(ex.getMessage().contains("BLOCKS"));
        assertTrue(ex.getMessage().contains("200000"));
        assertTrue(ex.getMessage().contains("150000"));
    }

    @Test
    void limitTypes() {
        assertEquals(4, BuildLimitExceededException.LimitType.values().length);
        assertNotNull(BuildLimitExceededException.LimitType.valueOf("BLOCKS"));
        assertNotNull(BuildLimitExceededException.LimitType.valueOf("TIME"));
        assertNotNull(BuildLimitExceededException.LimitType.valueOf("ENTITIES"));
        assertNotNull(BuildLimitExceededException.LimitType.valueOf("MEMORY"));
    }

    @Test
    void isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new BuildLimitExceededException("test"));
    }
}
