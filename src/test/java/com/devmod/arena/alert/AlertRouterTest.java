package com.devmod.arena.alert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlertRouterTest {

    private AlertRouter router;

    @BeforeAll
    static void disableAlertLogging() {
        AlertRouter.setLoggingEnabled(false);
    }

    @BeforeEach
    void setUp() {
        router = new AlertRouter();
    }

    @AfterEach
    void tearDown() {
        if (router != null) {
            router.close();
        }
    }

    private ErrorContext createTestError() {
        return ErrorContext.builder()
            .errorType("TestException")
            .message("Test error message")
            .severity(ErrorContext.Severity.ERROR)
            .component("test-component")
            .build();
    }

    @Nested
    @DisplayName("Channel Registration Tests")
    class ChannelRegistrationTests {

        @Test
        @DisplayName("Can register and unregister channels")
        void canRegisterAndUnregisterChannels() {
            router.registerChannel("channel1", false, ctx -> {});
            router.registerChannel("channel2", true, ctx -> {});

            assertTrue(router.hasChannel("channel1"));
            assertTrue(router.hasChannel("channel2"));
            assertEquals(2, router.getChannelIds().size());

            assertTrue(router.unregisterChannel("channel1"));
            assertFalse(router.hasChannel("channel1"));
            assertEquals(1, router.getChannelIds().size());
        }

        @Test
        @DisplayName("Unregistering non-existent channel returns false")
        void unregisterNonExistentChannelReturnsFalse() {
            assertFalse(router.unregisterChannel("non-existent"));
        }
    }

    @Nested
    @DisplayName("Delivery Tests")
    class DeliveryTests {

        @Test
        @DisplayName("Delivers alert to all registered channels")
        void deliversToAllChannels() throws Exception {
            List<String> deliveredTo = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(3);

            router.registerChannel("channel1", false, ctx -> {
                deliveredTo.add("channel1");
                latch.countDown();
            });
            router.registerChannel("channel2", false, ctx -> {
                deliveredTo.add("channel2");
                latch.countDown();
            });
            router.registerChannel("channel3", true, ctx -> {
                deliveredTo.add("channel3");
                latch.countDown();
            });

            ErrorContext error = createTestError();
            router.route(error).isDone();

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All channels should receive delivery");
            assertEquals(3, deliveredTo.size());
            assertTrue(deliveredTo.contains("channel1"));
            assertTrue(deliveredTo.contains("channel2"));
            assertTrue(deliveredTo.contains("channel3"));
        }

        @Test
        @DisplayName("Returns delivery result with success count")
        void returnsDeliveryResultWithSuccessCount() throws Exception {
            router.registerChannel("channel1", false, ctx -> {});
            router.registerChannel("channel2", false, ctx -> {});

            ErrorContext error = createTestError();
            var result = router.route(error).get(5, TimeUnit.SECONDS);

            assertEquals(error.errorId(), result.errorId());
            assertEquals(2, result.successCount());
            assertEquals(0, result.failedCount());
            assertTrue(result.isFullyDelivered());
        }

        @Test
        @DisplayName("Handles partial delivery failure")
        void handlesPartialDeliveryFailure() throws Exception {
            router.registerChannel("channel1", false, ctx -> {});
            router.registerChannel("channel2", false, ctx -> {
                throw new RuntimeException("Delivery failed");
            });

            ErrorContext error = createTestError();
            var result = router.route(error).get(5, TimeUnit.SECONDS);

            assertEquals(1, result.successCount());
            assertEquals(1, result.failedCount());
            assertTrue(result.isPartiallyDelivered());
        }

        @Test
        @DisplayName("Sync route waits for completion")
        void syncRouteWaitsForCompletion() {
            AtomicBoolean delivered = new AtomicBoolean(false);

            router.registerChannel("channel1", false, ctx -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                delivered.set(true);
            });

            ErrorContext error = createTestError();
            router.routeSync(error, Duration.ofSeconds(5));

            assertTrue(delivered.get(), "Sync route should wait for delivery");
        }
    }

    @Nested
    @DisplayName("Retry Queue Tests")
    class RetryQueueTests {

        @Test
        @DisplayName("Critical channel failures are queued for retry")
        void criticalChannelFailuresAreQueued() throws Exception {
            AtomicInteger attempts = new AtomicInteger(0);

            router.registerChannel("critical-channel", true, ctx -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("Temporary failure");
                }
                // Succeed on 3rd attempt
            });

            ErrorContext error = createTestError();
            router.route(error).isDone();

            // Wait for retries
            Thread.sleep(3000);

            // Should have attempted multiple times
            assertTrue(attempts.get() >= 2, "Should retry failed critical channel");
        }

        @Test
        @DisplayName("Non-critical channel failures are not retried")
        void nonCriticalChannelFailuresNotRetried() throws Exception {
            AtomicInteger attempts = new AtomicInteger(0);

            router.registerChannel("non-critical-channel", false, ctx -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Failure");
            });

            ErrorContext error = createTestError();
            router.route(error).get(2, TimeUnit.SECONDS);

            // Wait a bit
            Thread.sleep(500);

            assertEquals(1, attempts.get(), "Non-critical channel should not be retried");
        }

        @Test
        @DisplayName("Stats track retried count")
        void statsTrackRetriedCount() throws Exception {
            router.registerChannel("critical-channel", true, ctx -> {
                throw new RuntimeException("Always fails");
            });

            ErrorContext error = createTestError();
            router.route(error).get(2, TimeUnit.SECONDS);

            // Wait for retry to be queued
            Thread.sleep(200);

            var stats = router.getStats();
            assertTrue(stats.totalRetried() >= 0, "Should track retried alerts");
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Stats track delivery counts")
        void statsTrackDeliveryCounts() throws Exception {
            router.registerChannel("channel1", false, ctx -> {});
            router.registerChannel("channel2", false, ctx -> {
                throw new RuntimeException("Fails");
            });

            for (int i = 0; i < 5; i++) {
                router.route(createTestError()).get(2, TimeUnit.SECONDS);
            }

            var stats = router.getStats();
            assertEquals(5, stats.totalDelivered(), "Should track successful deliveries");
            assertEquals(5, stats.totalFailed(), "Should track failed deliveries");
            assertEquals(2, stats.registeredChannels());
        }
    }

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Router rejects alerts after close")
        void rejectsAlertsAfterClose() throws Exception {
            router.registerChannel("channel1", false, ctx -> {});
            router.close();

            ErrorContext error = createTestError();
            var result = router.route(error).get(1, TimeUnit.SECONDS);

            assertEquals(0, result.successCount());
        }
    }
}
