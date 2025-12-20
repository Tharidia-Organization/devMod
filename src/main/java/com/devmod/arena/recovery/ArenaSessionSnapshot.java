package com.devmod.arena.recovery;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arena session snapshot with versioning for recovery (DD25).
 *
 * <p>Supports schema versioning and migration chain.
 */
public record ArenaSessionSnapshot(
    // DD25: Schema version for migration
    String schemaVersion,

    // Identity
    UUID arenaId,
    UUID sessionId,
    String templateId,
    int templateVersion,

    // Timing
    Instant createdAt,
    Instant snapshotAt,
    @Nullable Instant expiresAt,

    // State
    ArenaState state,
    @Nullable String instanceName,

    // Players
    List<UUID> players,
    @Nullable UUID owner,

    // Configuration snapshot
    @Nullable Map<String, Object> configSnapshot,

    // Recovery metadata
    boolean recoverable,
    @Nullable String recoveryHint
) {
    // Current schema version
    public static final String CURRENT_SCHEMA_VERSION = "2.0.0";

    // Supported migration paths
    private static final Map<String, String> MIGRATION_PATH = Map.of(
        "1.0.0", "1.1.0",
        "1.1.0", "2.0.0"
    );

    public enum ArenaState {
        BUILDING,
        READY,
        IN_USE,
        PAUSED,
        CLEANING_UP,
        CLOSED
    }

    /**
     * Creates a minimal snapshot for a new arena.
     */
    public static ArenaSessionSnapshot create(
            UUID arenaId,
            String templateId,
            int templateVersion) {
        return new ArenaSessionSnapshot(
            CURRENT_SCHEMA_VERSION,
            arenaId,
            UUID.randomUUID(),
            templateId,
            templateVersion,
            Instant.now(),
            Instant.now(),
            null,
            ArenaState.BUILDING,
            null,
            List.of(),
            null,
            null,
            true,
            null
        );
    }

    /**
     * Checks if this snapshot needs migration.
     */
    public boolean needsMigration() {
        return !CURRENT_SCHEMA_VERSION.equals(schemaVersion);
    }

    /**
     * Returns the migration path to current version.
     */
    public List<String> getMigrationPath() {
        if (!needsMigration()) {
            return List.of();
        }

        List<String> path = new java.util.ArrayList<>();
        String current = schemaVersion;

        while (!CURRENT_SCHEMA_VERSION.equals(current)) {
            String next = MIGRATION_PATH.get(current);
            if (next == null) {
                // No migration path available
                break;
            }
            path.add(next);
            current = next;
        }

        return path;
    }

    /**
     * Migrates this snapshot to the current schema version.
     */
    public ArenaSessionSnapshot migrate() {
        if (!needsMigration()) {
            return this;
        }

        // Apply migrations in order
        ArenaSessionSnapshot migrated = this;
        for (String targetVersion : getMigrationPath()) {
            migrated = applyMigration(migrated, targetVersion);
        }

        return migrated;
    }

    private static ArenaSessionSnapshot applyMigration(ArenaSessionSnapshot snapshot, String targetVersion) {
        return switch (targetVersion) {
            case "1.1.0" -> migrateV1_0_to_V1_1(snapshot);
            case "2.0.0" -> migrateV1_1_to_V2_0(snapshot);
            default -> snapshot;
        };
    }

    private static ArenaSessionSnapshot migrateV1_0_to_V1_1(ArenaSessionSnapshot old) {
        // Migration: added sessionId in v1.1
        return new ArenaSessionSnapshot(
            "1.1.0",
            old.arenaId,
            old.sessionId != null ? old.sessionId : UUID.randomUUID(),
            old.templateId,
            old.templateVersion,
            old.createdAt,
            old.snapshotAt,
            old.expiresAt,
            old.state,
            old.instanceName,
            old.players,
            old.owner,
            old.configSnapshot,
            old.recoverable,
            old.recoveryHint
        );
    }

    private static ArenaSessionSnapshot migrateV1_1_to_V2_0(ArenaSessionSnapshot old) {
        // Migration: added recoverable and recoveryHint in v2.0
        return new ArenaSessionSnapshot(
            "2.0.0",
            old.arenaId,
            old.sessionId,
            old.templateId,
            old.templateVersion,
            old.createdAt,
            Instant.now(), // Update snapshot time
            old.expiresAt,
            old.state,
            old.instanceName,
            old.players,
            old.owner,
            old.configSnapshot,
            true, // Default recoverable
            null
        );
    }

    /**
     * Creates a copy with updated state.
     */
    public ArenaSessionSnapshot withState(ArenaState newState) {
        return new ArenaSessionSnapshot(
            schemaVersion,
            arenaId,
            sessionId,
            templateId,
            templateVersion,
            createdAt,
            Instant.now(),
            expiresAt,
            newState,
            instanceName,
            players,
            owner,
            configSnapshot,
            recoverable,
            recoveryHint
        );
    }

    /**
     * Creates a copy marked as non-recoverable.
     */
    public ArenaSessionSnapshot markNonRecoverable(String reason) {
        return new ArenaSessionSnapshot(
            schemaVersion,
            arenaId,
            sessionId,
            templateId,
            templateVersion,
            createdAt,
            Instant.now(),
            expiresAt,
            state,
            instanceName,
            players,
            owner,
            configSnapshot,
            false,
            reason
        );
    }
}
