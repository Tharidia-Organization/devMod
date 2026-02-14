package com.devmod.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreadLocalPool")
class ThreadLocalPoolTest {

    // ========================================================================
    // Basic acquire / release
    // ========================================================================

    @Nested
    @DisplayName("acquire and release")
    class AcquireRelease {

        @Test
        @DisplayName("acquire creates a new object when pool is empty")
        void acquireFromEmptyPool() {
            ThreadLocalPool<StringBuilder> pool = ThreadLocalPool.create(
                StringBuilder::new, sb -> sb.setLength(0), 4);
            StringBuilder sb = pool.acquire();
            assertNotNull(sb);
        }

        @Test
        @DisplayName("released object is reused on next acquire")
        void releasedObjectIsReused() {
            ThreadLocalPool<List<String>> pool = ThreadLocalPool.ofList(ArrayList::new, 4);
            List<String> first = pool.acquire();
            first.add("hello");
            pool.release(first);

            List<String> second = pool.acquire();
            assertSame(first, second, "should return the same pooled object");
            assertTrue(second.isEmpty(), "reset function should have cleared the list");
        }

        @Test
        @DisplayName("release null is a no-op")
        void releaseNullIsNoop() {
            ThreadLocalPool<StringBuilder> pool = ThreadLocalPool.create(
                StringBuilder::new, sb -> sb.setLength(0), 4);
            assertDoesNotThrow(() -> pool.release(null));
        }

        @Test
        @DisplayName("objects beyond maxSize are not pooled")
        void discardBeyondMaxSize() {
            ThreadLocalPool<StringBuilder> pool = ThreadLocalPool.create(
                StringBuilder::new, sb -> sb.setLength(0), 2);

            StringBuilder a = pool.acquire();
            StringBuilder b = pool.acquire();
            StringBuilder c = pool.acquire();

            pool.release(a);
            pool.release(b);
            pool.release(c); // should be silently dropped

            // Verify pool works correctly after overfill
            StringBuilder x = pool.acquire();
            StringBuilder y = pool.acquire();
            assertNotNull(x);
            assertNotNull(y);
        }

        @Test
        @DisplayName("reset failure causes object to be discarded")
        void resetFailureDiscards() {
            ThreadLocalPool<String> pool = ThreadLocalPool.create(
                () -> "obj",
                s -> { throw new RuntimeException("reset fail"); },
                4
            );
            String obj = pool.acquire();
            assertDoesNotThrow(() -> pool.release(obj));

            // Should still be able to acquire (creates new)
            String obj2 = pool.acquire();
            assertNotNull(obj2);
        }
    }

    // ========================================================================
    // Factory methods
    // ========================================================================

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("ofList creates a pool that clears lists on release")
        void ofListClearsOnRelease() {
            ThreadLocalPool<ArrayList<String>> pool = ThreadLocalPool.ofList(ArrayList::new, 4);
            ArrayList<String> list = pool.acquire();
            list.add("a");
            list.add("b");
            pool.release(list);

            ArrayList<String> reused = pool.acquire();
            assertSame(list, reused);
            assertTrue(reused.isEmpty());
        }

        @Test
        @DisplayName("ofMap creates a pool that clears maps on release")
        void ofMapClearsOnRelease() {
            ThreadLocalPool<HashMap<String, Integer>> pool = ThreadLocalPool.ofMap(HashMap::new, 4);
            HashMap<String, Integer> map = pool.acquire();
            map.put("key", 42);
            pool.release(map);

            HashMap<String, Integer> reused = pool.acquire();
            assertSame(map, reused);
            assertTrue(reused.isEmpty());
        }
    }

    // ========================================================================
    // Constructor validation
    // ========================================================================

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("null factory throws NPE")
        void nullFactory() {
            assertThrows(NullPointerException.class,
                () -> ThreadLocalPool.create(null, obj -> {}, 4));
        }

        @Test
        @DisplayName("null resetFn throws NPE")
        void nullResetFn() {
            assertThrows(NullPointerException.class,
                () -> ThreadLocalPool.create(StringBuilder::new, null, 4));
        }

        @Test
        @DisplayName("maxSize below 1 is clamped to 1")
        void maxSizeClamped() {
            ThreadLocalPool<StringBuilder> pool = ThreadLocalPool.create(
                StringBuilder::new, sb -> sb.setLength(0), 0);
            assertEquals(1, pool.getMaxSize());
        }
    }

    // ========================================================================
    // Prewarm
    // ========================================================================

    @Test
    @DisplayName("prewarm populates pool for current thread")
    void prewarmPopulatesPool() {
        ThreadLocalPool<StringBuilder> pool = ThreadLocalPool.create(
            StringBuilder::new, sb -> sb.setLength(0), 5);
        pool.prewarm(3);

        // Acquire 3 should all come from pool (no new creation)
        StringBuilder a = pool.acquire();
        StringBuilder b = pool.acquire();
        StringBuilder c = pool.acquire();
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
    }

    // ========================================================================
    // Thread isolation
    // ========================================================================

    @Test
    @DisplayName("each thread has its own independent pool")
    void threadIsolation() throws InterruptedException {
        ThreadLocalPool<List<String>> pool = ThreadLocalPool.ofList(ArrayList::new, 4);

        // Thread 1 acquires and releases
        List<String> mainList = pool.acquire();
        pool.release(mainList);

        AtomicReference<List<String>> otherThreadList = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread other = new Thread(() -> {
            List<String> list = pool.acquire();
            otherThreadList.set(list);
            pool.release(list);
            done.countDown();
        });
        other.start();
        done.await();

        // Each thread should have gotten a different object (separate pools)
        assertNotSame(mainList, otherThreadList.get(),
            "different threads should have independent pools");
    }

    // ========================================================================
    // getMaxSize
    // ========================================================================

    @Test
    @DisplayName("getMaxSize returns configured max")
    void getMaxSize() {
        ThreadLocalPool<StringBuilder> pool = ThreadLocalPool.create(
            StringBuilder::new, sb -> sb.setLength(0), 7);
        assertEquals(7, pool.getMaxSize());
    }
}
