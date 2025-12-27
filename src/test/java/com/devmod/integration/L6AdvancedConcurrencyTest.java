package com.devmod.integration;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nullable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class L6AdvancedConcurrencyTest {

    // =========================================================================
    // SECTION 1: DEADLOCK DETECTION AND PREVENTION
    // =========================================================================

    private static void awaitFutures(List<Future<?>> futures) throws Exception {
        for (Future<?> future : futures) {
            future.get();
        }
    }

    @Nested
    @DisplayName("L6-AC-01: Deadlock Detection and Prevention")
    class DeadlockPreventionTests {

        @Test
        @Order(1)
        @Timeout(10)
        @DisplayName("Ordered lock acquisition prevents deadlock")
        void testOrderedLockAcquisition() throws Exception {
            // Two resources that could cause deadlock if locked in different orders
            Object lockA = new Object();
            Object lockB = new Object();

            AtomicInteger completedOperations = new AtomicInteger(0);
            AtomicBoolean deadlockDetected = new AtomicBoolean(false);

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Always acquire locks in same order (A before B)
                        // This prevents deadlock
                        synchronized (lockA) {
                            synchronized (lockB) {
                                // Critical section
                                Thread.sleep(1);
                                completedOperations.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        deadlockDetected.set(true);
                    } finally {
                        endLatch.countDown();
                    }
                }));
            }

            startLatch.countDown();
            boolean completed = endLatch.await(5, TimeUnit.SECONDS);

            executor.shutdown();
            awaitFutures(futures);

            assertTrue(completed, "All operations should complete (no deadlock)");
            assertFalse(deadlockDetected.get(), "No deadlock should be detected");
            assertEquals(threadCount, completedOperations.get());
        }

        @Test
        @Order(2)
        @Timeout(10)
        @DisplayName("Try-lock with timeout prevents deadlock")
        void testTryLockWithTimeout() throws Exception {
            ReentrantLock lock1 = new ReentrantLock();
            ReentrantLock lock2 = new ReentrantLock();

            AtomicInteger successfulOperations = new AtomicInteger(0);
            AtomicInteger timeoutOperations = new AtomicInteger(0);

            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                futures.add(executor.submit(() -> {
                    try {
                        // Alternate lock order to potentially cause contention
                        ReentrantLock first = (threadId % 2 == 0) ? lock1 : lock2;
                        ReentrantLock second = (threadId % 2 == 0) ? lock2 : lock1;

                        if (first.tryLock(100, TimeUnit.MILLISECONDS)) {
                            try {
                                if (second.tryLock(100, TimeUnit.MILLISECONDS)) {
                                    try {
                                        Thread.sleep(5);
                                        successfulOperations.incrementAndGet();
                                    } finally {
                                        second.unlock();
                                    }
                                } else {
                                    timeoutOperations.incrementAndGet();
                                }
                            } finally {
                                first.unlock();
                            }
                        } else {
                            timeoutOperations.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            // All operations should complete (success or timeout)
            assertEquals(threadCount, successfulOperations.get() + timeoutOperations.get());
        }

        @Test
        @Order(3)
        @DisplayName("Lock hierarchy prevents instance-player deadlock")
        void testLockHierarchy() throws Exception {
            // Simulates: Instance lock -> Player lock (never reverse)
            Map<UUID, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();
            Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

            UUID instanceId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();

            instanceLocks.put(instanceId, new ReentrantLock());
            playerLocks.put(playerId, new ReentrantLock());

            AtomicInteger operations = new AtomicInteger(0);
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        // Always: instance lock first, then player lock
                        ReentrantLock instanceLock = Objects.requireNonNull(instanceLocks.get(instanceId));
                        ReentrantLock playerLock = Objects.requireNonNull(playerLocks.get(playerId));

                        instanceLock.lock();
                        try {
                            playerLock.lock();
                            try {
                                operations.incrementAndGet();
                            } finally {
                                playerLock.unlock();
                            }
                        } finally {
                            instanceLock.unlock();
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            awaitFutures(futures);

            assertEquals(threadCount, operations.get());
        }
    }

    // =========================================================================
    // SECTION 2: ABA PROBLEM PREVENTION
    // =========================================================================

    @Nested
    @DisplayName("L6-AC-02: ABA Problem Prevention")
    class ABAPreventionTests {

        @Test
        @Order(4)
        @DisplayName("Stamped reference prevents ABA problem")
        void testStampedReferencePreventsABA() {
            // Using AtomicStampedReference to detect ABA
            AtomicStampedReference<String> ref = new AtomicStampedReference<>("A", 0);

            // Thread 1 reads A with stamp 0
            int[] stampHolder = new int[1];
            String value = ref.get(stampHolder);
            int stamp = stampHolder[0];

            assertEquals("A", value);
            assertEquals(0, stamp);

            // Thread 2 changes A -> B -> A (ABA)
            ref.compareAndSet("A", "B", 0, 1);
            ref.compareAndSet("B", "A", 1, 2);

            // Value is back to A, but stamp has changed
            String newValue = ref.get(stampHolder);
            int newStamp = stampHolder[0];

            assertEquals("A", newValue);
            assertEquals(2, newStamp);

            // Thread 1's CAS with old stamp should fail
            boolean success = ref.compareAndSet("A", "C", stamp, stamp + 1);

            assertFalse(success, "CAS should fail because stamp changed (ABA detected)");
        }

        @Test
        @Order(5)
        @DisplayName("Version counter prevents state ABA")
        void testVersionCounterPreventsABA() {
            // State with version number
            String state = "ACTIVE";
            long version = 0;

            // Read state and version
            String oldState = state;
            long oldVersion = version;

            assertEquals("ACTIVE", oldState);
            assertEquals(0, oldVersion);

            // State changes: ACTIVE -> COMPLETING -> ACTIVE (ABA)
            state = "COMPLETING";
            version++;
            assertEquals("COMPLETING", state);
            state = "ACTIVE";
            version++;

            // Check if state changed
            String newState = state;
            long newVersion = version;

            assertEquals("ACTIVE", newState); // Same value
            assertNotEquals(oldVersion, newVersion); // But version changed

            // Operation should detect the change via version
            boolean stateUnchanged = oldVersion == version;
            assertFalse(stateUnchanged, "Version should detect intermediate changes");
        }

        @Test
        @Order(6)
        @DisplayName("Immutable object pattern prevents ABA")
        void testImmutableObjectPattern() {
            // Using immutable objects for state
            record ImmutableState(String status, int wave, long timestamp) {}

            ImmutableState state = new ImmutableState("ACTIVE", 1, System.nanoTime());
            ImmutableState original = state;

            // Even if status returns to ACTIVE, the object is different
            state = new ImmutableState("PAUSED", 1, System.nanoTime());
            assertEquals("PAUSED", state.status());
            state = new ImmutableState("ACTIVE", 1, System.nanoTime());
            ImmutableState current = state;

            assertEquals(original.status(), current.status());
            assertNotEquals(original.timestamp(), current.timestamp());
            assertNotSame(original, current); // Different objects
        }
    }

    // =========================================================================
    // SECTION 3: LOST UPDATE PREVENTION
    // =========================================================================

    @Nested
    @DisplayName("L6-AC-03: Lost Update Prevention")
    class LostUpdatePreventionTests {

        @Test
        @Order(7)
        @Timeout(30)
        @DisplayName("AtomicInteger prevents lost increments")
        void testAtomicIntegerPreventsLostUpdates() throws Exception {
            AtomicInteger counter = new AtomicInteger(0);
            int threadsCount = 100;
            int incrementsPerThread = 1000;

            CountDownLatch latch = new CountDownLatch(threadsCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
            List<Future<?>> futures = new ArrayList<>(threadsCount);

            for (int i = 0; i < threadsCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        for (int j = 0; j < incrementsPerThread; j++) {
                            counter.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            int expected = threadsCount * incrementsPerThread;
            assertEquals(expected, counter.get(),
                "No increments should be lost");
        }

        @Test
        @Order(8)
        @Timeout(30)
        @DisplayName("CAS loop prevents lost updates on complex state")
        void testCASLoopPreventsLostUpdates() throws Exception {
            // Complex state that requires read-modify-write
            AtomicReference<int[]> state = new AtomicReference<>(new int[]{0, 0, 0});

            int threadsCount = 50;
            int updatesPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadsCount);
            AtomicInteger retries = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
            List<Future<?>> futures = new ArrayList<>(threadsCount);

            for (int i = 0; i < threadsCount; i++) {
                final int threadId = i;
                futures.add(executor.submit(() -> {
                    try {
                        for (int j = 0; j < updatesPerThread; j++) {
                            // CAS loop
                            while (true) {
                                int[] current = Objects.requireNonNull(state.get());
                                int[] updated = current.clone();
                                updated[threadId % 3]++; // Increment one of three counters

                                if (state.compareAndSet(current, updated)) {
                                    break;
                                }
                                retries.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            int[] finalState = Objects.requireNonNull(state.get());
            int totalUpdates = finalState[0] + finalState[1] + finalState[2];
            int expected = threadsCount * updatesPerThread;

            assertEquals(expected, totalUpdates,
                "No updates should be lost. Retries: " + retries.get());
        }

        @Test
        @Order(9)
        @Timeout(30)
        @DisplayName("LongAdder for high-contention counters")
        void testLongAdderForHighContention() throws Exception {
            LongAdder adder = new LongAdder();
            int threadsCount = 100;
            int addsPerThread = 10000;

            CountDownLatch latch = new CountDownLatch(threadsCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
            List<Future<?>> futures = new ArrayList<>(threadsCount);

            for (int i = 0; i < threadsCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        for (int j = 0; j < addsPerThread; j++) {
                            adder.increment();
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            long expected = (long) threadsCount * addsPerThread;
            assertEquals(expected, adder.sum(),
                "LongAdder should not lose any additions");
        }
    }

    // =========================================================================
    // SECTION 4: READ-MODIFY-WRITE ATOMICITY
    // =========================================================================

    @Nested
    @DisplayName("L6-AC-04: Read-Modify-Write Atomicity")
    class ReadModifyWriteAtomicityTests {

        @Test
        @Order(10)
        @Timeout(30)
        @DisplayName("computeIfAbsent is atomic")
        void testComputeIfAbsentAtomicity() throws Exception {
            ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();
            String key = "counter";

            int threadsCount = 100;
            CountDownLatch latch = new CountDownLatch(threadsCount);
            AtomicInteger creations = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
            List<Future<?>> futures = new ArrayList<>(threadsCount);

            for (int i = 0; i < threadsCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        map.computeIfAbsent(key, k -> {
                            creations.incrementAndGet();
                            return new AtomicInteger(0);
                        }).incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            assertEquals(1, creations.get(),
                "Value should only be created once");
            assertEquals(threadsCount, Objects.requireNonNull(map.get(key)).get(),
                "All increments should be recorded");
        }

        @Test
        @Order(11)
        @Timeout(30)
        @DisplayName("merge is atomic for complex updates")
        void testMergeAtomicity() throws Exception {
            ConcurrentHashMap<UUID, Long> balances = new ConcurrentHashMap<>();
            UUID playerId = UUID.randomUUID();
            balances.put(playerId, 0L);

            int threadsCount = 100;
            long depositAmount = 10;
            CountDownLatch latch = new CountDownLatch(threadsCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
            List<Future<?>> futures = new ArrayList<>(threadsCount);

            for (int i = 0; i < threadsCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        balances.merge(playerId, depositAmount,
                            (prev, inc) -> (prev == null ? 0L : prev) + (inc == null ? 0L : inc));
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            long expected = threadsCount * depositAmount;
            assertEquals(expected, Objects.requireNonNull(balances.get(playerId)),
                "All deposits should be applied atomically");
        }

        @Test
        @Order(12)
        @DisplayName("updateAndGet provides atomic read-modify-write")
        void testUpdateAndGetAtomicity() throws Exception {
            AtomicInteger styleScore = new AtomicInteger(0);

            int threadsCount = 50;
            int updatesPerThread = 100;
            int scorePerUpdate = 10;
            CountDownLatch latch = new CountDownLatch(threadsCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
            List<Future<?>> futures = new ArrayList<>(threadsCount);

            for (int i = 0; i < threadsCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        for (int j = 0; j < updatesPerThread; j++) {
                            styleScore.updateAndGet(score -> {
                                // Complex update logic
                                int newScore = score + scorePerUpdate;
                                // Cap at max
                                return Math.min(newScore, 100000);
                            });
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            int expected = Math.min(threadsCount * updatesPerThread * scorePerUpdate, 100000);
            assertEquals(expected, styleScore.get());
        }
    }

    // =========================================================================
    // SECTION 5: PUBLICATION SAFETY
    // =========================================================================

    @Nested
    @DisplayName("L6-AC-05: Publication Safety")
    class PublicationSafetyTests {

        @Test
        @Order(13)
        @DisplayName("Volatile ensures visibility of published objects")
        void testVolatilePublicationVisibility() throws Exception {
            // Holder with volatile reference
            class Holder {
                @Nullable
                volatile Object published = null;
            }

            Holder holder = new Holder();
            AtomicBoolean sawNull = new AtomicBoolean(false);
            AtomicBoolean sawValue = new AtomicBoolean(false);

            CountDownLatch writerReady = new CountDownLatch(1);
            CountDownLatch readerDone = new CountDownLatch(1);

            // Writer thread
            Thread writer = new Thread(() -> {
                try {
                    writerReady.await();
                    holder.published = "VALUE";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Reader thread
            Thread reader = new Thread(() -> {
                while (!sawValue.get()) {
                    Object value = holder.published;
                    if (value == null) {
                        sawNull.set(true);
                    } else {
                        sawValue.set(true);
                    }
                    LockSupport.parkNanos(1L);
                }
                readerDone.countDown();
            });

            reader.start();
            writer.start();
            writerReady.countDown();

            readerDone.await(5, TimeUnit.SECONDS);
            writer.join(1000);

            assertTrue(sawValue.get(), "Reader should eventually see the published value");
        }

        @Test
        @Order(14)
        @DisplayName("Immutable objects are safely published")
        void testImmutableObjectPublication() throws Exception {
            // Immutable class
            record ImmutableData(int value, String name, List<String> items) {
                ImmutableData {
                    items = List.copyOf(items); // Defensive copy
                }
            }

            AtomicReference<ImmutableData> ref = new AtomicReference<>();

            int readerCount = 10;
            CountDownLatch readersStarted = new CountDownLatch(readerCount);
            CountDownLatch writerDone = new CountDownLatch(1);
            AtomicInteger validReads = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
            List<Future<?>> futures = new ArrayList<>(readerCount + 1);

            // Readers
            for (int i = 0; i < readerCount; i++) {
                futures.add(executor.submit(() -> {
                    readersStarted.countDown();
                    try {
                        writerDone.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        return;
                    }

                    ImmutableData data = ref.get();
                    if (data != null && data.value() == 42 &&
                        "test".equals(data.name()) &&
                        data.items().size() == 2) {
                        validReads.incrementAndGet();
                    }
                }));
            }

            // Writer
            futures.add(executor.submit(() -> {
                try {
                    readersStarted.await();
                } catch (InterruptedException e) {
                    return;
                }
                ref.set(new ImmutableData(42, "test", List.of("a", "b")));
                writerDone.countDown();
            }));

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            awaitFutures(futures);

            assertEquals(readerCount, validReads.get(),
                "All readers should see the complete immutable object");
        }

        @Test
        @Order(15)
        @DisplayName("CopyOnWriteArrayList safe iteration during modification")
        void testCopyOnWriteSafeIteration() throws Exception {
            CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
            list.add("initial");

            AtomicInteger iterations = new AtomicInteger(0);
            AtomicInteger modifications = new AtomicInteger(0);
            AtomicBoolean error = new AtomicBoolean(false);

            CountDownLatch latch = new CountDownLatch(2);

            // Iterator thread - iterates while modifications happen
            Thread iterator = new Thread(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        for (String item : list) {
                            if (item == null) {
                                error.set(true);
                            }
                            iterations.incrementAndGet();
                        }
                        Thread.sleep(1);
                    }
                } catch (ConcurrentModificationException e) {
                    error.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            // Modifier thread
            Thread modifier = new Thread(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        list.add("item" + i);
                        modifications.incrementAndGet();
                        Thread.sleep(2);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            iterator.start();
            modifier.start();
            latch.await(10, TimeUnit.SECONDS);

            assertFalse(error.get(),
                "No ConcurrentModificationException should occur");
            assertTrue(iterations.get() > 0);
            assertTrue(modifications.get() > 0);
        }
    }

    // =========================================================================
    // SECTION 6: STARVATION PREVENTION
    // =========================================================================

    @Nested
    @DisplayName("L6-AC-06: Starvation Prevention")
    class StarvationPreventionTests {

        @Test
        @Order(16)
        @Timeout(30)
        @DisplayName("Fair lock prevents starvation")
        void testFairLockPreventsStarvation() throws Exception {
            ReentrantLock fairLock = new ReentrantLock(true); // Fair mode
            int threadCount = 10;
            int acquisitionsPerThread = 10;

            Map<Integer, AtomicInteger> acquisitionCounts = new ConcurrentHashMap<>();
            for (int i = 0; i < threadCount; i++) {
                acquisitionCounts.put(i, new AtomicInteger(0));
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < acquisitionsPerThread; j++) {
                            fairLock.lock();
                            try {
                                Objects.requireNonNull(acquisitionCounts.get(threadId)).incrementAndGet();
                                Thread.sleep(1);
                            } finally {
                                fairLock.unlock();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                }));
            }

            startLatch.countDown();
            endLatch.await();
            executor.shutdown();
            awaitFutures(futures);

            // All threads should get roughly equal access
            int min = acquisitionCounts.values().stream().mapToInt(AtomicInteger::get).min().orElse(0);
            int max = acquisitionCounts.values().stream().mapToInt(AtomicInteger::get).max().orElse(0);

            assertEquals(acquisitionsPerThread, min, "All threads should complete");
            assertEquals(acquisitionsPerThread, max, "All threads should complete");
        }

        @Test
        @Order(17)
        @Timeout(30)
        @DisplayName("Semaphore ensures fair access to limited resource")
        void testSemaphoreFairAccess() throws Exception {
            int permits = 3;
            Semaphore semaphore = new Semaphore(permits, true); // Fair

            int threadCount = 10;
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            AtomicInteger currentConcurrent = new AtomicInteger(0);
            AtomicInteger completedOperations = new AtomicInteger(0);

            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            int current = currentConcurrent.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, current));

                            Thread.sleep(10); // Simulate work

                            currentConcurrent.decrementAndGet();
                            completedOperations.incrementAndGet();
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            assertTrue(maxConcurrent.get() <= permits,
                "Concurrent access should not exceed permits: " + maxConcurrent.get());
            assertEquals(threadCount, completedOperations.get(),
                "All operations should complete");
        }

        @Test
        @Order(18)
        @Timeout(30)
        @DisplayName("ReadWriteLock allows concurrent reads")
        void testReadWriteLockConcurrentReads() throws Exception {
            ReadWriteLock rwLock = new ReentrantReadWriteLock();
            AtomicInteger concurrentReaders = new AtomicInteger(0);
            AtomicInteger maxConcurrentReaders = new AtomicInteger(0);

            int readerCount = 20;
            CountDownLatch latch = new CountDownLatch(readerCount);

            ExecutorService executor = Executors.newFixedThreadPool(readerCount);
            List<Future<?>> futures = new ArrayList<>(readerCount);

            for (int i = 0; i < readerCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        rwLock.readLock().lock();
                        try {
                            int current = concurrentReaders.incrementAndGet();
                            maxConcurrentReaders.updateAndGet(max -> Math.max(max, current));

                            Thread.sleep(50); // Hold read lock

                            concurrentReaders.decrementAndGet();
                        } finally {
                            rwLock.readLock().unlock();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            assertTrue(maxConcurrentReaders.get() > 1,
                "Multiple readers should be able to hold lock concurrently: " +
                maxConcurrentReaders.get());
        }
    }

    // =========================================================================
    // SECTION 7: COMPLEX CONCURRENT SCENARIOS
    // =========================================================================

    @Nested
    @DisplayName("L6-AC-07: Complex Concurrent Scenarios")
    class ComplexConcurrentTests {

        @Test
        @Order(19)
        @Timeout(30)
        @DisplayName("Simulated concurrent quest operations")
        void testConcurrentQuestOperations() throws Exception {
            // Simulates real quest system with multiple concurrent operations
            ConcurrentHashMap<UUID, String> questStates = new ConcurrentHashMap<>();
            ConcurrentHashMap<UUID, AtomicLong> questScores = new ConcurrentHashMap<>();
            ConcurrentHashMap<UUID, Set<UUID>> instancePlayers = new ConcurrentHashMap<>();

            int playerCount = 20;
            int operationsPerPlayer = 50;
            AtomicInteger errors = new AtomicInteger(0);
            AtomicLong lastTotalScoreRead = new AtomicLong(0);
            AtomicInteger lastInstanceCount = new AtomicInteger(0);

            List<UUID> playerIds = new ArrayList<>();
            UUID sharedInstanceId = UUID.randomUUID();
            instancePlayers.put(sharedInstanceId, ConcurrentHashMap.newKeySet());

            for (int i = 0; i < playerCount; i++) {
                UUID playerId = UUID.randomUUID();
                playerIds.add(playerId);
                questStates.put(playerId, "ACTIVE");
                questScores.put(playerId, new AtomicLong(0));
                Objects.requireNonNull(instancePlayers.get(sharedInstanceId)).add(playerId);
            }

            CountDownLatch latch = new CountDownLatch(playerCount);
            ExecutorService executor = Executors.newFixedThreadPool(playerCount);
            List<Future<?>> futures = new ArrayList<>(playerCount);

            for (int i = 0; i < playerCount; i++) {
                final UUID playerId = playerIds.get(i);
                futures.add(executor.submit(() -> {
                    try {
                        ThreadLocalRandom random = ThreadLocalRandom.current();

                        for (int op = 0; op < operationsPerPlayer; op++) {
                            int operation = random.nextInt(4);

                            switch (operation) {
                                case 0 -> {
                                    // Add score
                                    Objects.requireNonNull(questScores.get(playerId))
                                        .addAndGet(random.nextInt(10, 100));
                                }
                                case 1 -> {
                                    // Read all scores
                                    long total = questScores.values().stream()
                                        .mapToLong(AtomicLong::get)
                                        .sum();
                                    lastTotalScoreRead.set(total);
                                }
                                case 2 -> {
                                    // Check instance players
                                    Set<UUID> players = Objects.requireNonNull(instancePlayers.get(sharedInstanceId));
                                    int count = players.size();
                                    lastInstanceCount.set(count);
                                }
                                case 3 -> {
                                    // Update state
                                    questStates.compute(playerId, (k, v) ->
                                        "ACTIVE".equals(v) ? "ACTIVE" : v);
                                }
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            assertEquals(0, errors.get(), "No errors during concurrent operations");
            assertEquals(playerCount, questStates.size());
            assertEquals(playerCount, questScores.size());
            assertTrue(lastTotalScoreRead.get() >= 0, "Aggregated score read should be non-negative");
            assertEquals(playerCount, lastInstanceCount.get(), "Instance should track all players");
        }

        @Test
        @Order(20)
        @Timeout(30)
        @DisplayName("Producer-consumer pattern for reward distribution")
        void testProducerConsumerRewards() throws Exception {
            BlockingQueue<Long> rewardQueue = new LinkedBlockingQueue<>(100);
            AtomicLong totalProduced = new AtomicLong(0);
            AtomicLong totalConsumed = new AtomicLong(0);
            AtomicBoolean producerDone = new AtomicBoolean(false);

            int producerCount = 5;
            int consumerCount = 3;
            int rewardsPerProducer = 100;

            CountDownLatch producerLatch = new CountDownLatch(producerCount);
            CountDownLatch consumerLatch = new CountDownLatch(consumerCount);

            ExecutorService executor = Executors.newFixedThreadPool(producerCount + consumerCount);
            List<Future<?>> futures = new ArrayList<>(producerCount + consumerCount);

            // Producers
            for (int i = 0; i < producerCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        for (int j = 0; j < rewardsPerProducer; j++) {
                            long reward = ThreadLocalRandom.current().nextLong(10, 100);
                            rewardQueue.put(reward);
                            totalProduced.addAndGet(reward);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        producerLatch.countDown();
                    }
                }));
            }

            // Consumers
            for (int i = 0; i < consumerCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        while (!producerDone.get() || !rewardQueue.isEmpty()) {
                            Long reward = rewardQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (reward != null) {
                                totalConsumed.addAndGet(reward);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        consumerLatch.countDown();
                    }
                }));
            }

            producerLatch.await();
            producerDone.set(true);
            consumerLatch.await();
            executor.shutdown();
            awaitFutures(futures);

            assertEquals(totalProduced.get(), totalConsumed.get(),
                "All produced rewards should be consumed");
        }
    }
}
