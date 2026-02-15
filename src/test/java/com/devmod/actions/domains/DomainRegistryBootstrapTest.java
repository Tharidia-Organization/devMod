package com.devmod.actions.domains;

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
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DomainRegistryBootstrap: full initialization of all 9 domains
 * (batch 1 + batch 2), cross-domain ID uniqueness, handler completeness,
 * and catalog validation.
 */
@DisplayName("DomainRegistryBootstrap")
class DomainRegistryBootstrapTest {

    private static DomainRegistry domainRegistry;

    @BeforeAll
    static void setUp() {
        domainRegistry = DomainRegistryBootstrap.initialize();
    }

    @Nested
    @DisplayName("Domain Count")
    class DomainCountTests {

        @Test
        @DisplayName("All 9 domains are registered")
        void nineDomains() {
            assertEquals(9, domainRegistry.domainCount());
        }
    }

    @Nested
    @DisplayName("Action Count")
    class ActionCountTests {

        @Test
        @DisplayName("Total action count across all 9 domains")
        void totalActionCount() {
            // Batch 1: Gameplay(7) + Arena(19) + Testing(26) + Telemetry(27) + Admin(22) = 101
            // Batch 2: UI(65) + Debug(62) + Config(58) + Commands(33) = 218
            // Total = 319
            int total = domainRegistry.actionCount();
            assertTrue(total >= 300,
                "Should have at least 300 actions across all 9 domains. Found: " + total);
        }

        @Test
        @DisplayName("Batch 2 domains contribute expected counts")
        void batch2ActionCounts() {
            List<DomainRegistrar> batch2 = List.of(
                new UiDomainRegistrar(),
                new DebugDomainRegistrar(),
                new ConfigDomainRegistrar(),
                new CommandDomainRegistrar()
            );
            Map<String, Integer> expected = Map.of(
                "ui", 65,
                "debug", 62,
                "config", 58,
                "commands", 33
            );

            for (DomainRegistrar registrar : batch2) {
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
        @DisplayName("No duplicate action IDs across all 9 domains")
        void noDuplicateIds() {
            Set<String> seen = new HashSet<>();
            Set<String> duplicates = new HashSet<>();

            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                if (!seen.add(spec.id())) {
                    duplicates.add(spec.id());
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
        @DisplayName("All specs have valid action IDs starting with devmod.")
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
                assertNotNull(spec.uiMeta().labelKey());
                assertFalse(spec.uiMeta().labelKey().isEmpty(),
                    "Label key should not be empty for: " + spec.id());
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
    @DisplayName("Channel Distribution")
    class ChannelDistributionTests {

        @Test
        @DisplayName("Has both CLIENT and SERVER channel actions")
        void hasBothChannels() {
            boolean hasClient = false;
            boolean hasServer = false;
            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                if (spec.channel() == ActionChannel.CLIENT) hasClient = true;
                if (spec.channel() == ActionChannel.SERVER) hasServer = true;
            }
            assertTrue(hasClient, "Should have CLIENT channel actions");
            assertTrue(hasServer, "Should have SERVER channel actions");
        }
    }

    @Nested
    @DisplayName("Category Distribution")
    class CategoryDistributionTests {

        @Test
        @DisplayName("All expected categories are represented")
        void allCategoriesRepresented() {
            Set<ActionCategory> categories = new HashSet<>();
            for (ActionSpec spec : domainRegistry.getCatalog().values()) {
                categories.add(spec.category());
            }

            assertTrue(categories.contains(ActionCategory.UI),
                "Should have UI category");
            assertTrue(categories.contains(ActionCategory.DEBUG),
                "Should have DEBUG category");
            assertTrue(categories.contains(ActionCategory.CONFIG),
                "Should have CONFIG category");
            assertTrue(categories.contains(ActionCategory.TOOLS),
                "Should have TOOLS category");
            assertTrue(categories.contains(ActionCategory.COMBAT),
                "Should have COMBAT category");
            assertTrue(categories.contains(ActionCategory.ENDURANCE),
                "Should have ENDURANCE category");
            assertTrue(categories.contains(ActionCategory.ARENA),
                "Should have ARENA category");
            assertTrue(categories.contains(ActionCategory.TELEMETRY),
                "Should have TELEMETRY category");
            assertTrue(categories.contains(ActionCategory.TESTING),
                "Should have TESTING category");
        }
    }

    @Nested
    @DisplayName("Dependency Resolution")
    class DependencyResolutionTests {

        @Test
        @DisplayName("DomainRegistryBootstrap initializes without cycle errors")
        void noCycles() {
            // The @BeforeAll already initialized - if we get here, no cycles
            assertTrue(domainRegistry.actionCount() > 0);
        }

        @Test
        @DisplayName("createAllRegistrars returns 9 registrars")
        void createAllRegistrarsCount() {
            List<DomainRegistrar> all = DomainRegistryBootstrap.createAllRegistrars();
            assertEquals(9, all.size());
        }
    }

    @Nested
    @DisplayName("Subset Initialization")
    class SubsetInitializationTests {

        @Test
        @DisplayName("Can initialize with a subset of registrars")
        void subsetInitialization() {
            List<DomainRegistrar> subset = List.of(
                new UiDomainRegistrar(),
                new DebugDomainRegistrar()
            );
            DomainRegistry subsetRegistry = DomainRegistryBootstrap.initialize(subset);
            assertEquals(2, subsetRegistry.domainCount());
            assertEquals(65 + 62, subsetRegistry.actionCount());
        }
    }
}
