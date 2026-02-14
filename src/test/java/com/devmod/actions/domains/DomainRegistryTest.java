package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DomainRegistry}: domain registration, dependency ordering,
 * cycle detection, duplicate rejection, and catalog/handler integration.
 */
@DisplayName("DomainRegistry Tests")
class DomainRegistryTest {

    private DomainRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DomainRegistry();
    }

    /** Creates a minimal test registrar with given name, specs, and dependencies. */
    private static DomainRegistrar stubRegistrar(String name, List<ActionSpec> specs,
                                                  Set<String> deps) {
        return new DomainRegistrar() {
            @Override
            public String domainName() {
                return name;
            }

            @Override
            public List<ActionSpec> getActionSpecs() {
                return specs;
            }

            @Override
            public void registerHandlers(HandlerRegistry handlerRegistry) {
                for (ActionSpec spec : specs) {
                    handlerRegistry.register(spec.id(), ctx -> {});
                }
            }

            @Override
            public Set<String> getDependencies() {
                return deps;
            }
        };
    }

    private static ActionSpec testSpec(String id) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(ActionCategory.UI)
            .ui("label." + id, "desc." + id)
            .build();
    }

    // =========================================================================
    // Single Domain Registration
    // =========================================================================

    @Nested
    @DisplayName("Single Domain Registration")
    class SingleDomainTests {

        @Test
        @DisplayName("Register single domain and initialize")
        void registerSingleDomain() {
            DomainRegistrar registrar = stubRegistrar("test",
                List.of(testSpec("devmod.test.action_a")), Set.of());

            registry.addRegistrar(registrar);
            registry.initialize();

            assertEquals(1, registry.domainCount());
            assertEquals(1, registry.actionCount());
        }

        @Test
        @DisplayName("Catalog contains registered action specs after init")
        void catalogContainsSpecs() {
            ActionSpec spec = testSpec("devmod.test.catalog_check");
            registry.addRegistrar(stubRegistrar("test",
                List.of(spec), Set.of()));
            registry.initialize();

            Map<String, ActionSpec> catalog = registry.getCatalog();
            assertTrue(catalog.containsKey("devmod.test.catalog_check"));
            assertEquals(spec.id(), catalog.get("devmod.test.catalog_check").id());
        }

        @Test
        @DisplayName("Handler registry populated after init")
        void handlerRegistryPopulated() {
            registry.addRegistrar(stubRegistrar("test",
                List.of(testSpec("devmod.test.handler_check")), Set.of()));
            registry.initialize();

            HandlerRegistry handlers = registry.getHandlerRegistry();
            assertTrue(handlers.hasHandler("devmod.test.handler_check"));
        }

        @Test
        @DisplayName("Domain with no actions initializes successfully")
        void emptyDomainInitializes() {
            registry.addRegistrar(stubRegistrar("empty", List.of(), Set.of()));
            registry.initialize();

            assertEquals(1, registry.domainCount());
            assertEquals(0, registry.actionCount());
        }
    }

    // =========================================================================
    // Multiple Domain Registration
    // =========================================================================

    @Nested
    @DisplayName("Multiple Domain Registration")
    class MultipleDomainTests {

        @Test
        @DisplayName("Register multiple domains and initialize")
        void registerMultipleDomains() {
            registry.addRegistrar(stubRegistrar("domain_a",
                List.of(testSpec("devmod.domain_a.action1")), Set.of()));
            registry.addRegistrar(stubRegistrar("domain_b",
                List.of(testSpec("devmod.domain_b.action1"),
                    testSpec("devmod.domain_b.action2")), Set.of()));
            registry.initialize();

            assertEquals(2, registry.domainCount());
            assertEquals(3, registry.actionCount());
        }

        @Test
        @DisplayName("Catalog has actions from all domains")
        void catalogHasAllDomainActions() {
            registry.addRegistrar(stubRegistrar("x",
                List.of(testSpec("devmod.x.one")), Set.of()));
            registry.addRegistrar(stubRegistrar("y",
                List.of(testSpec("devmod.y.two")), Set.of()));
            registry.initialize();

            Map<String, ActionSpec> catalog = registry.getCatalog();
            assertTrue(catalog.containsKey("devmod.x.one"));
            assertTrue(catalog.containsKey("devmod.y.two"));
        }
    }

    // =========================================================================
    // Dependency Ordering
    // =========================================================================

    @Nested
    @DisplayName("Dependency Ordering")
    class DependencyOrderingTests {

        @Test
        @DisplayName("Dependencies are initialized before dependents")
        void dependenciesInitializedFirst() {
            List<String> initOrder = new ArrayList<>();

            DomainRegistrar dep = new DomainRegistrar() {
                @Override public String domainName() { return "dep"; }
                @Override public List<ActionSpec> getActionSpecs() {
                    initOrder.add("dep");
                    return List.of(testSpec("devmod.dep.action1"));
                }
                @Override public void registerHandlers(HandlerRegistry r) {
                    r.register("devmod.dep.action1", ctx -> {});
                }
                @Override public Set<String> getDependencies() { return Set.of(); }
            };

            DomainRegistrar dependent = new DomainRegistrar() {
                @Override public String domainName() { return "dependent"; }
                @Override public List<ActionSpec> getActionSpecs() {
                    initOrder.add("dependent");
                    return List.of(testSpec("devmod.dependent.action1"));
                }
                @Override public void registerHandlers(HandlerRegistry r) {
                    r.register("devmod.dependent.action1", ctx -> {});
                }
                @Override public Set<String> getDependencies() { return Set.of("dep"); }
            };

            // Add dependent first, dep second - should still init dep first
            registry.addRegistrar(dependent);
            registry.addRegistrar(dep);
            registry.initialize();

            assertEquals(List.of("dep", "dependent"), initOrder,
                "Dependency 'dep' should be initialized before 'dependent'");
        }

        @Test
        @DisplayName("Transitive dependencies are resolved")
        void transitiveDependencies() {
            List<String> initOrder = new ArrayList<>();

            DomainRegistrar a = new DomainRegistrar() {
                @Override public String domainName() { return "a"; }
                @Override public List<ActionSpec> getActionSpecs() {
                    initOrder.add("a");
                    return List.of(testSpec("devmod.a.action1"));
                }
                @Override public void registerHandlers(HandlerRegistry r) {
                    r.register("devmod.a.action1", ctx -> {});
                }
                @Override public Set<String> getDependencies() { return Set.of(); }
            };

            DomainRegistrar b = new DomainRegistrar() {
                @Override public String domainName() { return "b"; }
                @Override public List<ActionSpec> getActionSpecs() {
                    initOrder.add("b");
                    return List.of(testSpec("devmod.b.action1"));
                }
                @Override public void registerHandlers(HandlerRegistry r) {
                    r.register("devmod.b.action1", ctx -> {});
                }
                @Override public Set<String> getDependencies() { return Set.of("a"); }
            };

            DomainRegistrar c = new DomainRegistrar() {
                @Override public String domainName() { return "c"; }
                @Override public List<ActionSpec> getActionSpecs() {
                    initOrder.add("c");
                    return List.of(testSpec("devmod.c.action1"));
                }
                @Override public void registerHandlers(HandlerRegistry r) {
                    r.register("devmod.c.action1", ctx -> {});
                }
                @Override public Set<String> getDependencies() { return Set.of("b"); }
            };

            registry.addRegistrar(c);
            registry.addRegistrar(a);
            registry.addRegistrar(b);
            registry.initialize();

            assertEquals(List.of("a", "b", "c"), initOrder,
                "Transitive deps should resolve: a -> b -> c");
        }
    }

    // =========================================================================
    // Cycle Detection
    // =========================================================================

    @Nested
    @DisplayName("Cycle Detection")
    class CycleDetectionTests {

        @Test
        @DisplayName("Direct cycle (A depends on B, B depends on A) throws")
        void directCycleThrows() {
            registry.addRegistrar(stubRegistrar("cycle_a",
                List.of(testSpec("devmod.cycle_a.action1")), Set.of("cycle_b")));
            registry.addRegistrar(stubRegistrar("cycle_b",
                List.of(testSpec("devmod.cycle_b.action1")), Set.of("cycle_a")));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.initialize());
            assertTrue(ex.getMessage().contains("cycle"),
                "Should mention cycle: " + ex.getMessage());
        }

        @Test
        @DisplayName("Self-dependency throws")
        void selfDependencyThrows() {
            registry.addRegistrar(stubRegistrar("self_dep",
                List.of(testSpec("devmod.self_dep.action1")), Set.of("self_dep")));

            assertThrows(IllegalStateException.class, () -> registry.initialize());
        }

        @Test
        @DisplayName("Transitive cycle (A->B->C->A) throws")
        void transitiveCycleThrows() {
            registry.addRegistrar(stubRegistrar("t_a",
                List.of(testSpec("devmod.t_a.action1")), Set.of("t_c")));
            registry.addRegistrar(stubRegistrar("t_b",
                List.of(testSpec("devmod.t_b.action1")), Set.of("t_a")));
            registry.addRegistrar(stubRegistrar("t_c",
                List.of(testSpec("devmod.t_c.action1")), Set.of("t_b")));

            assertThrows(IllegalStateException.class, () -> registry.initialize());
        }
    }

    // =========================================================================
    // Duplicate Domain Name Rejection
    // =========================================================================

    @Nested
    @DisplayName("Duplicate Domain Name Rejection")
    class DuplicateNameTests {

        @Test
        @DisplayName("Adding two registrars with same name throws")
        void duplicateNameThrows() {
            registry.addRegistrar(stubRegistrar("dup_name",
                List.of(testSpec("devmod.dup_name.a1")), Set.of()));

            assertThrows(IllegalStateException.class, () ->
                registry.addRegistrar(stubRegistrar("dup_name",
                    List.of(testSpec("devmod.dup_name.a2")), Set.of())));
        }
    }

    // =========================================================================
    // Action ID Conflict Detection
    // =========================================================================

    @Nested
    @DisplayName("Action ID Conflict Detection")
    class ActionIdConflictTests {

        @Test
        @DisplayName("Two domains with same action ID throw on initialize")
        void conflictingActionIdThrows() {
            registry.addRegistrar(stubRegistrar("conflict_a",
                List.of(testSpec("devmod.shared.same_id")), Set.of()));
            registry.addRegistrar(stubRegistrar("conflict_b",
                List.of(testSpec("devmod.shared.same_id")), Set.of()));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.initialize());
            assertTrue(ex.getMessage().contains("conflict") ||
                ex.getMessage().contains("devmod.shared.same_id"),
                "Should mention the conflicting ID: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Initialization Guards
    // =========================================================================

    @Nested
    @DisplayName("Initialization Guards")
    class InitGuardTests {

        @Test
        @DisplayName("Cannot add registrar after initialization")
        void cannotAddAfterInit() {
            registry.addRegistrar(stubRegistrar("guard",
                List.of(testSpec("devmod.guard.action1")), Set.of()));
            registry.initialize();

            assertThrows(IllegalStateException.class, () ->
                registry.addRegistrar(stubRegistrar("late",
                    List.of(testSpec("devmod.late.action1")), Set.of())));
        }

        @Test
        @DisplayName("Cannot initialize twice")
        void cannotInitTwice() {
            registry.addRegistrar(stubRegistrar("once",
                List.of(testSpec("devmod.once.action1")), Set.of()));
            registry.initialize();

            assertThrows(IllegalStateException.class, () -> registry.initialize());
        }

        @Test
        @DisplayName("getCatalog returns unmodifiable copy")
        void catalogUnmodifiable() {
            registry.addRegistrar(stubRegistrar("unmod",
                List.of(testSpec("devmod.unmod.action1")), Set.of()));
            registry.initialize();

            Map<String, ActionSpec> catalog = registry.getCatalog();
            assertThrows(UnsupportedOperationException.class, () ->
                catalog.put("devmod.hack.injected", testSpec("devmod.hack.injected")));
        }
    }

    // =========================================================================
    // getDomain / getAllDomains equivalent
    // =========================================================================

    @Nested
    @DisplayName("Domain Accessors")
    class DomainAccessorTests {

        @Test
        @DisplayName("domainCount reflects registered domains")
        void domainCountReflectsRegistrations() {
            assertEquals(0, registry.domainCount());

            registry.addRegistrar(stubRegistrar("d1",
                List.of(testSpec("devmod.d1.a1")), Set.of()));
            assertEquals(1, registry.domainCount());

            registry.addRegistrar(stubRegistrar("d2",
                List.of(testSpec("devmod.d2.a1")), Set.of()));
            assertEquals(2, registry.domainCount());
        }

        @Test
        @DisplayName("actionCount is zero before initialization")
        void actionCountZeroBeforeInit() {
            registry.addRegistrar(stubRegistrar("pre",
                List.of(testSpec("devmod.pre.a1")), Set.of()));
            assertEquals(0, registry.actionCount(),
                "Actions should not be counted until initialize()");
        }

        @Test
        @DisplayName("actionCount accurate after initialization")
        void actionCountAfterInit() {
            registry.addRegistrar(stubRegistrar("count_a",
                List.of(testSpec("devmod.count_a.a1"), testSpec("devmod.count_a.a2")),
                Set.of()));
            registry.addRegistrar(stubRegistrar("count_b",
                List.of(testSpec("devmod.count_b.a1")), Set.of()));
            registry.initialize();

            assertEquals(3, registry.actionCount());
        }
    }
}
