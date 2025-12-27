package com.devmod.arena.validation;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.arena.cleanup.ArenaCleanupExecutor;
import com.devmod.arena.cleanup.RobustCleanupManager;
import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.performance.PerformanceBudgetEnforcer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Fase 3 Hardening Edge Tests")
class HardeningEdgeTests {

    // ========== Malformed Template Tests ==========

    @Nested
    @DisplayName("Malformed Template Validation")
    class MalformedTemplateTests {

        private AdvancedArenaTemplateValidator validator;

        @BeforeEach
        void setUp() {
            validator = new AdvancedArenaTemplateValidator();
        }

        @Test
        @DisplayName("Should reject null template ID")
        void shouldRejectNullTemplateId() {
            var context = new TestValidationContext(null, 1);

            var report = validator.validateStatic(context);

            assertFalse(report.valid());
            assertTrue(report.hasErrors());
            assertTrue(report.issues().stream()
                .anyMatch(i -> i.ruleId().equals("template_id_required")));
        }

        @Test
        @DisplayName("Should reject empty template ID")
        void shouldRejectEmptyTemplateId() {
            var context = new TestValidationContext("", 1);

            var report = validator.validateStatic(context);

            assertFalse(report.valid());
        }

        @Test
        @DisplayName("Should reject zero version")
        void shouldRejectZeroVersion() {
            var context = new TestValidationContext("test_template", 0);

            var report = validator.validateStatic(context);

            assertFalse(report.valid());
            assertTrue(report.issues().stream()
                .anyMatch(i -> i.ruleId().equals("version_positive")));
        }

        @Test
        @DisplayName("Should reject negative version")
        void shouldRejectNegativeVersion() {
            var context = new TestValidationContext("test_template", -1);

            var report = validator.validateStatic(context);

            assertFalse(report.valid());
        }

        @Test
        @DisplayName("Should reject null bounds")
        void shouldRejectNullBounds() {
            var context = new TestValidationContext("test", 1)
                .withBounds(null);

            var report = validator.validateStatic(context);

            assertFalse(report.valid());
            assertTrue(report.issues().stream()
                .anyMatch(i -> i.ruleId().equals("bounds_defined")));
        }

        @Test
        @DisplayName("Should reject zero spawn slots")
        void shouldRejectZeroSpawnSlots() {
            var context = new TestValidationContext("test", 1)
                .withSpawnSlotCount(0);

            var report = validator.validateStatic(context);

            assertFalse(report.valid());
            assertTrue(report.issues().stream()
                .anyMatch(i -> i.ruleId().equals("spawn_slots_defined")));
        }

        @Test
        @DisplayName("Should pass valid template")
        void shouldPassValidTemplate() {
            var context = new TestValidationContext("valid_template", 1)
                .withBounds(new TestBounds(64, 64, 64))
                .withSpawnSlotCount(10);

            var report = validator.validateStatic(context);

            assertTrue(report.valid());
            assertFalse(report.hasErrors());
        }
    }

    // ========== Security Limits Tests ==========

    @Nested
    @DisplayName("Security Limits Enforcement")
    class SecurityLimitsTests {

        private SecurityLimitsEnforcer enforcer;

        @BeforeEach
        void setUp() {
            enforcer = new SecurityLimitsEnforcer();
        }

        @Test
        @DisplayName("Should block arena size exceeding hard limit")
        void shouldBlockOversizedArena() {
            var context = SecurityLimitsEnforcer.SimpleLimitsContext.builder()
                .size(300, 64, 64)  // X exceeds 256
                .build();

            var result = enforcer.enforce(context);

            assertFalse(result.passed());
            assertTrue(result.hasHardViolations());
            assertTrue(result.shouldBlock());
        }

        @Test
        @DisplayName("Should block hazard count exceeding limit")
        void shouldBlockExcessiveHazards() {
            var context = SecurityLimitsEnforcer.SimpleLimitsContext.builder()
                .hazardCount(100)  // Exceeds 50
                .build();

            var result = enforcer.enforce(context);

            assertFalse(result.passed());
            assertTrue(result.hasHardViolations());
        }

        @Test
        @DisplayName("Should block spawn count exceeding limit")
        void shouldBlockExcessiveSpawns() {
            var context = SecurityLimitsEnforcer.SimpleLimitsContext.builder()
                .spawnCount(150)  // Exceeds 100
                .build();

            var result = enforcer.enforce(context);

            assertFalse(result.passed());
            assertTrue(result.hasHardViolations());
        }

        @Test
        @DisplayName("Should pass within limits")
        void shouldPassWithinLimits() {
            var context = SecurityLimitsEnforcer.SimpleLimitsContext.builder()
                .size(64, 64, 64)
                .hazardCount(10)
                .spawnCount(20)
                .entityCount(50)
                .blockCount(10000)
                .build();

            var result = enforcer.enforce(context);

            assertTrue(result.passed());
            assertFalse(result.hasHardViolations());
        }

        @Test
        @DisplayName("Should allow soft limit warning without blocking")
        void shouldAllowSoftLimitWarning() {
            // Use restrictive config
            var restrictiveEnforcer = new SecurityLimitsEnforcer(
                SecurityLimitsEnforcer.LimitsConfig.restrictive()
            );

            var context = SecurityLimitsEnforcer.SimpleLimitsContext.builder()
                .size(100, 64, 64)  // Exceeds soft (64) but under hard (256)
                .build();

            var result = restrictiveEnforcer.enforce(context);

            assertFalse(result.passed());  // Has violations
            assertFalse(result.hasHardViolations());  // But not hard
            assertTrue(result.canProceedWithWarnings());
        }
    }

    // ========== Runtime Preflight Tests ==========

    @Nested
    @DisplayName("Runtime Preflight Checks")
    class RuntimePreflightTests {

        private RuntimePreflightCheck preflightCheck;

        @BeforeEach
        void setUp() {
            preflightCheck = new RuntimePreflightCheck();
        }

        @Test
        @DisplayName("Should block non-instance world")
        void shouldBlockNonInstanceWorld() {
            var context = RuntimePreflightCheck.SimplePreflightContext.builder()
                .isInstanceWorld(false)
                .worldType("overworld")
                .dimensionKey("minecraft:overworld")
                .build();

            var result = preflightCheck.check(context, false);

            assertFalse(result.passed());
            assertTrue(result.hasBlockingIssues());
            assertTrue(result.getBlockingIssues().stream()
                .anyMatch(i -> i.type() == RuntimePreflightCheck.PreflightCheckType.INSTANCE_ONLY_GATE));
        }

        @Test
        @DisplayName("Should block blacklisted dimension")
        void shouldBlockBlacklistedDimension() {
            var context = RuntimePreflightCheck.SimplePreflightContext.builder()
                .isInstanceWorld(false)
                .dimensionKey("minecraft:the_nether")
                .build();

            var result = preflightCheck.check(context, false);

            assertFalse(result.passed());
        }

        @Test
        @DisplayName("Should block high MSPT")
        void shouldBlockHighMspt() {
            var context = RuntimePreflightCheck.SimplePreflightContext.builder()
                .isInstanceWorld(true)
                .currentMspt(50.0)  // Exceeds 45ms threshold
                .currentTps(20.0)
                .build();

            var result = preflightCheck.check(context, false);

            assertFalse(result.passed());
            assertTrue(result.getBlockingIssues().stream()
                .anyMatch(i -> i.type() == RuntimePreflightCheck.PreflightCheckType.MSPT_THRESHOLD));
        }

        @Test
        @DisplayName("Should block low TPS")
        void shouldBlockLowTps() {
            var context = RuntimePreflightCheck.SimplePreflightContext.builder()
                .isInstanceWorld(true)
                .currentMspt(30.0)
                .currentTps(15.0)  // Below 18 threshold
                .build();

            var result = preflightCheck.check(context, false);

            assertFalse(result.passed());
            assertTrue(result.getBlockingIssues().stream()
                .anyMatch(i -> i.type() == RuntimePreflightCheck.PreflightCheckType.TPS_THRESHOLD));
        }

        @Test
        @DisplayName("Should block concurrent arena limit")
        void shouldBlockConcurrentArenaLimit() {
            var context = RuntimePreflightCheck.SimplePreflightContext.builder()
                .isInstanceWorld(true)
                .currentMspt(20.0)
                .currentTps(20.0)
                .activeArenasInWorld(10)  // At limit
                .build();

            var result = preflightCheck.check(context, false);

            assertFalse(result.passed());
            assertTrue(result.getBlockingIssues().stream()
                .anyMatch(i -> i.type() == RuntimePreflightCheck.PreflightCheckType.CONCURRENT_ARENA_LIMIT));
        }

        @Test
        @DisplayName("Should pass valid preflight")
        void shouldPassValidPreflight() {
            var context = RuntimePreflightCheck.SimplePreflightContext.builder()
                .isInstanceWorld(true)
                .dimensionKey("devmod:arena")
                .currentMspt(20.0)
                .currentTps(20.0)
                .activeArenasInWorld(2)
                .availableMemoryMb(1024)
                .build();

            var result = preflightCheck.check(context, false);

            assertTrue(result.passed());
            assertFalse(result.hasBlockingIssues());
        }

        @Test
        @DisplayName("Should warn on low memory")
        void shouldWarnOnLowMemory() {
            var context = RuntimePreflightCheck.SimplePreflightContext.builder()
                .isInstanceWorld(true)
                .currentMspt(20.0)
                .currentTps(20.0)
                .availableMemoryMb(100)  // Below 256 MB threshold
                .build();

            var result = preflightCheck.check(context, false);

            assertTrue(result.passed());  // Warning doesn't block
            assertTrue(result.hasWarnings());
        }
    }

    // ========== Cleanup/Rollback Tests ==========

    @Nested
    @DisplayName("Cleanup and Rollback")
    class CleanupRollbackTests {

        @Test
        @DisplayName("Should enforce zero-residual invariant")
        void shouldEnforceZeroResidualInvariant() {
            // Create mock that leaves residuals on first attempt
            var mockLevelAccess = new MockLevelAccess() {
                private int cleanupAttempts = 0;

                @Override
                public int countEntitiesInBounds(int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ, boolean excludePlayers, Set<UUID> excluded) {
                    // Return residuals on first attempt, clean on retry
                    return cleanupAttempts++ < 1 ? 5 : 0;
                }
            };

            var executor = new ArenaCleanupExecutor(mockLevelAccess);
            var manager = new RobustCleanupManager(executor);

            var bounds = new ArenaCleanupExecutor.ArenaBounds(0, 0, 0, 63, 63, 63);
            var result = manager.cleanupWithInvariant(UUID.randomUUID(), bounds, false);

            assertTrue(result.isSuccess());
            assertTrue(result.requiredRetries());
            assertEquals(2, result.attemptCount());
        }

        @Test
        @DisplayName("Should fail after max retry attempts")
        void shouldFailAfterMaxRetries() {
            // Create mock that always leaves residuals
            var mockLevelAccess = new MockLevelAccess() {
                @Override
                public int countEntitiesInBounds(int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ, boolean excludePlayers, Set<UUID> excluded) {
                    return 10;  // Always residual
                }
            };

            var executor = new ArenaCleanupExecutor(mockLevelAccess);
            var manager = new RobustCleanupManager(executor);

            var bounds = new ArenaCleanupExecutor.ArenaBounds(0, 0, 0, 63, 63, 63);
            var result = manager.cleanupWithInvariant(UUID.randomUUID(), bounds, false);

            assertFalse(result.isSuccess());
            assertEquals(RobustCleanupManager.MAX_RETRY_ATTEMPTS, result.attemptCount());
        }

        @Test
        @DisplayName("Should succeed on first attempt with clean state")
        void shouldSucceedOnFirstAttempt() {
            var mockLevelAccess = new MockLevelAccess();  // Clean by default
            var executor = new ArenaCleanupExecutor(mockLevelAccess);
            var manager = new RobustCleanupManager(executor);

            var bounds = new ArenaCleanupExecutor.ArenaBounds(0, 0, 0, 63, 63, 63);
            var result = manager.cleanupWithInvariant(UUID.randomUUID(), bounds, false);

            assertTrue(result.isSuccess());
            assertFalse(result.requiredRetries());
            assertEquals(1, result.attemptCount());
        }
    }

    // ========== Concurrency Tests ==========

    @Nested
    @DisplayName("Concurrency Stress Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent validations")
        void shouldHandleConcurrentValidations() throws Exception {
            var validator = new AdvancedArenaTemplateValidator();
            int threadCount = 10;
            int validationsPerThread = 100;

            var latch = new CountDownLatch(threadCount);
            var errors = new AtomicInteger(0);
            var successes = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < validationsPerThread; i++) {
                            var context = new TestValidationContext(
                                "template_" + threadId + "_" + i,
                                i + 1
                            ).withBounds(new TestBounds(64, 64, 64))
                             .withSpawnSlotCount(10);

                            var report = validator.validateStatic(context);
                            if (report.valid()) {
                                successes.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).isDone();
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(0, errors.get());
            assertEquals(threadCount * validationsPerThread, successes.get());
        }

        @Test
        @DisplayName("Should handle concurrent limit enforcement")
        void shouldHandleConcurrentLimitEnforcement() throws Exception {
            var enforcer = new SecurityLimitsEnforcer();
            int threadCount = 10;
            int checksPerThread = 100;

            var latch = new CountDownLatch(threadCount);
            var errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < checksPerThread; i++) {
                            var context = SecurityLimitsEnforcer.SimpleLimitsContext.builder()
                                .size(64, 64, 64)
                                .hazardCount(i % 40)
                                .spawnCount(i % 80)
                                .build();

                            var result = enforcer.enforce(context);
                            assertNotNull(result);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).isDone();
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(0, errors.get());
        }

        @Test
        @DisplayName("Should handle concurrent cleanup operations")
        void shouldHandleConcurrentCleanups() throws Exception {
            var mockLevelAccess = new MockLevelAccess();
            var executor = new ArenaCleanupExecutor(mockLevelAccess);
            var manager = new RobustCleanupManager(executor);

            int cleanupCount = 20;
            var futures = new ArrayList<CompletableFuture<?>>(cleanupCount);

            for (int i = 0; i < cleanupCount; i++) {
                var bounds = new ArenaCleanupExecutor.ArenaBounds(
                    i * 64, 0, 0, (i + 1) * 64 - 1, 63, 63);
                futures.add(manager.cleanupWithInvariantAsync(UUID.randomUUID(), bounds));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);

            var stats = manager.getStats();
            assertEquals(cleanupCount, stats.total());
            assertEquals(cleanupCount, stats.successful());
        }
    }

    // ========== Performance Budget Tests ==========

    @Nested
    @DisplayName("Performance Budget Enforcement")
    class PerformanceBudgetTests {

        private MsptMonitor msptMonitor;
        private PerformanceBudgetEnforcer enforcer;

        @BeforeEach
        void setUp() {
            msptMonitor = new MsptMonitor();
            enforcer = new PerformanceBudgetEnforcer(msptMonitor);
        }

        @Test
        @DisplayName("Should capture baseline")
        void shouldCaptureBaseline() {
            // Record some samples
            for (int i = 0; i < 30; i++) {
                msptMonitor.recordSample(20.0 + (i % 5));
            }

            double baseline = enforcer.captureBaseline();

            assertTrue(baseline > 0);
            assertEquals(baseline, enforcer.getBaseline());
        }

        @Test
        @DisplayName("Should continue on good performance")
        void shouldContinueOnGoodPerformance() {
            for (int i = 0; i < 30; i++) {
                msptMonitor.recordSample(20.0);
            }

            enforcer.captureBaseline();
            enforcer.beginBuild();

            msptMonitor.recordSample(25.0);  // Good MSPT
            var action = enforcer.checkPerformance();

            assertEquals(PerformanceBudgetEnforcer.PerformanceAction.CONTINUE, action);
            assertFalse(enforcer.isBuildAborted());
        }

        @Test
        @DisplayName("Should throttle on moderate performance issues")
        void shouldThrottleOnModerateIssues() {
            for (int i = 0; i < 30; i++) {
                msptMonitor.recordSample(20.0);
            }

            enforcer.captureBaseline();
            enforcer.beginBuild();

            // Record high MSPT sample
            msptMonitor.recordSample(50.0);  // Exceeds threshold
            var action = enforcer.checkPerformance();

            assertTrue(action == PerformanceBudgetEnforcer.PerformanceAction.THROTTLE
                    || action == PerformanceBudgetEnforcer.PerformanceAction.PAUSE);
        }

        @Test
        @DisplayName("Should abort after consecutive violations")
        void shouldAbortAfterConsecutiveViolations() {
            for (int i = 0; i < 30; i++) {
                msptMonitor.recordSample(20.0);
            }

            enforcer.captureBaseline();
            enforcer.beginBuild();

            // Simulate consecutive violations
            for (int i = 0; i <= PerformanceBudgetEnforcer.DEFAULT_MAX_CONSECUTIVE_VIOLATIONS; i++) {
                msptMonitor.recordSample(50.0);
                var action = enforcer.checkPerformance();

                if (action == PerformanceBudgetEnforcer.PerformanceAction.ABORT) {
                    assertTrue(enforcer.isBuildAborted());
                    return;
                }
            }

            assertTrue(enforcer.isBuildAborted());
        }

        @Test
        @DisplayName("Should generate build performance report")
        void shouldGenerateBuildPerformanceReport() {
            for (int i = 0; i < 30; i++) {
                msptMonitor.recordSample(20.0);
            }

            enforcer.captureBaseline();
            enforcer.beginBuild();

            // Simulate some build work
            for (int i = 0; i < 10; i++) {
                msptMonitor.recordSample(25.0 + i);
                enforcer.checkPerformance();
            }

            var report = enforcer.endBuild();

            assertNotNull(report);
            assertTrue(report.baseline() > 0);
            assertTrue(report.averageMspt() > 0);
            assertTrue(report.buildDuration().toMillis() >= 0);
        }
    }

    // ========== Test Helpers ==========

    /**
     * Test implementation of ValidationContext.
     */
    static class TestValidationContext implements AdvancedArenaTemplateValidator.ValidationContext {
        private final String templateId;
        private final int version;
        private AdvancedArenaTemplateValidator.ArenaBounds bounds = new TestBounds(64, 64, 64);
        private int spawnSlotCount = 10;
        private int hazardCount = 5;
        private int estimatedBlockCount = 10000;
        private int inheritanceDepth = 0;

        TestValidationContext(String templateId, int version) {
            this.templateId = templateId;
            this.version = version;
        }

        TestValidationContext withBounds(AdvancedArenaTemplateValidator.ArenaBounds bounds) {
            this.bounds = bounds;
            return this;
        }

        TestValidationContext withSpawnSlotCount(int count) {
            this.spawnSlotCount = count;
            return this;
        }

        @Override public String templateId() { return templateId; }
        @Override public int version() { return version; }
        @Override public AdvancedArenaTemplateValidator.ArenaBounds bounds() { return bounds; }
        @Override public int spawnSlotCount() { return spawnSlotCount; }
        @Override public AdvancedArenaTemplateValidator.Vec3i playerSpawn() { return new AdvancedArenaTemplateValidator.Vec3i(32, 32, 32); }
        @Override public int hazardCount() { return hazardCount; }
        @Override public int estimatedBlockCount() { return estimatedBlockCount; }
        @Override public int inheritanceDepth() { return inheritanceDepth; }
        @Override public boolean isClosedArena() { return false; }
        @Override public boolean hasExitPortal() { return true; }
        @Override public boolean hasWaveSpawner() { return false; }
        @Override public boolean waveSpawnerHasValidConfig() { return true; }
    }

    /**
     * Test implementation of ArenaBounds.
     */
    record TestBounds(int sizeX, int sizeY, int sizeZ) implements AdvancedArenaTemplateValidator.ArenaBounds {
        @Override
        public boolean contains(AdvancedArenaTemplateValidator.Vec3i pos) {
            return pos.x() >= 0 && pos.x() < sizeX
                && pos.y() >= 0 && pos.y() < sizeY
                && pos.z() >= 0 && pos.z() < sizeZ;
        }
    }

    /**
     * Mock LevelAccess for cleanup tests.
     */
    static class MockLevelAccess implements ArenaCleanupExecutor.LevelAccess {
        @Override
        public int removeEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                boolean preservePlayers, Set<UUID> excludedEntities) {
            return 10;
        }

        @Override
        public int removeBlockEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                boolean clearContainers) {
            return 5;
        }

        @Override
        public int cancelScheduledTicksInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return 3;
        }

        @Override
        public int setBlocksToAirInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                int maxBlocksPerTick) {
            return 1000;
        }

        @Override
        public int countEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                boolean excludePlayers, Set<UUID> excludedEntities) {
            return 0;  // Clean by default
        }

        @Override
        public int countBlockEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return 0;
        }

        @Override
        public int countNonAirBlocksInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return 0;
        }
    }
}
