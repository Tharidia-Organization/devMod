package com.devmod.actions.hardening;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.ActionExecutor;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;
import com.devmod.actions.engine.steps.ConfirmationStep;
import com.devmod.actions.engine.steps.ExecuteStep;
import com.devmod.actions.engine.steps.PolicyAuthorizeStep;
import com.devmod.actions.engine.steps.PreconditionStep;
import com.devmod.actions.engine.steps.ResolveStep;
import com.devmod.actions.engine.steps.TelemetryStep;
import com.devmod.actions.engine.steps.ValidatePayloadStep;
import com.devmod.actions.policy.CommandSafetyPolicy;
import com.devmod.actions.policy.OriginPolicy;
import com.devmod.actions.policy.PermissionPolicy;
import com.devmod.actions.policy.PolicyChain;
import com.devmod.actions.policy.RateLimitPolicy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmark tests for the Actions V2 pipeline.
 * Measures latency, throughput, and overhead of individual components.
 */
@DisplayName("Pipeline Performance Tests")
class PipelinePerformanceTest {

    private ActionCatalog catalog;
    private HandlerRegistry handlers;
    private PolicyChain fullPolicyChain;
    private List<String> telemetryLines;
    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
        handlers = new HandlerRegistry();
        telemetryLines = new ArrayList<>();

        fullPolicyChain = PolicyChain.builder()
            .add(OriginPolicy.INSTANCE)
            .add(PermissionPolicy.INSTANCE)
            .add(CommandSafetyPolicy.INSTANCE)
            .add(new RateLimitPolicy())
            .build();

        TelemetryStep.TelemetrySink testSink = telemetryLines::add;
        executor = ActionExecutor.createDefault(catalog, handlers, fullPolicyChain, testSink);
    }

    private void registerAction(String id) {
        catalog.register(ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(ActionCategory.UI)
            .ui("label." + id, "desc." + id)
            .build());
        handlers.register(id, ctx -> { /* noop */ });
    }

    private ActionContext trustedContext() {
        return ActionContext.builder(ActionOrigin.RADIAL)
            .clientSide(true)
            .build();
    }

    // =========================================================================
    // 1. Baseline Latency
    // =========================================================================

    @Nested
    @DisplayName("1. Baseline Latency")
    class BaselineLatencyTests {

        @Test
        @DisplayName("Single invoke through full pipeline measures latency in nanos")
        void singleInvokeLatency() {
            registerAction("devmod.perf.baseline_latency");

            // Warm up
            for (int i = 0; i < 100; i++) {
                executor.execute("devmod.perf.baseline_latency", trustedContext());
            }
            telemetryLines.clear();

            long startNanos = System.nanoTime();
            ActionResult result = executor.execute("devmod.perf.baseline_latency", trustedContext());
            long elapsedNanos = System.nanoTime() - startNanos;

            assertTrue(result.isSuccess());
            // Single invocation should be under 10ms (10_000_000 nanos)
            assertTrue(elapsedNanos < 10_000_000,
                "Single pipeline invocation took " + (elapsedNanos / 1_000) + " micros, expected <10ms");

            System.out.println("[Perf] Single pipeline invocation: " +
                (elapsedNanos / 1_000) + " micros");
        }
    }

    // =========================================================================
    // 2. Throughput
    // =========================================================================

    @Nested
    @DisplayName("2. Throughput")
    class ThroughputTests {

        @Test
        @DisplayName("10,000 invocations throughput measurement")
        void throughput10k() {
            registerAction("devmod.perf.throughput");
            int iterations = 10_000;

            // Warm up
            for (int i = 0; i < 500; i++) {
                executor.execute("devmod.perf.throughput", trustedContext());
            }
            telemetryLines.clear();

            long startNanos = System.nanoTime();
            int successCount = 0;
            for (int i = 0; i < iterations; i++) {
                ActionResult result = executor.execute("devmod.perf.throughput", trustedContext());
                if (result.isSuccess()) successCount++;
            }
            long elapsedNanos = System.nanoTime() - startNanos;

            assertEquals(iterations, successCount, "All invocations should succeed");

            double elapsedMs = elapsedNanos / 1_000_000.0;
            double opsPerSec = iterations / (elapsedMs / 1000.0);

            System.out.println("[Perf] 10k invocations: " +
                String.format("%.1f", elapsedMs) + " ms (" +
                String.format("%.0f", opsPerSec) + " ops/sec)");

            // Should achieve at least 10k ops/sec (1ms per invocation average)
            assertTrue(opsPerSec > 10_000,
                "Throughput " + String.format("%.0f", opsPerSec) + " ops/sec is below 10k threshold");
        }
    }

    // =========================================================================
    // 3. Policy Chain Overhead
    // =========================================================================

    @Nested
    @DisplayName("3. Policy Chain Overhead")
    class PolicyChainOverheadTests {

        @Test
        @DisplayName("Measure time with 0, 1, 2, 3, 4 policies")
        void policyChainOverhead() {
            int iterations = 5_000;
            long[] avgNanos = new long[5];

            PolicyChain[] chains = {
                PolicyChain.empty(),
                PolicyChain.builder().add(OriginPolicy.INSTANCE).build(),
                PolicyChain.builder().add(OriginPolicy.INSTANCE)
                    .add(PermissionPolicy.INSTANCE).build(),
                PolicyChain.builder().add(OriginPolicy.INSTANCE)
                    .add(PermissionPolicy.INSTANCE)
                    .add(CommandSafetyPolicy.INSTANCE).build(),
                fullPolicyChain
            };

            for (int c = 0; c < chains.length; c++) {
                ActionCatalog localCatalog = new ActionCatalog();
                HandlerRegistry localHandlers = new HandlerRegistry();
                String id = "devmod.perf.policy_" + c;
                localCatalog.register(ActionSpec.builder(id)
                    .channel(ActionChannel.CLIENT)
                    .category(ActionCategory.UI)
                    .ui("label", "desc")
                    .build());
                localHandlers.register(id, ctx -> {});

                ActionExecutor localExecutor = ActionExecutor.createDefault(
                    localCatalog, localHandlers, chains[c], line -> {});

                // Warm up
                for (int i = 0; i < 200; i++) {
                    localExecutor.execute(id, trustedContext());
                }

                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    localExecutor.execute(id, trustedContext());
                }
                long elapsed = System.nanoTime() - start;
                avgNanos[c] = elapsed / iterations;
            }

            System.out.println("[Perf] Policy chain overhead:");
            for (int c = 0; c < avgNanos.length; c++) {
                System.out.println("  " + c + " policies: " + avgNanos[c] + " ns/invocation");
            }

            // Each additional policy should add less than 1ms overhead
            for (int c = 1; c < avgNanos.length; c++) {
                long overhead = avgNanos[c] - avgNanos[0];
                assertTrue(overhead < 1_000_000,
                    c + " policies added " + (overhead / 1000) + " micros overhead, expected <1ms");
            }
        }
    }

    // =========================================================================
    // 4. Allocation Count Estimation
    // =========================================================================

    @Nested
    @DisplayName("4. Allocation Count")
    class AllocationCountTests {

        @Test
        @DisplayName("Estimate allocations per invocation")
        void allocationEstimate() {
            registerAction("devmod.perf.alloc_test");

            // Warm up and GC
            for (int i = 0; i < 1000; i++) {
                executor.execute("devmod.perf.alloc_test", trustedContext());
            }
            telemetryLines.clear();
            System.gc();

            // Measure memory before
            long beforeMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            int iterations = 10_000;
            for (int i = 0; i < iterations; i++) {
                executor.execute("devmod.perf.alloc_test", trustedContext());
            }

            long afterMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long totalBytes = afterMem - beforeMem;
            long bytesPerInvocation = totalBytes / iterations;

            System.out.println("[Perf] Allocation estimate: " +
                bytesPerInvocation + " bytes/invocation (" +
                totalBytes + " total for " + iterations + " iterations)");

            // Each invocation creates ExecutionContext + UUID + StepResults + ActionResult
            // + telemetry StringBuilder. Expect < 10KB per invocation.
            assertTrue(bytesPerInvocation < 10_000,
                "Allocating " + bytesPerInvocation + " bytes/invocation exceeds 10KB threshold");
        }
    }

    // =========================================================================
    // 5. Step Timing
    // =========================================================================

    @Nested
    @DisplayName("5. Step Timing")
    class StepTimingTests {

        @Test
        @DisplayName("Measure each pipeline step individually")
        void stepTiming() {
            String actionId = "devmod.perf.step_timing";
            catalog.register(ActionSpec.builder(actionId)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build());
            handlers.register(actionId, ctx -> {});

            // Time each step individually
            PipelineStep[] steps = {
                new ResolveStep(catalog),
                new ValidatePayloadStep(),
                new PolicyAuthorizeStep(fullPolicyChain),
                new PreconditionStep(),
                new ConfirmationStep(),
                new ExecuteStep(handlers),
                new TelemetryStep(line -> {})
            };

            int iterations = 5_000;
            long[] avgNanos = new long[steps.length];

            for (int s = 0; s < steps.length; s++) {
                // Warm up
                for (int i = 0; i < 200; i++) {
                    ExecutionContext ctx = new ExecutionContext(actionId, trustedContext());
                    // Set resolved spec for steps that need it
                    ActionSpec spec = catalog.get(actionId);
                    if (spec != null) ctx.setResolvedSpec(spec);
                    steps[s].process(ctx);
                }

                long total = 0;
                for (int i = 0; i < iterations; i++) {
                    ExecutionContext ctx = new ExecutionContext(actionId, trustedContext());
                    ActionSpec spec = catalog.get(actionId);
                    if (spec != null) ctx.setResolvedSpec(spec);

                    long start = System.nanoTime();
                    steps[s].process(ctx);
                    total += System.nanoTime() - start;
                }
                avgNanos[s] = total / iterations;
            }

            System.out.println("[Perf] Step timing:");
            for (int s = 0; s < steps.length; s++) {
                System.out.println("  " + steps[s].stepName() + ": " + avgNanos[s] + " ns");
            }

            // No step should take more than 1ms
            for (int s = 0; s < steps.length; s++) {
                assertTrue(avgNanos[s] < 1_000_000,
                    steps[s].stepName() + " took " + (avgNanos[s] / 1000) + " micros, expected <1ms");
            }
        }
    }

    // =========================================================================
    // 6. V1 vs V2 Latency Comparison
    // =========================================================================

    @Nested
    @DisplayName("6. V1 vs V2 Comparison")
    class V1V2ComparisonTests {

        @Test
        @DisplayName("V2 pipeline latency comparable to or better than manual overhead")
        void v2LatencyComparison() {
            registerAction("devmod.perf.v2_compare");
            int iterations = 5_000;

            // Measure V2 pipeline
            for (int i = 0; i < 200; i++) {
                executor.execute("devmod.perf.v2_compare", trustedContext());
            }
            telemetryLines.clear();

            long v2Start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                executor.execute("devmod.perf.v2_compare", trustedContext());
            }
            long v2Elapsed = System.nanoTime() - v2Start;
            long v2AvgNanos = v2Elapsed / iterations;

            // Measure "minimal" pipeline (resolve + execute only)
            ActionCatalog minCatalog = new ActionCatalog();
            HandlerRegistry minHandlers = new HandlerRegistry();
            String minId = "devmod.perf.minimal_compare";
            minCatalog.register(ActionSpec.builder(minId)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build());
            minHandlers.register(minId, ctx -> {});

            ActionExecutor minimal = ActionExecutor.builder()
                .addStep(new ResolveStep(minCatalog))
                .addStep(new ExecuteStep(minHandlers))
                .build();

            for (int i = 0; i < 200; i++) {
                minimal.execute(minId, trustedContext());
            }

            long minStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                minimal.execute(minId, trustedContext());
            }
            long minElapsed = System.nanoTime() - minStart;
            long minAvgNanos = minElapsed / iterations;

            System.out.println("[Perf] V2 full pipeline: " + v2AvgNanos + " ns/invocation");
            System.out.println("[Perf] Minimal pipeline: " + minAvgNanos + " ns/invocation");
            System.out.println("[Perf] Overhead: " + (v2AvgNanos - minAvgNanos) + " ns");

            // V2 full pipeline should be no more than 10x slower than minimal
            assertTrue(v2AvgNanos < minAvgNanos * 10,
                "V2 pipeline is too slow compared to minimal: " +
                v2AvgNanos + " vs " + minAvgNanos + " ns");
        }
    }

    // =========================================================================
    // 7. Rate Limiter Performance
    // =========================================================================

    @Nested
    @DisplayName("7. Rate Limiter Performance")
    class RateLimiterPerformanceTests {

        @Test
        @DisplayName("Rate limiter overhead per invocation")
        void rateLimiterOverhead() {
            int iterations = 5_000;

            // Without rate limit
            ActionCatalog noRlCatalog = new ActionCatalog();
            HandlerRegistry noRlHandlers = new HandlerRegistry();
            String noRlId = "devmod.perf.no_ratelimit";
            noRlCatalog.register(ActionSpec.builder(noRlId)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build());
            noRlHandlers.register(noRlId, ctx -> {});

            PolicyChain noRlChain = PolicyChain.builder()
                .add(OriginPolicy.INSTANCE)
                .build();
            ActionExecutor noRlExecutor = ActionExecutor.createDefault(
                noRlCatalog, noRlHandlers, noRlChain, line -> {});

            // With rate limit
            ActionCatalog rlCatalog = new ActionCatalog();
            HandlerRegistry rlHandlers = new HandlerRegistry();
            String rlId = "devmod.perf.with_ratelimit";
            rlCatalog.register(ActionSpec.builder(rlId)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .policy(false, 100000, null) // Very high rate limit so nothing is blocked
                .build());
            rlHandlers.register(rlId, ctx -> {});

            PolicyChain rlChain = PolicyChain.builder()
                .add(OriginPolicy.INSTANCE)
                .add(new RateLimitPolicy())
                .build();
            ActionExecutor rlExecutor = ActionExecutor.createDefault(
                rlCatalog, rlHandlers, rlChain, line -> {});

            // Warm up
            for (int i = 0; i < 200; i++) {
                noRlExecutor.execute(noRlId, trustedContext());
                rlExecutor.execute(rlId, trustedContext());
            }

            long noRlStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                noRlExecutor.execute(noRlId, trustedContext());
            }
            long noRlAvg = (System.nanoTime() - noRlStart) / iterations;

            long rlStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                rlExecutor.execute(rlId, trustedContext());
            }
            long rlAvg = (System.nanoTime() - rlStart) / iterations;

            long overhead = rlAvg - noRlAvg;
            System.out.println("[Perf] Rate limiter overhead: " + overhead + " ns/invocation " +
                "(without: " + noRlAvg + " ns, with: " + rlAvg + " ns)");

            // Rate limiter should add less than 500 micros
            assertTrue(overhead < 500_000,
                "Rate limiter overhead " + (overhead / 1000) + " micros exceeds 500 micros threshold");
        }
    }

    // =========================================================================
    // 8. Catalog Lookup Performance
    // =========================================================================

    @Nested
    @DisplayName("8. Catalog Lookup Performance")
    class CatalogLookupPerformanceTests {

        @Test
        @DisplayName("Get by ID with 100, 500, 1000 registered specs")
        void catalogLookupScaling() {
            int[] sizes = {100, 500, 1000};
            long[] avgNanos = new long[sizes.length];

            for (int s = 0; s < sizes.length; s++) {
                ActionCatalog localCatalog = new ActionCatalog();
                for (int i = 0; i < sizes[s]; i++) {
                    String id = "devmod.lookup.action_" + i;
                    localCatalog.register(ActionSpec.builder(id)
                        .channel(ActionChannel.CLIENT)
                        .category(ActionCategory.UI)
                        .ui("label", "desc")
                        .build());
                }

                // Look up the last registered action (worst case for some implementations)
                String targetId = "devmod.lookup.action_" + (sizes[s] - 1);

                // Warm up
                for (int i = 0; i < 1000; i++) {
                    localCatalog.get(targetId);
                }

                int iterations = 10_000;
                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    ActionSpec spec = localCatalog.get(targetId);
                    assertNotNull(spec);
                }
                avgNanos[s] = (System.nanoTime() - start) / iterations;
            }

            System.out.println("[Perf] Catalog lookup scaling:");
            for (int s = 0; s < sizes.length; s++) {
                System.out.println("  " + sizes[s] + " specs: " + avgNanos[s] + " ns/lookup");
            }

            // HashMap-based catalog should be O(1) - lookup at 1000 should be
            // within 5x of lookup at 100
            assertTrue(avgNanos[2] < avgNanos[0] * 5,
                "Catalog lookup at 1000 (" + avgNanos[2] + " ns) is more than 5x " +
                "slower than at 100 (" + avgNanos[0] + " ns) - not O(1)");
        }
    }
}
