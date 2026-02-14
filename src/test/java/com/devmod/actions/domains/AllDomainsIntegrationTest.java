package com.devmod.actions.domains;

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
import com.devmod.actions.catalog.ActionSpec;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that loads ALL domain registrars and validates
 * cross-domain invariants: no duplicate IDs, handler completeness,
 * and catalog consistency.
 */
@DisplayName("All Domains Integration Test")
class AllDomainsIntegrationTest {

    private static DomainRegistry domainRegistry;
    private static List<DomainRegistrar> allRegistrars;

    @BeforeAll
    static void setUp() {
        allRegistrars = List.of(
            new GameplayDomainRegistrar(),
            new ArenaDomainRegistrar(),
            new TestingDomainRegistrar(),
            new TelemetryDomainRegistrar(),
            new AdminDomainRegistrar()
        );

        domainRegistry = new DomainRegistry();
        for (DomainRegistrar registrar : allRegistrars) {
            domainRegistry.addRegistrar(registrar);
        }
        domainRegistry.initialize();
    }

    @Nested
    @DisplayName("Domain Count")
    class DomainCountTests {

        @Test
        @DisplayName("All 5 batch-2 domains are registered")
        void fiveDomains() {
            assertEquals(5, domainRegistry.domainCount());
        }
    }

    @Nested
    @DisplayName("Action Count")
    class ActionCountTests {

        @Test
        @DisplayName("Total action count across all batch-2 domains")
        void totalActionCount() {
            int total = domainRegistry.actionCount();
            // Gameplay: 7, Arena: 19, Testing: 26, Telemetry: 27, Admin: 29 = 108
            assertTrue(total >= 100,
                "Should have at least 100 actions across batch-2 domains. Found: " + total);
        }

        @Test
        @DisplayName("Each domain contributes expected action count")
        void perDomainActionCount() {
            Map<String, Integer> expected = Map.of(
                "gameplay", 7,
                "arena", 19,
                "testing", 26,
                "telemetry", 27,
                "admin", 22
            );

            for (DomainRegistrar registrar : allRegistrars) {
                int count = registrar.getActionSpecs().size();
                int expectedCount = expected.getOrDefault(registrar.domainName(), -1);
                assertEquals(expectedCount, count,
                    "Domain '" + registrar.domainName() + "' should have " + expectedCount +
                    " actions but has " + count);
            }
        }
    }

    @Nested
    @DisplayName("No Duplicate IDs")
    class NoDuplicateIdsTests {

        @Test
        @DisplayName("No duplicate action IDs across all domains")
        void noDuplicateIds() {
            Set<String> seen = new HashSet<>();
            Map<String, String> duplicates = new HashMap<>();

            for (DomainRegistrar registrar : allRegistrars) {
                for (ActionSpec spec : registrar.getActionSpecs()) {
                    if (!seen.add(spec.id())) {
                        duplicates.put(spec.id(), registrar.domainName());
                    }
                }
            }

            assertTrue(duplicates.isEmpty(),
                "Found duplicate action IDs across domains: " + duplicates);
        }
    }

    @Nested
    @DisplayName("Handler Registry Completeness")
    class HandlerRegistryTests {

        @Test
        @DisplayName("Every action spec has a registered handler")
        void everySpecHasHandler() {
            HandlerRegistry handlerRegistry = domainRegistry.getHandlerRegistry();

            for (Map.Entry<String, ActionSpec> entry : domainRegistry.getCatalog().entrySet()) {
                assertTrue(handlerRegistry.hasHandler(entry.getKey()),
                    "Handler missing for action: " + entry.getKey());
            }
        }

        @Test
        @DisplayName("Handler count matches action count")
        void handlerCountMatchesActionCount() {
            assertEquals(domainRegistry.actionCount(),
                domainRegistry.getHandlerRegistry().size());
        }
    }

    @Nested
    @DisplayName("Catalog Validation")
    class CatalogValidationTests {

        @Test
        @DisplayName("All specs have valid action IDs")
        void allSpecsHaveValidIds() {
            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                assertTrue(spec.id().startsWith("devmod."),
                    "Action ID should start with 'devmod.': " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-empty label keys")
        void allSpecsHaveLabelKeys() {
            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                assertNotNull(spec.uiMeta().labelKey(),
                    "Label key should not be null for: " + spec.id());
                assertFalse(spec.uiMeta().labelKey().isEmpty(),
                    "Label key should not be empty for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-empty description keys")
        void allSpecsHaveDescKeys() {
            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                assertNotNull(spec.uiMeta().descKey(),
                    "Desc key should not be null for: " + spec.id());
                assertFalse(spec.uiMeta().descKey().isEmpty(),
                    "Desc key should not be empty for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have a menu path")
        void allSpecsHaveMenuPath() {
            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                assertNotNull(spec.uiMeta().menuPath(),
                    "Menu path should not be null for: " + spec.id());
                assertTrue(spec.uiMeta().menuPath().startsWith("Root/"),
                    "Menu path should start with 'Root/' for: " + spec.id());
            }
        }

        @Test
        @DisplayName("Permission levels are in valid range (0-4)")
        void permissionLevelsValid() {
            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                assertTrue(spec.permissionLevel() >= 0 && spec.permissionLevel() <= 4,
                    "Permission level should be 0-4 for: " + spec.id() +
                    " but was " + spec.permissionLevel());
            }
        }
    }

    @Nested
    @DisplayName("Category Distribution")
    class CategoryDistributionTests {

        @Test
        @DisplayName("Multiple categories are represented")
        void multipleCategoriesRepresented() {
            Set<ActionCategory> categories = new HashSet<>();
            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                categories.add(spec.category());
            }

            assertTrue(categories.size() >= 5,
                "Should have at least 5 different categories. Found: " + categories);
            assertTrue(categories.contains(ActionCategory.COMBAT));
            assertTrue(categories.contains(ActionCategory.ENDURANCE));
            assertTrue(categories.contains(ActionCategory.ARENA));
            assertTrue(categories.contains(ActionCategory.TELEMETRY));
            assertTrue(categories.contains(ActionCategory.TESTING));
        }
    }

    @Nested
    @DisplayName("Dependency Resolution")
    class DependencyResolutionTests {

        @Test
        @DisplayName("DomainRegistry initializes without cycle errors")
        void noCycles() {
            // The @BeforeAll already initialized - if we get here, no cycles
            assertTrue(domainRegistry.actionCount() > 0);
        }
    }
}
