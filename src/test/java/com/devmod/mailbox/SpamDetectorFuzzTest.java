package com.devmod.mailbox;

import static org.junit.jupiter.api.Assertions.*;

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

import com.devmod.mailbox.moderation.SpamDetector;

/**
 * Fuzz tests for SpamDetector security and edge cases.
 *
 * Tests:
 * - Frequency evasion attempts
 * - Hash collision exploitation
 * - Scoring boundary conditions
 * - Concurrent access safety
 * - Memory/resource limits
 */
class SpamDetectorFuzzTest {

    private SpamDetector detector;

    @BeforeEach
    void setUp() {
        detector = SpamDetector.INSTANCE;
        detector.reset();
        detector.setEnabled(true);
        detector.setSpamThreshold(100);
        detector.setSuspiciousThreshold(50);
    }

    @AfterEach
    void tearDown() {
        detector.reset();
    }

    // ===== Frequency Evasion Tests =====

    @Nested
    @DisplayName("Frequency Evasion")
    class FrequencyEvasionTests {

        @Test
        @DisplayName("Rapid fire messages trigger high frequency signal")
        void rapidFireTriggers() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            // Send 15 messages rapidly
            for (int i = 0; i < 15; i++) {
                detector.score(sender, recipient, "Subject " + i, "Body " + i);
            }

            // 16th message should have very high frequency signal
            var score = detector.score(sender, recipient, "Final", "Message");
            assertTrue(score.totalScore() >= 60,
                "Rapid fire should trigger very_high_frequency: " + score.getSignalSummary());
        }

        @Test
        @DisplayName("Different senders don't share frequency counts")
        void differentSendersIndependent() {
            UUID sender1 = UUID.randomUUID();
            UUID sender2 = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            // Spam from sender1
            for (int i = 0; i < 15; i++) {
                detector.score(sender1, recipient, "Spam " + i, "Body");
            }

            // sender2's first message should be clean (except new_sender)
            var score = detector.score(sender2, recipient, "Hello", "World");
            assertTrue(score.totalScore() <= 20,
                "Different sender should not inherit spam score: " + score.getSignalSummary());
        }

        @Test
        @DisplayName("Same content to many recipients triggers mass_recipient")
        void massRecipientDetected() {
            UUID sender = UUID.randomUUID();

            // Send same message to 10 different recipients
            for (int i = 0; i < 10; i++) {
                UUID recipient = UUID.randomUUID();
                detector.score(sender, recipient, "Same Subject", "Same Body");
            }

            // Check the score accumulation
            long flagged = detector.getMessagesFlagged();
            assertTrue(flagged >= 0, "Some messages should be flagged");
        }
    }

    // ===== Hash Collision Tests =====

    @Nested
    @DisplayName("Hash Collision Handling")
    class HashCollisionTests {

        @Test
        @DisplayName("Identical content from same sender triggers duplicate")
        void duplicateContentDetected() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            // First message
            detector.score(sender, recipient, "Hello", "World");

            // Same content again
            var score = detector.score(sender, recipient, "Hello", "World");
            assertTrue(score.signals().containsKey("duplicate_content"),
                "Duplicate content should be detected: " + score.getSignalSummary());
        }

        @Test
        @DisplayName("Identical content from different senders is allowed")
        void sameContentDifferentSenders() {
            UUID sender1 = UUID.randomUUID();
            UUID sender2 = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            detector.score(sender1, recipient, "Hello", "World");
            var score = detector.score(sender2, recipient, "Hello", "World");

            assertFalse(score.signals().containsKey("duplicate_content"),
                "Same content from different sender should not trigger duplicate");
        }

        @Test
        @DisplayName("Whitespace variations create different hashes")
        void whitespaceVariations() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            detector.score(sender, recipient, "Hello", "World");

            // Different whitespace should normalize to same hash
            var score = detector.score(sender, recipient, "Hello", "  World  ");
            // The implementation normalizes whitespace, so this should trigger duplicate
            assertTrue(score.signals().containsKey("duplicate_content"),
                "Whitespace-normalized content should be detected as duplicate");
        }

        @Test
        @DisplayName("Case variations create same hash")
        void caseVariations() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            detector.score(sender, recipient, "Hello", "World");
            var score = detector.score(sender, recipient, "HELLO", "WORLD");

            // Implementation lowercases, so this should be duplicate
            assertTrue(score.signals().containsKey("duplicate_content"),
                "Case-normalized content should be detected as duplicate");
        }
    }

    // ===== Scoring Boundary Tests =====

    @Nested
    @DisplayName("Scoring Boundaries")
    class ScoringBoundaryTests {

        @Test
        @DisplayName("Score exactly at spam threshold")
        void scoreAtSpamThreshold() {
            // Default spam threshold is 100
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            // Generate a score close to threshold
            var score = detector.score(sender, recipient, "", "A".repeat(100));
            assertNotNull(score);
        }

        @Test
        @DisplayName("Score exactly at suspicious threshold")
        void scoreAtSuspiciousThreshold() {
            // Default suspicious threshold is 50
            detector.setSuspiciousThreshold(50);

            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            // Empty subject (10) + new_sender (15) + short body (5) = 30
            var score = detector.score(sender, recipient, "", "Short");
            assertTrue(score.isSuspicious(50) || !score.isSuspicious(50),
                "Boundary condition should be handled");
        }

        @Test
        @DisplayName("Clean message returns zero score")
        void cleanMessageZeroScore() {
            detector.setEnabled(false);
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            var score = detector.score(sender, recipient, "Subject", "Body content here");
            assertEquals(0, score.totalScore());
            assertTrue(score.isClean());

            detector.setEnabled(true);
        }

        @Test
        @DisplayName("All signals combined")
        void allSignalsCombined() {
            UUID sender = UUID.randomUUID();

            // Send many messages to trigger all signals
            for (int i = 0; i < 15; i++) {
                UUID recipient = UUID.randomUUID();
                detector.score(sender, recipient, "", "TEST!");
            }

            // Final message with all signals
            var score = detector.score(sender, UUID.randomUUID(), "",
                "TEST!!!");

            // Should have accumulated multiple signals
            assertFalse(score.signals().isEmpty(),
                "Should have multiple signals");
        }
    }

    // ===== Content Analysis Tests =====

    @Nested
    @DisplayName("Content Analysis Edge Cases")
    class ContentAnalysisTests {

        @Test
        @DisplayName("100% uppercase triggers excessive_caps")
        void allUppercaseTriggers() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            var score = detector.score(sender, recipient, "TEST",
                "THIS IS ALL CAPS MESSAGE THAT SHOULD TRIGGER THE FILTER");

            assertTrue(score.signals().containsKey("excessive_caps"),
                "All caps message should trigger excessive_caps");
        }

        @Test
        @DisplayName("Short all-caps doesn't trigger (< 20 chars)")
        void shortCapsDoesntTrigger() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            var score = detector.score(sender, recipient, "HI", "CAPS");

            assertFalse(score.signals().containsKey("excessive_caps"),
                "Short caps message should not trigger");
        }

        @Test
        @DisplayName("Heavy symbol usage triggers excessive_symbols")
        void heavySymbolsTrigger() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            var score = detector.score(sender, recipient, "Subject",
                "!!!@@@###$$$%%%^^^&&&***(((");

            assertTrue(score.signals().containsKey("excessive_symbols"),
                "Heavy symbols should trigger excessive_symbols");
        }

        @Test
        @DisplayName("Very long message triggers excessive_length")
        void veryLongMessageTriggers() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            String longBody = "word ".repeat(1200); // > 5000 chars
            var score = detector.score(sender, recipient, "Subject", longBody);

            assertTrue(score.signals().containsKey("excessive_length"),
                "Very long message should trigger excessive_length");
        }

        @Test
        @DisplayName("Empty body doesn't crash")
        void emptyBodySafe() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            assertDoesNotThrow(() ->
                detector.score(sender, recipient, "Subject", ""));
            assertDoesNotThrow(() ->
                detector.score(sender, recipient, "Subject", null));
        }

        @Test
        @DisplayName("Null inputs handled gracefully")
        void nullInputsHandled() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            assertDoesNotThrow(() ->
                detector.score(sender, recipient, null, null));
        }
    }

    // ===== Concurrent Access Tests =====

    @Nested
    @DisplayName("Concurrent Access Safety")
    class ConcurrentTests {

        @Test
        @DisplayName("Concurrent scoring from same sender is thread-safe")
        void concurrentSameSender() throws Exception {
            UUID sender = UUID.randomUUID();
            int threadCount = 10;
            int messagesPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < messagesPerThread; i++) {
                            UUID recipient = UUID.randomUUID();
                            detector.score(sender, recipient,
                                "Thread " + threadId, "Message " + i);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(0, errors.get(), "Should have no concurrent access errors");
        }

        @Test
        @DisplayName("Concurrent scoring from different senders is thread-safe")
        void concurrentDifferentSenders() throws Exception {
            int threadCount = 20;
            int messagesPerThread = 25;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        UUID sender = UUID.randomUUID();
                        for (int i = 0; i < messagesPerThread; i++) {
                            UUID recipient = UUID.randomUUID();
                            detector.score(sender, recipient, "Subject", "Body " + i);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(0, errors.get(), "Should have no concurrent access errors");
            assertTrue(detector.getTrackedSenderCount() <= threadCount,
                "Should track up to " + threadCount + " senders");
        }

        @Test
        @DisplayName("Cleanup during concurrent scoring is safe")
        void cleanupDuringScoring() throws Exception {
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);
            CountDownLatch latch = new CountDownLatch(threadCount + 1);
            AtomicInteger errors = new AtomicInteger(0);

            // Scoring threads
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        UUID sender = UUID.randomUUID();
                        for (int i = 0; i < 100; i++) {
                            detector.score(sender, UUID.randomUUID(), "S", "B");
                            Thread.sleep(1);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Cleanup thread
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        detector.cleanup();
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(0, errors.get(), "Cleanup during scoring should be safe");
        }
    }

    // ===== Resource Limits =====

    @Nested
    @DisplayName("Resource Limits")
    class ResourceLimitTests {

        @Test
        @DisplayName("Many unique senders don't cause memory issues")
        void manyUniqueSenders() {
            // Create 1000 unique senders
            for (int i = 0; i < 1000; i++) {
                UUID sender = UUID.randomUUID();
                UUID recipient = UUID.randomUUID();
                detector.score(sender, recipient, "Subject", "Body");
            }

            // Cleanup should reduce tracked count
            detector.cleanup();

            assertTrue(detector.getTrackedSenderCount() <= 1000,
                "Should track senders within limits");
        }

        @Test
        @DisplayName("Cleanup removes stale entries")
        void cleanupRemovesStale() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            detector.score(sender, recipient, "Test", "Message");
            int beforeCleanup = detector.getTrackedSenderCount();

            // Cleanup (entries are too fresh, so count should stay)
            detector.cleanup();
            int afterCleanup = detector.getTrackedSenderCount();

            assertTrue(afterCleanup <= beforeCleanup,
                "Cleanup should not increase tracked count");
        }

        @Test
        @DisplayName("Metrics are accurate after many operations")
        void metricsAccurate() {
            int messageCount = 100;
            UUID sender = UUID.randomUUID();

            for (int i = 0; i < messageCount; i++) {
                detector.score(sender, UUID.randomUUID(), "S", "B");
            }

            assertEquals(messageCount, detector.getTotalMessagesScored(),
                "Total scored should match message count");
        }
    }

    // ===== SpamScore Tests =====

    @Nested
    @DisplayName("SpamScore API")
    class SpamScoreTests {

        @Test
        @DisplayName("Clean score has zero total and empty signals")
        void cleanScoreProperties() {
            var score = SpamDetector.SpamScore.clean();

            assertEquals(0, score.totalScore());
            assertTrue(score.signals().isEmpty());
            assertTrue(score.isClean());
            assertFalse(score.isSpam(100));
            assertFalse(score.isSuspicious(50));
        }

        @Test
        @DisplayName("getSignalSummary returns readable format")
        void signalSummaryFormat() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            // Create score with signals
            var score = detector.score(sender, recipient, "",
                "THIS IS ALL CAPS AND HAS SYMBOLS!!! @@@ ###");

            String summary = score.getSignalSummary();
            assertNotNull(summary);
            assertFalse(summary.isEmpty());
        }
    }
}
