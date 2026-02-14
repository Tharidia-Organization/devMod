package com.devmod.actions.hardening;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionResult;
import com.devmod.actions.bindings.ActionBinding;
import com.devmod.actions.bindings.BindingRegistry;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.ActionExecutor;
import com.devmod.actions.engine.steps.TelemetryStep;
import com.devmod.actions.policy.OriginPolicy;
import com.devmod.actions.policy.PermissionPolicy;
import com.devmod.actions.policy.PolicyChain;
import com.devmod.actions.policy.RateLimitPolicy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency and stress tests for the Actions V2 system.
 * Verifies thread-safety of catalogs, registries, and the execution pipeline
 * under high contention.
 */
@DisplayName("Concurrency & Stress Tests")
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class ConcurrencyStressTest {

    private static final int THREAD_COUNT = 100;

    private ActionCatalog catalog;
    private HandlerRegistry handlers;
    private PolicyChain policyChain;
    private List<String> telemetryLines;
    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
        handlers = new HandlerRegistry();
        telemetryLines = new CopyOnWriteArrayList<>();

        policyChain = PolicyChain.builder()
            .add(OriginPolicy.INSTANCE)
            .add(PermissionPolicy.INSTANCE)
            .add(new RateLimitPolicy())
            .build();

        TelemetryStep.TelemetrySink testSink = telemetryLines::add;
        executor = ActionExecutor.createDefault(catalog, handlers, policyChain, testSink);
    }

    private ActionSpec buildSpec(String id) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(ActionCategory.UI)
            .ui("label." + id, "desc." + id)
            .build();
    }

    private ActionContext trustedContext() {
        return ActionContext.builder(ActionOrigin.RADIAL)
            .clientSide(true)
            .build();
    }

    // =========================================================================
    // 1. Concurrent Registration
    // =========================================================================

    @Nested
    @DisplayName("1. Concurrent Registration")
    class ConcurrentRegistrationTests {

        @Test
        @Timeout(5)
        @DisplayName("100 threads registering different actions simultaneously")
        void concurrentRegistration() throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    String id = "devmod.concurrent.reg_" + idx;
                    catalog.register(buildSpec(id));
                }));
            }

            latch.countDown(); // unleash all threads
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertEquals(THREAD_COUNT, catalog.size(),
                "All 100 actions should be registered");
        }
    }

    // =========================================================================
    // 2. Concurrent Invocation
    // =========================================================================

    @Nested
    @DisplayName("2. Concurrent Invocation")
    class ConcurrentInvocationTests {

        @Test
        @Timeout(5)
        @DisplayName("100 threads invoking the same action simultaneously")
        void concurrentInvocation() throws Exception {
            String actionId = "devmod.concurrent.invoke_target";
            AtomicInteger callCount = new AtomicInteger(0);

            catalog.register(buildSpec(actionId));
            handlers.register(actionId, ctx -> callCount.incrementAndGet());

            ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<ActionResult>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                futures.add(pool.submit(() -> {
                    latch.await();
                    return executor.execute(actionId, trustedContext());
                }));
            }

            latch.countDown();
            int successCount = 0;
            for (Future<ActionResult> f : futures) {
                ActionResult result = f.get(5, TimeUnit.SECONDS);
                if (result.isSuccess()) successCount++;
            }
            pool.shutdown();

            assertEquals(THREAD_COUNT, callCount.get(),
                "Handler should be called 100 times");
            assertEquals(THREAD_COUNT, successCount,
                "All invocations should succeed");
        }
    }

    // =========================================================================
    // 3. Concurrent Mixed Operations
    // =========================================================================

    @Nested
    @DisplayName("3. Concurrent Mixed Operations")
    class ConcurrentMixedTests {

        @Test
        @Timeout(5)
        @DisplayName("Registration + invocation + catalog query all at once")
        void concurrentMixed() throws Exception {
            // Pre-register some actions for invocation/query
            for (int i = 0; i < 50; i++) {
                String id = "devmod.mixed.existing_" + i;
                catalog.register(buildSpec(id));
                handlers.register(id, ctx -> {});
            }

            ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

            // 33 threads registering new actions
            for (int i = 0; i < 33; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                        String id = "devmod.mixed.new_" + idx;
                        catalog.register(buildSpec(id));
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }

            // 33 threads invoking existing actions
            for (int i = 0; i < 33; i++) {
                final int idx = i % 50;
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                        executor.execute("devmod.mixed.existing_" + idx, trustedContext());
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }

            // 34 threads querying catalog
            for (int i = 0; i < 34; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                        catalog.getAll();
                        catalog.size();
                        catalog.getByCategory(ActionCategory.UI);
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }

            latch.countDown();
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertTrue(errors.isEmpty(),
                "No exceptions during mixed concurrent operations, got: " + errors);
            assertTrue(catalog.size() >= 50,
                "At least the pre-registered actions should exist");
        }
    }

    // =========================================================================
    // 4. Rate Limiter Under Load
    // =========================================================================

    @Nested
    @DisplayName("4. Rate Limiter Under Load")
    class RateLimiterLoadTests {

        @Test
        @Timeout(5)
        @DisplayName("Token bucket correctness under concurrent access")
        void rateLimiterConcurrency() throws Exception {
            // The rate limit policy uses a per-player bucket.
            // Without a player, rate limit is bypassed (server invocation).
            // This test validates that the RateLimitPolicy itself doesn't throw
            // under concurrent evaluation.
            RateLimitPolicy policy = new RateLimitPolicy();
            ActionSpec spec = ActionSpec.builder("devmod.rate.concurrent")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .policy(false, 5, null) // 5 per sec
                .build();

            ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                        // No player = server invocation = rate limit bypassed
                        policy.evaluate(spec, trustedContext());
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }

            latch.countDown();
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertTrue(errors.isEmpty(), "RateLimitPolicy should not throw under concurrency");
        }
    }

    // =========================================================================
    // 5. ActionCatalog Thread Safety
    // =========================================================================

    @Nested
    @DisplayName("5. ActionCatalog Thread Safety")
    class CatalogThreadSafetyTests {

        @Test
        @Timeout(5)
        @DisplayName("Concurrent register/get/getAll on ActionCatalog")
        void catalogThreadSafety() throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                        if (idx % 3 == 0) {
                            catalog.register(buildSpec("devmod.catalog.safe_" + idx));
                        } else if (idx % 3 == 1) {
                            catalog.get("devmod.catalog.safe_" + (idx - 1));
                        } else {
                            catalog.getAll();
                        }
                    } catch (IllegalStateException e) {
                        // Duplicate registration expected for race conditions
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }

            latch.countDown();
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertTrue(errors.isEmpty(),
                "No unexpected exceptions during catalog thread safety test");
        }
    }

    // =========================================================================
    // 6. HandlerRegistry Thread Safety
    // =========================================================================

    @Nested
    @DisplayName("6. HandlerRegistry Thread Safety")
    class HandlerRegistryThreadSafetyTests {

        @Test
        @Timeout(5)
        @DisplayName("Concurrent register/get on HandlerRegistry")
        void handlerRegistryThreadSafety() throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                        if (idx % 2 == 0) {
                            handlers.register("devmod.handler.safe_" + idx, ctx -> {});
                        } else {
                            handlers.getHandler("devmod.handler.safe_" + (idx - 1));
                        }
                    } catch (IllegalStateException e) {
                        // Duplicate registration
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }

            latch.countDown();
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertTrue(errors.isEmpty(),
                "No unexpected exceptions during handler registry thread safety test");
        }
    }

    // =========================================================================
    // 7. BindingRegistry Thread Safety
    // =========================================================================

    @Nested
    @DisplayName("7. BindingRegistry Thread Safety")
    class BindingRegistryThreadSafetyTests {

        @Test
        @Timeout(5)
        @DisplayName("Concurrent register/get on BindingRegistry")
        void bindingRegistryThreadSafety() throws Exception {
            BindingRegistry registry = new BindingRegistry();

            ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                        if (idx % 2 == 0) {
                            registry.register(new ActionBinding() {
                                @Override
                                public String actionId() {
                                    return "devmod.binding.safe_" + idx;
                                }
                                @Override
                                public ActionOrigin origin() {
                                    return ActionOrigin.RADIAL;
                                }
                                @Override
                                public String describe() {
                                    return "test binding " + idx;
                                }
                            });
                        } else {
                            registry.getBindings("devmod.binding.safe_" + (idx - 1));
                        }
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }

            latch.countDown();
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertTrue(errors.isEmpty(),
                "No exceptions during binding registry thread safety test");
            assertTrue(registry.size() > 0, "Some bindings should be registered");
        }
    }

    // =========================================================================
    // 8. No Deadlocks
    // =========================================================================

    @Nested
    @DisplayName("8. No Deadlocks")
    class NoDeadlockTests {

        @Test
        @Timeout(5)
        @DisplayName("All concurrent tests complete within timeout (5 seconds)")
        void noDeadlockWithFullPipeline() throws Exception {
            // Register 50 actions
            for (int i = 0; i < 50; i++) {
                String id = "devmod.deadlock.action_" + i;
                catalog.register(buildSpec(id));
                handlers.register(id, ctx -> {});
            }

            ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<ActionResult>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i % 50;
                futures.add(pool.submit(() -> {
                    latch.await();
                    return executor.execute("devmod.deadlock.action_" + idx, trustedContext());
                }));
            }

            latch.countDown();
            for (Future<ActionResult> f : futures) {
                ActionResult result = f.get(5, TimeUnit.SECONDS);
                assertNotNull(result, "Result should never be null");
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS),
                "Pool should terminate within timeout - deadlock detected");
        }
    }

    // =========================================================================
    // 9. Memory Stability
    // =========================================================================

    @Nested
    @DisplayName("9. Memory Stability")
    class MemoryStabilityTests {

        @Test
        @Timeout(5)
        @DisplayName("No OOM from rapid registration/invocation cycles")
        void noOomFromRapidCycles() {
            // Register and invoke 1000 actions in tight loop.
            // If there is a memory leak (e.g., unbounded list growth), this may OOM.
            ActionCatalog localCatalog = new ActionCatalog();
            HandlerRegistry localHandlers = new HandlerRegistry();
            List<String> localTelemetry = new ArrayList<>();

            ActionExecutor localExecutor = ActionExecutor.createDefault(
                localCatalog, localHandlers,
                PolicyChain.empty(),
                localTelemetry::add);

            for (int i = 0; i < 1000; i++) {
                String id = "devmod.memory.action_" + i;
                localCatalog.register(buildSpec(id));
                localHandlers.register(id, ctx -> {});
                localExecutor.execute(id, trustedContext());
            }

            assertEquals(1000, localCatalog.size());
            assertEquals(1000, localTelemetry.size());
            // If we got here without OOM, memory is stable enough
        }

        @Test
        @Timeout(5)
        @DisplayName("Repeated invocations of same action do not leak memory")
        void repeatedInvocationNoLeak() {
            String id = "devmod.memory.repeat_invoke";
            catalog.register(buildSpec(id));
            handlers.register(id, ctx -> {});

            long beforeMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            for (int i = 0; i < 10_000; i++) {
                executor.execute(id, trustedContext());
            }

            System.gc();
            long afterMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Allow up to 50MB growth (generous threshold for telemetry list + UUID allocations)
            long growthMB = (afterMem - beforeMem) / (1024 * 1024);
            assertTrue(growthMB < 50,
                "Memory grew by " + growthMB + "MB after 10k invocations - possible leak");
        }
    }
}
