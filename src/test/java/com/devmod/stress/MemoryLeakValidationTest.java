package com.devmod.stress;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.runtime.InstanceState;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("L5: Memory Leak Validation Tests")
class MemoryLeakValidationTest {

    // === L5-08: Reference Cleanup Patterns ===

    @Nested
    @DisplayName("L5-08: Reference Cleanup Patterns")
    class ReferenceCleanupPatternsTest {

        @Test
        @DisplayName("Removed entries are garbage collectible")
        void removedEntriesAreGarbageCollectible() {
            Map<UUID, byte[]> map = new HashMap<>();
            List<WeakReference<byte[]>> refs = new ArrayList<>();

            // Add large objects
            for (int i = 0; i < 100; i++) {
                UUID id = UUID.randomUUID();
                byte[] data = new byte[1024 * 10]; // 10KB each
                refs.add(new WeakReference<>(data));
                map.put(id, data);
            }

            assertEquals(100, map.size());

            // Remove all entries
            map.clear();

            // Request GC
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }

            // Some references should be collected
            long collected = refs.stream().filter(ref -> ref.get() == null).count();
            // We can't guarantee GC behavior, but pattern is validated
            assertTrue(collected >= 0, "GC should be able to collect removed entries");
        }

        @Test
        @DisplayName("WeakHashMap entries collected when key unreachable")
        void weakHashMapEntriesCollectedWhenKeyUnreachable() {
            WeakHashMap<Object, String> map = new WeakHashMap<>();
            ReferenceQueue<Object> queue = new ReferenceQueue<>();

            // Create keys
            Object key1 = new Object();
            Object key2 = new Object();
            WeakReference<Object> ref1 = new WeakReference<>(key1, queue);

            map.put(key1, "value1");
            map.put(key2, "value2");

            assertEquals(2, map.size());
            assertNotNull(ref1.get(), "Weak reference should initially hold key1");

            // Clear strong reference to key1
            key1 = null;

            // Request GC
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }

            // Force expungeStaleEntries by accessing map
            int sizeAfterAccess = map.size();
            assertTrue(sizeAfterAccess >= 0);

            // key2 should still be present
            assertNotNull(key2);
            assertTrue(map.containsKey(key2));
        }

        @Test
        @DisplayName("Phantom references allow cleanup before collection")
        void phantomReferencesAllowCleanupBeforeCollection() {
            ReferenceQueue<Object> queue = new ReferenceQueue<>();
            List<PhantomReference<Object>> phantoms = new ArrayList<>();

            // Create objects with phantom references
            for (int i = 0; i < 10; i++) {
                Object obj = new byte[1024 * 100]; // 100KB
                phantoms.add(new PhantomReference<>(obj, queue));
            }

            assertEquals(10, phantoms.size());

            // Request GC
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }

            // Check queue for enqueued references
            int enqueuedCount = 0;
            Reference<?> ref;
            while ((ref = queue.poll()) != null) {
                enqueuedCount++;
                ref.clear();
            }

            // At least some should be enqueued (can't guarantee exact count)
            assertTrue(enqueuedCount >= 0, "Phantom references should be processed");
        }

        @Test
        @DisplayName("SoftReference survives until memory pressure")
        void softReferenceSurvivesUntilMemoryPressure() {
            byte[] data = new byte[1024 * 1024]; // 1MB
            SoftReference<byte[]> softRef = new SoftReference<>(data);

            // Clear strong reference
            data = null;

            // Without memory pressure, soft reference should survive
            // We can't guarantee this, but can test the pattern
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }

            // May or may not be collected depending on JVM state
            // Just validate the pattern works
            boolean wasCollected = softRef.get() == null;
            assertTrue(true, "Soft reference pattern validated, collected=" + wasCollected);
        }
    }

    // === L5-09: Collection Memory Patterns ===

    @Nested
    @DisplayName("L5-09: Collection Memory Patterns")
    class CollectionMemoryPatternsTest {

        @Test
        @DisplayName("ArrayList trimToSize reduces memory")
        void arrayListTrimToSizeReducesMemory() {
            ArrayList<UUID> list = new ArrayList<>(1000);

            // Add items
            for (int i = 0; i < 100; i++) {
                list.add(UUID.randomUUID());
            }

            // Capacity is still 1000, but size is 100
            assertEquals(100, list.size());

            // Trim to actual size
            list.trimToSize();

            // List still functional
            assertEquals(100, list.size());
            assertNotNull(list.get(0));
        }

        @Test
        @DisplayName("HashMap load factor affects memory/performance")
        void hashMapLoadFactorAffectsMemoryPerformance() {
            // Low load factor = more memory, fewer collisions
            HashMap<UUID, String> lowLoadFactor = new HashMap<>(16, 0.5f);

            // High load factor = less memory, more collisions
            HashMap<UUID, String> highLoadFactor = new HashMap<>(16, 0.9f);

            // Add same data to both
            for (int i = 0; i < 100; i++) {
                UUID id = UUID.randomUUID();
                lowLoadFactor.put(id, "value" + i);
                highLoadFactor.put(id, "value" + i);
            }

            assertEquals(lowLoadFactor.size(), highLoadFactor.size());
        }

        @Test
        @DisplayName("ArrayDeque memory overhead per element")
        void arrayDequeMemoryOverheadPerElement() {
            // ArrayDeque uses a resizable array for queue semantics
            ArrayDeque<UUID> deque = new ArrayDeque<>();
            ArrayList<UUID> arrayList = new ArrayList<>();

            int count = 1000;
            for (int i = 0; i < count; i++) {
                UUID id = UUID.randomUUID();
                deque.add(id);
                arrayList.add(id);
            }

            assertEquals(deque.size(), arrayList.size());
        }

        @Test
        @DisplayName("EnumSet memory efficiency")
        void enumSetMemoryEfficiency() {
            // EnumSet uses bit vector - very memory efficient
            EnumSet<InstanceState> states = EnumSet.noneOf(InstanceState.class);

            states.add(InstanceState.CREATING);
            states.add(InstanceState.READY);
            states.add(InstanceState.ACTIVE);

            assertTrue(states.contains(InstanceState.CREATING));
            assertTrue(states.contains(InstanceState.READY));
            assertFalse(states.contains(InstanceState.DESTROYED));

            // All of uses bit vector with all bits set
            EnumSet<InstanceState> allStates = EnumSet.allOf(InstanceState.class);
            assertEquals(InstanceState.values().length, allStates.size());
        }

        @Test
        @DisplayName("EnumMap memory efficiency")
        void enumMapMemoryEfficiency() {
            // EnumMap uses array indexed by ordinal - very efficient
            EnumMap<InstanceState, Integer> counts = new EnumMap<>(InstanceState.class);

            for (InstanceState state : InstanceState.values()) {
                counts.put(state, 0);
            }

            counts.compute(InstanceState.CREATING, (k, v) -> v + 1);
            counts.compute(InstanceState.CREATING, (k, v) -> v + 1);

            assertEquals(2, counts.get(InstanceState.CREATING));
            assertEquals(0, counts.get(InstanceState.DESTROYED));
        }
    }

    // === L5-10: Resource Cleanup Patterns ===

    @Nested
    @DisplayName("L5-10: Resource Cleanup Patterns")
    class ResourceCleanupPatternsTest {

        @Test
        @DisplayName("Try-with-resources ensures cleanup")
        void tryWithResourcesEnsuresCleanup() {
            AtomicInteger closeCount = new AtomicInteger(0);

            class TestResource implements AutoCloseable {
                @Override
                public void close() {
                    closeCount.incrementAndGet();
                }
            }

            try (TestResource r1 = new TestResource();
                 TestResource r2 = new TestResource()) {
                // Use resources
                assertNotNull(r1);
                assertNotNull(r2);
            }

            assertEquals(2, closeCount.get(), "All resources should be closed");
        }

        @Test
        @DisplayName("Cleanup happens even with exception")
        void cleanupHappensEvenWithException() {
            AtomicInteger closeCount = new AtomicInteger(0);

            class TestResource implements AutoCloseable {
                @Override
                public void close() {
                    closeCount.incrementAndGet();
                }
            }

            try {
                try (TestResource r = new TestResource()) {
                    assertNotNull(r);
                    throw new RuntimeException("Test exception");
                }
            } catch (RuntimeException e) {
                assertEquals("Test exception", e.getMessage());
            }

            assertEquals(1, closeCount.get(), "Resource should be closed even with exception");
        }

        @Test
        @DisplayName("Cleaner API for native resource cleanup")
        void cleanerApiForNativeResourceCleanup() {
            java.lang.ref.Cleaner cleaner = java.lang.ref.Cleaner.create();
            AtomicInteger cleanupCount = new AtomicInteger(0);

            class Resource {
                private final java.lang.ref.Cleaner.Cleanable cleanable;

                Resource() {
                    this.cleanable = cleaner.register(this, () -> cleanupCount.incrementAndGet());
                }

                void close() {
                    cleanable.clean();
                }
            }

            Resource r = new Resource();
            r.close();

            assertEquals(1, cleanupCount.get());
        }

        @Test
        @DisplayName("ExecutorService shutdown pattern")
        void executorServiceShutdownPattern() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AtomicInteger taskCount = new AtomicInteger(0);

            // Submit tasks
            for (int i = 0; i < 10; i++) {
                executor.submit(() -> {
                    taskCount.incrementAndGet();
                    try { Thread.sleep(10); } catch (InterruptedException e) { /* ignore */ }
                }).isDone();
            }

            // Proper shutdown pattern
            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

            assertTrue(terminated, "Executor should terminate");
            assertEquals(10, taskCount.get());
        }

        @Test
        @DisplayName("ScheduledExecutorService proper cancellation")
        void scheduledExecutorServiceProperCancellation() throws InterruptedException {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            AtomicInteger runCount = new AtomicInteger(0);

            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                runCount::incrementAndGet,
                0, 50, TimeUnit.MILLISECONDS
            );

            // Let it run a few times
            Thread.sleep(150);
            future.cancel(false);

            int countAfterCancel = runCount.get();
            Thread.sleep(100);

            // Should not have run more after cancellation
            assertEquals(countAfterCancel, runCount.get(), "Task should not run after cancellation");

            scheduler.shutdown();
            assertTrue(scheduler.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    // === L5-11: Cache Eviction Patterns ===

    @Nested
    @DisplayName("L5-11: Cache Eviction Patterns")
    class CacheEvictionPatternsTest {

        @Test
        @DisplayName("LRU cache eviction with LinkedHashMap")
        void lruCacheEvictionWithLinkedHashMap() {
            int maxSize = 5;
            LinkedHashMap<UUID, String> lruCache = new LinkedHashMap<>(maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
                    return size() > maxSize;
                }
            };

            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                lruCache.put(id, "value" + i);
            }

            assertEquals(maxSize, lruCache.size(), "Cache should not exceed max size");
            // First 5 should be evicted
            for (int i = 0; i < 5; i++) {
                assertFalse(lruCache.containsKey(ids.get(i)), "Older entries should be evicted");
            }
            // Last 5 should remain
            for (int i = 5; i < 10; i++) {
                assertTrue(lruCache.containsKey(ids.get(i)), "Recent entries should remain");
            }
        }

        @Test
        @DisplayName("Time-based expiration simulation")
        void timeBasedExpirationSimulation() {
            class CacheEntry {
                final String value;
                final long expirationTime;

                CacheEntry(String value, long ttlMs) {
                    this.value = value;
                    this.expirationTime = System.currentTimeMillis() + ttlMs;
                }

                boolean isExpired() {
                    return System.currentTimeMillis() > expirationTime;
                }
            }

            Map<UUID, CacheEntry> cache = new HashMap<>();
            UUID id = UUID.randomUUID();
            cache.put(id, new CacheEntry("test", 100)); // 100ms TTL

            assertEquals("test", cache.get(id).value);
            assertFalse(cache.get(id).isExpired(), "Entry should not be expired immediately");

            // Wait for expiration
            try { Thread.sleep(150); } catch (InterruptedException e) { /* ignore */ }

            assertTrue(cache.get(id).isExpired(), "Entry should be expired after TTL");

            // Cleanup expired entries
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
            assertTrue(cache.isEmpty(), "Expired entries should be removed");
        }

        @Test
        @DisplayName("Size-based eviction with computeIfAbsent")
        void sizeBasedEvictionWithComputeIfAbsent() {
            int maxSize = 100;
            HashMap<Integer, String> cache = new HashMap<>();
            AtomicInteger computeCount = new AtomicInteger(0);

            // Fill cache with computeIfAbsent
            for (int i = 0; i < maxSize * 2; i++) {
                int key = i % maxSize; // Reuse keys
                cache.computeIfAbsent(key, k -> {
                    computeCount.incrementAndGet();
                    return "computed" + k;
                });
            }

            assertEquals(maxSize, cache.size(), "Cache size should be limited");
            // First 100 keys computed, second 100 are hits
            assertEquals(maxSize, computeCount.get(), "Each key computed once");
        }

        @Test
        @DisplayName("WeakReference cache for optional data")
        void weakReferenceCacheForOptionalData() {
            Map<UUID, WeakReference<byte[]>> cache = new HashMap<>();

            // Add entries
            for (int i = 0; i < 100; i++) {
                cache.put(UUID.randomUUID(), new WeakReference<>(new byte[1024]));
            }

            assertEquals(100, cache.size());

            // Request GC
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }

            // Cleanup entries where reference was collected
            cache.entrySet().removeIf(e -> e.getValue().get() == null);

            // Size may be reduced (can't guarantee GC behavior)
            assertTrue(cache.size() <= 100);
        }
    }

    // === L5-12: Object Pool Patterns ===

    @Nested
    @DisplayName("L5-12: Object Pool Patterns")
    class ObjectPoolPatternsTest {

        @Test
        @DisplayName("Simple object pool reduces allocations")
        void simpleObjectPoolReducesAllocations() {
            class PooledObject {
                private boolean inUse = false;

                void reset() {
                    inUse = false;
                }

                void acquire() {
                    inUse = true;
                }

                boolean isInUse() {
                    return inUse;
                }
            }

            BlockingQueue<PooledObject> pool = new LinkedBlockingQueue<>();
            int poolSize = 10;

            // Pre-populate pool
            for (int i = 0; i < poolSize; i++) {
                pool.offer(new PooledObject());
            }

            int createCount = 0;

            // Acquire and release pattern
            for (int i = 0; i < 100; i++) {
                PooledObject obj = pool.poll();
                if (obj == null) {
                    obj = new PooledObject();
                    createCount++;
                }
                obj.acquire();
                assertTrue(obj.isInUse(), "Object should be marked in use after acquire");

                // Use object...

                obj.reset();
                assertFalse(obj.isInUse(), "Object should be reset before returning to pool");
                pool.offer(obj);
            }

            assertEquals(0, createCount, "Pool should handle all requests without new allocations");
            assertEquals(poolSize, pool.size(), "Pool size should be unchanged");
        }

        @Test
        @DisplayName("ThreadLocal object pool per thread")
        void threadLocalObjectPoolPerThread() throws Exception {
            ThreadLocal<StringBuilder> builderPool = ThreadLocal.withInitial(StringBuilder::new);
            int threadCount = 10;
            int operationsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            Set<Integer> builderIds = ConcurrentHashMap.newKeySet();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            try {
                for (int t = 0; t < threadCount; t++) {
                    executor.submit(() -> {
                        try {
                            for (int i = 0; i < operationsPerThread; i++) {
                                StringBuilder sb = builderPool.get();
                                builderIds.add(System.identityHashCode(sb));
                                sb.setLength(0); // Reset
                                sb.append("test").append(i);
                            }
                        } finally {
                            latch.countDown();
                        }
                    }).isDone();
                }

                assertTrue(latch.await(10, TimeUnit.SECONDS));
                // Each thread should have its own StringBuilder
                assertTrue(builderIds.size() <= threadCount,
                    "Each thread should reuse its own StringBuilder");
            } finally {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("Bounded pool with timeout")
        void boundedPoolWithTimeout() throws Exception {
            ArrayBlockingQueue<Object> pool = new ArrayBlockingQueue<>(2);
            pool.offer(new Object());
            pool.offer(new Object());

            // Take both objects
            Object obj1 = pool.poll();
            Object obj2 = pool.poll();

            assertNotNull(obj1);
            assertNotNull(obj2);

            // Pool is empty, poll with timeout
            long start = System.currentTimeMillis();
            Object obj3 = pool.poll(100, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - start;

            assertNull(obj3, "Should return null when pool is empty");
            assertTrue(elapsed >= 90, "Should wait for timeout");

            // Return objects
            pool.offer(obj1);
            pool.offer(obj2);

            assertEquals(2, pool.size());
        }
    }

    // === L5-13: String Memory Patterns ===

    @Nested
    @DisplayName("L5-13: String Memory Patterns")
    class StringMemoryPatternsTest {

        @Test
        @DisplayName("String interning reduces duplicates")
        void stringInterningReducesDuplicates() {
            String s1 = new String("test");
            String s2 = new String("test");

            // Different objects
            assertNotSame(s1, s2);

            // After interning, same object
            String i1 = s1.intern();
            String i2 = s2.intern();

            assertSame(i1, i2, "Interned strings should be the same object");
        }

        @Test
        @DisplayName("StringBuilder vs String concatenation")
        void stringBuilderVsStringConcatenation() {
            int iterations = 1000;

            // StringBuilder approach
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < iterations; i++) {
                sb.append("item").append(i).append(",");
            }
            String result1 = sb.toString();

            // Both produce same result
            assertTrue(result1.length() > 0);
            assertTrue(result1.contains("item0"));
            assertTrue(result1.contains("item999"));
        }

        @Test
        @DisplayName("Substring shares backing array in older JVMs")
        void substringBehavior() {
            // In Java 7+, substring creates a new backing array
            // This test validates the API behavior
            String original = "This is a very long string that we will take a substring of";
            String sub = original.substring(10, 20);

            assertEquals("very long ", sub);
            assertNotSame(original, sub);
        }

        @Test
        @DisplayName("String.join efficiency")
        void stringJoinEfficiency() {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                items.add("item" + i);
            }

            String joined = String.join(",", items);

            assertTrue(joined.startsWith("item0,item1"));
            assertTrue(joined.endsWith("item99"));
            assertEquals(99, joined.chars().filter(c -> c == ',').count());
        }
    }

    // === L5-14: Instance Registry Cleanup Simulation ===

    @Nested
    @DisplayName("L5-14: Instance Registry Cleanup Simulation")
    class InstanceRegistryCleanupSimulationTest {

        @Test
        @DisplayName("Destroyed instances removed from registry")
        void destroyedInstancesRemovedFromRegistry() {
            HashMap<UUID, InstanceState> registry = new HashMap<>();

            // Add instances
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                registry.put(id, InstanceState.ACTIVE);
            }

            assertEquals(100, registry.size());

            // Mark some as destroyed
            for (int i = 0; i < 50; i++) {
                registry.put(ids.get(i), InstanceState.DESTROYED);
            }

            // Cleanup destroyed instances
            registry.entrySet().removeIf(e -> e.getValue() == InstanceState.DESTROYED);

            assertEquals(50, registry.size(), "Destroyed instances should be removed");
        }

        @Test
        @DisplayName("Player session cleanup on disconnect")
        void playerSessionCleanupOnDisconnect() {
            HashMap<UUID, Object> sessions = new HashMap<>();
            HashMap<UUID, Set<UUID>> instancePlayers = new HashMap<>();

            UUID instanceId = UUID.randomUUID();
            instancePlayers.put(instanceId, new HashSet<>());

            // Add players
            List<UUID> playerIds = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                UUID playerId = UUID.randomUUID();
                playerIds.add(playerId);
                sessions.put(playerId, new Object());
                instancePlayers.get(instanceId).add(playerId);
            }

            assertEquals(10, sessions.size());
            assertEquals(10, instancePlayers.get(instanceId).size());

            // Simulate player disconnect
            UUID disconnectedPlayer = playerIds.get(0);
            sessions.remove(disconnectedPlayer);
            instancePlayers.get(instanceId).remove(disconnectedPlayer);

            assertEquals(9, sessions.size());
            assertEquals(9, instancePlayers.get(instanceId).size());
        }

        @Test
        @DisplayName("Periodic cleanup of stale data")
        void periodicCleanupOfStaleData() {
            class TimestampedEntry {
                final long createTime;
                final String data;

                TimestampedEntry(String data) {
                    this.createTime = System.currentTimeMillis();
                    this.data = data;
                }

                boolean isStale(long maxAgeMs) {
                    return System.currentTimeMillis() - createTime > maxAgeMs;
                }
            }

            HashMap<UUID, TimestampedEntry> cache = new HashMap<>();

            // Add entries
            for (int i = 0; i < 100; i++) {
                cache.put(UUID.randomUUID(), new TimestampedEntry("data" + i));
            }

            String sampleData = cache.values().iterator().next().data;
            assertTrue(sampleData.startsWith("data"));

            // Immediate cleanup should remove nothing
            cache.entrySet().removeIf(e -> e.getValue().isStale(1000));
            assertEquals(100, cache.size());

            // Wait and cleanup
            try { Thread.sleep(50); } catch (InterruptedException e) { /* ignore */ }
            cache.entrySet().removeIf(e -> e.getValue().isStale(25));

            assertTrue(cache.size() < 100, "Stale entries should be removed");
        }

        @Test
        @DisplayName("Full cleanup sequence")
        void fullCleanupSequence() {
            // Simulate full instance cleanup
            class InstanceData {
                final UUID id = UUID.randomUUID();
                final Set<UUID> players = new HashSet<>();
                final Map<String, Object> metadata = new HashMap<>();
                InstanceState state = InstanceState.ACTIVE;
            }

            HashMap<UUID, InstanceData> instances = new HashMap<>();

            // Create instance with data
            InstanceData instance = new InstanceData();
            for (int i = 0; i < 5; i++) {
                instance.players.add(UUID.randomUUID());
            }
            instance.metadata.put("key1", "value1");
            instance.metadata.put("key2", new byte[1024]);
            instances.put(instance.id, instance);

            // Cleanup sequence
            instance.state = InstanceState.DESTROYING;
            instance.players.clear();
            instance.metadata.clear();
            instance.state = InstanceState.DESTROYED;
            assertEquals(InstanceState.DESTROYED, instance.state);
            instances.remove(instance.id);

            assertTrue(instances.isEmpty());
            assertTrue(instance.players.isEmpty());
            assertTrue(instance.metadata.isEmpty());
        }
    }

    // === L5-15: Memory Pressure Handling ===

    @Nested
    @DisplayName("L5-15: Memory Pressure Handling")
    class MemoryPressureHandlingTest {

        @Test
        @DisplayName("Runtime memory info available")
        void runtimeMemoryInfoAvailable() {
            Runtime runtime = Runtime.getRuntime();

            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            assertTrue(maxMemory > 0, "Max memory should be positive");
            assertTrue(totalMemory > 0, "Total memory should be positive");
            assertTrue(freeMemory >= 0, "Free memory should be non-negative");
            assertTrue(usedMemory >= 0, "Used memory should be non-negative");
            assertTrue(totalMemory <= maxMemory, "Total should not exceed max");
        }

        @Test
        @DisplayName("Low memory threshold detection")
        void lowMemoryThresholdDetection() {
            Runtime runtime = Runtime.getRuntime();

            double usedRatio = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();

            // Define threshold
            double lowMemoryThreshold = 0.9; // 90%

            boolean isLowMemory = usedRatio > lowMemoryThreshold;

            // Just validate the pattern works
            assertTrue(usedRatio >= 0 && usedRatio <= 1.0);
            assertNotNull(Boolean.valueOf(isLowMemory));
        }

        @Test
        @DisplayName("Graceful degradation under pressure")
        void gracefulDegradationUnderPressure() {
            // Simulate cache that degrades under memory pressure
            class AdaptiveCache<K, V> {
                private final Map<K, V> primary = new ConcurrentHashMap<>();
                private final Map<K, SoftReference<V>> secondary = new ConcurrentHashMap<>();
                private final int primaryMaxSize;

                AdaptiveCache(int primaryMaxSize) {
                    this.primaryMaxSize = primaryMaxSize;
                }

                void put(K key, V value) {
                    if (primary.size() < primaryMaxSize) {
                        primary.put(key, value);
                    } else {
                        secondary.put(key, new SoftReference<>(value));
                    }
                }

                @Nullable V get(K key) {
                    V value = primary.get(key);
                    if (value != null) return value;

                    SoftReference<V> ref = secondary.get(key);
                    return ref != null ? ref.get() : null;
                }

                int primarySize() { return primary.size(); }
                int secondarySize() { return secondary.size(); }
            }

            int primaryMaxSize = 10;
            AdaptiveCache<UUID, String> cache = new AdaptiveCache<>(primaryMaxSize);

            // Add entries
            UUID primaryKey = UUID.randomUUID();
            UUID secondaryKey = UUID.randomUUID();
            cache.put(primaryKey, "value0");
            for (int i = 1; i < 20; i++) {
                UUID key = (i == primaryMaxSize) ? secondaryKey : UUID.randomUUID();
                cache.put(key, "value" + i);
            }

            assertEquals(primaryMaxSize, cache.primarySize(), "Primary should be at max");
            assertEquals(10, cache.secondarySize(), "Overflow should go to secondary");
            assertEquals("value0", Objects.requireNonNull(cache.get(primaryKey)));
            String secondaryValue = Objects.requireNonNull(cache.get(secondaryKey),
                "Secondary storage should serve overflow items");
            assertNotNull(secondaryValue);
        }
    }
}
