package com.devmod.arena.failure;

/**
 * Types of arena failures.
 * DD46: Used for mapping to user-friendly messages.
 */
public enum FailureType {
    /** Template could not be found */
    TEMPLATE_NOT_FOUND,

    /** Template validation failed */
    TEMPLATE_INVALID,

    /** Arena build failed during execution */
    BUILD_FAILED,

    /** Spawn slot could not be resolved */
    SPAWN_FAILED,

    /** Arena initialization timeout */
    TIMEOUT,

    /** Circuit breaker is open due to repeated failures */
    CIRCUIT_OPEN,

    /** Resource exhaustion (memory, disk, etc.) */
    RESOURCE_EXHAUSTED,

    /** Network error during arena setup */
    NETWORK_ERROR,

    /** Configuration error */
    CONFIG_ERROR,

    /** Internal server error */
    INTERNAL_ERROR,

    /** Player count mismatch */
    PLAYER_COUNT_MISMATCH,

    /** Map not available */
    MAP_UNAVAILABLE,

    /** Permission denied */
    PERMISSION_DENIED,

    /** Arena is already in use */
    ARENA_IN_USE,

    /** Unknown or unclassified failure */
    UNKNOWN
}
