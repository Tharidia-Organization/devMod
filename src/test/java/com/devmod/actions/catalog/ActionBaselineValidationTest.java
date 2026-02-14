package com.devmod.actions.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the action baseline inventory against source code.
 *
 * <p>This test operates purely at the source-code level (no Minecraft runtime),
 * following the same pattern as {@code RadialOrphanCheckTest}.
 *
 * <p>Checks performed:
 * <ul>
 *   <li>Action ID count matches expected baseline</li>
 *   <li>Naming convention: all IDs start with "devmod." and use dot-separated segments</li>
 *   <li>No duplicate action ID values</li>
 *   <li>Every ActionId constant appears in at least one registration file</li>
 *   <li>CLIENT_SAFE_ACTIONS whitelist matches expected set</li>
 *   <li>CSV report generation (ground-truth snapshot)</li>
 *   <li>Category inference coverage (no MISC fallbacks)</li>
 * </ul>
 */
@DisplayName("Action Baseline Validation")
class ActionBaselineValidationTest {

    private static Map<String, BaselineActionInventory.InventoryEntry> inventory;
    private static String actionIdsSource;

    // All registration source files
    private static String devModActionsSource;
    private static String devModClientActionsSource;
    private static String arenaActionRegistrySource;
    private static String debugCommandSource;
    private static String telemetryReloadCommandSource;
    private static String dashboardCommandSource;
    private static String dungeonCommandSource;
    private static String testHarnessCommandsSource;
    private static String mailboxCommandsSource;
    private static String leaderboardCommandEventsSource;
    private static String clientUIActionsSource;
    private static String clientDebugActionsSource;
    private static String clientConfigActionsSource;
    private static String clientGameplayActionsSource;

    // Source for CLIENT_SAFE_ACTIONS extraction
    private static String actionRegistrySource;

    private static final Path ACTION_IDS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/ActionIds.java");
    private static final Path DEVMOD_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/DevModActions.java");
    private static final Path DEVMOD_CLIENT_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/DevModClientActions.java");
    private static final Path ARENA_ACTION_REGISTRY_PATH = Paths.get(
        "src/main/java/com/devmod/arena/command/ArenaActionRegistry.java");
    private static final Path DEBUG_COMMAND_PATH = Paths.get(
        "src/main/java/com/devmod/debug/DebugCommand.java");
    private static final Path TELEMETRY_RELOAD_COMMAND_PATH = Paths.get(
        "src/main/java/com/devmod/telemetry/TelemetryReloadCommand.java");
    private static final Path DASHBOARD_COMMAND_PATH = Paths.get(
        "src/main/java/com/devmod/telemetry/dashboard/DashboardCommand.java");
    private static final Path DUNGEON_COMMAND_PATH = Paths.get(
        "src/main/java/com/devmod/telemetry/dungeon/DungeonCommand.java");
    private static final Path TEST_HARNESS_COMMANDS_PATH = Paths.get(
        "src/main/java/com/devmod/gametest/TestHarnessCommands.java");
    private static final Path MAILBOX_COMMANDS_PATH = Paths.get(
        "src/main/java/com/devmod/mailbox/admin/MailboxCommands.java");
    private static final Path LEADERBOARD_COMMAND_EVENTS_PATH = Paths.get(
        "src/main/java/com/devmod/endurance/LeaderboardCommandEvents.java");
    private static final Path ACTION_REGISTRY_PATH = Paths.get(
        "src/main/java/com/devmod/actions/ActionRegistry.java");
    private static final Path CLIENT_UI_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ClientUIActions.java");
    private static final Path CLIENT_DEBUG_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ClientDebugActions.java");
    private static final Path CLIENT_CONFIG_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ClientConfigActions.java");
    private static final Path CLIENT_GAMEPLAY_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ClientGameplayActions.java");

    /** Regex to extract ActionIds.FIELD_NAME references from registration files. */
    private static final Pattern ACTION_ID_REF_PATTERN = Pattern.compile(
        "ActionIds\\.(\\w+)");

    /** Regex to extract field declarations from ActionIds.java */
    private static final Pattern ACTION_ID_FIELD_PATTERN = Pattern.compile(
        "public\\s+static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"");

    @BeforeAll
    static void loadSources() throws IOException {
        inventory = BaselineActionInventory.loadInventory(ACTION_IDS_PATH);
        actionIdsSource = Files.readString(ACTION_IDS_PATH);
        devModActionsSource = Files.readString(DEVMOD_ACTIONS_PATH);
        devModClientActionsSource = Files.readString(DEVMOD_CLIENT_ACTIONS_PATH);
        arenaActionRegistrySource = Files.readString(ARENA_ACTION_REGISTRY_PATH);
        debugCommandSource = Files.readString(DEBUG_COMMAND_PATH);
        telemetryReloadCommandSource = Files.readString(TELEMETRY_RELOAD_COMMAND_PATH);
        dashboardCommandSource = Files.readString(DASHBOARD_COMMAND_PATH);
        dungeonCommandSource = Files.readString(DUNGEON_COMMAND_PATH);
        testHarnessCommandsSource = Files.readString(TEST_HARNESS_COMMANDS_PATH);
        mailboxCommandsSource = Files.readString(MAILBOX_COMMANDS_PATH);
        leaderboardCommandEventsSource = Files.readString(LEADERBOARD_COMMAND_EVENTS_PATH);
        clientUIActionsSource = Files.readString(CLIENT_UI_ACTIONS_PATH);
        clientDebugActionsSource = Files.readString(CLIENT_DEBUG_ACTIONS_PATH);
        clientConfigActionsSource = Files.readString(CLIENT_CONFIG_ACTIONS_PATH);
        clientGameplayActionsSource = Files.readString(CLIENT_GAMEPLAY_ACTIONS_PATH);
        actionRegistrySource = Files.readString(ACTION_REGISTRY_PATH);
    }

    @Nested
    @DisplayName("Count Validation")
    class CountValidation {

        @Test
        @DisplayName("ActionIds.java declares expected number of constants")
        void actionIdCountMatchesBaseline() {
            assertEquals(BaselineActionInventory.EXPECTED_ACTION_ID_COUNT, inventory.size(),
                "ActionIds.java constant count changed. Update EXPECTED_ACTION_ID_COUNT if intentional.");
        }

        @Test
        @DisplayName("Inventory loads all constants from ActionIds.java")
        void inventoryLoadsAllConstants() {
            // Count fields directly from source
            Matcher matcher = ACTION_ID_FIELD_PATTERN.matcher(actionIdsSource);
            int sourceCount = 0;
            while (matcher.find()) {
                sourceCount++;
            }
            assertEquals(sourceCount, inventory.size(),
                "Inventory missed some ActionIds constants");
        }
    }

    @Nested
    @DisplayName("Naming Convention")
    class NamingConvention {

        @Test
        @DisplayName("All action IDs start with 'devmod.' prefix")
        void allIdsHaveDevmodPrefix() {
            List<String> violations = inventory.keySet().stream()
                .filter(id -> !id.startsWith("devmod."))
                .collect(Collectors.toList());

            assertTrue(violations.isEmpty(),
                "Action IDs missing 'devmod.' prefix: " + violations);
        }

        @Test
        @DisplayName("All action IDs use dot-separated lowercase segments")
        void allIdsUseDotSeparatedFormat() {
            Pattern validId = Pattern.compile("^devmod(\\.[a-z][a-z0-9_]*)+$");
            List<String> violations = inventory.keySet().stream()
                .filter(id -> !validId.matcher(id).matches())
                .collect(Collectors.toList());

            assertTrue(violations.isEmpty(),
                "Action IDs with invalid format: " + violations);
        }

        @Test
        @DisplayName("Field names are UPPER_SNAKE_CASE")
        void fieldNamesAreUpperSnakeCase() {
            Pattern upperSnake = Pattern.compile("^[A-Z][A-Z0-9_]*$");
            List<String> violations = inventory.values().stream()
                .map(BaselineActionInventory.InventoryEntry::fieldName)
                .filter(name -> !upperSnake.matcher(name).matches())
                .collect(Collectors.toList());

            assertTrue(violations.isEmpty(),
                "Field names not UPPER_SNAKE_CASE: " + violations);
        }
    }

    @Nested
    @DisplayName("No Duplicates")
    class NoDuplicates {

        @Test
        @DisplayName("No duplicate action ID values")
        void noDuplicateActionIdValues() {
            Matcher matcher = ACTION_ID_FIELD_PATTERN.matcher(actionIdsSource);
            Map<String, List<String>> valueToFields = new java.util.HashMap<>();
            while (matcher.find()) {
                String field = matcher.group(1);
                String value = matcher.group(2);
                valueToFields.computeIfAbsent(value, k -> new ArrayList<>()).add(field);
            }

            List<String> duplicates = valueToFields.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " -> " + e.getValue())
                .collect(Collectors.toList());

            assertTrue(duplicates.isEmpty(),
                "Duplicate action ID values found: " + duplicates);
        }

        @Test
        @DisplayName("No duplicate field names")
        void noDuplicateFieldNames() {
            Matcher matcher = ACTION_ID_FIELD_PATTERN.matcher(actionIdsSource);
            Set<String> seen = new HashSet<>();
            List<String> duplicates = new ArrayList<>();
            while (matcher.find()) {
                String field = matcher.group(1);
                if (!seen.add(field)) {
                    duplicates.add(field);
                }
            }

            assertTrue(duplicates.isEmpty(),
                "Duplicate field names in ActionIds: " + duplicates);
        }
    }

    @Nested
    @DisplayName("Registration Coverage")
    class RegistrationCoverage {

        @Test
        @DisplayName("Every ActionIds constant is referenced in at least one registration file")
        void everyActionIdIsRegistered() {
            // Collect all ActionIds.FIELD references across all registration files
            // DevModClientActions delegates to sub-classes, so include them too
            String allRegistrationSources = String.join("\n",
                devModActionsSource,
                devModClientActionsSource,
                clientUIActionsSource,
                clientDebugActionsSource,
                clientConfigActionsSource,
                clientGameplayActionsSource,
                arenaActionRegistrySource,
                debugCommandSource,
                telemetryReloadCommandSource,
                dashboardCommandSource,
                dungeonCommandSource,
                testHarnessCommandsSource,
                mailboxCommandsSource,
                leaderboardCommandEventsSource
            );

            Set<String> referencedFields = new HashSet<>();
            Matcher refMatcher = ACTION_ID_REF_PATTERN.matcher(allRegistrationSources);
            while (refMatcher.find()) {
                referencedFields.add(refMatcher.group(1));
            }

            List<String> orphans = inventory.values().stream()
                .map(BaselineActionInventory.InventoryEntry::fieldName)
                .filter(field -> !referencedFields.contains(field))
                .collect(Collectors.toList());

            assertTrue(orphans.isEmpty(),
                "ActionIds constants not referenced in any registration file (orphans): " + orphans);
        }
    }

    @Nested
    @DisplayName("CLIENT_SAFE_ACTIONS Whitelist")
    class ClientSafeActionsWhitelist {

        @Test
        @DisplayName("Expected client-safe action IDs match source whitelist")
        void clientSafeActionsMatchExpected() {
            // Extract ActionIds.FIELD references from CLIENT_SAFE_ACTIONS block
            Pattern clientSafeBlock = Pattern.compile(
                "CLIENT_SAFE_ACTIONS\\s*=\\s*Set\\.of\\((.*?)\\)",
                Pattern.DOTALL);
            Matcher blockMatcher = clientSafeBlock.matcher(actionRegistrySource);
            assertTrue(blockMatcher.find(), "Could not find CLIENT_SAFE_ACTIONS definition");

            String block = blockMatcher.group(1);
            Matcher refMatcher = ACTION_ID_REF_PATTERN.matcher(block);

            Set<String> sourceWhitelistFields = new HashSet<>();
            while (refMatcher.find()) {
                sourceWhitelistFields.add(refMatcher.group(1));
            }

            // Resolve field names to action ID values
            Set<String> sourceWhitelistIds = sourceWhitelistFields.stream()
                .map(field -> inventory.values().stream()
                    .filter(e -> e.fieldName().equals(field))
                    .map(BaselineActionInventory.InventoryEntry::actionId)
                    .findFirst()
                    .orElse("UNKNOWN:" + field))
                .collect(Collectors.toSet());

            Set<String> expectedIds = BaselineActionInventory.getExpectedClientSafeActionIds();

            // Check both directions
            Set<String> missingFromExpected = new HashSet<>(sourceWhitelistIds);
            missingFromExpected.removeAll(expectedIds);

            Set<String> extraInExpected = new HashSet<>(expectedIds);
            extraInExpected.removeAll(sourceWhitelistIds);

            assertTrue(missingFromExpected.isEmpty(),
                "Actions in CLIENT_SAFE_ACTIONS but not in expected set: " + missingFromExpected);
            assertTrue(extraInExpected.isEmpty(),
                "Actions in expected set but not in CLIENT_SAFE_ACTIONS: " + extraInExpected);
        }

        @Test
        @DisplayName("CLIENT_SAFE_ACTIONS count is 26")
        void clientSafeActionsCount() {
            Set<String> expected = BaselineActionInventory.getExpectedClientSafeActionIds();
            assertEquals(26, expected.size(),
                "CLIENT_SAFE_ACTIONS whitelist size changed");
        }
    }

    @Nested
    @DisplayName("Category Inference")
    class CategoryInference {

        @Test
        @DisplayName("No action falls through to MISC category")
        void noCategoryFallsToMisc() {
            List<String> miscActions = inventory.values().stream()
                .filter(e -> e.expectedCategory() == com.devmod.actions.ActionCategory.MISC)
                .map(BaselineActionInventory.InventoryEntry::actionId)
                .collect(Collectors.toList());

            assertTrue(miscActions.isEmpty(),
                "Actions that fell through to MISC (update inferCategory): " + miscActions);
        }

        @Test
        @DisplayName("All ActionCategory values are used by at least one action")
        void allCategoriesUsed() {
            Set<com.devmod.actions.ActionCategory> usedCategories = inventory.values().stream()
                .map(BaselineActionInventory.InventoryEntry::expectedCategory)
                .collect(Collectors.toSet());

            // MISC and ADMIN may not be used - those are allowed exceptions
            Set<com.devmod.actions.ActionCategory> required = Set.of(
                com.devmod.actions.ActionCategory.TOOLS,
                com.devmod.actions.ActionCategory.COMBAT,
                com.devmod.actions.ActionCategory.ENDURANCE,
                com.devmod.actions.ActionCategory.ARENA,
                com.devmod.actions.ActionCategory.TELEMETRY,
                com.devmod.actions.ActionCategory.DEBUG,
                com.devmod.actions.ActionCategory.CONFIG,
                com.devmod.actions.ActionCategory.TESTING,
                com.devmod.actions.ActionCategory.PARTY,
                com.devmod.actions.ActionCategory.UI
            );

            Set<com.devmod.actions.ActionCategory> missing = new HashSet<>(required);
            missing.removeAll(usedCategories);

            assertTrue(missing.isEmpty(),
                "Categories with zero actions (expected at least one): " + missing);
        }
    }

    @Nested
    @DisplayName("CSV Report")
    class CsvReport {

        @Test
        @DisplayName("Ground-truth CSV snapshot can be generated from inventory")
        void csvSnapshotGeneration() {
            StringBuilder csv = new StringBuilder();
            csv.append("actionId,fieldName,expectedCategory,registrationSource\n");
            for (var entry : inventory.values()) {
                csv.append(csvEscape(entry.actionId())).append(',');
                csv.append(csvEscape(entry.fieldName())).append(',');
                csv.append(entry.expectedCategory().name()).append(',');
                csv.append(entry.registrationSource().name()).append('\n');
            }

            String report = csv.toString();
            assertFalse(report.isEmpty(), "CSV report should not be empty");

            // Count lines (header + data)
            long lineCount = report.lines().count();
            assertEquals(inventory.size() + 1, lineCount,
                "CSV line count should be header + one line per action");
        }

        private String csvEscape(String value) {
            if (value.contains(",") || value.contains("\"")) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            return value;
        }
    }

    @Nested
    @DisplayName("Permission Matrix")
    class PermissionMatrix {

        @Test
        @DisplayName("Client-safe actions do not require high permission levels")
        void clientSafeActionsAreNotHighPermission() {
            // Client-safe actions should generally not be gated behind op permissions,
            // since they're meant to be invocable from untrusted network context.
            Set<String> clientSafe = BaselineActionInventory.getExpectedClientSafeActionIds();

            // Actions in client-safe list should NOT be in categories requiring op
            // (TOOLS commands, ADMIN, etc.) - they should be player-facing actions
            for (String id : clientSafe) {
                var entry = inventory.get(id);
                assertNotNull(entry, "Client-safe action " + id + " not found in inventory");
                assertNotEquals(
                    BaselineActionInventory.RegistrationSource.DEVMOD_ACTIONS_COMMANDS,
                    entry.registrationSource(),
                    "Client-safe action should not be a server command shortcut: " + id);
            }
        }
    }
}
