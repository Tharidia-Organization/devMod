package com.devmod.mailbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.devmod.mailbox.persistence.DbPerformanceMonitor;
import com.devmod.mailbox.persistence.DuckDbMailboxRepository;
import com.devmod.mailbox.persistence.MailboxRepository;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for the broadcast system.
 *
 * These tests simulate high-volume broadcast operations to verify
 * system stability and performance under load.
 *
 * Run with: ./gradlew test --tests "com.devmod.mailbox.BroadcastLoadTest"
 */
@Tag("load")
@DisplayName("Broadcast Load Tests")
class BroadcastLoadTest {

    @TempDir
    @Nullable
    Path tempDir;

    private MailboxRepository repository;
    private Path dbPath;

    @BeforeEach
    void setUp() throws Exception {
        Path tempRoot = Objects.requireNonNull(tempDir, "tempDir");
        dbPath = tempRoot.resolve("test-mailbox.duckdb");
        repository = new DuckDbMailboxRepository(dbPath);
        repository.initialize().join();

        // Reset performance monitor
        DbPerformanceMonitor.INSTANCE.reset();
        DbPerformanceMonitor.INSTANCE.setEnabled(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (repository != null) {
            repository.shutdown().join();
        }

        // Print performance metrics
        var metrics = DbPerformanceMonitor.INSTANCE.getMetrics();
        System.out.println("\n=== Performance Metrics ===");
        System.out.printf("Total queries: %d%n", metrics.totalQueries());
        System.out.printf("Slow queries: %d (%.2f%%)%n", metrics.slowQueries(), metrics.slowQueryPercentage());
        System.out.printf("Avg query time: %.2f ms%n", metrics.avgQueryTimeMs());

        if (!metrics.slowestQueries().isEmpty()) {
            System.out.println("\nSlowest queries:");
            for (var q : metrics.slowestQueries()) {
                System.out.printf("  %s: avg=%.2f ms, max=%.2f ms, count=%d%n",
                    q.queryName(), q.avgTimeMs(), q.maxTimeMs(), q.executionCount());
            }
        }

        // Print index recommendations
        var recommendations = DbPerformanceMonitor.INSTANCE.getIndexRecommendations();
        if (!recommendations.isEmpty()) {
            System.out.println("\nIndex recommendations:");
            for (var rec : recommendations) {
                System.out.printf("  [%s] %s: %s%n", rec.priority(), rec.queryName(), rec.suggestion());
            }
        }
    }

    @Test
    @DisplayName("Simulated broadcast to 100 users")
    void testBroadcastTo100Users() throws Exception {
        int userCount = 100;
        List<UUID> users = generateUsers(userCount);

        Instant start = Instant.now();

        // Simulate sending messages to all users
        List<CompletableFuture<MailboxMessage>> futures = new ArrayList<>();
        for (UUID user : users) {
            MailboxMessage message = MailboxMessage.builder()
                .sender(null, "System")
                .recipient(user)
                .subject("Load Test Message")
                .body("This is a load test broadcast message.")
                .messageType(MessageType.SYSTEM)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

            futures.add(repository.saveMessage(message));
        }

        // Wait for all messages to be saved
        awaitAll(futures);

        Duration elapsed = Duration.between(start, Instant.now());

        System.out.printf("%nBroadcast to %d users completed in %d ms%n", userCount, elapsed.toMillis());
        System.out.printf("Throughput: %.2f messages/second%n", userCount * 1000.0 / elapsed.toMillis());

        // Verify all messages were saved
        int totalCount = repository.getTotalMessageCount().join();
        assertEquals(userCount, totalCount, "All messages should be saved");

        // Performance assertion
        assertTrue(elapsed.toMillis() < 10000, "Broadcast should complete within 10 seconds");
    }

    @Test
    @DisplayName("Simulated broadcast to 500 users with batching")
    void testBroadcastTo500UsersWithBatching() throws Exception {
        int userCount = 500;
        int batchSize = 50;
        List<UUID> users = generateUsers(userCount);

        Instant start = Instant.now();

        // Process in batches
        for (int i = 0; i < users.size(); i += batchSize) {
            int end = Math.min(i + batchSize, users.size());
            List<UUID> batch = users.subList(i, end);

            List<CompletableFuture<MailboxMessage>> batchFutures = new ArrayList<>();
            for (UUID user : batch) {
                MailboxMessage message = MailboxMessage.builder()
                    .sender(null, "System")
                    .recipient(user)
                    .subject("Batch Load Test #" + (i / batchSize + 1))
                    .body("Batch test message body.")
                    .messageType(MessageType.SYSTEM)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

                batchFutures.add(repository.saveMessage(message));
            }

            awaitAll(batchFutures);

            // Small delay between batches to simulate rate limiting
            Thread.sleep(10);
        }

        Duration elapsed = Duration.between(start, Instant.now());

        System.out.printf("%nBatched broadcast to %d users completed in %d ms%n", userCount, elapsed.toMillis());
        System.out.printf("Throughput: %.2f messages/second%n", userCount * 1000.0 / elapsed.toMillis());

        // Verify
        int totalCount = repository.getTotalMessageCount().join();
        assertEquals(userCount, totalCount, "All messages should be saved");

        // Performance assertion
        assertTrue(elapsed.toMillis() < 30000, "Batched broadcast should complete within 30 seconds");
    }

    @Test
    @DisplayName("Concurrent read/write operations")
    void testConcurrentReadWriteOperations() throws Exception {
        int userCount = 50;
        int operationsPerUser = 10;
        List<UUID> users = generateUsers(userCount);

        // Pre-populate some messages
        for (UUID user : users) {
            MailboxMessage message = MailboxMessage.builder()
                .sender(null, "System")
                .recipient(user)
                .subject("Initial message")
                .body("Initial body")
                .messageType(MessageType.SYSTEM)
                .build();
            repository.saveMessage(message).join();
        }

        Instant start = Instant.now();
        CountDownLatch latch = new CountDownLatch(userCount * operationsPerUser);
        AtomicInteger errors = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int threads = Math.min(userCount, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // Concurrent operations
        try {
            for (UUID user : users) {
                for (int i = 0; i < operationsPerUser; i++) {
                    final int opIndex = i;
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            if (opIndex % 3 == 0) {
                                // Write operation
                                MailboxMessage message = MailboxMessage.builder()
                                    .sender(null, "System")
                                    .recipient(user)
                                    .subject("Concurrent message #" + opIndex)
                                    .body("Concurrent test body")
                                    .messageType(MessageType.SYSTEM)
                                    .build();
                                repository.saveMessage(message).join();
                            } else if (opIndex % 3 == 1) {
                                // Read messages
                                repository.getMessagesForPlayer(user, false).join();
                            } else {
                                // Get unread count
                                repository.getUnreadCount(user).join();
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                            e.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    }, executor));
                }
            }
        } finally {
            executor.shutdown();
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        if (completed) {
            awaitAll(futures);
        }
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        Duration elapsed = Duration.between(start, Instant.now());

        System.out.printf("%nConcurrent operations completed in %d ms%n", elapsed.toMillis());
        System.out.printf("Total operations: %d%n", userCount * operationsPerUser);
        System.out.printf("Errors: %d%n", errors.get());

        assertTrue(completed, "All operations should complete within timeout");
        assertEquals(0, errors.get(), "No errors should occur");
    }

    @Test
    @DisplayName("Queue worker throughput test")
    void testQueueWorkerThroughput() throws Exception {
        // Note: This test simulates what the queue worker would do
        // without actually starting the worker (which needs MailboxManager)

        int jobCount = 10;
        int recipientsPerJob = 50;

        Instant start = Instant.now();

        for (int job = 0; job < jobCount; job++) {
            List<UUID> recipients = generateUsers(recipientsPerJob);

            List<CompletableFuture<MailboxMessage>> futures = new ArrayList<>();
            for (UUID recipient : recipients) {
                MailboxMessage message = MailboxMessage.builder()
                    .sender(null, "Admin")
                    .recipient(recipient)
                    .subject("Queue Job #" + job)
                    .body("Queue worker simulation test")
                    .messageType(MessageType.ADMIN)
                    .build();
                futures.add(repository.saveMessage(message));
            }

            awaitAll(futures);
        }

        Duration elapsed = Duration.between(start, Instant.now());
        int totalMessages = jobCount * recipientsPerJob;

        System.out.printf("%nQueue worker simulation: %d jobs x %d recipients = %d messages%n",
            jobCount, recipientsPerJob, totalMessages);
        System.out.printf("Completed in %d ms%n", elapsed.toMillis());
        System.out.printf("Throughput: %.2f messages/second%n", totalMessages * 1000.0 / elapsed.toMillis());

        // Verify
        int totalCount = repository.getTotalMessageCount().join();
        assertEquals(totalMessages, totalCount, "All messages should be saved");
    }

    @Test
    @DisplayName("Database size growth test")
    void testDatabaseSizeGrowth() throws Exception {
        int iterations = 5;
        int messagesPerIteration = 100;

        long initialSize = Files.exists(dbPath) ? Files.size(dbPath) : 0;
        System.out.printf("%nInitial DB size: %d bytes%n", initialSize);

        for (int iter = 0; iter < iterations; iter++) {
            List<UUID> users = generateUsers(messagesPerIteration);

            List<CompletableFuture<MailboxMessage>> futures = new ArrayList<>();
            for (UUID user : users) {
                MailboxMessage message = MailboxMessage.builder()
                    .sender(null, "System")
                    .recipient(user)
                    .subject("Size test iteration " + iter)
                    .body("This is a test message to measure database growth. ".repeat(10))
                    .messageType(MessageType.SYSTEM)
                    .build();
                futures.add(repository.saveMessage(message));
            }

            awaitAll(futures);

            long currentSize = Files.size(dbPath);
            System.out.printf("After iteration %d: %d messages, DB size: %d bytes (%.2f KB/message)%n",
                iter + 1,
                (iter + 1) * messagesPerIteration,
                currentSize,
                (currentSize - initialSize) / (double) ((iter + 1) * messagesPerIteration) / 1024.0);
        }
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private List<UUID> generateUsers(int count) {
        List<UUID> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            users.add(UUID.randomUUID());
        }
        return users;
    }

    private static void awaitAll(List<? extends CompletableFuture<?>> futures) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
    }
}
