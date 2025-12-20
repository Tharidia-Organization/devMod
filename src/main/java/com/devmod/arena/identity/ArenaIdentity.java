package com.devmod.arena.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable arena identity (DD58).
 *
 * <p>The arenaId (roomId) is immutable once created.
 * For reconnection scenarios, use separate sessionId tracking.
 *
 * @param arenaId The immutable arena identifier
 * @param templateId The template used to create this arena
 * @param createdAt Timestamp when the arena was created
 * @param region The region where this arena is hosted
 */
public record ArenaIdentity(
    UUID arenaId,
    String templateId,
    Instant createdAt,
    String region
) {
    public ArenaIdentity {
        Objects.requireNonNull(arenaId, "arenaId must not be null");
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(region, "region must not be null");
    }

    /**
     * Returns the roomId (alias for arenaId for legacy compatibility).
     */
    public UUID roomId() {
        return arenaId;
    }

    /**
     * Creates a new ArenaIdentity with current timestamp.
     */
    public static ArenaIdentity create(UUID arenaId, String templateId, String region) {
        return new ArenaIdentity(arenaId, templateId, Instant.now(), region);
    }

    /**
     * Creates a new ArenaIdentity with a random UUID.
     */
    public static ArenaIdentity createNew(String templateId, String region) {
        return new ArenaIdentity(UUID.randomUUID(), templateId, Instant.now(), region);
    }
}
