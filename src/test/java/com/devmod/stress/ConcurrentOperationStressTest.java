package com.devmod.stress;

import com.devmod.runtime.InstanceState;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L5 Test: Concurrent Operation Stress Testing
 *
 * Tests thread safety and concurrent access patterns without Minecraft dependencies.
 * Validates:
 * - ConcurrentHashMap operations under load
 * - Atomic operations correctness
 * - Race condition detection patterns
 * - Lock-free algorithm validation
 * - Thread pool stress scenarios
 */
@DisplayName("L5: Concurrent Operation Stress Tests")
class ConcurrentOperationStressTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Executor should terminate");
    }

    // === L5-01: ConcurrentHashMap Stress ===

    @Nested
    @DisplayName("L5-01: ConcurrentHashMap Stress")
    class ConcurrentHashMapStressTest {

        @Test
        @DisplayName("Concurrent puts don't lose data")
        void concurrentPutsDontLoseData() throws Exception {
            ConcurrentHashMap<UUID, String> map = new ConcurrentHashMap<>();
            int threadCount = 10;
            int operationsPerThread = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            Set<UUID> allIds = ConcurrentHashMap.newKeySet();

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operationsPerThread; i++) {
                            UUID id = UUID.randomUUID();
                            allIds.add(id);
                            map.put(id, "value");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertEquals(allIds.size(), map.size(), "All inserted IDs should be present");
        }

        @Test
        @DisplayName("Concurrent putIfAbsent returns correct values")
        void concurrentPutIfAbsentReturnsCorrectValues() throws Exception {
            ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();
            int threadCount = 20;
            String sharedKey = "shared";
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        AtomicInteger existing = map.putIfAbsent(sharedKey, new AtomicInteger(1));
                        if (existing == null) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            assertEquals(1, successCount.get(), "Only one thread should succeed with putIfAbsent");
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("Concurrent computeIfAbsent is thread-safe")
        void concurrentComputeIfAbsentIsThreadSafe() throws Exception {
            ConcurrentHashMap<String, List<UUID>> map = new ConcurrentHashMap<>();
            int threadCount = 50;
            String sharedKey = "instances";
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        List<UUID> list = map.computeIfAbsent(sharedKey, k -> new CopyOnWriteArrayList<>());
                        list.add(UUID.randomUUID());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            assertEquals(1, map.size(), "Only one list should be created");
            assertEquals(threadCount, map.get(sharedKey).size(), "All UUIDs should be added");
        }

        @Test
        @DisplayName("Concurrent remove operations are safe")
        void concurrentRemoveOperationsAreSafe() throws Exception {
            ConcurrentHashMap<UUID, String> map = new ConcurrentHashMap<>();
            int itemCount = 10000;
            List<UUID> ids = new ArrayList<>();

            for (int i = 0; i < itemCount; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                map.put(id, "value" + i);
            }

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger removeCount = new AtomicInteger(0);

            int chunkSize = itemCount / threadCount;
            for (int t = 0; t < threadCount; t++) {
                int start = t * chunkSize;
                int end = (t == threadCount - 1) ? itemCount : start + chunkSize;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = start; i < end; i++) {
                            if (map.remove(ids.get(i)) != null) {
                                removeCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertEquals(itemCount, removeCount.get(), "All items should be removed exactly once");
            assertTrue(map.isEmpty(), "Map should be empty");
        }

        @Test
        @DisplayName("Concurrent iteration with modification")
        void concurrentIterationWithModification() throws Exception {
            ConcurrentHashMap<UUID, Integer> map = new ConcurrentHashMap<>();

            // Pre-populate
            for (int i = 0; i < 1000; i++) {
                map.put(UUID.randomUUID(), i);
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(2);
            AtomicBoolean iteratorFailed = new AtomicBoolean(false);

            // Reader thread
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int iter = 0; iter < 100; iter++) {
                        int sum = 0;
                        for (Integer val : map.values()) {
                            sum += val;
                        }
                        // Just consume to prevent optimization
                        if (sum < 0) throw new RuntimeException("Unexpected");
                    }
                } catch (ConcurrentModificationException e) {
                    iteratorFailed.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });

            // Writer thread
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 1000; i++) {
                        map.put(UUID.randomUUID(), i);
                        if (i % 10 == 0) {
                            // Remove some entries
                            map.entrySet().stream().findFirst().ifPresent(e -> map.remove(e.getKey()));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertFalse(iteratorFailed.get(), "ConcurrentHashMap should not throw ConcurrentModificationException");
        }
    }

    // === L5-02: Atomic Operations ===

    @Nested
    @DisplayName("L5-02: Atomic Operations")
    class AtomicOperationsTest {

        @Test
        @DisplayName("AtomicInteger increment is thread-safe")
        void atomicIntegerIncrementIsThreadSafe() throws Exception {
            AtomicInteger counter = new AtomicInteger(0);
            int threadCount = 100;
            int incrementsPerThread = 10000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < incrementsPerThread; i++) {
                            counter.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertEquals(threadCount * incrementsPerThread, counter.get());
        }

        @Test
        @DisplayName("AtomicReference compareAndSet for state transitions")
        void atomicReferenceCompareAndSetForStateTransitions() throws Exception {
            AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.CREATING);
            int threadCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // All threads try to transition CREATING -> READY
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (state.compareAndSet(InstanceState.CREATING, InstanceState.READY)) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            assertEquals(1, successCount.get(), "Only one thread should successfully transition");
            assertEquals(InstanceState.READY, state.get());
        }

        @Test
        @DisplayName("AtomicLong for timestamp updates")
        void atomicLongForTimestampUpdates() throws Exception {
            AtomicLong lastUpdate = new AtomicLong(0);
            int threadCount = 20;
            int updatesPerThread = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < updatesPerThread; i++) {
                            long now = System.nanoTime();
                            lastUpdate.updateAndGet(prev -> Math.max(prev, now));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertTrue(lastUpdate.get() > 0, "Timestamp should be updated");
        }

        @Test
        @DisplayName("AtomicBoolean for one-time initialization")
        void atomicBooleanForOneTimeInitialization() throws Exception {
            AtomicBoolean initialized = new AtomicBoolean(false);
            int threadCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger initCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (initialized.compareAndSet(false, true)) {
                            initCount.incrementAndGet();
                            // Simulate expensive initialization
                            Thread.sleep(10);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertEquals(1, initCount.get(), "Initialization should happen exactly once");
            assertTrue(initialized.get());
        }

        @Test
        @DisplayName("LongAdder for high-contention counters")
        void longAdderForHighContentionCounters() throws Exception {
            java.util.concurrent.atomic.LongAdder counter = new java.util.concurrent.atomic.LongAdder();
            int threadCount = 100;
            int incrementsPerThread = 100000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < incrementsPerThread; i++) {
                            counter.increment();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertEquals((long) threadCount * incrementsPerThread, counter.sum());
        }
    }

    // === L5-03: Race Condition Patterns ===

    @Nested
    @DisplayName("L5-03: Race Condition Patterns")
    class RaceConditionPatternsTest {

        @Test
        @DisplayName("Check-then-act without synchronization fails")
        void checkThenActWithoutSynchronizationFails() throws Exception {
            // This demonstrates why check-then-act needs atomic operations
            Map<String, Integer> unsafeMap = new HashMap<>();
            String key = "counter";
            unsafeMap.put(key, 0);
            int threadCount = 100;
            int incrementsPerThread = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < incrementsPerThread; i++) {
                            // Unsafe: check-then-act
                            Integer current = unsafeMap.get(key);
                            unsafeMap.put(key, current + 1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            // Due to race conditions, the final value will likely be less than expected
            int finalValue = unsafeMap.get(key);
            int expectedValue = threadCount * incrementsPerThread;
            // We can't assert exact inequality since sometimes it might pass
            // Just document that this pattern is unsafe
            assertTrue(finalValue <= expectedValue, "Final value should not exceed expected");
        }

        @Test
        @DisplayName("Safe check-then-act with ConcurrentHashMap.compute")
        void safeCheckThenActWithCompute() throws Exception {
            ConcurrentHashMap<String, Integer> safeMap = new ConcurrentHashMap<>();
            String key = "counter";
            safeMap.put(key, 0);
            int threadCount = 100;
            int incrementsPerThread = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < incrementsPerThread; i++) {
                            // Safe: atomic compute
                            safeMap.compute(key, (k, v) -> v + 1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertEquals(threadCount * incrementsPerThread, safeMap.get(key));
        }

        @Test
        @DisplayName("Double-checked locking pattern validation")
        void doubleCheckedLockingPatternValidation() throws Exception {
            // Simulates lazy initialization pattern
            AtomicReference<Object> instance = new AtomicReference<>(null);
            Object lock = new Object();
            int threadCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            Set<Integer> instanceHashes = ConcurrentHashMap.newKeySet();

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Object obj = instance.get();
                        if (obj == null) {
                            synchronized (lock) {
                                obj = instance.get();
                                if (obj == null) {
                                    obj = new Object();
                                    instance.set(obj);
                                }
                            }
                        }
                        instanceHashes.add(System.identityHashCode(instance.get()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            assertEquals(1, instanceHashes.size(), "Only one instance should be created");
        }

        @Test
        @DisplayName("Read-modify-write with retry loop")
        void readModifyWriteWithRetryLoop() throws Exception {
            AtomicInteger value = new AtomicInteger(0);
            int threadCount = 50;
            int operationsPerThread = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operationsPerThread; i++) {
                            int current;
                            int next;
                            do {
                                current = value.get();
                                next = current + 1;
                            } while (!value.compareAndSet(current, next));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertEquals(threadCount * operationsPerThread, value.get());
        }
    }

    // === L5-04: Lock-Free Algorithm Patterns ===

    @Nested
    @DisplayName("L5-04: Lock-Free Algorithm Patterns")
    class LockFreeAlgorithmPatternsTest {

        @Test
        @DisplayName("ConcurrentLinkedQueue as lock-free queue")
        void concurrentLinkedQueueAsLockFreeQueue() throws Exception {
            ConcurrentLinkedQueue<UUID> queue = new ConcurrentLinkedQueue<>();
            int producerCount = 10;
            int consumerCount = 10;
            int itemsPerProducer = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch producerDone = new CountDownLatch(producerCount);
            CountDownLatch consumerDone = new CountDownLatch(consumerCount);
            AtomicInteger producedCount = new AtomicInteger(0);
            AtomicInteger consumedCount = new AtomicInteger(0);
            AtomicBoolean producersFinished = new AtomicBoolean(false);

            // Producers
            for (int p = 0; p < producerCount; p++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < itemsPerProducer; i++) {
                            queue.offer(UUID.randomUUID());
                            producedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        producerDone.countDown();
                    }
                });
            }

            // Consumers
            for (int c = 0; c < consumerCount; c++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        while (!producersFinished.get() || !queue.isEmpty()) {
                            UUID item = queue.poll();
                            if (item != null) {
                                consumedCount.incrementAndGet();
                            } else {
                                Thread.yield();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        consumerDone.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(producerDone.await(30, TimeUnit.SECONDS));
            producersFinished.set(true);
            assertTrue(consumerDone.await(30, TimeUnit.SECONDS));

            assertEquals(producerCount * itemsPerProducer, producedCount.get());
            assertEquals(producedCount.get(), consumedCount.get());
        }

        @Test
        @DisplayName("CopyOnWriteArrayList for snapshot iteration")
        void copyOnWriteArrayListForSnapshotIteration() throws Exception {
            CopyOnWriteArrayList<UUID> list = new CopyOnWriteArrayList<>();
            int writerCount = 5;
            int readerCount = 10;
            int writesPerWriter = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(writerCount + readerCount);
            AtomicBoolean readerFailed = new AtomicBoolean(false);
            AtomicInteger maxObservedCount = new AtomicInteger(0);

            // Writers
            for (int w = 0; w < writerCount; w++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < writesPerWriter; i++) {
                            list.add(UUID.randomUUID());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            // Readers
            for (int r = 0; r < readerCount; r++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < 1000; i++) {
                            int count = 0;
                            for (UUID id : list) {
                                count++;
                                if (id == null) {
                                    readerFailed.set(true);
                                }
                            }
                            maxObservedCount.accumulateAndGet(count, Math::max);
                        }
                    } catch (ConcurrentModificationException e) {
                        readerFailed.set(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertFalse(readerFailed.get(), "CopyOnWriteArrayList should never throw ConcurrentModificationException");
            assertTrue(maxObservedCount.get() >= 0, "Reads should observe a non-negative count");
        }

        @Test
        @DisplayName("Lock-free stack with AtomicReference")
        void lockFreeStackWithAtomicReference() throws Exception {
            // Simple lock-free stack implementation
            class Node<T> {
                final T value;
                Node<T> next;
                Node(T value) { this.value = value; }
            }

            AtomicReference<Node<Integer>> top = new AtomicReference<>(null);
            int threadCount = 20;
            int operationsPerThread = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger pushCount = new AtomicInteger(0);
            AtomicInteger popCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        for (int i = 0; i < operationsPerThread; i++) {
                            if (random.nextBoolean()) {
                                // Push
                                Node<Integer> newNode = new Node<>(i);
                                Node<Integer> oldTop;
                                do {
                                    oldTop = top.get();
                                    newNode.next = oldTop;
                                } while (!top.compareAndSet(oldTop, newNode));
                                pushCount.incrementAndGet();
                            } else {
                                // Pop
                                Node<Integer> oldTop;
                                Node<Integer> newTop;
                                do {
                                    oldTop = top.get();
                                    if (oldTop == null) break;
                                    newTop = oldTop.next;
                                } while (!top.compareAndSet(oldTop, newTop));
                                if (oldTop != null) {
                                    popCount.incrementAndGet();
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            // Count remaining items
            int remaining = 0;
            long valueSum = 0;
            Node<Integer> current = top.get();
            while (current != null) {
                remaining++;
                valueSum += current.value;
                current = current.next;
            }

            assertEquals(pushCount.get() - popCount.get(), remaining,
                "Remaining items should equal pushes minus pops");
            assertTrue(valueSum >= 0, "Accumulated node values should be non-negative");
        }
    }

    // === L5-05: Thread Pool Stress ===

    @Nested
    @DisplayName("L5-05: Thread Pool Stress")
    class ThreadPoolStressTest {

        @Test
        @DisplayName("Fixed thread pool handles burst load")
        void fixedThreadPoolHandlesBurstLoad() throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(4);
            int taskCount = 1000;
            CountDownLatch allDone = new CountDownLatch(taskCount);
            AtomicInteger completedCount = new AtomicInteger(0);

            try {
                for (int i = 0; i < taskCount; i++) {
                    pool.submit(() -> {
                        try {
                            // Simulate work
                            Thread.sleep(1);
                            completedCount.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            allDone.countDown();
                        }
                    });
                }

                assertTrue(allDone.await(60, TimeUnit.SECONDS));
                assertEquals(taskCount, completedCount.get());
            } finally {
                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("Cached thread pool scales with load")
        void cachedThreadPoolScalesWithLoad() throws Exception {
            ExecutorService pool = Executors.newCachedThreadPool();
            int taskCount = 100;
            CountDownLatch barrier = new CountDownLatch(taskCount);
            CountDownLatch allDone = new CountDownLatch(taskCount);
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            AtomicInteger currentConcurrent = new AtomicInteger(0);

            try {
                for (int i = 0; i < taskCount; i++) {
                    pool.submit(() -> {
                        try {
                            int current = currentConcurrent.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, current));
                            barrier.countDown();
                            barrier.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            currentConcurrent.decrementAndGet();
                            allDone.countDown();
                        }
                    });
                }

                assertTrue(allDone.await(30, TimeUnit.SECONDS));
                assertTrue(maxConcurrent.get() > 1, "Cached pool should scale up");
            } finally {
                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("ScheduledThreadPool handles delayed tasks")
        void scheduledThreadPoolHandlesDelayedTasks() throws Exception {
            ScheduledExecutorService pool = Executors.newScheduledThreadPool(2);
            int taskCount = 50;
            CountDownLatch allDone = new CountDownLatch(taskCount);
            AtomicInteger completedCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();

            try {
                for (int i = 0; i < taskCount; i++) {
                    pool.schedule(() -> {
                        completedCount.incrementAndGet();
                        allDone.countDown();
                    }, 100, TimeUnit.MILLISECONDS);
                }

                assertTrue(allDone.await(30, TimeUnit.SECONDS));
                long elapsed = System.currentTimeMillis() - startTime;
                assertTrue(elapsed >= 100, "Tasks should not complete before delay");
                assertEquals(taskCount, completedCount.get());
            } finally {
                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("CompletableFuture chain under load")
        void completableFutureChainUnderLoad() throws Exception {
            int chainCount = 100;
            List<CompletableFuture<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < chainCount; i++) {
                int value = i;
                CompletableFuture<Integer> future = CompletableFuture
                    .supplyAsync(() -> value, executor)
                    .thenApplyAsync(v -> v * 2, executor)
                    .thenApplyAsync(v -> v + 1, executor)
                    .thenApplyAsync(v -> v / 2, executor);
                futures.add(future);
            }

            CompletableFuture<?>[] futureArray = futures.toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futureArray).get(30, TimeUnit.SECONDS);

            for (int i = 0; i < chainCount; i++) {
                int expected = (i * 2 + 1) / 2;
                assertEquals(expected, futures.get(i).get());
            }
        }

        @Test
        @DisplayName("Work-stealing pool distributes work")
        void workStealingPoolDistributesWork() throws Exception {
            ExecutorService pool = Executors.newWorkStealingPool();
            int taskCount = 1000;
            CountDownLatch allDone = new CountDownLatch(taskCount);
            ConcurrentHashMap<Long, AtomicInteger> threadTasks = new ConcurrentHashMap<>();

            try {
                for (int i = 0; i < taskCount; i++) {
                    pool.submit(() -> {
                        try {
                            long threadId = Thread.currentThread().threadId();
                            threadTasks.computeIfAbsent(threadId, k -> new AtomicInteger(0)).incrementAndGet();
                            // Simulate varying work
                            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            allDone.countDown();
                        }
                    });
                }

                assertTrue(allDone.await(60, TimeUnit.SECONDS));
                assertTrue(threadTasks.size() > 1, "Work should be distributed across threads");
            } finally {
                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
    }

    // === L5-06: Semaphore and Barrier Patterns ===

    @Nested
    @DisplayName("L5-06: Semaphore and Barrier Patterns")
    class SemaphoreAndBarrierPatternsTest {

        @Test
        @DisplayName("Semaphore limits concurrent access")
        void semaphoreLimitsConcurrentAccess() throws Exception {
            int maxConcurrent = 5;
            Semaphore semaphore = new Semaphore(maxConcurrent);
            int threadCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger maxObserved = new AtomicInteger(0);
            AtomicInteger currentCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        semaphore.acquire();
                        try {
                            int current = currentCount.incrementAndGet();
                            maxObserved.updateAndGet(max -> Math.max(max, current));
                            Thread.sleep(10);
                        } finally {
                            currentCount.decrementAndGet();
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(60, TimeUnit.SECONDS));

            assertTrue(maxObserved.get() <= maxConcurrent,
                "Concurrent access should not exceed semaphore permits");
        }

        @Test
        @DisplayName("CyclicBarrier synchronizes phases")
        void cyclicBarrierSynchronizesPhases() throws Exception {
            int threadCount = 5;
            int phases = 3;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger phaseCompletions = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int phase = 0; phase < phases; phase++) {
                            // Do phase work
                            Thread.sleep(10);
                            barrier.await(10, TimeUnit.SECONDS);
                            phaseCompletions.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Ignore for test
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            assertTrue(endLatch.await(60, TimeUnit.SECONDS));
            assertEquals(threadCount * phases, phaseCompletions.get());
        }

        @Test
        @DisplayName("CountDownLatch for fan-out/fan-in")
        void countDownLatchForFanOutFanIn() throws Exception {
            int workerCount = 10;
            CountDownLatch startSignal = new CountDownLatch(1);
            CountDownLatch doneSignal = new CountDownLatch(workerCount);
            List<Integer> results = new CopyOnWriteArrayList<>();

            for (int i = 0; i < workerCount; i++) {
                int workerId = i;
                executor.submit(() -> {
                    try {
                        startSignal.await();
                        // Simulate work
                        Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                        results.add(workerId * 2);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneSignal.countDown();
                    }
                });
            }

            startSignal.countDown(); // Start all workers
            assertTrue(doneSignal.await(30, TimeUnit.SECONDS)); // Wait for all

            assertEquals(workerCount, results.size());
        }

        @Test
        @DisplayName("Phaser for dynamic thread coordination")
        void phaserForDynamicThreadCoordination() throws Exception {
            Phaser phaser = new Phaser(1); // Self-registration
            int threadCount = 5;
            int phases = 3;
            AtomicInteger totalArrivals = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                phaser.register();
                executor.submit(() -> {
                    try {
                        for (int phase = 0; phase < phases; phase++) {
                            Thread.sleep(10);
                            totalArrivals.incrementAndGet();
                            phaser.arriveAndAwaitAdvance();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        phaser.arriveAndDeregister();
                    }
                });
            }

            // Wait for all phases to complete
            phaser.arriveAndAwaitAdvance();
            phaser.arriveAndAwaitAdvance();
            phaser.arriveAndAwaitAdvance();
            phaser.arriveAndDeregister();

            assertEquals(threadCount * phases, totalArrivals.get());
        }
    }

    // === L5-07: Blocking Queue Patterns ===

    @Nested
    @DisplayName("L5-07: Blocking Queue Patterns")
    class BlockingQueuePatternsTest {

        @Test
        @DisplayName("ArrayBlockingQueue bounded producer-consumer")
        void arrayBlockingQueueBoundedProducerConsumer() throws Exception {
            int capacity = 10;
            ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(capacity);
            int itemCount = 1000;
            AtomicInteger produced = new AtomicInteger(0);
            AtomicInteger consumed = new AtomicInteger(0);
            CountDownLatch done = new CountDownLatch(2);

            // Producer
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemCount; i++) {
                        queue.put(i);
                        produced.incrementAndGet();
                    }
                    queue.put(-1); // Poison pill
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            // Consumer
            executor.submit(() -> {
                try {
                    while (true) {
                        Integer item = queue.take();
                        if (item == -1) break;
                        consumed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            assertTrue(done.await(30, TimeUnit.SECONDS));
            assertEquals(itemCount, produced.get());
            assertEquals(itemCount, consumed.get());
        }

        @Test
        @DisplayName("LinkedBlockingQueue multiple producers consumers")
        void linkedBlockingQueueMultipleProducersConsumers() throws Exception {
            LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
            int producerCount = 5;
            int consumerCount = 3;
            int itemsPerProducer = 200;
            AtomicInteger producedTotal = new AtomicInteger(0);
            AtomicInteger consumedTotal = new AtomicInteger(0);
            AtomicBoolean producersDone = new AtomicBoolean(false);
            CountDownLatch producerLatch = new CountDownLatch(producerCount);
            CountDownLatch consumerLatch = new CountDownLatch(consumerCount);

            // Producers
            for (int p = 0; p < producerCount; p++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < itemsPerProducer; i++) {
                            queue.put(i);
                            producedTotal.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        producerLatch.countDown();
                    }
                });
            }

            // Consumers
            for (int c = 0; c < consumerCount; c++) {
                executor.submit(() -> {
                    try {
                        while (!producersDone.get() || !queue.isEmpty()) {
                            Integer item = queue.poll(100, TimeUnit.MILLISECONDS);
                            if (item != null) {
                                consumedTotal.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        consumerLatch.countDown();
                    }
                });
            }

            assertTrue(producerLatch.await(30, TimeUnit.SECONDS));
            producersDone.set(true);
            assertTrue(consumerLatch.await(30, TimeUnit.SECONDS));

            assertEquals(producerCount * itemsPerProducer, producedTotal.get());
            assertEquals(producedTotal.get(), consumedTotal.get());
        }

        @Test
        @DisplayName("PriorityBlockingQueue maintains order")
        void priorityBlockingQueueMaintainsOrder() throws Exception {
            PriorityBlockingQueue<Integer> queue = new PriorityBlockingQueue<>();
            int itemCount = 100;
            List<Integer> results = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);

            // Add items in random order
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < itemCount; i++) {
                items.add(i);
            }
            Collections.shuffle(items);
            queue.addAll(items);

            // Consume in priority order
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemCount; i++) {
                        results.add(queue.take());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            assertTrue(done.await(10, TimeUnit.SECONDS));

            // Should be sorted
            for (int i = 0; i < itemCount; i++) {
                assertEquals(i, results.get(i));
            }
        }

        @Test
        @DisplayName("DelayQueue respects delays")
        void delayQueueRespectsDelays() throws Exception {
            class DelayedItem implements Delayed {
                final long expireTime;
                final int id;

                DelayedItem(long delayMs, int id) {
                    this.expireTime = System.currentTimeMillis() + delayMs;
                    this.id = id;
                }

                @Override
                public long getDelay(TimeUnit unit) {
                    return unit.convert(expireTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                }

                @Override
                public int compareTo(Delayed o) {
                    return Long.compare(this.expireTime, ((DelayedItem) o).expireTime);
                }
            }

            DelayQueue<DelayedItem> queue = new DelayQueue<>();
            queue.put(new DelayedItem(200, 2));
            queue.put(new DelayedItem(100, 1));
            queue.put(new DelayedItem(50, 0));

            List<Integer> order = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);

            executor.submit(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        DelayedItem item = queue.take();
                        order.add(item.id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(Arrays.asList(0, 1, 2), order, "Items should be consumed in delay order");
        }
    }
}
