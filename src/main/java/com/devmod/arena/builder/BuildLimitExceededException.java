package com.devmod.arena.builder;

/**
 * Exception thrown when a build exceeds the configured limits (DD8).
 */
public class BuildLimitExceededException extends RuntimeException {

    private final int attempted;
    private final int limit;
    private final LimitType type;

    public enum LimitType {
        BLOCKS,
        TIME,
        ENTITIES,
        MEMORY
    }

    public BuildLimitExceededException(String message) {
        super(message);
        this.attempted = -1;
        this.limit = -1;
        this.type = LimitType.BLOCKS;
    }

    public BuildLimitExceededException(LimitType type, int attempted, int limit) {
        super("%s limit exceeded: %d > %d".formatted(type, attempted, limit));
        this.attempted = attempted;
        this.limit = limit;
        this.type = type;
    }

    public int getAttempted() {
        return attempted;
    }

    public int getLimit() {
        return limit;
    }

    public LimitType getType() {
        return type;
    }
}
