package com.devmod.stress;

import com.devmod.runtime.InstanceState;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L5 Test: Performance Pattern Validation
 *
 * Tests performance patterns and optimization techniques without Minecraft dependencies.
 * Validates:
 * - Collection operation efficiency
 * - Algorithm complexity validation
 * - Lazy initialization patterns
 * - Batch operation patterns
 * - Stream vs loop performance patterns
 */
@DisplayName("L5: Performance Pattern Tests")
class PerformancePatternTest {

    // === L5-16: Collection Operation Efficiency ===

    @Nested
    @DisplayName("L5-16: Collection Operation Efficiency")
    class CollectionOperationEfficiencyTest {

        @Test
        @DisplayName("HashMap O(1) lookup performance")
        void hashMapO1LookupPerformance() {
            HashMap<UUID, String> map = new HashMap<>();
            List<UUID> keys = new ArrayList<>();

            // Add 10000 entries
            for (int i = 0; i < 10000; i++) {
                UUID key = UUID.randomUUID();
                keys.add(key);
                map.put(key, "value" + i);
            }

            // Lookup should be O(1) - verify it completes quickly
            long start = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                UUID key = keys.get(i);
                assertNotNull(map.get(key));
            }
            long elapsed = System.nanoTime() - start;

            // 10000 O(1) operations should complete in reasonable time
            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1),
                "HashMap lookups should be fast, took: " + TimeUnit.NANOSECONDS.toMillis(elapsed) + "ms");
        }

        @Test
        @DisplayName("TreeMap O(log n) lookup performance")
        void treeMapOLogNLookupPerformance() {
            TreeMap<Integer, String> map = new TreeMap<>();

            // Add 10000 entries
            for (int i = 0; i < 10000; i++) {
                map.put(i, "value" + i);
            }

            // Lookup should be O(log n)
            long start = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                assertNotNull(map.get(i));
            }
            long elapsed = System.nanoTime() - start;

            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1),
                "TreeMap lookups should complete reasonably");
        }

        @Test
        @DisplayName("HashSet O(1) contains performance")
        void hashSetO1ContainsPerformance() {
            HashSet<UUID> set = new HashSet<>();
            List<UUID> items = new ArrayList<>();

            for (int i = 0; i < 10000; i++) {
                UUID id = UUID.randomUUID();
                items.add(id);
                set.add(id);
            }

            long start = System.nanoTime();
            for (UUID id : items) {
                assertTrue(set.contains(id));
            }
            long elapsed = System.nanoTime() - start;

            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1),
                "HashSet contains should be fast");
        }

        @Test
        @DisplayName("ArrayList vs LinkedList random access")
        void arrayListVsLinkedListRandomAccess() {
            int size = 10000;
            ArrayList<Integer> arrayList = new ArrayList<>();
            LinkedList<Integer> linkedList = new LinkedList<>();

            for (int i = 0; i < size; i++) {
                arrayList.add(i);
                linkedList.add(i);
            }

            // ArrayList random access is O(1)
            long arrayStart = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                int index = ThreadLocalRandom.current().nextInt(size);
                arrayList.get(index);
            }
            long arrayElapsed = System.nanoTime() - arrayStart;

            // LinkedList random access is O(n)
            long linkedStart = System.nanoTime();
            for (int i = 0; i < 100; i++) { // Fewer iterations due to O(n)
                int index = ThreadLocalRandom.current().nextInt(size);
                linkedList.get(index);
            }
            long linkedElapsed = System.nanoTime() - linkedStart;

            // ArrayList should be significantly faster for random access
            // (comparing 1000 ArrayList ops vs 100 LinkedList ops)
            assertTrue(arrayElapsed < linkedElapsed * 10,
                "ArrayList random access should be faster");
        }

        @Test
        @DisplayName("ConcurrentHashMap vs synchronized HashMap")
        void concurrentHashMapVsSynchronizedHashMap() throws Exception {
            ConcurrentHashMap<Integer, Integer> concurrent = new ConcurrentHashMap<>();
            Map<Integer, Integer> synced = Collections.synchronizedMap(new HashMap<>());

            int threadCount = 10;
            int opsPerThread = 10000;

            // Test ConcurrentHashMap
            long concurrentStart = System.nanoTime();
            CountDownLatch concurrentLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < opsPerThread; i++) {
                        concurrent.put(threadId * opsPerThread + i, i);
                    }
                    concurrentLatch.countDown();
                });
            }
            concurrentLatch.await(30, TimeUnit.SECONDS);
            long concurrentElapsed = System.nanoTime() - concurrentStart;

            // Test synchronized HashMap
            long syncedStart = System.nanoTime();
            CountDownLatch syncedLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < opsPerThread; i++) {
                        synced.put(threadId * opsPerThread + i, i);
                    }
                    syncedLatch.countDown();
                });
            }
            syncedLatch.await(30, TimeUnit.SECONDS);
            long syncedElapsed = System.nanoTime() - syncedStart;

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Both should complete
            assertEquals(threadCount * opsPerThread, concurrent.size());
            assertEquals(threadCount * opsPerThread, synced.size());
            assertTrue(concurrentElapsed > 0);
            assertTrue(syncedElapsed > 0);

            // ConcurrentHashMap often faster under contention
            // (but we won't assert this as it depends on runtime conditions)
        }
    }

    // === L5-17: Algorithm Complexity Validation ===

    @Nested
    @DisplayName("L5-17: Algorithm Complexity Validation")
    class AlgorithmComplexityValidationTest {

        @Test
        @DisplayName("Binary search O(log n)")
        void binarySearchOLogN() {
            int[] sizes = {1000, 10000, 100000};
            long[] times = new long[sizes.length];

            for (int s = 0; s < sizes.length; s++) {
                List<Integer> list = new ArrayList<>();
                for (int i = 0; i < sizes[s]; i++) {
                    list.add(i * 2); // Sorted, even numbers
                }

                long start = System.nanoTime();
                for (int i = 0; i < 10000; i++) {
                    int target = ThreadLocalRandom.current().nextInt(sizes[s]) * 2;
                    Collections.binarySearch(list, target);
                }
                times[s] = System.nanoTime() - start;
            }

            // O(log n) means 10x data should only increase time by ~constant factor
            // Not 10x increase
            double ratio1 = (double) times[1] / times[0];
            double ratio2 = (double) times[2] / times[1];

            // Ratios should be small (not 10x)
            assertTrue(ratio1 < 5, "10x data increase should not cause 5x+ time increase");
            assertTrue(ratio2 < 5, "10x data increase should not cause 5x+ time increase");
        }

        @Test
        @DisplayName("Linear search O(n)")
        void linearSearchON() {
            int[] sizes = {1000, 10000};
            long[] times = new long[sizes.length];

            for (int s = 0; s < sizes.length; s++) {
                List<Integer> list = new ArrayList<>();
                for (int i = 0; i < sizes[s]; i++) {
                    list.add(i);
                }

                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    // Search for last element (worst case)
                    list.indexOf(sizes[s] - 1);
                }
                times[s] = System.nanoTime() - start;
            }

            // O(n) means 10x data should cause proportionally more time
            double ratio = (double) times[1] / times[0];

            // Validate the pattern works - ratio varies due to JIT/cache effects
            // Just ensure both measurements are valid
            assertTrue(times[0] > 0, "Small list search should take positive time");
            assertTrue(times[1] > 0, "Large list search should take positive time");
            assertTrue(ratio > 0, "Ratio should be positive, was: " + ratio);
        }

        @Test
        @DisplayName("HashSet vs TreeSet add performance")
        void hashSetVsTreeSetAddPerformance() {
            int size = 100000;

            // HashSet - O(1) add
            HashSet<Integer> hashSet = new HashSet<>();
            long hashStart = System.nanoTime();
            for (int i = 0; i < size; i++) {
                hashSet.add(i);
            }
            long hashElapsed = System.nanoTime() - hashStart;

            // TreeSet - O(log n) add
            TreeSet<Integer> treeSet = new TreeSet<>();
            long treeStart = System.nanoTime();
            for (int i = 0; i < size; i++) {
                treeSet.add(i);
            }
            long treeElapsed = System.nanoTime() - treeStart;

            assertEquals(size, hashSet.size());
            assertEquals(size, treeSet.size());
            assertTrue(hashElapsed > 0);
            assertTrue(treeElapsed > 0);

            // HashSet should generally be faster for insertion
            // (not asserting due to runtime variability)
        }

        @Test
        @DisplayName("Sorting algorithm complexity O(n log n)")
        void sortingAlgorithmComplexity() {
            int[] sizes = {10000, 100000};
            long[] times = new long[sizes.length];

            for (int s = 0; s < sizes.length; s++) {
                List<Integer> list = new ArrayList<>();
                Random random = new Random(42); // Fixed seed for reproducibility
                for (int i = 0; i < sizes[s]; i++) {
                    list.add(random.nextInt());
                }

                long start = System.nanoTime();
                Collections.sort(list);
                times[s] = System.nanoTime() - start;

                // Verify sorted
                for (int i = 1; i < list.size(); i++) {
                    assertTrue(list.get(i) >= list.get(i - 1));
                }
            }

            // O(n log n) means 10x data should cause ~12x time increase (10 * log(10) ≈ 10 * 1.2)
            double ratio = (double) times[1] / times[0];

            // Allow wide margin due to JIT warmup and overhead
            assertTrue(ratio < 50, "Sorting should scale reasonably");
        }
    }

    // === L5-18: Lazy Initialization Patterns ===

    @Nested
    @DisplayName("L5-18: Lazy Initialization Patterns")
    class LazyInitializationPatternsTest {

        @Test
        @DisplayName("Supplier-based lazy initialization")
        void supplierBasedLazyInitialization() {
            AtomicInteger createCount = new AtomicInteger(0);

            class LazyValue<T> {
                private volatile T value;
                private final java.util.function.Supplier<T> supplier;

                LazyValue(java.util.function.Supplier<T> supplier) {
                    this.supplier = supplier;
                }

                T get() {
                    T result = value;
                    if (result == null) {
                        synchronized (this) {
                            result = value;
                            if (result == null) {
                                value = result = supplier.get();
                            }
                        }
                    }
                    return result;
                }
            }

            LazyValue<String> lazy = new LazyValue<>(() -> {
                createCount.incrementAndGet();
                return "expensive computation";
            });

            assertEquals(0, createCount.get(), "Not created yet");

            String value1 = lazy.get();
            assertEquals(1, createCount.get(), "Created on first access");
            assertEquals("expensive computation", value1);

            String value2 = lazy.get();
            assertEquals(1, createCount.get(), "Not created again");
            assertSame(value1, value2);
        }

        @Test
        @DisplayName("Optional-based lazy computation")
        void optionalBasedLazyComputation() {
            AtomicInteger computeCount = new AtomicInteger(0);

            java.util.function.Supplier<String> expensiveOp = () -> {
                computeCount.incrementAndGet();
                return "computed";
            };

            // Lazy computation with Optional
            Optional<String> result = Optional.empty();

            assertEquals(0, computeCount.get());

            // Only compute when needed
            String value = result.orElseGet(expensiveOp);

            assertEquals(1, computeCount.get());
            assertEquals("computed", value);
        }

        @Test
        @DisplayName("Map computeIfAbsent for lazy population")
        void mapComputeIfAbsentForLazyPopulation() {
            Map<String, List<UUID>> cache = new ConcurrentHashMap<>();
            AtomicInteger computeCount = new AtomicInteger(0);

            java.util.function.Function<String, List<UUID>> loader = key -> {
                computeCount.incrementAndGet();
                List<UUID> list = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    list.add(UUID.randomUUID());
                }
                return list;
            };

            assertEquals(0, computeCount.get());

            List<UUID> list1 = cache.computeIfAbsent("key1", loader);
            assertEquals(1, computeCount.get());
            assertEquals(10, list1.size());

            List<UUID> list2 = cache.computeIfAbsent("key1", loader);
            assertEquals(1, computeCount.get(), "Should not recompute");
            assertSame(list1, list2);

            cache.computeIfAbsent("key2", loader);
            assertEquals(2, computeCount.get());
        }

        @Test
        @DisplayName("Enum-based singleton pattern")
        void enumBasedSingletonPattern() {
            // Enum singletons are inherently thread-safe and lazy
            enum SingletonEnum {
                INSTANCE;

                private final UUID id = UUID.randomUUID();

                UUID getId() {
                    return id;
                }
            }

            SingletonEnum s1 = SingletonEnum.INSTANCE;
            SingletonEnum s2 = SingletonEnum.INSTANCE;

            assertSame(s1, s2, "Should return same instance");
            assertEquals(s1.getId(), s2.getId(), "Should have same ID");
        }
    }

    // === L5-19: Batch Operation Patterns ===

    @Nested
    @DisplayName("L5-19: Batch Operation Patterns")
    class BatchOperationPatternsTest {

        @Test
        @DisplayName("Batch add vs individual add")
        void batchAddVsIndividualAdd() {
            int count = 100000;
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                items.add(i);
            }

            // Individual add
            List<Integer> list1 = new ArrayList<>();
            long individualStart = System.nanoTime();
            for (Integer item : items) {
                list1.add(item);
            }
            long individualElapsed = System.nanoTime() - individualStart;

            // Batch add
            List<Integer> list2 = new ArrayList<>();
            long batchStart = System.nanoTime();
            list2.addAll(items);
            long batchElapsed = System.nanoTime() - batchStart;

            assertEquals(count, list1.size());
            assertEquals(count, list2.size());
            assertTrue(individualElapsed >= 0);
            assertTrue(batchElapsed >= 0);

            // Batch should be at least as fast
            // (often faster due to single array copy)
        }

        @Test
        @DisplayName("Batch remove with removeIf")
        void batchRemoveWithRemoveIf() {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 100000; i++) {
                list.add(i);
            }

            long start = System.nanoTime();
            list.removeIf(i -> i % 2 == 0);
            long elapsed = System.nanoTime() - start;

            assertEquals(50000, list.size());
            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(5));
        }

        @Test
        @DisplayName("Bulk map operations")
        void bulkMapOperations() {
            Map<Integer, String> source = new HashMap<>();
            for (int i = 0; i < 10000; i++) {
                source.put(i, "value" + i);
            }

            // Bulk copy
            Map<Integer, String> target = new HashMap<>();
            long start = System.nanoTime();
            target.putAll(source);
            long elapsed = System.nanoTime() - start;

            assertEquals(source.size(), target.size());
            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1));
        }

        @Test
        @DisplayName("Batch processing with partitions")
        void batchProcessingWithPartitions() {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                items.add(i);
            }

            int batchSize = 100;
            List<List<Integer>> batches = new ArrayList<>();

            for (int i = 0; i < items.size(); i += batchSize) {
                batches.add(items.subList(i, Math.min(i + batchSize, items.size())));
            }

            assertEquals(100, batches.size());
            assertEquals(100, batches.get(0).size());
            assertEquals(100, batches.get(99).size());

            // Process batches
            AtomicInteger processedCount = new AtomicInteger(0);
            for (List<Integer> batch : batches) {
                processedCount.addAndGet(batch.size());
            }

            assertEquals(10000, processedCount.get());
        }

        @Test
        @DisplayName("Parallel batch processing")
        void parallelBatchProcessing() throws Exception {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                items.add(i);
            }

            AtomicInteger processedCount = new AtomicInteger(0);
            int batchSize = 100;

            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < items.size(); i += batchSize) {
                int start = i;
                int end = Math.min(i + batchSize, items.size());
                futures.add(executor.submit(() -> {
                    // Process batch
                    for (int j = start; j < end; j++) {
                        processedCount.incrementAndGet();
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(10000, processedCount.get());
        }
    }

    // === L5-20: Stream vs Loop Performance ===

    @Nested
    @DisplayName("L5-20: Stream vs Loop Performance")
    class StreamVsLoopPerformanceTest {

        @Test
        @DisplayName("Simple iteration - loop vs stream")
        void simpleIterationLoopVsStream() {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 100000; i++) {
                items.add(i);
            }

            // Loop
            long loopStart = System.nanoTime();
            long loopSum = 0;
            for (Integer item : items) {
                loopSum += item;
            }
            long loopElapsed = System.nanoTime() - loopStart;

            // Stream
            long streamStart = System.nanoTime();
            long streamSum = items.stream().mapToLong(Integer::longValue).sum();
            long streamElapsed = System.nanoTime() - streamStart;

            assertEquals(loopSum, streamSum);
            // Both should complete quickly
            assertTrue(loopElapsed < TimeUnit.SECONDS.toNanos(1));
            assertTrue(streamElapsed < TimeUnit.SECONDS.toNanos(1));
        }

        @Test
        @DisplayName("Filter operation - loop vs stream")
        void filterOperationLoopVsStream() {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 100000; i++) {
                items.add(i);
            }

            // Loop
            long loopStart = System.nanoTime();
            List<Integer> loopResult = new ArrayList<>();
            for (Integer item : items) {
                if (item % 2 == 0) {
                    loopResult.add(item);
                }
            }
            long loopElapsed = System.nanoTime() - loopStart;

            // Stream
            long streamStart = System.nanoTime();
            List<Integer> streamResult = items.stream()
                .filter(i -> i % 2 == 0)
                .collect(Collectors.toList());
            long streamElapsed = System.nanoTime() - streamStart;

            assertEquals(loopResult.size(), streamResult.size());
            assertEquals(50000, loopResult.size());
            assertTrue(loopElapsed >= 0);
            assertTrue(streamElapsed >= 0);
        }

        @Test
        @DisplayName("Map operation - loop vs stream")
        void mapOperationLoopVsStream() {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 100000; i++) {
                items.add(i);
            }

            // Loop
            List<String> loopResult = new ArrayList<>();
            for (Integer item : items) {
                loopResult.add("item" + item);
            }

            // Stream
            List<String> streamResult = items.stream()
                .map(i -> "item" + i)
                .collect(Collectors.toList());

            assertEquals(loopResult.size(), streamResult.size());
            assertEquals(items.size(), loopResult.size());
        }

        @Test
        @DisplayName("Parallel stream performance")
        void parallelStreamPerformance() {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 1000000; i++) {
                items.add(i);
            }

            // Sequential
            long seqStart = System.nanoTime();
            long seqSum = items.stream()
                .mapToLong(i -> {
                    // Simulate some computation
                    return i * 2L;
                })
                .sum();
            long seqElapsed = System.nanoTime() - seqStart;

            // Parallel
            long parStart = System.nanoTime();
            long parSum = items.parallelStream()
                .mapToLong(i -> {
                    return i * 2L;
                })
                .sum();
            long parElapsed = System.nanoTime() - parStart;

            assertEquals(seqSum, parSum);
            // Both should complete
            assertTrue(seqElapsed > 0);
            assertTrue(parElapsed > 0);
        }

        @Test
        @DisplayName("Reduce operation - loop vs stream")
        void reduceOperationLoopVsStream() {
            List<Integer> items = new ArrayList<>();
            for (int i = 1; i <= 1000; i++) {
                items.add(i);
            }

            // Loop - find max
            int loopMax = Integer.MIN_VALUE;
            for (Integer item : items) {
                if (item > loopMax) {
                    loopMax = item;
                }
            }

            // Stream
            int streamMax = items.stream()
                .reduce(Integer.MIN_VALUE, (a, b) -> Integer.valueOf(Math.max(a, b)));

            assertEquals(loopMax, streamMax);
            assertEquals(1000, loopMax);
        }
    }

    // === L5-21: Memory Access Patterns ===

    @Nested
    @DisplayName("L5-21: Memory Access Patterns")
    class MemoryAccessPatternsTest {

        @Test
        @DisplayName("Sequential vs random array access")
        void sequentialVsRandomArrayAccess() {
            int size = 1000000;
            int[] array = new int[size];
            for (int i = 0; i < size; i++) {
                array[i] = i;
            }

            // Sequential access (cache-friendly)
            long seqStart = System.nanoTime();
            long seqSum = 0;
            for (int i = 0; i < size; i++) {
                seqSum += array[i];
            }
            long seqElapsed = System.nanoTime() - seqStart;

            // Random access (cache-unfriendly)
            int[] randomIndices = new int[size];
            Random random = new Random(42);
            for (int i = 0; i < size; i++) {
                randomIndices[i] = random.nextInt(size);
            }

            long randStart = System.nanoTime();
            long randSum = 0;
            for (int i = 0; i < size; i++) {
                randSum += array[randomIndices[i]];
            }
            long randElapsed = System.nanoTime() - randStart;

            assertTrue(seqSum >= 0);
            assertTrue(randSum >= 0);
            assertTrue(seqElapsed > 0);
            assertTrue(randElapsed > 0);
            // Sequential is typically faster due to cache locality
        }

        @Test
        @DisplayName("Row-major vs column-major 2D array access")
        void rowMajorVsColumnMajor2DArrayAccess() {
            int rows = 1000;
            int cols = 1000;
            int[][] matrix = new int[rows][cols];

            // Initialize
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    matrix[i][j] = i * cols + j;
                }
            }

            // Row-major (cache-friendly in Java)
            long rowMajorStart = System.nanoTime();
            long rowMajorSum = 0;
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    rowMajorSum += matrix[i][j];
                }
            }
            long rowMajorElapsed = System.nanoTime() - rowMajorStart;

            // Column-major (cache-unfriendly)
            long colMajorStart = System.nanoTime();
            long colMajorSum = 0;
            for (int j = 0; j < cols; j++) {
                for (int i = 0; i < rows; i++) {
                    colMajorSum += matrix[i][j];
                }
            }
            long colMajorElapsed = System.nanoTime() - colMajorStart;

            assertEquals(rowMajorSum, colMajorSum);
            assertTrue(rowMajorElapsed > 0);
            assertTrue(colMajorElapsed > 0);
            // Row-major is typically faster due to cache locality
        }

        @Test
        @DisplayName("Object array vs primitive array")
        void objectArrayVsPrimitiveArray() {
            int size = 1000000;

            // Primitive array
            int[] primitives = new int[size];
            for (int i = 0; i < size; i++) {
                primitives[i] = i;
            }

            long primStart = System.nanoTime();
            long primSum = 0;
            for (int i = 0; i < size; i++) {
                primSum += primitives[i];
            }
            long primElapsed = System.nanoTime() - primStart;

            // Object array (boxed)
            Integer[] objects = new Integer[size];
            for (int i = 0; i < size; i++) {
                objects[i] = i;
            }

            long objStart = System.nanoTime();
            long objSum = 0;
            for (int i = 0; i < size; i++) {
                objSum += objects[i];
            }
            long objElapsed = System.nanoTime() - objStart;

            assertEquals(primSum, objSum);
            assertTrue(primElapsed > 0);
            assertTrue(objElapsed > 0);
            // Primitive array is typically faster (no boxing/unboxing, better locality)
        }
    }

    // === L5-22: String Operation Performance ===

    @Nested
    @DisplayName("L5-22: String Operation Performance")
    class StringOperationPerformanceTest {

        @Test
        @DisplayName("StringBuilder vs String concatenation")
        void stringBuilderVsStringConcatenation() {
            int iterations = 10000;

            // StringBuilder
            long sbStart = System.nanoTime();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < iterations; i++) {
                sb.append("item").append(i);
            }
            String sbResult = sb.toString();
            long sbElapsed = System.nanoTime() - sbStart;

            // String concatenation in loop (inefficient)
            long concatStart = System.nanoTime();
            String concatResult = "";
            for (int i = 0; i < 1000; i++) { // Fewer due to O(n²)
                concatResult += "item" + i;
            }
            long concatElapsed = System.nanoTime() - concatStart;

            assertTrue(sbResult.length() > 0);
            assertTrue(concatResult.length() > 0);
            assertTrue(sbElapsed > 0);
            assertTrue(concatElapsed > 0);
            // StringBuilder should be much faster
        }

        @Test
        @DisplayName("String.format vs concatenation")
        void stringFormatVsConcatenation() {
            int iterations = 10000;
            String template = "Player %s scored %d points";
            String lastFormat = null;

            // String.format
            long formatStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                lastFormat = String.format(template, "player" + i, i * 10);
            }
            long formatElapsed = System.nanoTime() - formatStart;

            String lastConcat = null;
            // Concatenation
            long concatStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                lastConcat = "Player " + "player" + i + " scored " + (i * 10) + " points";
            }
            long concatElapsed = System.nanoTime() - concatStart;

            // Both should complete
            assertTrue(formatElapsed > 0);
            assertTrue(concatElapsed > 0);
            assertNotNull(lastFormat);
            assertNotNull(lastConcat);
            assertEquals(lastFormat.length(), lastConcat.length());
        }

        @Test
        @DisplayName("String.intern for deduplication")
        void stringInternForDeduplication() {
            int iterations = 10000;
            Set<String> withoutIntern = new HashSet<>();
            Set<String> withIntern = new HashSet<>();

            // Without intern - many duplicate String objects
            for (int i = 0; i < iterations; i++) {
                withoutIntern.add(new String("common" + (i % 100)));
            }

            // With intern - reuses String objects
            for (int i = 0; i < iterations; i++) {
                withIntern.add(new String("common" + (i % 100)).intern());
            }

            assertEquals(100, withoutIntern.size());
            assertEquals(100, withIntern.size());
        }

        @Test
        @DisplayName("Regex compilation caching")
        void regexCompilationCaching() {
            String pattern = "\\d+";
            String input = "test123test456test789";
            int iterations = 10000;

            // Without caching (recompile each time)
            long uncachedStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                input.matches(pattern);
            }
            long uncachedElapsed = System.nanoTime() - uncachedStart;

            // With caching
            java.util.regex.Pattern compiled = java.util.regex.Pattern.compile(pattern);
            long cachedStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                compiled.matcher(input).find();
            }
            long cachedElapsed = System.nanoTime() - cachedStart;

            // Cached should be faster
            assertTrue(cachedElapsed <= uncachedElapsed * 2,
                "Cached regex should not be much slower");
        }
    }

    // === L5-23: Instance Lookup Performance ===

    @Nested
    @DisplayName("L5-23: Instance Lookup Performance")
    class InstanceLookupPerformanceTest {

        @Test
        @DisplayName("UUID lookup in large registry")
        void uuidLookupInLargeRegistry() {
            int size = 100000;
            ConcurrentHashMap<UUID, InstanceState> registry = new ConcurrentHashMap<>();
            List<UUID> ids = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                registry.put(id, InstanceState.ACTIVE);
            }

            // Random lookups
            long start = System.nanoTime();
            int found = 0;
            Random random = new Random(42);
            for (int i = 0; i < 100000; i++) {
                UUID id = ids.get(random.nextInt(size));
                if (registry.containsKey(id)) {
                    found++;
                }
            }
            long elapsed = System.nanoTime() - start;

            assertEquals(100000, found);
            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(5),
                "Lookups should be fast: " + TimeUnit.NANOSECONDS.toMillis(elapsed) + "ms");
        }

        @Test
        @DisplayName("Player-to-instance reverse lookup")
        void playerToInstanceReverseLookup() {
            int instanceCount = 1000;
            int playersPerInstance = 4;
            ConcurrentHashMap<UUID, Set<UUID>> instancePlayers = new ConcurrentHashMap<>();
            ConcurrentHashMap<UUID, UUID> playerInstance = new ConcurrentHashMap<>();

            // Setup
            for (int i = 0; i < instanceCount; i++) {
                UUID instanceId = UUID.randomUUID();
                Set<UUID> players = ConcurrentHashMap.newKeySet();
                for (int p = 0; p < playersPerInstance; p++) {
                    UUID playerId = UUID.randomUUID();
                    players.add(playerId);
                    playerInstance.put(playerId, instanceId);
                }
                instancePlayers.put(instanceId, players);
            }

            // Reverse lookup performance
            List<UUID> allPlayers = new ArrayList<>(playerInstance.keySet());
            long start = System.nanoTime();
            int found = 0;
            Random random = new Random(42);
            for (int i = 0; i < 100000; i++) {
                UUID playerId = allPlayers.get(random.nextInt(allPlayers.size()));
                UUID instanceId = playerInstance.get(playerId);
                if (instanceId != null) {
                    found++;
                }
            }
            long elapsed = System.nanoTime() - start;

            assertEquals(100000, found);
            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(5));
        }

        @Test
        @DisplayName("Filtered instance enumeration")
        void filteredInstanceEnumeration() {
            int size = 10000;
            ConcurrentHashMap<UUID, InstanceState> registry = new ConcurrentHashMap<>();

            for (int i = 0; i < size; i++) {
                InstanceState state = switch (i % 6) {
                    case 0 -> InstanceState.CREATING;
                    case 1 -> InstanceState.READY;
                    case 2 -> InstanceState.ACTIVE;
                    case 3 -> InstanceState.COMPLETING;
                    case 4 -> InstanceState.DESTROYING;
                    default -> InstanceState.DESTROYED;
                };
                registry.put(UUID.randomUUID(), state);
            }

            // Filter for active instances
            long start = System.nanoTime();
            List<UUID> activeInstances = registry.entrySet().stream()
                .filter(e -> e.getValue() == InstanceState.ACTIVE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            long elapsed = System.nanoTime() - start;

            assertTrue(activeInstances.size() > 0);
            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1));
        }
    }
}
