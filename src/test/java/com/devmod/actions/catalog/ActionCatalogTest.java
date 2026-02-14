package com.devmod.actions.catalog;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.catalog.ActionCatalogValidator.CatalogValidationReport;
import com.devmod.actions.catalog.ActionCatalogValidator.Severity;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ActionCatalog and ActionCatalogValidator.
 */
@DisplayName("ActionCatalog Tests")
class ActionCatalogTest {

    private ActionCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
    }

    private ActionSpec makeSpec(String id) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(ActionCategory.UI)
            .ui("label." + id, "desc." + id)
            .build();
    }

    private ActionSpec makeSpec(String id, ActionChannel channel, ActionCategory category) {
        ActionSpec.Builder builder = ActionSpec.builder(id)
            .channel(channel)
            .category(category)
            .ui("label." + id, "desc." + id);
        if (channel == ActionChannel.SERVER || channel == ActionChannel.BOTH) {
            builder.allowedOrigins(EnumSet.of(
                ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
                ActionOrigin.UI, ActionOrigin.EVENT, ActionOrigin.NETWORK
            ));
        }
        return builder.build();
    }

    // =========================================================================
    // Registration and retrieval
    // =========================================================================

    @Nested
    @DisplayName("Register and Retrieve")
    class RegisterRetrieveTests {

        @Test
        @DisplayName("register and get by ID")
        void registerAndGet() {
            ActionSpec spec = makeSpec("devmod.ui.test");
            catalog.register(spec);

            ActionSpec retrieved = catalog.get("devmod.ui.test");
            assertNotNull(retrieved);
            assertEquals("devmod.ui.test", retrieved.id());
        }

        @Test
        @DisplayName("get returns null for unknown ID")
        void getReturnsNullForUnknown() {
            assertNull(catalog.get("devmod.nonexistent.action"));
        }

        @Test
        @DisplayName("contains returns true for registered ID")
        void containsReturnsTrueForRegistered() {
            catalog.register(makeSpec("devmod.ui.test"));
            assertTrue(catalog.contains("devmod.ui.test"));
        }

        @Test
        @DisplayName("contains returns false for unknown ID")
        void containsReturnsFalseForUnknown() {
            assertFalse(catalog.contains("devmod.nonexistent.action"));
        }

        @Test
        @DisplayName("size returns correct count")
        void sizeReturnsCorrectCount() {
            assertEquals(0, catalog.size());
            catalog.register(makeSpec("devmod.ui.one"));
            assertEquals(1, catalog.size());
            catalog.register(makeSpec("devmod.ui.two"));
            assertEquals(2, catalog.size());
        }

        @Test
        @DisplayName("getAll returns all registered specs")
        void getAllReturnsAll() {
            catalog.register(makeSpec("devmod.ui.one"));
            catalog.register(makeSpec("devmod.ui.two"));
            catalog.register(makeSpec("devmod.ui.three"));

            Collection<ActionSpec> all = catalog.getAll();
            assertEquals(3, all.size());
        }

        @Test
        @DisplayName("getAll returns defensive copy")
        void getAllReturnsDefensiveCopy() {
            catalog.register(makeSpec("devmod.ui.one"));
            Collection<ActionSpec> all = catalog.getAll();
            assertThrows(UnsupportedOperationException.class, () -> {
                ((List<ActionSpec>) all).add(makeSpec("devmod.ui.hack"));
            });
        }
    }

    // =========================================================================
    // Duplicate ID rejection
    // =========================================================================

    @Nested
    @DisplayName("Duplicate ID Rejection")
    class DuplicateIdTests {

        @Test
        @DisplayName("duplicate ID throws IllegalStateException")
        void duplicateIdThrows() {
            catalog.register(makeSpec("devmod.ui.test"));
            assertThrows(IllegalStateException.class, () ->
                catalog.register(makeSpec("devmod.ui.test")));
        }

        @Test
        @DisplayName("duplicate does not corrupt existing entry")
        void duplicateDoesNotCorrupt() {
            ActionSpec original = makeSpec("devmod.ui.test");
            catalog.register(original);

            try {
                catalog.register(ActionSpec.builder("devmod.ui.test")
                    .channel(ActionChannel.SERVER)
                    .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                    .category(ActionCategory.ADMIN)
                    .ui("different", "different")
                    .build());
            } catch (IllegalStateException ignored) {}

            ActionSpec retrieved = catalog.get("devmod.ui.test");
            assertSame(original, retrieved);
        }
    }

    // =========================================================================
    // Category and channel filtering
    // =========================================================================

    @Nested
    @DisplayName("Filtering")
    class FilteringTests {

        @Test
        @DisplayName("getByCategory filters correctly")
        void getByCategoryFilters() {
            catalog.register(makeSpec("devmod.ui.one", ActionChannel.CLIENT, ActionCategory.UI));
            catalog.register(makeSpec("devmod.combat.two", ActionChannel.CLIENT, ActionCategory.COMBAT));
            catalog.register(makeSpec("devmod.ui.three", ActionChannel.CLIENT, ActionCategory.UI));

            List<ActionSpec> uiSpecs = catalog.getByCategory(ActionCategory.UI);
            assertEquals(2, uiSpecs.size());
            assertTrue(uiSpecs.stream().allMatch(s -> s.category() == ActionCategory.UI));
        }

        @Test
        @DisplayName("getByCategory returns empty for no matches")
        void getByCategoryEmpty() {
            catalog.register(makeSpec("devmod.ui.one"));
            List<ActionSpec> adminSpecs = catalog.getByCategory(ActionCategory.ADMIN);
            assertTrue(adminSpecs.isEmpty());
        }

        @Test
        @DisplayName("getByChannel filters correctly")
        void getByChannelFilters() {
            catalog.register(makeSpec("devmod.ui.client", ActionChannel.CLIENT, ActionCategory.UI));
            catalog.register(makeSpec("devmod.server.action", ActionChannel.SERVER, ActionCategory.ADMIN));
            catalog.register(makeSpec("devmod.ui.client2", ActionChannel.CLIENT, ActionCategory.UI));

            List<ActionSpec> clientSpecs = catalog.getByChannel(ActionChannel.CLIENT);
            assertEquals(2, clientSpecs.size());
            assertTrue(clientSpecs.stream().allMatch(s -> s.channel() == ActionChannel.CLIENT));
        }

        @Test
        @DisplayName("getByChannel returns empty for no matches")
        void getByChannelEmpty() {
            catalog.register(makeSpec("devmod.ui.one"));
            List<ActionSpec> serverSpecs = catalog.getByChannel(ActionChannel.BOTH);
            assertTrue(serverSpecs.isEmpty());
        }
    }

    // =========================================================================
    // Validation rules (ActionCatalogValidator)
    // =========================================================================

    @Nested
    @DisplayName("Validation Rules")
    class ValidationTests {

        @Test
        @DisplayName("valid catalog produces no errors")
        void validCatalogNoErrors() {
            catalog.register(makeSpec("devmod.ui.one"));
            catalog.register(makeSpec("devmod.ui.two"));

            HandlerRegistry handlers = new HandlerRegistry();
            handlers.register("devmod.ui.one", ctx -> {});
            handlers.register("devmod.ui.two", ctx -> {});

            CatalogValidationReport report = ActionCatalogValidator.validate(catalog, handlers);
            assertTrue(report.isValid());
            assertEquals(0, report.errorCount());
        }

        @Test
        @DisplayName("missing handler produces ERROR violation")
        void missingHandlerError() {
            catalog.register(makeSpec("devmod.ui.orphan"));
            HandlerRegistry handlers = new HandlerRegistry();

            CatalogValidationReport report = ActionCatalogValidator.validate(catalog, handlers);
            assertFalse(report.isValid());
            assertTrue(report.errorCount() > 0);
            assertTrue(report.getBySeverity(Severity.ERROR).stream()
                .anyMatch(v -> v.rule().equals("handlerRef")));
        }

        @Test
        @DisplayName("null handlerRegistry skips handler checks")
        void nullHandlerRegistrySkipsHandlerChecks() {
            catalog.register(makeSpec("devmod.ui.orphan"));

            CatalogValidationReport report = ActionCatalogValidator.validate(catalog, null);
            assertTrue(report.isValid());
        }

        @Test
        @DisplayName("empty labelKey produces WARNING")
        void emptyLabelKeyWarning() {
            ActionSpec spec = ActionSpec.builder("devmod.ui.nolabel")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("", "desc")
                .build();
            catalog.register(spec);

            CatalogValidationReport report = ActionCatalogValidator.validate(catalog, null);
            assertTrue(report.warningCount() > 0);
            assertTrue(report.getBySeverity(Severity.WARNING).stream()
                .anyMatch(v -> v.rule().equals("uiMeta")));
        }

        @Test
        @DisplayName("empty descKey produces INFO")
        void emptyDescKeyInfo() {
            ActionSpec spec = ActionSpec.builder("devmod.ui.nodesc")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "")
                .build();
            catalog.register(spec);

            CatalogValidationReport report = ActionCatalogValidator.validate(catalog, null);
            List<ActionCatalogValidator.Violation> infos = report.getBySeverity(Severity.INFO);
            assertTrue(infos.stream().anyMatch(v -> v.rule().equals("uiMeta")));
        }

        @Test
        @DisplayName("report totalViolations counts all severities")
        void totalViolations() {
            ActionSpec spec = ActionSpec.builder("devmod.ui.both")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("", "")
                .build();
            catalog.register(spec);

            HandlerRegistry handlers = new HandlerRegistry();
            CatalogValidationReport report = ActionCatalogValidator.validate(catalog, handlers);

            // empty label = WARNING, empty desc = INFO, no handler = ERROR
            assertTrue(report.totalViolations() >= 3);
        }
    }

    // =========================================================================
    // Thread safety
    // =========================================================================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("concurrent register/get does not lose data")
        void concurrentRegisterGet() throws InterruptedException {
            int threadCount = 8;
            int perThread = 50;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger failures = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                pool.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < perThread; i++) {
                            String id = "devmod.thread" + threadIdx + ".action" + i;
                            ActionSpec spec = makeSpec(id);
                            catalog.register(spec);
                            if (catalog.get(id) == null) {
                                failures.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            pool.shutdown();

            assertEquals(0, failures.get());
            assertEquals(threadCount * perThread, catalog.size());
        }

        @Test
        @DisplayName("concurrent duplicate register throws correctly")
        void concurrentDuplicateRegister() throws InterruptedException {
            int threadCount = 4;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger duplicateErrors = new AtomicInteger(0);

            ActionSpec spec = makeSpec("devmod.ui.contested");

            for (int t = 0; t < threadCount; t++) {
                pool.submit(() -> {
                    try {
                        startLatch.await();
                        catalog.register(spec);
                        successes.incrementAndGet();
                    } catch (IllegalStateException e) {
                        duplicateErrors.incrementAndGet();
                    } catch (Exception e) {
                        // unexpected
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            pool.shutdown();

            // Exactly one thread should succeed, the rest get duplicate errors
            assertEquals(1, successes.get());
            assertEquals(threadCount - 1, duplicateErrors.get());
        }
    }
}
