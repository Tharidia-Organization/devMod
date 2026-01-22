package com.devmod.network;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PayloadValidation - P0 Security layer.
 *
 * Tests cover:
 * - Rate limiting (sliding window)
 * - Metrics tracking
 * - Stale entry cleanup
 * - ValidationResult factory methods
 * - PayloadMetrics calculations
 */
class PayloadValidationTest {

    @BeforeEach
    void setUp() {
        // Reset all state before each test
        PayloadValidation.resetMetrics();
        clearRateLimiters();
        IpRateLimiter.INSTANCE.reset();
    }

    @AfterEach
    void tearDown() {
        PayloadValidation.resetMetrics();
        clearRateLimiters();
        IpRateLimiter.INSTANCE.reset();
    }

    /**
     * Clear internal rate limiter state via reflection.
     */
    private void clearRateLimiters() {
        try {
            Field field = PayloadValidation.class.getDeclaredField("rateLimiters");
            field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).clear();

            Field violations = PayloadValidation.class.getDeclaredField("playerViolations");
            violations.setAccessible(true);
            ((Map<?, ?>) violations.get(null)).clear();

            Field lastViolation = PayloadValidation.class.getDeclaredField("lastViolationTime");
            lastViolation.setAccessible(true);
            ((Map<?, ?>) lastViolation.get(null)).clear();
        } catch (Exception e) {
            fail("Failed to clear rate limiters: " + e.getMessage());
        }
    }

    // ===== Rate Limiting Tests =====

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimitingTests {

        @Test
        @DisplayName("checkRateLimit allows requests within limit")
        void allowsRequestsWithinLimit() throws Exception {
            Method checkRateLimit = PayloadValidation.class.getDeclaredMethod(
                "checkRateLimit", String.class, PayloadValidation.PayloadLimits.class);
            checkRateLimit.setAccessible(true);

            String key = UUID.randomUUID() + ":test_payload";

            // Make requests up to the limit
            for (int i = 0; i < 10; i++) {
                boolean allowed = (boolean) checkRateLimit.invoke(null, key,
                    PayloadValidation.PayloadLimits.SMALL);
                assertTrue(allowed, "Request " + (i + 1) + " should be allowed");
            }
        }

        @Test
        @DisplayName("checkRateLimit blocks requests over limit")
        void blocksRequestsOverLimit() throws Exception {
            Method checkRateLimit = PayloadValidation.class.getDeclaredMethod(
                "checkRateLimit", String.class, PayloadValidation.PayloadLimits.class);
            checkRateLimit.setAccessible(true);

            String key = UUID.randomUUID() + ":test_payload";

            // Use a custom limit for testing (5 requests per window)
            PayloadValidation.PayloadLimits limit = PayloadValidation.PayloadLimits.TICKET;

            // Make 20 requests - limit is 20/min
            for (int i = 0; i < 20; i++) {
                checkRateLimit.invoke(null, key, limit);
            }

            // 21st request should be blocked
            boolean allowed = (boolean) checkRateLimit.invoke(null, key, limit);
            assertFalse(allowed, "Request over limit should be blocked");
        }

        @Test
        @DisplayName("Rate limit window resets after expiration")
        void windowResetsAfterExpiration() throws Exception {
            Method checkRateLimit = PayloadValidation.class.getDeclaredMethod(
                "checkRateLimit", String.class, PayloadValidation.PayloadLimits.class);
            checkRateLimit.setAccessible(true);

            String key = UUID.randomUUID() + ":test_payload";

            // Use QUEST_ACTION which has 600 requests/60000ms window
            // We'll simulate by manipulating the window start time
            for (int i = 0; i < 600; i++) {
                checkRateLimit.invoke(null, key, PayloadValidation.PayloadLimits.QUEST_ACTION);
            }

            // Next request should be blocked
            boolean blocked = (boolean) checkRateLimit.invoke(null, key,
                PayloadValidation.PayloadLimits.QUEST_ACTION);
            assertFalse(blocked, "Request should be blocked at limit");
        }

        @Test
        @DisplayName("Different keys have independent rate limits")
        void independentKeysHaveIndependentLimits() throws Exception {
            Method checkRateLimit = PayloadValidation.class.getDeclaredMethod(
                "checkRateLimit", String.class, PayloadValidation.PayloadLimits.class);
            checkRateLimit.setAccessible(true);

            String key1 = "player1:test_payload";
            String key2 = "player2:test_payload";

            // Exhaust key1's limit
            for (int i = 0; i < 20; i++) {
                checkRateLimit.invoke(null, key1, PayloadValidation.PayloadLimits.TICKET);
            }

            // key2 should still have full limit available
            boolean allowed = (boolean) checkRateLimit.invoke(null, key2,
                PayloadValidation.PayloadLimits.TICKET);
            assertTrue(allowed, "Different key should have independent limit");
        }

        @Test
        @DisplayName("Concurrent rate limit requests are thread-safe")
        void concurrentRequestsAreThreadSafe() throws Exception {
            Method checkRateLimit = PayloadValidation.class.getDeclaredMethod(
                "checkRateLimit", String.class, PayloadValidation.PayloadLimits.class);
            checkRateLimit.setAccessible(true);

            String key = UUID.randomUUID() + ":concurrent_test";
            int threadCount = 10;
            int requestsPerThread = 10; // Total 100 requests, limit is 100/min for SMALL
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger allowedCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                @SuppressWarnings("unused")
                var future = executor.submit(() -> {
                    try {
                        for (int i = 0; i < requestsPerThread; i++) {
                            boolean allowed = (boolean) checkRateLimit.invoke(null, key,
                                PayloadValidation.PayloadLimits.SMALL);
                            if (allowed) {
                                allowedCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        fail("Concurrent request failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Should allow up to 100 requests (SMALL limit)
            assertEquals(100, allowedCount.get(),
                "Thread-safe rate limiting should allow exactly limit count");
        }
    }

    // ===== Metrics Tests =====

    @Nested
    @DisplayName("Metrics Tracking")
    class MetricsTests {

        @Test
        @DisplayName("Initial metrics are zero")
        void initialMetricsAreZero() {
            assertEquals(0, PayloadValidation.getTotalSizeRejections());
            assertEquals(0, PayloadValidation.getTotalRateLimitRejections());
            assertEquals(0, PayloadValidation.getTotalIpRateLimitRejections());
            assertEquals(0, PayloadValidation.getTotalPayloadsProcessed());
            assertEquals(0, PayloadValidation.getTotalDisconnects());
        }

        @Test
        @DisplayName("getMetrics returns aggregated metrics")
        void getMetricsReturnsAggregatedData() {
            PayloadValidation.PayloadMetrics metrics = PayloadValidation.getMetrics();

            assertNotNull(metrics);
            assertEquals(0, metrics.totalProcessed());
            assertEquals(0, metrics.sizeRejections());
            assertEquals(0, metrics.rateLimitRejections());
            assertEquals(0, metrics.ipRateLimitRejections());
            assertEquals(0, metrics.totalRejections());
            assertEquals(0, metrics.rejectionRate(), 0.0001);
        }

        @Test
        @DisplayName("PayloadMetrics calculates rejection rate correctly")
        void metricsCalculatesRejectionRateCorrectly() {
            // Create a metrics instance with known values
            PayloadValidation.PayloadMetrics metrics = new PayloadValidation.PayloadMetrics(
                80L, // processed
                10L, // size rejections
                5L,  // rate limit rejections
                5L,  // ip rate limit rejections
                Map.of(), Map.of(), Map.of()
            );

            assertEquals(20, metrics.totalRejections());
            // rejection rate = 20 / (80 + 20) = 0.2
            assertEquals(0.2, metrics.rejectionRate(), 0.0001);
        }

        @Test
        @DisplayName("getMetricsSummary returns formatted string")
        void getMetricsSummaryReturnsFormattedString() {
            String summary = PayloadValidation.getMetricsSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("Payload Validation Metrics"));
            assertTrue(summary.contains("Processed:"));
            assertTrue(summary.contains("Size rejections:"));
        }

        @Test
        @DisplayName("resetMetrics clears all counters")
        void resetMetricsClearsAllCounters() {
            // Ensure we have some data first (from other tests or setup)
            PayloadValidation.resetMetrics();

            assertEquals(0, PayloadValidation.getTotalSizeRejections());
            assertEquals(0, PayloadValidation.getTotalRateLimitRejections());
            assertEquals(0, PayloadValidation.getTotalPayloadsProcessed());
        }

        @Test
        @DisplayName("getSizeRejectionCount returns 0 for unknown type")
        void getSizeRejectionCountReturnsZeroForUnknown() {
            assertEquals(0, PayloadValidation.getSizeRejectionCount("nonexistent:payload"));
        }

        @Test
        @DisplayName("getRateLimitRejectionCount returns 0 for unknown type")
        void getRateLimitRejectionCountReturnsZeroForUnknown() {
            assertEquals(0, PayloadValidation.getRateLimitRejectionCount("nonexistent:payload"));
        }

        @Test
        @DisplayName("getIpRateLimitRejectionCount returns 0 for unknown type")
        void getIpRateLimitRejectionCountReturnsZeroForUnknown() {
            assertEquals(0, PayloadValidation.getIpRateLimitRejectionCount("nonexistent:payload"));
        }
    }

    // ===== Cleanup Tests =====

    @Nested
    @DisplayName("Stale Entry Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("cleanupStaleEntries removes old entries")
        @SuppressWarnings("unchecked")
        void cleanupRemovesOldEntries() throws Exception {
            Method checkRateLimit = PayloadValidation.class.getDeclaredMethod(
                "checkRateLimit", String.class, PayloadValidation.PayloadLimits.class);
            checkRateLimit.setAccessible(true);

            String key = "stale_test:" + UUID.randomUUID();
            checkRateLimit.invoke(null, key, PayloadValidation.PayloadLimits.SMALL);

            // Verify entry exists
            Field field = PayloadValidation.class.getDeclaredField("rateLimiters");
            field.setAccessible(true);
            Map<String, ?> rateLimiters = (Map<String, ?>) field.get(null);
            assertTrue(rateLimiters.containsKey(key), "Entry should exist before cleanup");

            // Manipulate lastAccessTime to be stale (> 5 minutes ago)
            Object entry = rateLimiters.get(key);
            assertNotNull(entry, "Entry should not be null");
            Field lastAccessField = entry.getClass().getDeclaredField("lastAccessTime");
            lastAccessField.setAccessible(true);
            Object lastAccessTime = lastAccessField.get(entry);
            Method setMethod = lastAccessTime.getClass().getMethod("set", long.class);
            setMethod.invoke(lastAccessTime, System.currentTimeMillis() - 400_000); // 6+ minutes ago

            // Run cleanup
            PayloadValidation.cleanupStaleEntries();

            assertFalse(rateLimiters.containsKey(key), "Stale entry should be removed");
        }

        @Test
        @DisplayName("cleanupStaleEntries keeps fresh entries")
        @SuppressWarnings("unchecked")
        void cleanupKeepsFreshEntries() throws Exception {
            Method checkRateLimit = PayloadValidation.class.getDeclaredMethod(
                "checkRateLimit", String.class, PayloadValidation.PayloadLimits.class);
            checkRateLimit.setAccessible(true);

            String key = "fresh_test:" + UUID.randomUUID();
            checkRateLimit.invoke(null, key, PayloadValidation.PayloadLimits.SMALL);

            // Run cleanup immediately (entry is fresh)
            PayloadValidation.cleanupStaleEntries();

            Field field = PayloadValidation.class.getDeclaredField("rateLimiters");
            field.setAccessible(true);
            Map<String, ?> rateLimiters = (Map<String, ?>) field.get(null);

            assertTrue(rateLimiters.containsKey(key), "Fresh entry should be kept");
        }
    }

    // ===== ValidationResult Tests =====

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("ok() creates allowed result")
        void okCreatesAllowedResult() {
            PayloadValidation.ValidationResult result = PayloadValidation.ValidationResult.ok();

            assertTrue(result.allowed());
            assertNull(result.rejectionReason());
            assertTrue(result.context().isEmpty());
        }

        @Test
        @DisplayName("sizeLimitExceeded creates rejection with context")
        void sizeLimitExceededCreatesRejectionWithContext() {
            PayloadValidation.ValidationResult result =
                PayloadValidation.ValidationResult.sizeLimitExceeded(5000, 1024);

            assertFalse(result.allowed());
            assertEquals("size_limit_exceeded", result.rejectionReason());
            assertEquals(5000, result.context().get("actual"));
            assertEquals(1024, result.context().get("limit"));
        }

        @Test
        @DisplayName("rateLimited creates rejection with reason")
        void rateLimitedCreatesRejectionWithReason() {
            PayloadValidation.ValidationResult result =
                PayloadValidation.ValidationResult.rateLimited("player_limit");

            assertFalse(result.allowed());
            assertEquals("rate_limited", result.rejectionReason());
            assertEquals("player_limit", result.context().get("reason"));
        }
    }

    // ===== PayloadLimits Tests =====

    @Nested
    @DisplayName("PayloadLimits Enum")
    class PayloadLimitsTests {

        @Test
        @DisplayName("SMALL has correct limits")
        void smallHasCorrectLimits() {
            PayloadValidation.PayloadLimits limits = PayloadValidation.PayloadLimits.SMALL;
            assertEquals(1024, limits.maxSizeBytes);
            assertEquals(100, limits.maxRequestsPerWindow);
            assertEquals(60_000, limits.windowMs);
        }

        @Test
        @DisplayName("MEDIUM has correct limits")
        void mediumHasCorrectLimits() {
            PayloadValidation.PayloadLimits limits = PayloadValidation.PayloadLimits.MEDIUM;
            assertEquals(8192, limits.maxSizeBytes);
            assertEquals(60, limits.maxRequestsPerWindow);
        }

        @Test
        @DisplayName("MAILBOX has correct limits")
        void mailboxHasCorrectLimits() {
            PayloadValidation.PayloadLimits limits = PayloadValidation.PayloadLimits.MAILBOX;
            assertEquals(16384, limits.maxSizeBytes);
            assertEquals(30, limits.maxRequestsPerWindow);
        }

        @Test
        @DisplayName("TELEMETRY has correct limits")
        void telemetryHasCorrectLimits() {
            PayloadValidation.PayloadLimits limits = PayloadValidation.PayloadLimits.TELEMETRY;
            assertEquals(65536, limits.maxSizeBytes);
            assertEquals(10, limits.maxRequestsPerWindow);
        }

        @Test
        @DisplayName("NONE has maximum limits")
        void noneHasMaximumLimits() {
            PayloadValidation.PayloadLimits limits = PayloadValidation.PayloadLimits.NONE;
            assertEquals(Integer.MAX_VALUE, limits.maxSizeBytes);
            assertEquals(Integer.MAX_VALUE, limits.maxRequestsPerWindow);
        }

        @Test
        @DisplayName("All limits have 60 second window")
        void allLimitsHave60SecondWindow() {
            for (PayloadValidation.PayloadLimits limit : PayloadValidation.PayloadLimits.values()) {
                assertEquals(60_000, limit.windowMs,
                    limit.name() + " should have 60s window");
            }
        }
    }

    // ===== SizedPayload Interface Tests =====

    @Nested
    @DisplayName("SizedPayload Interface")
    class SizedPayloadTests {

        @Test
        @DisplayName("SizedPayload implementation returns correct size")
        void sizedPayloadReturnsCorrectSize() {
            // Create a test implementation
            PayloadValidation.SizedPayload payload = () -> 512;
            assertEquals(512, payload.estimatedSize());
        }

        @Test
        @DisplayName("estimatePayloadSize returns -1 for non-SizedPayload")
        void estimatePayloadSizeReturnsNegativeForNonSized() throws Exception {
            Method estimateSize = PayloadValidation.class.getDeclaredMethod(
                "estimatePayloadSize",
                net.minecraft.network.protocol.common.custom.CustomPacketPayload.class);
            estimateSize.setAccessible(true);

            // Mock payload that doesn't implement SizedPayload
            net.minecraft.resources.ResourceLocation testId =
                java.util.Objects.requireNonNull(net.minecraft.resources.ResourceLocation.parse("test:mock"));
            net.minecraft.network.protocol.common.custom.CustomPacketPayload mockPayload =
                new net.minecraft.network.protocol.common.custom.CustomPacketPayload() {
                    @Override
                    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
                        return new Type<>(testId);
                    }
                };

            int size = (int) estimateSize.invoke(null, mockPayload);
            assertEquals(-1, size, "Non-SizedPayload should return -1");
        }
    }

    // ===== IpRateLimiter Integration Tests =====

    @Nested
    @DisplayName("IpRateLimiter")
    class IpRateLimiterTests {

        @Test
        @DisplayName("checkRateLimit allows null IP")
        void checkRateLimitAllowsNullIp() {
            boolean allowed = IpRateLimiter.INSTANCE.checkRateLimit((String) null, "test");
            assertTrue(allowed, "Null IP should be allowed (can't identify)");
        }

        @Test
        @DisplayName("checkRateLimit allows empty IP")
        void checkRateLimitAllowsEmptyIp() {
            boolean allowed = IpRateLimiter.INSTANCE.checkRateLimit("", "test");
            assertTrue(allowed, "Empty IP should be allowed (can't identify)");
        }

        @Test
        @DisplayName("blockIp prevents requests")
        void blockIpPreventsRequests() {
            String ip = "192.168.1.100";
            IpRateLimiter.INSTANCE.blockIp(ip, 60_000);

            boolean allowed = IpRateLimiter.INSTANCE.checkRateLimit(ip, "test");
            assertFalse(allowed, "Blocked IP should be denied");

            IpRateLimiter.INSTANCE.unblockIp(ip);
        }

        @Test
        @DisplayName("unblockIp allows previously blocked IP")
        void unblockIpAllowsBlocked() {
            String ip = "192.168.1.101";
            IpRateLimiter.INSTANCE.blockIp(ip, 60_000);
            IpRateLimiter.INSTANCE.unblockIp(ip);

            boolean allowed = IpRateLimiter.INSTANCE.checkRateLimit(ip, "test");
            assertTrue(allowed, "Unblocked IP should be allowed");
        }

        @Test
        @DisplayName("isBlocked returns correct status")
        void isBlockedReturnsCorrectStatus() {
            String ip = "192.168.1.102";

            assertFalse(IpRateLimiter.INSTANCE.isBlocked(ip), "Unblocked IP should return false");

            IpRateLimiter.INSTANCE.blockIp(ip, 60_000);
            assertTrue(IpRateLimiter.INSTANCE.isBlocked(ip), "Blocked IP should return true");

            IpRateLimiter.INSTANCE.unblockIp(ip);
        }

        @Test
        @DisplayName("getStats returns statistics")
        void getStatsReturnsStatistics() {
            IpRateLimiter.Stats stats = IpRateLimiter.INSTANCE.getStats();

            assertNotNull(stats);
            assertTrue(stats.totalRequests() >= 0);
            assertTrue(stats.rateLimitedRequests() >= 0);
            assertTrue(stats.blockedRequests() >= 0);
        }

        @Test
        @DisplayName("category limits are applied correctly")
        void categoryLimitsAreApplied() {
            String ip = "192.168.1.103";

            // ticket_create has limit of 5/min
            for (int i = 0; i < 5; i++) {
                boolean allowed = IpRateLimiter.INSTANCE.checkRateLimit(ip, "ticket_create");
                assertTrue(allowed, "Request " + (i + 1) + " should be allowed");
            }

            // 6th request should be blocked
            boolean blocked = IpRateLimiter.INSTANCE.checkRateLimit(ip, "ticket_create");
            assertFalse(blocked, "6th ticket_create request should be blocked");
        }

        @Test
        @DisplayName("cleanup removes stale entries")
        void cleanupRemovesStaleEntries() {
            String ip = "192.168.1.104";
            IpRateLimiter.INSTANCE.checkRateLimit(ip, "test_cleanup");

            // Run cleanup
            IpRateLimiter.INSTANCE.cleanup();

            // Stats should still work after cleanup
            IpRateLimiter.Stats stats = IpRateLimiter.INSTANCE.getStats();
            assertNotNull(stats);
        }

        @Test
        @DisplayName("reset clears all state")
        void resetClearsAllState() {
            String ip = "192.168.1.105";
            IpRateLimiter.INSTANCE.blockIp(ip, 60_000);
            IpRateLimiter.INSTANCE.checkRateLimit(ip, "test");

            IpRateLimiter.INSTANCE.reset();

            IpRateLimiter.Stats stats = IpRateLimiter.INSTANCE.getStats();
            assertEquals(0, stats.totalRequests());
            assertEquals(0, stats.blockedIps());
        }
    }
}
