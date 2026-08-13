package com.devmod.combat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.combat.bridge.CombatVisualsBridge;
import com.devmod.combat.bridge.CombatVisualsBridge.RangedStatsSnapshot;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CombatVisualsBridge")
public class CombatVisualsBridgeTest {

    @BeforeEach
    void setUp() {
        // Reset to NoOp before each test
        CombatVisualsBridge.setInstance(new CombatVisualsBridge.NoOp());
    }

    @AfterEach
    void tearDown() {
        CombatVisualsBridge.setInstance(new CombatVisualsBridge.NoOp());
    }

    @Nested
    @DisplayName("Default NoOp implementation")
    class NoOpTests {

        @Test
        @DisplayName("get() returns NoOp by default")
        void getReturnsNoOp() {
            CombatVisualsBridge bridge = CombatVisualsBridge.get();
            assertNotNull(bridge);
            assertInstanceOf(CombatVisualsBridge.NoOp.class, bridge);
        }

        @Test
        @DisplayName("NoOp createAndStoreImpactData (UUID variant) returns null")
        void noOpCreateAndStoreImpactDataUuidReturnsNull() {
            CombatVisualsBridge bridge = CombatVisualsBridge.get();
            // Use the UUID overload to avoid LivingEntity class initialization
            Object result = bridge.createAndStoreImpactData(
                    UUID.randomUUID(), null, HitHelper.BodyPart.BODY,
                    1.0f, null, "test", false, null, null);
            assertNull(result);
        }

        @Test
        @DisplayName("NoOp getImpactData returns null")
        void noOpGetImpactDataReturnsNull() {
            assertNull(CombatVisualsBridge.get().getImpactData());
        }

        @Test
        @DisplayName("NoOp getImpactTarget returns null")
        void noOpGetImpactTargetReturnsNull() {
            assertNull(CombatVisualsBridge.get().getImpactTarget(new Object()));
        }

        @Test
        @DisplayName("NoOp getImpactAttackerUUID returns null")
        void noOpGetImpactAttackerUuidReturnsNull() {
            assertNull(CombatVisualsBridge.get().getImpactAttackerUUID(new Object()));
        }

        @Test
        @DisplayName("NoOp resolveRangedStats returns DEFAULTS")
        void noOpResolveRangedStatsReturnsDefaults() {
            // Pass null instead of ItemStack.EMPTY to avoid Minecraft class initialization
            RangedStatsSnapshot snapshot = CombatVisualsBridge.get().resolveRangedStats(null);
            assertSame(RangedStatsSnapshot.DEFAULTS, snapshot);
        }

        @Test
        @DisplayName("NoOp void methods do not throw")
        void noOpVoidMethodsDoNotThrow() {
            CombatVisualsBridge bridge = CombatVisualsBridge.get();
            assertDoesNotThrow(() -> bridge.triggerImpactVfx(null, null, null, null));
            assertDoesNotThrow(() -> bridge.triggerDamageShakeIfApplicable(null, null, 0, 0, null));
            assertDoesNotThrow(() -> bridge.setImpactActualDamage(null, 0, 0, 0));
            assertDoesNotThrow(() -> bridge.setImpactDamageReductionBreakdown(null, 0, 0, 0, 0, 0));
            assertDoesNotThrow(() -> bridge.recordDpsDamage(UUID.randomUUID(), 10f));
            assertDoesNotThrow(() -> bridge.spawnMeleeEvasionPanel(null, null, null, null));
            assertDoesNotThrow(() -> bridge.recordShieldImpact(null, null, 5f));
        }
    }

    @Nested
    @DisplayName("setInstance / get pattern")
    class SetInstanceTests {

        @Test
        @DisplayName("setInstance replaces the bridge implementation")
        void setInstanceReplacesImplementation() {
            var custom = new TrackingBridge();
            CombatVisualsBridge.setInstance(custom);

            assertSame(custom, CombatVisualsBridge.get());
        }

        @Test
        @DisplayName("setInstance back to NoOp restores default behavior")
        void setInstanceBackToNoOp() {
            CombatVisualsBridge.setInstance(new TrackingBridge());
            CombatVisualsBridge.setInstance(new CombatVisualsBridge.NoOp());

            assertInstanceOf(CombatVisualsBridge.NoOp.class, CombatVisualsBridge.get());
        }

        @Test
        @DisplayName("Custom implementation receives method calls")
        void customImplReceivesCalls() {
            var custom = new TrackingBridge();
            CombatVisualsBridge.setInstance(custom);

            UUID id = UUID.randomUUID();
            CombatVisualsBridge.get().recordDpsDamage(id, 42f);

            assertEquals(id, custom.lastDpsAttackerId);
            assertEquals(42f, custom.lastDpsDamage, 0.001f);
        }
    }

    @Nested
    @DisplayName("Thread safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Concurrent setInstance/get does not lose updates")
        void concurrentSetAndGet() throws InterruptedException {
            int threadCount = 8;
            int iterations = 500;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int t = 0; t < threadCount; t++) {
                @SuppressWarnings("FutureReturnValueIgnored")
                var unused = pool.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < iterations; i++) {
                            CombatVisualsBridge.setInstance(new TrackingBridge());
                            CombatVisualsBridge bridge = CombatVisualsBridge.get();
                            assertNotNull(bridge, "get() returned null during concurrent access");
                        }
                    } catch (Throwable ex) {
                        failure.compareAndSet(null, ex);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            pool.shutdown();

            if (failure.get() != null) {
                fail("Concurrent access failed: " + failure.get().getMessage());
            }
        }
    }

    @Nested
    @DisplayName("RangedStatsSnapshot defaults")
    class RangedStatsTests {

        @Test
        @DisplayName("DEFAULTS has expected values")
        void defaultValues() {
            assertEquals(0f, RangedStatsSnapshot.DEFAULTS.baseDamage(), 0.001f);
            assertEquals(1f, RangedStatsSnapshot.DEFAULTS.projectileSpeed(), 0.001f);
            assertEquals(0f, RangedStatsSnapshot.DEFAULTS.critChance(), 0.001f);
            assertEquals(1.5f, RangedStatsSnapshot.DEFAULTS.critDamage(), 0.001f);
        }
    }

    // --- Helper: tracking implementation for verification ---
    static class TrackingBridge implements CombatVisualsBridge {
        @Nullable UUID lastDpsAttackerId;
        float lastDpsDamage;

        @Override
        public void recordDpsDamage(UUID attackerId, float damage) {
            this.lastDpsAttackerId = attackerId;
            this.lastDpsDamage = damage;
        }
    }
}
