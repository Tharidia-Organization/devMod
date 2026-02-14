package com.devmod.actions.hardening;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.catalog.BaselineActionInventory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression safety net tests that ensure existing behavior is preserved.
 * These tests guard against accidental action removal, count drift,
 * and whitelist changes.
 */
@DisplayName("Regression Safety Net Tests")
class RegressionSafetyTest {

    private static final Path ACTION_IDS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/ActionIds.java");
    private static final Path ACTION_REGISTRY_PATH = Paths.get(
        "src/main/java/com/devmod/actions/ActionRegistry.java");

    private static String actionIdsSource;
    private static String actionRegistrySource;
    private static Set<String> parsedActionIds;

    @BeforeAll
    static void loadSources() throws IOException {
        actionIdsSource = Files.readString(ACTION_IDS_PATH);
        actionRegistrySource = Files.readString(ACTION_REGISTRY_PATH);
        parsedActionIds = parseActionIdValues(actionIdsSource);
    }

    /**
     * Parse all action ID string values from ActionIds.java.
     */
    private static Set<String> parseActionIdValues(String source) {
        Pattern pattern = Pattern.compile(
            "String\\s+\\w+\\s*=\\s*\"(devmod\\.[^\"]+)\"");
        Matcher matcher = pattern.matcher(source);
        Set<String> ids = new HashSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    // =========================================================================
    // 2. Action Count Stability
    // =========================================================================

    @Nested
    @DisplayName("2. Action Count Stability")
    class ActionCountStabilityTests {

        @Test
        @DisplayName("Exactly 328 action IDs in ActionIds.java (baseline)")
        void actionCountMatchesBaseline() {
            int expectedCount = BaselineActionInventory.EXPECTED_ACTION_ID_COUNT;
            assertEquals(expectedCount, parsedActionIds.size(),
                "Action ID count has drifted from baseline " + expectedCount +
                ". If this is intentional, update BaselineActionInventory.EXPECTED_ACTION_ID_COUNT.");
        }

        @Test
        @DisplayName("No empty action IDs")
        void noEmptyActionIds() {
            for (String id : parsedActionIds) {
                assertFalse(id.isEmpty(), "Found empty action ID");
                assertFalse(id.isBlank(), "Found blank action ID");
            }
        }

        @Test
        @DisplayName("All action IDs start with 'devmod.'")
        void allIdsStartWithDevmod() {
            for (String id : parsedActionIds) {
                assertTrue(id.startsWith("devmod."),
                    "Action ID does not start with 'devmod.': " + id);
            }
        }
    }

    // =========================================================================
    // 3. Client-Safe Count
    // =========================================================================

    @Nested
    @DisplayName("3. Client-Safe Count")
    class ClientSafeCountTests {

        @Test
        @DisplayName("CLIENT_SAFE_ACTIONS whitelist count matches baseline inventory")
        void clientSafeCount() {
            Set<String> expectedClientSafe = BaselineActionInventory.getExpectedClientSafeActionIds();

            // Parse CLIENT_SAFE_ACTIONS from ActionRegistry source
            Set<String> actualClientSafe = parseClientSafeActions(actionRegistrySource);

            assertEquals(expectedClientSafe.size(), actualClientSafe.size(),
                "CLIENT_SAFE_ACTIONS count has drifted. " +
                "Expected: " + expectedClientSafe.size() + ", Actual: " + actualClientSafe.size());
        }

        @Test
        @DisplayName("All expected client-safe IDs are in CLIENT_SAFE_ACTIONS")
        void allExpectedClientSafePresent() {
            Set<String> expectedClientSafe = BaselineActionInventory.getExpectedClientSafeActionIds();
            Set<String> actualClientSafe = parseClientSafeActions(actionRegistrySource);

            for (String expected : expectedClientSafe) {
                // Check if the action ID (as a constant ref or literal) appears in the whitelist
                boolean found = actualClientSafe.stream()
                    .anyMatch(actual -> actual.equals(expected));
                if (!found) {
                    // Fall back to checking if the constant name appears in the whitelist block
                    assertTrue(actionRegistrySource.contains(expected) ||
                        containsConstantRefFor(actionRegistrySource, expected),
                        "Expected client-safe action not found in whitelist: " + expected);
                }
            }
        }

        private Set<String> parseClientSafeActions(String source) {
            // Find the CLIENT_SAFE_ACTIONS block
            int start = source.indexOf("CLIENT_SAFE_ACTIONS = Set.of(");
            if (start < 0) return Set.of();
            int end = source.indexOf(");", start);
            if (end < 0) return Set.of();

            String block = source.substring(start, end);

            // Extract ActionIds.CONSTANT_NAME references
            Pattern constRef = Pattern.compile("ActionIds\\.(\\w+)");
            Matcher matcher = constRef.matcher(block);
            Set<String> refs = new HashSet<>();
            while (matcher.find()) {
                refs.add(matcher.group(1));
            }

            // Map constant names to values by looking them up in actionIdsSource
            Set<String> resolvedIds = new HashSet<>();
            for (String constName : refs) {
                Pattern valPattern = Pattern.compile(
                    constName + "\\s*=\\s*\"(devmod\\.[^\"]+)\"");
                Matcher valMatcher = valPattern.matcher(actionIdsSource);
                if (valMatcher.find()) {
                    resolvedIds.add(valMatcher.group(1));
                }
            }
            return resolvedIds;
        }

        private boolean containsConstantRefFor(String source, String actionId) {
            // Find the constant name in ActionIds for this action ID
            Pattern pattern = Pattern.compile(
                "(\\w+)\\s*=\\s*\"" + Pattern.quote(actionId) + "\"");
            Matcher matcher = pattern.matcher(actionIdsSource);
            if (matcher.find()) {
                String constName = matcher.group(1);
                return source.contains("ActionIds." + constName);
            }
            return false;
        }
    }

    // =========================================================================
    // 4. Category Distribution
    // =========================================================================

    @Nested
    @DisplayName("4. Category Distribution")
    class CategoryDistributionTests {

        @Test
        @DisplayName("Every category has at least one action")
        void everyCategoryHasActions() {
            // Every category domain prefix should have at least one action
            String[] expectedDomains = {
                "devmod.ui", "devmod.ability", "devmod.endurance",
                "devmod.arena", "devmod.debug", "devmod.command",
                "devmod.config", "devmod.telemetry"
            };

            for (String domain : expectedDomains) {
                long count = parsedActionIds.stream()
                    .filter(id -> id.startsWith(domain + "."))
                    .count();
                assertTrue(count > 0,
                    "Expected at least 1 action with prefix '" + domain + "' but found 0");
            }
        }

        @Test
        @DisplayName("Category distribution is stable (verify major categories)")
        void categoryDistributionStable() {
            // Count actions by their second segment (domain)
            Map<String, Integer> domainCounts = new HashMap<>();
            for (String id : parsedActionIds) {
                String[] parts = id.split("\\.");
                if (parts.length >= 3) {
                    String domain = parts[1]; // e.g., "ui", "combat", "arena"
                    domainCounts.merge(domain, 1, Integer::sum);
                }
            }

            System.out.println("[Regression] Category distribution:");
            domainCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));

            // Major categories should have reasonable counts
            assertTrue(domainCounts.getOrDefault("ui", 0) > 10,
                "UI actions should have >10, got: " + domainCounts.get("ui"));
            assertTrue(domainCounts.getOrDefault("arena", 0) > 5,
                "Arena actions should have >5, got: " + domainCounts.get("arena"));
        }

    }

    // =========================================================================
    // 5. Keybind Mapping
    // =========================================================================

    @Nested
    @DisplayName("5. Keybind Mapping")
    class KeybindMappingTests {

        @Test
        @DisplayName("Keybind-related actions exist")
        void keybindActionsExist() {
            // Check that key action IDs that should have keybindings exist
            Set<String> keybindActions = parsedActionIds.stream()
                .filter(id -> id.contains("keybind") ||
                    id.contains("dash") ||
                    id.contains("dodge") ||
                    id.contains("radial.open"))
                .collect(Collectors.toSet());

            assertFalse(keybindActions.isEmpty(),
                "Expected keybind-related actions to exist");
        }
    }

    // =========================================================================
    // 6. No Removed Actions
    // =========================================================================

    @Nested
    @DisplayName("6. No Removed Actions")
    class NoRemovedActionsTests {

        @Test
        @DisplayName("All baseline client-safe actions still exist in ActionIds")
        void clientSafeActionsStillExist() {
            Set<String> expectedClientSafe = BaselineActionInventory.getExpectedClientSafeActionIds();

            for (String clientSafeId : expectedClientSafe) {
                assertTrue(parsedActionIds.contains(clientSafeId),
                    "Client-safe action removed from ActionIds: " + clientSafeId);
            }
        }

        @Test
        @DisplayName("No action IDs have been silently renamed")
        void noSilentRenames() {
            // Check that critical action IDs still exist
            String[] criticalIds = {
                "devmod.ui.radial.open",
                "devmod.ui.settings.open",
                "devmod.ability.dash",
                "devmod.ability.dodge",
                "devmod.endurance.quest.start",
                "devmod.endurance.quest.continue",
                "devmod.endurance.quest.exit"
            };

            for (String id : criticalIds) {
                assertTrue(parsedActionIds.contains(id),
                    "Critical action ID missing or renamed: " + id);
            }
        }
    }

    // =========================================================================
    // Origin Trust Model
    // =========================================================================

    @Nested
    @DisplayName("Origin Trust Model Regression")
    class OriginTrustModelTests {

        @Test
        @DisplayName("Trust model has not changed (5 trusted, 2 untrusted)")
        void trustModelUnchanged() {
            int trusted = 0;
            int untrusted = 0;
            for (ActionOrigin origin : ActionOrigin.values()) {
                if (origin.isTrusted()) trusted++;
                else untrusted++;
            }

            assertEquals(5, trusted, "Trusted origin count changed");
            assertEquals(2, untrusted, "Untrusted origin count changed");
            assertEquals(7, ActionOrigin.values().length, "Total origin count changed");
        }

        @Test
        @DisplayName("ActionOrigin enum has exactly 7 values")
        void originEnumSize() {
            assertEquals(7, ActionOrigin.values().length,
                "ActionOrigin enum has changed size - review trust boundaries");
        }
    }
}
