package com.devmod.arena.concurrency;

import java.time.Duration;
import java.time.Instant;

public sealed interface BuildPermit permits BuildPermit.Granted, BuildPermit.Rejected {

    /**
     * Returns true if this permit allows the build to proceed.
     */
    boolean isGranted();

    /**
     * A granted build permit.
     *
     * @param permitId Unique permit identifier
     * @param grantedAt When the permit was granted
     * @param waitTimeMs How long the request waited in queue
     */
    record Granted(
        String permitId,
        Instant grantedAt,
        long waitTimeMs
    ) implements BuildPermit {
        @Override
        public boolean isGranted() {
            return true;
        }

        /**
         * Creates a new granted permit.
         */
        public static Granted now(String permitId, long waitTimeMs) {
            return new Granted(permitId, Instant.now(), waitTimeMs);
        }
    }

    /**
     * A rejected build permit.
     *
     * @param reason Why the permit was rejected
     * @param retryAfter How long to wait before retrying
     * @param queuePosition Position in queue (if queued)
     */
    record Rejected(
        RejectionReason reason,
        Duration retryAfter,
        int queuePosition
    ) implements BuildPermit {
        @Override
        public boolean isGranted() {
            return false;
        }

        /**
         * Creates a rejection for queue full.
         */
        public static Rejected queueFull(Duration retryAfter) {
            return new Rejected(RejectionReason.QUEUE_FULL, retryAfter, -1);
        }

        /**
         * Creates a rejection for timeout.
         */
        public static Rejected timeout(int queuePosition) {
            return new Rejected(RejectionReason.TIMEOUT, Duration.ofSeconds(30), queuePosition);
        }

        /**
         * Creates a rejection for rate limited.
         */
        public static Rejected rateLimited(Duration retryAfter, int queuePosition) {
            return new Rejected(RejectionReason.RATE_LIMITED, retryAfter, queuePosition);
        }
    }

    /**
     * Reasons for permit rejection.
     */
    enum RejectionReason {
        /** Queue is at maximum capacity */
        QUEUE_FULL,
        /** Request timed out waiting for permit */
        TIMEOUT,
        /** Rate limit exceeded */
        RATE_LIMITED
    }
}
