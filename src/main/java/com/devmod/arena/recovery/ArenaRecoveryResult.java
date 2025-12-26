package com.devmod.arena.recovery;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Sealed interface for arena recovery results (DD27).
 *
 * <p>Represents the outcome of attempting to recover an arena from snapshot.
 */
public sealed interface ArenaRecoveryResult {

    /**
     * Returns whether the recovery was successful (full or degraded).
     */
    boolean isUsable();

    /**
     * Returns the arena ID if recovery produced a usable result.
     */
    @Nullable UUID arenaId();

    /**
     * Full recovery - arena restored exactly as before.
     */
    record FullRecovery(
        UUID arenaId,
        String templateId,
        int templateVersion,
        long recoveryDurationMs
    ) implements ArenaRecoveryResult {
        @Override
        public boolean isUsable() {
            return true;
        }

        @Override
        public UUID arenaId() {
            return arenaId;
        }
    }

    /**
     * Degraded recovery - arena restored with fallback template (DD27).
     *
     * <p>This happens when:
     * <ul>
     *   <li>Original template is missing</li>
     *   <li>Template version mismatch (breaking change)</li>
     *   <li>Template validation fails</li>
     * </ul>
     */
    record DegradedRecovery(
        UUID arenaId,
        String originalTemplateId,
        String fallbackTemplateId,
        String reason,
        List<String> warnings,
        long recoveryDurationMs
    ) implements ArenaRecoveryResult {
        @Override
        public boolean isUsable() {
            return true;
        }

        @Override
        public UUID arenaId() {
            return arenaId;
        }

        /**
         * Convenience constructor with single warning.
         */
        public static DegradedRecovery withWarning(
                UUID arenaId,
                String originalTemplateId,
                String fallbackTemplateId,
                String reason,
                long durationMs) {
            return new DegradedRecovery(
                arenaId,
                originalTemplateId,
                fallbackTemplateId,
                reason,
                List.of(reason),
                durationMs
            );
        }
    }

    /**
     * Failed recovery - arena could not be restored.
     */
    record FailedRecovery(
        String snapshotId,
        String reason,
        @Nullable Throwable cause
    ) implements ArenaRecoveryResult {
        @Override
        public boolean isUsable() {
            return false;
        }

        @Override
        public UUID arenaId() {
            return null;
        }

        /**
         * Convenience constructor without cause.
         */
        public static FailedRecovery of(String snapshotId, String reason) {
            return new FailedRecovery(snapshotId, reason, null);
        }

        /**
         * Convenience constructor with exception.
         */
        public static FailedRecovery fromException(String snapshotId, Exception e) {
            return new FailedRecovery(snapshotId, e.getMessage(), e);
        }
    }

    /**
     * Snapshot not found - nothing to recover.
     */
    record NotFound(
        String requestedSnapshotId
    ) implements ArenaRecoveryResult {
        @Override
        public boolean isUsable() {
            return false;
        }

        @Override
        public UUID arenaId() {
            return null;
        }
    }

    /**
     * Recovery skipped - snapshot expired or marked as non-recoverable.
     */
    record Skipped(
        String snapshotId,
        String reason
    ) implements ArenaRecoveryResult {
        @Override
        public boolean isUsable() {
            return false;
        }

        @Override
        public UUID arenaId() {
            return null;
        }
    }
}
