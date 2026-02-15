package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.catalog.ActionSpec;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-domain validation tests. Instantiates ALL domain registrars and validates
 * that their action specs are consistent, unique, and well-formed.
 *
 * <p>These tests call only {@code getActionSpecs()} (pure spec metadata),
 * NOT {@code registerHandlers()} (which references Minecraft runtime classes).
 */
@DisplayName("Cross-Domain Validation Tests")
class CrossDomainValidationTest {

    /** All domain registrar instances. */
    private static final List<DomainRegistrar> ALL_REGISTRARS = List.of(
        new UiDomainRegistrar(),
        new GameplayDomainRegistrar(),
        new ArenaDomainRegistrar(),
        new CommandDomainRegistrar(),
        new ConfigDomainRegistrar(),
        new DebugDomainRegistrar(),
        new TelemetryDomainRegistrar(),
        new TestingDomainRegistrar(),
        new AdminDomainRegistrar()
    );

    /** All specs from all registrars, collected once. */
    private static List<ActionSpec> allSpecs;
    /** Map from domain name to its specs. */
    private static Map<String, List<ActionSpec>> specsByDomain;

    @BeforeAll
    static void collectAllSpecs() {
        allSpecs = new ArrayList<>();
        specsByDomain = new HashMap<>();

        for (DomainRegistrar registrar : ALL_REGISTRARS) {
            List<ActionSpec> specs = registrar.getActionSpecs();
            allSpecs.addAll(specs);
            specsByDomain.put(registrar.domainName(), specs);
        }

        System.out.println("[CrossDomain] Collected " + allSpecs.size() +
            " specs from " + ALL_REGISTRARS.size() + " domains");
    }

    // =========================================================================
    // 1. No Duplicate IDs Across ALL Domains
    // =========================================================================

    @Nested
    @DisplayName("1. No Duplicate IDs Across Domains")
    class NoDuplicateIdsTests {

        @Test
        @DisplayName("No action ID appears in more than one domain")
        void noDuplicateIdsAcrossDomains() {
            Map<String, String> idToDomain = new HashMap<>();
            List<String> duplicates = new ArrayList<>();

            for (DomainRegistrar registrar : ALL_REGISTRARS) {
                for (ActionSpec spec : registrar.getActionSpecs()) {
                    String existing = idToDomain.put(spec.id(), registrar.domainName());
                    if (existing != null) {
                        duplicates.add(spec.id() + " (in '" + existing +
                            "' and '" + registrar.domainName() + "')");
                    }
                }
            }

            assertTrue(duplicates.isEmpty(),
                "Duplicate action IDs found across domains: " + duplicates);
        }

        @Test
        @DisplayName("No duplicate IDs within any single domain")
        void noDuplicateIdsWithinDomain() {
            for (DomainRegistrar registrar : ALL_REGISTRARS) {
                Set<String> seen = new HashSet<>();
                List<String> dups = new ArrayList<>();

                for (ActionSpec spec : registrar.getActionSpecs()) {
                    if (!seen.add(spec.id())) {
                        dups.add(spec.id());
                    }
                }

                assertTrue(dups.isEmpty(),
                    "Duplicate IDs in domain '" + registrar.domainName() + "': " + dups);
            }
        }
    }

    // =========================================================================
    // 2. Every Domain Has Unique Name
    // =========================================================================

    @Nested
    @DisplayName("2. Unique Domain Names")
    class UniqueDomainNameTests {

        @Test
        @DisplayName("All domain registrars have unique names")
        void allDomainsHaveUniqueNames() {
            Set<String> names = new HashSet<>();
            List<String> dups = new ArrayList<>();

            for (DomainRegistrar registrar : ALL_REGISTRARS) {
                if (!names.add(registrar.domainName())) {
                    dups.add(registrar.domainName());
                }
            }

            assertTrue(dups.isEmpty(), "Duplicate domain names: " + dups);
        }

        @Test
        @DisplayName("All domain names are non-null and non-empty")
        void allDomainNamesValid() {
            for (DomainRegistrar registrar : ALL_REGISTRARS) {
                String name = registrar.domainName();
                assertNotNull(name, "Domain name is null for " + registrar.getClass().getSimpleName());
                assertFalse(name.isEmpty(), "Domain name is empty for " + registrar.getClass().getSimpleName());
            }
        }

        @Test
        @DisplayName("Expected domain names are present")
        void expectedDomainsPresent() {
            Set<String> names = new HashSet<>();
            for (DomainRegistrar r : ALL_REGISTRARS) {
                names.add(r.domainName());
            }

            Set<String> expected = Set.of(
                "ui", "gameplay", "arena", "commands", "config",
                "debug", "telemetry", "testing", "admin");

            for (String exp : expected) {
                assertTrue(names.contains(exp),
                    "Expected domain '" + exp + "' not found");
            }
        }
    }

    // =========================================================================
    // 3. All Specs Have Valid Fields
    // =========================================================================

    @Nested
    @DisplayName("3. Spec Field Validation")
    class SpecFieldValidationTests {

        @Test
        @DisplayName("All spec IDs follow naming convention (devmod.domain.name)")
        void allIdsFollowConvention() {
            for (ActionSpec spec : allSpecs) {
                assertTrue(spec.id().startsWith("devmod."),
                    "Spec ID does not start with 'devmod.': " + spec.id());
                assertTrue(spec.id().contains("."),
                    "Spec ID has no dot-separated segments: " + spec.id());
                // ActionSpec validates pattern in its constructor, so if we got here it's valid
            }
        }

        @Test
        @DisplayName("All specs have non-null category")
        void allSpecsHaveCategory() {
            for (ActionSpec spec : allSpecs) {
                assertNotNull(spec.category(), "Null category for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-null channel")
        void allSpecsHaveChannel() {
            for (ActionSpec spec : allSpecs) {
                assertNotNull(spec.channel(), "Null channel for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-null uiMeta with label and desc")
        void allSpecsHaveUiMeta() {
            for (ActionSpec spec : allSpecs) {
                assertNotNull(spec.uiMeta(), "Null uiMeta for: " + spec.id());
                assertNotNull(spec.uiMeta().labelKey(),
                    "Null labelKey for: " + spec.id());
                assertNotNull(spec.uiMeta().descKey(),
                    "Null descKey for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have permission level 0-4")
        void allSpecsHaveValidPermission() {
            for (ActionSpec spec : allSpecs) {
                int perm = spec.permissionLevel();
                assertTrue(perm >= 0 && perm <= 4,
                    "Invalid permission level " + perm + " for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-empty allowedOrigins")
        void allSpecsHaveOrigins() {
            for (ActionSpec spec : allSpecs) {
                assertNotNull(spec.allowedOrigins(),
                    "Null allowedOrigins for: " + spec.id());
                assertFalse(spec.allowedOrigins().isEmpty(),
                    "Empty allowedOrigins for: " + spec.id());
            }
        }

        @Test
        @DisplayName("CLIENT channel specs do not allow NETWORK origin")
        void clientSpecsNoNetwork() {
            for (ActionSpec spec : allSpecs) {
                if (spec.channel() == ActionSpec.ActionChannel.CLIENT) {
                    assertFalse(spec.allowsOrigin(ActionOrigin.NETWORK),
                        "CLIENT spec allows NETWORK: " + spec.id());
                }
            }
        }

        @Test
        @DisplayName("All specs have non-null policyMeta")
        void allSpecsHavePolicyMeta() {
            for (ActionSpec spec : allSpecs) {
                assertNotNull(spec.policyMeta(), "Null policyMeta for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-null telemetryMeta")
        void allSpecsHaveTelemetryMeta() {
            for (ActionSpec spec : allSpecs) {
                assertNotNull(spec.telemetryMeta(),
                    "Null telemetryMeta for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-null bindingMeta")
        void allSpecsHaveBindingMeta() {
            for (ActionSpec spec : allSpecs) {
                assertNotNull(spec.bindingMeta(),
                    "Null bindingMeta for: " + spec.id());
            }
        }
    }

    // =========================================================================
    // 4. Total Action Count Across All Domains
    // =========================================================================

    @Nested
    @DisplayName("4. Total Action Count")
    class TotalActionCountTests {

        @Test
        @DisplayName("All registrars produce at least one action spec")
        void allRegistrarsHaveSpecs() {
            for (DomainRegistrar registrar : ALL_REGISTRARS) {
                List<ActionSpec> specs = registrar.getActionSpecs();
                assertFalse(specs.isEmpty(),
                    "Domain '" + registrar.domainName() + "' has no action specs");
            }
        }

        @Test
        @DisplayName("Total spec count is positive")
        void totalCountPositive() {
            assertTrue(allSpecs.size() > 0, "Total spec count should be > 0");
        }

        @Test
        @DisplayName("Total action count equals 319 (baseline)")
        void totalActionCountEquals319() {
            assertEquals(319, allSpecs.size(),
                "Total action count should be 319 across all 9 domains");
        }

        @Test
        @DisplayName("Per-domain action counts match expected baseline")
        void perDomainActionCounts() {
            Map<String, Integer> expected = Map.of(
                "gameplay", 7,
                "arena", 19,
                "testing", 26,
                "telemetry", 27,
                "admin", 22,
                "ui", 65,
                "debug", 62,
                "config", 58,
                "commands", 33
            );

            for (DomainRegistrar registrar : ALL_REGISTRARS) {
                int count = registrar.getActionSpecs().size();
                int expectedCount = expected.getOrDefault(registrar.domainName(), -1);
                assertEquals(expectedCount, count,
                    "Domain '" + registrar.domainName() + "' should have " + expectedCount +
                    " actions but has " + count);
            }
        }

        @Test
        @DisplayName("Print domain-by-domain action count summary")
        void printDomainSummary() {
            System.out.println("\n=== DOMAIN ACTION COUNT SUMMARY ===");
            int total = 0;
            for (DomainRegistrar registrar : ALL_REGISTRARS) {
                int count = registrar.getActionSpecs().size();
                System.out.printf("  %-15s %d actions%n", registrar.domainName(), count);
                total += count;
            }
            System.out.println("  " + "-".repeat(30));
            System.out.printf("  %-15s %d actions%n", "TOTAL", total);

            assertEquals(allSpecs.size(), total);
        }
    }

    // =========================================================================
    // 5. No Handler Conflicts (spec-level check)
    // =========================================================================

    @Nested
    @DisplayName("5. No Handler Conflicts")
    class NoHandlerConflictTests {

        @Test
        @DisplayName("DomainRegistry validates no ID conflicts when all domains added")
        void domainRegistryValidatesNoConflicts() {
            DomainRegistry registry = new DomainRegistry();

            // Use stub registrars that provide the same specs but no-op handlers
            // to avoid Minecraft runtime dependencies
            for (DomainRegistrar original : ALL_REGISTRARS) {
                List<ActionSpec> specs = original.getActionSpecs();
                String name = original.domainName();
                Set<String> deps = original.getDependencies();

                registry.addRegistrar(new DomainRegistrar() {
                    @Override
                    public String domainName() { return name; }

                    @Override
                    public List<ActionSpec> getActionSpecs() { return specs; }

                    @Override
                    public void registerHandlers(HandlerRegistry handlerRegistry) {
                        // Register no-op handlers to avoid Minecraft class loading
                        for (ActionSpec spec : specs) {
                            handlerRegistry.register(spec.id(), ctx -> {});
                        }
                    }

                    @Override
                    public Set<String> getDependencies() { return deps; }
                });
            }

            // This should succeed without throwing
            assertDoesNotThrow(() -> registry.initialize(),
                "DomainRegistry should initialize without ID conflicts");

            assertEquals(ALL_REGISTRARS.size(), registry.domainCount());
            assertEquals(allSpecs.size(), registry.actionCount());
        }
    }

    // =========================================================================
    // 6. Category Coverage
    // =========================================================================

    @Nested
    @DisplayName("6. Category Coverage")
    class CategoryCoverageTests {

        @Test
        @DisplayName("All major categories are represented across domains")
        void majorCategoriesCovered() {
            Set<ActionCategory> usedCategories = new HashSet<>();
            for (ActionSpec spec : allSpecs) {
                usedCategories.add(spec.category());
            }

            System.out.println("[CrossDomain] Categories used: " + usedCategories);

            // These categories should have at least one action across all domains
            ActionCategory[] expected = {
                ActionCategory.UI, ActionCategory.COMBAT, ActionCategory.ARENA,
                ActionCategory.ADMIN, ActionCategory.DEBUG, ActionCategory.CONFIG
            };

            for (ActionCategory cat : expected) {
                assertTrue(usedCategories.contains(cat),
                    "Expected category " + cat + " to have at least one action");
            }
        }
    }

    // =========================================================================
    // 7. Dependency Graph Validity
    // =========================================================================

    @Nested
    @DisplayName("7. Dependency Graph Validity")
    class DependencyGraphTests {

        @Test
        @DisplayName("All declared dependencies reference existing domains")
        void allDependenciesExist() {
            Set<String> domainNames = new HashSet<>();
            for (DomainRegistrar r : ALL_REGISTRARS) {
                domainNames.add(r.domainName());
            }

            for (DomainRegistrar r : ALL_REGISTRARS) {
                for (String dep : r.getDependencies()) {
                    assertTrue(domainNames.contains(dep),
                        "Domain '" + r.domainName() + "' depends on unknown domain '" + dep + "'");
                }
            }
        }

        @Test
        @DisplayName("No domain depends on itself")
        void noSelfDependency() {
            for (DomainRegistrar r : ALL_REGISTRARS) {
                assertFalse(r.getDependencies().contains(r.domainName()),
                    "Domain '" + r.domainName() + "' depends on itself");
            }
        }
    }
}
