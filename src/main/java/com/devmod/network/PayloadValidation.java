package com.devmod.network;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadHandler;

/**
 * P0-002: Network payload validation layer.
 *
 * Provides:
 * - Payload size validation (prevents oversized packets from being processed)
 * - Rate limiting per player (prevents packet flooding)
 * - Logging of violations for security monitoring
 *
 * Usage:
 * Instead of:
 *   event.registrar(...).playToServer(TYPE, CODEC, MyHandler::handle);
 *
 * Use:
 *   event.registrar(...).playToServer(TYPE, CODEC,
 *       PayloadValidation.validated(MyHandler::handle, PayloadLimits.MAILBOX));
 */
public final class PayloadValidation {
    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadValidation.class);

    /**
     * Predefined payload limits for different payload categories.
     */
    public enum PayloadLimits {
        /** Small control packets (actions, toggles, simple requests) */
        SMALL(1024, 100, 60_000),            // 1KB, 100/min

        /** Medium data packets (config updates, single entity data) */
        MEDIUM(8192, 60, 60_000),             // 8KB, 60/min

        /** Large data packets (batch updates, telemetry) */
        LARGE(32768, 30, 60_000),             // 32KB, 30/min

        /** Extra large packets (bulk sync, complex data) */
        XLARGE(65536, 15, 60_000),            // 64KB, 15/min

        /** Medium sync packets (bulk lists, client state) */
        SYNC_MEDIUM(524_288, 30, 60_000),     // 512KB, 30/min

        /** Large sync packets (mailbox/news full sync) */
        SYNC_LARGE(2_097_152, 10, 60_000),    // 2MB, 10/min

        /** Mailbox system limits */
        MAILBOX(16384, 30, 60_000),           // 16KB, 30/min

        /** Telemetry batch limits */
        TELEMETRY(65536, 10, 60_000),         // 64KB, 10/min

        /** Ticket system limits */
        TICKET(32768, 20, 60_000),            // 32KB, 20/min

        /** Quest/party actions */
        QUEST_ACTION(2048, 600, 60_000),      // 2KB, 600/min (10/sec)

        /** Item editor updates */
        EDITOR(16384, 60, 60_000),            // 16KB, 60/min

        /** No validation (for trusted internal payloads) */
        NONE(Integer.MAX_VALUE, Integer.MAX_VALUE, 60_000);

        public final int maxSizeBytes;
        public final int maxRequestsPerWindow;
        public final long windowMs;

        PayloadLimits(int maxSizeBytes, int maxRequestsPerWindow, long windowMs) {
            this.maxSizeBytes = maxSizeBytes;
            this.maxRequestsPerWindow = maxRequestsPerWindow;
            this.windowMs = windowMs;
        }
    }

    /**
     * Rate limiter state per player per payload type.
     */
    private static final Map<String, RateLimiterState> rateLimiters = new ConcurrentHashMap<>();

    /**
     * P1: Consecutive violation tracker per player for disconnect policy.
     * Key: player UUID, Value: consecutive violation count
     */
    private static final Map<UUID, AtomicInteger> playerViolations = new ConcurrentHashMap<>();

    /**
     * P1: Disconnect policy thresholds.
     */
    private static final int DISCONNECT_THRESHOLD = 10; // Disconnect after 10 consecutive violations
    private static final long VIOLATION_RESET_MS = 60_000; // Reset after 1 minute of no violations
    private static final Map<UUID, AtomicLong> lastViolationTime = new ConcurrentHashMap<>();
    private static final AtomicLong totalDisconnects = new AtomicLong(0);

    // P0: Metrics counters for payload rejection telemetry
    private static final Map<String, AtomicLong> sizeRejectionCounters = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> rateLimitRejectionCounters = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> ipRateLimitRejectionCounters = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> maxSizeOverLimitBytes = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> maxRejectedSizeBytes = new ConcurrentHashMap<>();
    private static final AtomicLong totalSizeRejections = new AtomicLong(0);
    private static final AtomicLong totalRateLimitRejections = new AtomicLong(0);
    private static final AtomicLong totalIpRateLimitRejections = new AtomicLong(0);
    private static final AtomicLong totalPayloadsProcessed = new AtomicLong(0);

    /**
     * Wraps a payload handler with validation.
     *
     * @param handler The original handler
     * @param limits The validation limits to apply
     * @return A validated handler that checks size and rate before delegating
     */
    @Nonnull
    public static <T extends CustomPacketPayload> IPayloadHandler<T> validated(
            @Nonnull IPayloadHandler<T> handler,
            @Nonnull PayloadLimits limits) {
        return (payload, context) -> {
            // Skip validation for NONE limits
            if (limits == PayloadLimits.NONE) {
                handler.handle(payload, context);
                return;
            }

            // Get player for rate limiting
            UUID playerId = null;
            ServerPlayer serverPlayer = null;
            if (context.player() instanceof ServerPlayer sp) {
                serverPlayer = sp;
                playerId = sp.getUUID();
            }

            String payloadType = payload.type().id().toString();

            // P0: Per-IP rate limiting (catches multi-account abuse and pre-auth attacks)
            if (serverPlayer != null && playerId != null) {
                String payloadCategory = payload.type().id().getPath();
                if (!IpRateLimiter.INSTANCE.checkRateLimit(serverPlayer, payloadCategory)) {
                    recordIpRateLimitRejection(payloadType);
                    LOGGER.warn("[SECURITY] IP rate limit exceeded: type={}, player={}",
                        payload.type().id(), playerId);
                    recordViolationAndMaybeDisconnect(serverPlayer, playerId, "IP rate limit");
                    return;
                }
            }

            // Validate size (estimate based on payload type)
            int estimatedSize = estimatePayloadSize(payload);
            if (estimatedSize >= 0 && estimatedSize > limits.maxSizeBytes) {
                recordSizeRejection(payloadType, estimatedSize, limits.maxSizeBytes);
                LOGGER.warn("[SECURITY] Oversized payload rejected: type={}, size~={}, limit={}, player={}",
                    payload.type().id(), estimatedSize, limits.maxSizeBytes,
                    playerId != null ? playerId : "unknown");
                if (serverPlayer != null && playerId != null) {
                    recordViolationAndMaybeDisconnect(serverPlayer, playerId, "oversized payload");
                }
                return;
            }

            // Per-player rate limit validation
            if (playerId != null) {
                String key = playerId + ":" + payload.type().id().toString();
                if (!checkRateLimit(key, limits)) {
                    recordRateLimitRejection(payloadType);
                    LOGGER.warn("[SECURITY] Player rate limit exceeded: type={}, player={}",
                        payload.type().id(), playerId);
                    if (serverPlayer != null) {
                        recordViolationAndMaybeDisconnect(serverPlayer, playerId, "rate limit");
                    }
                    return;
                }
            }

            // All validations passed - reset violation counter
            if (playerId != null) {
                resetViolations(playerId);
            }

            // All validations passed, delegate to original handler
            totalPayloadsProcessed.incrementAndGet();
            handler.handle(payload, context);
        };
    }

    /**
     * Estimates the size of a payload.
     *
     * Returns -1 for unknown sizes to skip size validation. Implementations
     * should implement SizedPayload for accurate size checks.
     */
    private static int estimatePayloadSize(CustomPacketPayload payload) {
        if (payload instanceof SizedPayload sized) {
            return sized.estimatedSize();
        }
        // Unknown size; skip size check for this payload.
        return -1;
    }

    /**
     * Checks and updates rate limit for a key.
     *
     * @return true if request is allowed, false if rate limited
     */
    private static boolean checkRateLimit(String key, PayloadLimits limits) {
        RateLimiterState state = rateLimiters.computeIfAbsent(key,
            k -> new RateLimiterState(limits.windowMs));
        return state.tryAcquire(limits.maxRequestsPerWindow);
    }

    /**
     * Clean up stale rate limiter entries.
     * Should be called periodically (e.g., every minute from server tick).
     */
    public static void cleanupStaleEntries() {
        long now = System.currentTimeMillis();
        rateLimiters.entrySet().removeIf(entry ->
            now - entry.getValue().lastAccessTime.get() > 300_000); // 5 minutes
    }

    /**
     * Interface for payloads that can report their size.
     * Implement this for accurate size validation.
     */
    public interface SizedPayload {
        /**
         * Returns estimated serialized size in bytes.
         *
         * @return estimated serialized size in bytes
         */
        int estimatedSize();
    }

    /**
     * Sliding window rate limiter state.
     */
    private static class RateLimiterState {
        private final long windowMs;
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());

        RateLimiterState(long windowMs) {
            this.windowMs = windowMs;
        }

        boolean tryAcquire(int maxRequests) {
            long now = System.currentTimeMillis();
            lastAccessTime.set(now);

            // Check if we need to reset the window
            long windowStartVal = windowStart.get();
            if (now - windowStartVal >= windowMs) {
                // Atomic compare-and-set to reset window
                if (windowStart.compareAndSet(windowStartVal, now)) {
                    count.set(0);
                }
            }

            // Try to increment count
            int currentCount = count.incrementAndGet();
            if (currentCount > maxRequests) {
                count.decrementAndGet(); // Rollback
                return false;
            }
            return true;
        }
    }

    // ===== Metrics Recording Methods =====

    private static void recordSizeRejection(String payloadType, int actualSize, int limitSize) {
        totalSizeRejections.incrementAndGet();
        sizeRejectionCounters.computeIfAbsent(payloadType, k -> new AtomicLong(0)).incrementAndGet();
        int overBy = Math.max(0, actualSize - limitSize);
        updateMax(maxSizeOverLimitBytes.computeIfAbsent(payloadType, k -> new AtomicLong(0)), overBy);
        updateMax(maxRejectedSizeBytes.computeIfAbsent(payloadType, k -> new AtomicLong(0)), actualSize);
    }

    private static void recordRateLimitRejection(String payloadType) {
        totalRateLimitRejections.incrementAndGet();
        rateLimitRejectionCounters.computeIfAbsent(payloadType, k -> new AtomicLong(0)).incrementAndGet();
    }

    private static void recordIpRateLimitRejection(String payloadType) {
        totalIpRateLimitRejections.incrementAndGet();
        ipRateLimitRejectionCounters.computeIfAbsent(payloadType, k -> new AtomicLong(0)).incrementAndGet();
    }

    // ===== P1: Disconnect Policy Methods =====

    /**
     * P1: Records a violation and disconnects the player if threshold is exceeded.
     *
     * @param player The server player
     * @param playerId The player's UUID
     * @param reason The reason for the violation
     */
    private static void recordViolationAndMaybeDisconnect(ServerPlayer player, UUID playerId, String reason) {
        if (playerId == null || player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        AtomicLong lastTime = lastViolationTime.computeIfAbsent(playerId, k -> new AtomicLong(0));
        long lastViolation = lastTime.get();

        // Reset violations if enough time has passed
        if (now - lastViolation > VIOLATION_RESET_MS) {
            playerViolations.remove(playerId);
        }
        lastTime.set(now);

        // Increment violation count
        AtomicInteger violations = playerViolations.computeIfAbsent(playerId, k -> new AtomicInteger(0));
        int count = violations.incrementAndGet();

        if (count >= DISCONNECT_THRESHOLD) {
            totalDisconnects.incrementAndGet();
            LOGGER.error("[SECURITY] Disconnecting player {} after {} consecutive violations (last: {})",
                playerId, count, reason);

            // Disconnect the player with a message
            player.connection.disconnect(
                Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                    "Disconnected: Too many invalid network requests. Please rejoin."
                ))
            );

            // Clean up tracking state
            playerViolations.remove(playerId);
            lastViolationTime.remove(playerId);
        } else if (count >= DISCONNECT_THRESHOLD / 2) {
            // Warning at 50% threshold
            LOGGER.warn("[SECURITY] Player {} has {} consecutive violations (threshold: {})",
                playerId, count, DISCONNECT_THRESHOLD);
        }
    }

    /**
     * P1: Resets the violation counter for a player (called on successful payload).
     */
    private static void resetViolations(UUID playerId) {
        if (playerId == null) {
            return;
        }
        // Only reset if there were violations
        if (playerViolations.containsKey(playerId)) {
            playerViolations.remove(playerId);
            lastViolationTime.remove(playerId);
        }
    }

    /**
     * P1: Get total number of disconnects due to repeated violations.
     */
    public static long getTotalDisconnects() {
        return totalDisconnects.get();
    }

    // ===== Metrics Getters (for admin UI and telemetry) =====

    /**
     * Get total size rejections across all payload types.
     */
    public static long getTotalSizeRejections() {
        return totalSizeRejections.get();
    }

    /**
     * Get total player rate limit rejections.
     */
    public static long getTotalRateLimitRejections() {
        return totalRateLimitRejections.get();
    }

    /**
     * Get total IP rate limit rejections.
     */
    public static long getTotalIpRateLimitRejections() {
        return totalIpRateLimitRejections.get();
    }

    /**
     * Get total payloads successfully processed.
     */
    public static long getTotalPayloadsProcessed() {
        return totalPayloadsProcessed.get();
    }

    /**
     * Get size rejection count for a specific payload type.
     */
    public static long getSizeRejectionCount(String payloadType) {
        AtomicLong counter = sizeRejectionCounters.get(payloadType);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get rate limit rejection count for a specific payload type.
     */
    public static long getRateLimitRejectionCount(String payloadType) {
        AtomicLong counter = rateLimitRejectionCounters.get(payloadType);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get IP rate limit rejection count for a specific payload type.
     */
    public static long getIpRateLimitRejectionCount(String payloadType) {
        AtomicLong counter = ipRateLimitRejectionCounters.get(payloadType);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get aggregated metrics for all payload types.
     */
    public static PayloadMetrics getMetrics() {
        return new PayloadMetrics(
            totalPayloadsProcessed.get(),
            totalSizeRejections.get(),
            totalRateLimitRejections.get(),
            totalIpRateLimitRejections.get(),
            Map.copyOf(sizeRejectionCounters),
            Map.copyOf(rateLimitRejectionCounters),
            Map.copyOf(ipRateLimitRejectionCounters)
        );
    }

    /**
     * Reset all metrics (for testing or periodic reset).
     */
    public static void resetMetrics() {
        totalPayloadsProcessed.set(0);
        totalSizeRejections.set(0);
        totalRateLimitRejections.set(0);
        totalIpRateLimitRejections.set(0);
        sizeRejectionCounters.clear();
        rateLimitRejectionCounters.clear();
        ipRateLimitRejectionCounters.clear();
        maxSizeOverLimitBytes.clear();
        maxRejectedSizeBytes.clear();
    }

    /**
     * Get a summary string for logging/monitoring.
     */
    public static String getMetricsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Payload Validation Metrics ===\n");
        sb.append(String.format("Processed: %d%n", totalPayloadsProcessed.get()));
        sb.append(String.format("Size rejections: %d%n", totalSizeRejections.get()));
        sb.append(String.format("Player rate limit rejections: %d%n", totalRateLimitRejections.get()));
        sb.append(String.format("IP rate limit rejections: %d%n", totalIpRateLimitRejections.get()));

        if (!sizeRejectionCounters.isEmpty()) {
            sb.append("Size rejections by type:\n");
            sizeRejectionCounters.forEach((type, count) ->
                sb.append(String.format("  %s: %d%n", type, count.get())));
        }
        if (!maxSizeOverLimitBytes.isEmpty()) {
            sb.append("Max size over limit by type:\n");
            maxSizeOverLimitBytes.forEach((type, maxOver) ->
                sb.append(String.format("  %s: %d bytes%n", type, maxOver.get())));
        }
        if (!maxRejectedSizeBytes.isEmpty()) {
            sb.append("Max rejected payload size by type:\n");
            maxRejectedSizeBytes.forEach((type, maxSize) ->
                sb.append(String.format("  %s: %d bytes%n", type, maxSize.get())));
        }

        return sb.toString();
    }

    /**
     * Metrics record for external consumption (admin UI, telemetry export).
     */
    public record PayloadMetrics(
        long totalProcessed,
        long sizeRejections,
        long rateLimitRejections,
        long ipRateLimitRejections,
        Map<String, AtomicLong> sizeRejectionsByType,
        Map<String, AtomicLong> rateLimitRejectionsByType,
        Map<String, AtomicLong> ipRateLimitRejectionsByType
    ) {
        public long totalRejections() {
            return sizeRejections + rateLimitRejections + ipRateLimitRejections;
        }

        public double rejectionRate() {
            long total = totalProcessed + totalRejections();
            return total > 0 ? (double) totalRejections() / total : 0;
        }
    }

    private static void updateMax(AtomicLong holder, long value) {
        long prev;
        do {
            prev = holder.get();
            if (value <= prev) {
                return;
            }
        } while (!holder.compareAndSet(prev, value));
    }

    /**
     * Validation result for detailed reporting.
     */
    public record ValidationResult(
        boolean allowed,
        @Nullable String rejectionReason,
        Map<String, Object> context
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null, Map.of());
        }

        public static ValidationResult sizeLimitExceeded(int actual, int limit) {
            return new ValidationResult(false, "size_limit_exceeded",
                Map.of("actual", actual, "limit", limit));
        }

        public static ValidationResult rateLimited(String reason) {
            return new ValidationResult(false, "rate_limited",
                Map.of("reason", reason));
        }
    }

    private PayloadValidation() {} // Prevent instantiation
}
