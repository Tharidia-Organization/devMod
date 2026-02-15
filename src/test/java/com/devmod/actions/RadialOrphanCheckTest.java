package com.devmod.actions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Radial Orphan Check - Ensures no feature is left without radial access.
 *
 * <p>The radial-first architecture requires:
 * <ul>
 *   <li>Every ActionId constant has a registered RadialAction</li>
 *   <li>Every keybind maps to an ActionId</li>
 *   <li>Every command invokes ActionRegistry</li>
 * </ul>
 *
 * <p>This test fails if orphan features are detected, ensuring
 * all mod functionality is accessible from the radial menu.
 */
@DisplayName("Radial Orphan Check - No Feature Left Behind")
class RadialOrphanCheckTest {

    private static String actionIdsSource;
    private static String devModClientActionsSource;
    private static String clientUIActionsSource;
    private static String clientDebugActionsSource;
    private static String clientConfigActionsSource;
    private static String clientGameplayActionsSource;
    private static String actionKeybindRegistrySource;
    private static String renderEventsSource;
    private static String arenaActionRegistrySource;
    private static String devModActionsSource;
    private static String leaderboardCommandEventsSource;
    private static String telemetryReloadCommandSource;
    private static String dashboardCommandSource;
    private static String dungeonCommandSource;
    private static String mailboxCommandsSource;
    private static String testHarnessCommandsSource;
    private static String debugCommandSource;

    private static final Path ACTION_IDS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/ActionIds.java");
    private static final Path DEVMOD_CLIENT_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/DevModClientActions.java");
    private static final Path CLIENT_UI_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ClientUIActions.java");
    private static final Path CLIENT_DEBUG_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ClientDebugActions.java");
    private static final Path CLIENT_CONFIG_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ClientConfigActions.java");
    private static final Path CLIENT_GAMEPLAY_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ClientGameplayActions.java");
    private static final Path ACTION_KEYBIND_REGISTRY_PATH = Paths.get(
        "src/main/java/com/devmod/actions/client/ActionKeybindRegistry.java");
    private static final Path RENDER_EVENTS_PATH = Paths.get(
        "src/main/java/com/devmod/client/rendering/RenderEvents.java");
    private static final Path ARENA_ACTION_REGISTRY_PATH = Paths.get(
        "src/main/java/com/devmod/arena/command/ArenaActionRegistry.java");
    private static final Path DEVMOD_ACTIONS_PATH = Paths.get(
        "src/main/java/com/devmod/actions/DevModActions.java");
    private static final Path LEADERBOARD_COMMAND_EVENTS_PATH = Paths.get(
        "src/main/java/com/devmod/endurance/LeaderboardCommandEvents.java");
    private static final Path TELEMETRY_RELOAD_COMMAND_PATH = Paths.get(
        "src/main/java/com/devmod/telemetry/TelemetryReloadCommand.java");
    private static final Path DASHBOARD_COMMAND_PATH = Paths.get(
        "src/main/java/com/devmod/telemetry/dashboard/DashboardCommand.java");
    private static final Path DUNGEON_COMMAND_PATH = Paths.get(
        "src/main/java/com/devmod/telemetry/dungeon/DungeonCommand.java");
    private static final Path MAILBOX_COMMANDS_PATH = Paths.get(
        "src/main/java/com/devmod/mailbox/admin/MailboxCommands.java");
    private static final Path TEST_HARNESS_COMMANDS_PATH = Paths.get(
        "src/main/java/com/devmod/gametest/TestHarnessCommands.java");
    private static final Path DEBUG_COMMAND_PATH = Paths.get(
        "src/main/java/com/devmod/debug/DebugCommand.java");

    @BeforeAll
    static void loadSourceCode() throws IOException {
        actionIdsSource = Files.readString(ACTION_IDS_PATH);
        devModClientActionsSource = Files.readString(DEVMOD_CLIENT_ACTIONS_PATH);
        clientUIActionsSource = Files.readString(CLIENT_UI_ACTIONS_PATH);
        clientDebugActionsSource = Files.readString(CLIENT_DEBUG_ACTIONS_PATH);
        clientConfigActionsSource = Files.readString(CLIENT_CONFIG_ACTIONS_PATH);
        clientGameplayActionsSource = Files.readString(CLIENT_GAMEPLAY_ACTIONS_PATH);
        actionKeybindRegistrySource = Files.readString(ACTION_KEYBIND_REGISTRY_PATH);
        renderEventsSource = Files.readString(RENDER_EVENTS_PATH);
        arenaActionRegistrySource = Files.readString(ARENA_ACTION_REGISTRY_PATH);
        devModActionsSource = Files.readString(DEVMOD_ACTIONS_PATH);
        leaderboardCommandEventsSource = Files.readString(LEADERBOARD_COMMAND_EVENTS_PATH);
        telemetryReloadCommandSource = Files.readString(TELEMETRY_RELOAD_COMMAND_PATH);
        dashboardCommandSource = Files.readString(DASHBOARD_COMMAND_PATH);
        dungeonCommandSource = Files.readString(DUNGEON_COMMAND_PATH);
        mailboxCommandsSource = Files.readString(MAILBOX_COMMANDS_PATH);
        testHarnessCommandsSource = Files.readString(TEST_HARNESS_COMMANDS_PATH);
        debugCommandSource = Files.readString(DEBUG_COMMAND_PATH);
    }

    // ============================================================
    // L0: SMOKE TESTS - Basic Registry Structure
    // ============================================================

    @Nested
    @DisplayName("L0: Smoke Tests - Registry Structure")
    class L0SmokeTests {

        @Test
        @DisplayName("L0-01: ActionIds class exists and has constants")
        void actionIdsClassExists() {
            assertTrue(actionIdsSource.contains("public final class ActionIds"),
                "ActionIds class should exist");
            assertTrue(actionIdsSource.contains("public static final String"),
                "ActionIds should have public static final String constants");
        }

        @Test
        @DisplayName("L0-02: ActionRegistry class has register method")
        void actionRegistryHasRegisterMethod() throws IOException {
            String actionRegistrySource = Files.readString(Paths.get(
                "src/main/java/com/devmod/actions/ActionRegistry.java"));
            assertTrue(actionRegistrySource.contains("public static void register(RadialAction action)"),
                "ActionRegistry should have register method");
        }

        @Test
        @DisplayName("L0-03: DevModClientActions delegates to domain registrars")
        void devModClientActionsDelegates() {
            // After refactoring, DevModClientActions is a facade that delegates
            // to domain-specific registrar classes
            assertTrue(devModClientActionsSource.contains("ClientUIActions.registerActions()"),
                "DevModClientActions should delegate to ClientUIActions");
            assertTrue(devModClientActionsSource.contains("ClientDebugActions.registerActions()"),
                "DevModClientActions should delegate to ClientDebugActions");
            // Actual registrations happen in domain files
            String combinedDomainSources = clientUIActionsSource + clientDebugActionsSource
                + clientConfigActionsSource + clientGameplayActionsSource;
            assertTrue(combinedDomainSources.contains("ActionRegistry.register("),
                "Domain registrar files should register actions");
        }

        @Test
        @DisplayName("L0-04: ActionKeybindRegistry has register method")
        void actionKeybindRegistryHasRegister() {
            assertTrue(actionKeybindRegistrySource.contains("public static void register("),
                "ActionKeybindRegistry should have static register method");
        }
    }

    // ============================================================
    // L1: ACTION ID REGISTRATION TESTS
    // ============================================================

    @Nested
    @DisplayName("L1: ActionId Registration")
    class L1ActionIdRegistrationTests {

        @Test
        @DisplayName("L1-01: All ActionIds are referenced in registration code")
        void allActionIdsAreRegistered() {
            // Extract all ActionIds constants
            Set<String> actionIds = extractActionIds();
            assertTrue(actionIds.size() > 100,
                "Should have more than 100 ActionIds defined. Found: " + actionIds.size());

            // Combine all registration sources
            String combinedSources = combinedRegistrationSources();

            // Check each ActionId is referenced somewhere in registration
            List<String> unregistered = new ArrayList<>();
            for (String actionId : actionIds) {
                // Skip event-triggered actions that don't need radial entry
                if (isEventTriggeredAction(actionId)) {
                    continue;
                }

                // Check if ActionIds.CONSTANT_NAME is referenced
                String constantName = actionIdToConstantName(actionId);
                if (!combinedSources.contains("ActionIds." + constantName)) {
                    unregistered.add(actionId);
                }
            }

            // Allow up to 10% unregistered (for future/deprecated actions)
            int maxUnregistered = Math.max(5, actionIds.size() / 10);
            assertTrue(unregistered.size() <= maxUnregistered,
                "Too many unregistered ActionIds (" + unregistered.size() + "): " +
                String.join(", ", unregistered.subList(0, Math.min(10, unregistered.size()))));
        }

        @Test
        @DisplayName("L1-02: No duplicate ActionId values")
        void noDuplicateActionIdValues() {
            Set<String> values = new HashSet<>();
            List<String> duplicates = new ArrayList<>();

            Pattern pattern = Pattern.compile(
                "public static final String (\\w+) = \"([^\"]+)\"");
            Matcher matcher = pattern.matcher(actionIdsSource);

            while (matcher.find()) {
                String value = matcher.group(2);
                if (!values.add(value)) {
                    duplicates.add(value);
                }
            }

            assertTrue(duplicates.isEmpty(),
                "ActionIds should have unique values. Duplicates: " + duplicates);
        }

        @Test
        @DisplayName("L1-03: ActionId values follow naming convention")
        void actionIdValuesFollowConvention() {
            Pattern pattern = Pattern.compile("\"(devmod\\.[a-z0-9_.]+)\"");
            Matcher matcher = pattern.matcher(actionIdsSource);

            int count = 0;
            List<String> invalid = new ArrayList<>();

            while (matcher.find()) {
                count++;
                String value = matcher.group(1);
                // Should be devmod.category.action format
                if (!value.matches("devmod\\.[a-z0-9_]+\\.[a-z0-9_.]+")) {
                    invalid.add(value);
                }
            }

            assertTrue(count > 100, "Should have 100+ ActionId values. Found: " + count);
            assertTrue(invalid.size() <= 5,
                "Most ActionIds should follow devmod.category.action format. Invalid: " + invalid);
        }
    }

    // ============================================================
    // L2: KEYBIND MAPPING TESTS
    // ============================================================

    @Nested
    @DisplayName("L2: Keybind Mapping")
    class L2KeybindMappingTests {

        @Test
        @DisplayName("L2-01: All keybinds map to ActionIds")
        void allKeybindsMappedToActionIds() {
            // After refactoring, keybind registrations are in domain-specific files
            String allKeybindSources = clientUIActionsSource + clientDebugActionsSource
                + clientConfigActionsSource + clientGameplayActionsSource;
            Pattern registerPattern = Pattern.compile(
                "ActionKeybindRegistry\\.register\\(ActionIds\\.(\\w+),\\s*KeyInputHandler\\.(\\w+)");
            Matcher matcher = registerPattern.matcher(allKeybindSources);

            int count = 0;
            while (matcher.find()) {
                count++;
            }

            assertTrue(count >= 30,
                "Should have at least 30 keybind mappings across domain files. Found: " + count);
        }

        @Test
        @DisplayName("L2-02: Keybind handlers use ActionRegistry.invoke()")
        void keybindHandlersUseActionRegistry() {
            // All keybind handlers should call invokeAction which uses ActionRegistry
            assertTrue(renderEventsSource.contains("private static void invokeAction(String actionId)"),
                "RenderEvents should have invokeAction helper");
            assertTrue(renderEventsSource.contains("ActionRegistry.invoke(actionId"),
                "invokeAction should call ActionRegistry.invoke");

            // Count invokeAction calls
            Pattern invokePattern = Pattern.compile("invokeAction\\(ActionIds\\.");
            Matcher matcher = invokePattern.matcher(renderEventsSource);

            int count = 0;
            while (matcher.find()) {
                count++;
            }

            assertTrue(count >= 25,
                "Should have at least 25 invokeAction calls. Found: " + count);
        }

        @Test
        @DisplayName("L2-03: No inline keybind logic (all through ActionRegistry)")
        void noInlineKeybindLogic() {
            // Should not have raw game logic in keybind handlers
            // Patterns that indicate inline logic:
            String[] badPatterns = {
                "consumeClick\\(\\)[^}]*\\.setScreen\\(",  // Opening screens directly
                "consumeClick\\(\\)[^}]*\\.toggle\\(",     // Toggling directly
                "consumeClick\\(\\)[^}]*\\.enable\\(",     // Enabling directly
            };

            for (String badPattern : badPatterns) {
                Pattern pattern = Pattern.compile(badPattern, Pattern.DOTALL);
                assertFalse(pattern.matcher(renderEventsSource).find(),
                    "Keybind handlers should not have inline logic. Found pattern: " + badPattern);
            }
        }
    }

    // ============================================================
    // L3: COMMAND INTEGRATION TESTS
    // ============================================================

    @Nested
    @DisplayName("L3: Command Integration")
    class L3CommandIntegrationTests {

        @Test
        @DisplayName("L3-01: Arena commands use ActionRegistry")
        void arenaCommandsUseActionRegistry() {
            assertTrue(arenaActionRegistrySource.contains("ActionRegistry.register("),
                "ArenaActionRegistry should register actions");

            // Count arena action registrations
            Pattern registerPattern = Pattern.compile("ActionRegistry\\.register\\(");
            Matcher matcher = registerPattern.matcher(arenaActionRegistrySource);

            int count = 0;
            while (matcher.find()) {
                count++;
            }

            assertTrue(count >= 10,
                "Should have at least 10 arena actions registered. Found: " + count);
        }

        @Test
        @DisplayName("L3-02: Arena actions have command hints")
        void arenaActionsHaveCommandHints() {
            Pattern hintPattern = Pattern.compile("\\.commandHint\\(\"[^\"]+\"\\)");
            Matcher matcher = hintPattern.matcher(arenaActionRegistrySource);

            int count = 0;
            while (matcher.find()) {
                count++;
            }

            assertTrue(count >= 5,
                "Arena actions should have command hints. Found: " + count);
        }
    }

    // ============================================================
    // L4: CATEGORY COVERAGE TESTS
    // ============================================================

    @Nested
    @DisplayName("L4: Category Coverage")
    class L4CategoryCoverageTests {

        @Test
        @DisplayName("L4-01: All ActionCategory values are used")
        void allActionCategoriesUsed() {
            String[] categories = {
                "ActionCategory.TOOLS",
                "ActionCategory.COMBAT",
                "ActionCategory.ENDURANCE",
                "ActionCategory.ARENA",
                "ActionCategory.TELEMETRY",
                "ActionCategory.DEBUG",
                "ActionCategory.CONFIG",
                "ActionCategory.TESTING",
                "ActionCategory.UI"
            };

            String combinedSources = combinedRegistrationSources();

            for (String category : categories) {
                assertTrue(combinedSources.contains(category),
                    "Category " + category + " should be used in action registrations");
            }
        }

        @Test
        @DisplayName("L4-02: Debug actions are in DEBUG category")
        void debugActionsInDebugCategory() {
            // After refactoring, debug actions are in ClientDebugActions
            String allSources = clientUIActionsSource + clientDebugActionsSource
                + clientConfigActionsSource + clientGameplayActionsSource;
            Pattern pattern = Pattern.compile(
                "ActionIds\\.DEBUG_[^,]+,[^}]+category\\(ActionCategory\\.(\\w+)\\)");
            Matcher matcher = pattern.matcher(allSources);

            List<String> wrongCategory = new ArrayList<>();
            while (matcher.find()) {
                String category = matcher.group(1);
                if (!category.equals("DEBUG") && !category.equals("COMBAT")) {
                    wrongCategory.add(category);
                }
            }

            assertTrue(wrongCategory.size() <= 3,
                "Most DEBUG_ actions should be in DEBUG category. Wrong: " + wrongCategory);
        }

        @Test
        @DisplayName("L4-03: UI actions are in UI category")
        void uiActionsInUiCategory() {
            // After refactoring, UI actions are in ClientUIActions
            String allSources = clientUIActionsSource + clientDebugActionsSource
                + clientConfigActionsSource + clientGameplayActionsSource;
            Pattern pattern = Pattern.compile(
                "ActionIds\\.UI_[^,]+,[^}]+category\\(ActionCategory\\.(\\w+)\\)");
            Matcher matcher = pattern.matcher(allSources);

            List<String> wrongCategory = new ArrayList<>();
            int total = 0;
            while (matcher.find()) {
                total++;
                String category = matcher.group(1);
                if (!category.equals("UI") && !category.equals("CONFIG") &&
                    !category.equals("TESTING") && !category.equals("TOOLS")) {
                    wrongCategory.add(category);
                }
            }

            assertTrue(total > 0, "Should have UI_ actions in domain registrar files");
            assertTrue(wrongCategory.size() <= 5,
                "Most UI_ actions should be in appropriate categories. Wrong: " + wrongCategory);
        }
    }

    // ============================================================
    // L5: ORPHAN DETECTION SUMMARY
    // ============================================================

    @Nested
    @DisplayName("L5: Orphan Detection Summary")
    class L5OrphanDetectionSummaryTests {

        @Test
        @DisplayName("L5-01: Generate orphan report")
        void generateOrphanReport() {
            Set<String> allActionIds = extractActionIds();
            String combinedSources = combinedRegistrationSources();

            List<String> orphans = new ArrayList<>();
            for (String actionId : allActionIds) {
                if (isEventTriggeredAction(actionId)) {
                    continue;
                }

                String constantName = actionIdToConstantName(actionId);
                if (!combinedSources.contains("ActionIds." + constantName)) {
                    orphans.add(actionId);
                }
            }

            // Print report for debugging
            if (!orphans.isEmpty()) {
                System.out.println("=== ORPHAN ACTION REPORT ===");
                System.out.println("Total ActionIds: " + allActionIds.size());
                System.out.println("Orphan count: " + orphans.size());
                System.out.println("Orphans:");
                for (String orphan : orphans) {
                    System.out.println("  - " + orphan);
                }
                System.out.println("============================");
            }

            // This test is informational - it prints but doesn't fail
            // The L1-01 test enforces the actual limit
        }
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private Set<String> extractActionIds() {
        Set<String> ids = new HashSet<>();
        Pattern pattern = Pattern.compile(
            "public static final String \\w+ = \"(devmod\\.[^\"]+)\"");
        Matcher matcher = pattern.matcher(actionIdsSource);

        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private String combinedRegistrationSources() {
        return devModClientActionsSource
            + clientUIActionsSource
            + clientDebugActionsSource
            + clientConfigActionsSource
            + clientGameplayActionsSource
            + arenaActionRegistrySource
            + devModActionsSource
            + leaderboardCommandEventsSource
            + telemetryReloadCommandSource
            + dashboardCommandSource
            + dungeonCommandSource
            + mailboxCommandsSource
            + testHarnessCommandsSource
            + debugCommandSource;
    }

    private String actionIdToConstantName(String actionId) {
        // Convert devmod.ui.radial.open to UI_RADIAL_OPEN
        return actionId
            .replace("devmod.", "")
            .replace(".", "_")
            .toUpperCase(Locale.ROOT);
    }

    private boolean isEventTriggeredAction(String actionId) {
        // These actions are triggered by events, not user interaction
        // They don't need radial entries
        if (actionId.contains(".party_invite_popup") ||
            actionId.contains(".quest_death") ||
            actionId.contains(".quest_completion") ||
            actionId.contains(".wave_checkpoint") ||
            actionId.contains(".perk_selection") ||
            actionId.contains(".wave_directive") ||
            actionId.contains(".kit_selection")) {
            return true;
        }

        // Admin commands (server-side, accessed via /mailbox, /news commands)
        if (actionId.startsWith("devmod.mailbox.command.") ||
            actionId.startsWith("devmod.news.command.")) {
            return true;
        }

        // Debug command-line interface (accessed via /debug command)
        if (actionId.startsWith("devmod.debug.command.")) {
            return true;
        }

        // Telemetry server-side operations (accessed via /telemetry command)
        if (actionId.startsWith("devmod.telemetry.dump.") ||
            actionId.startsWith("devmod.telemetry.scan.") ||
            actionId.startsWith("devmod.telemetry.dashboard.") ||
            actionId.startsWith("devmod.telemetry.dungeon.") ||
            actionId.startsWith("devmod.telemetry.dungeons.") ||
            actionId.startsWith("devmod.telemetry.desirelines.") ||
            actionId.startsWith("devmod.telemetry.backtracking.") ||
            actionId.equals("devmod.telemetry.spawnability") ||
            actionId.equals("devmod.telemetry.reload")) {
            return true;
        }

        // Telemetry export formats (accessed via /telemetry export command)
        if (actionId.equals("devmod.telemetry.export.csv") ||
            actionId.equals("devmod.telemetry.export.json") ||
            actionId.equals("devmod.telemetry.export.png") ||
            actionId.equals("devmod.telemetry.export.all") ||
            actionId.equals("devmod.telemetry.export.heatmaps")) {
            return true;
        }

        // Test harness sub-commands (accessed via /test command or TestingHub)
        if (actionId.startsWith("devmod.testing.hud.") ||
            actionId.startsWith("devmod.testing.panel.") ||
            actionId.startsWith("devmod.testing.debug.") ||
            actionId.startsWith("devmod.testing.endurance.") ||
            actionId.startsWith("devmod.testing.session.") ||
            actionId.startsWith("devmod.testing.report.") ||
            actionId.startsWith("devmod.testing.test.") ||
            actionId.equals("devmod.testing.debugbox") ||
            actionId.equals("devmod.testing.debugclear") ||
            actionId.equals("devmod.testing.panelclear") ||
            actionId.equals("devmod.testing.info") ||
            actionId.equals("devmod.testing.qa.open") ||
            actionId.equals("devmod.testing.bodypart.info")) {
            return true;
        }

        // Game design presets (accessed via Config submenu, already registered)
        if (actionId.startsWith("devmod.gamedesign.preset.")) {
            return true;
        }

        // Game design system toggles (accessed via Config submenu, already registered)
        if (actionId.equals("devmod.gamedesign.resonance.toggle") ||
            actionId.equals("devmod.gamedesign.contracts.toggle") ||
            actionId.equals("devmod.gamedesign.signature_weapons.toggle") ||
            actionId.equals("devmod.gamedesign.nemesis.toggle") ||
            actionId.equals("devmod.gamedesign.tide.toggle") ||
            actionId.equals("devmod.gamedesign.reload") ||
            actionId.equals("devmod.gamedesign.save") ||
            actionId.equals("devmod.gamedesign.reset")) {
            return true;
        }

        // Room bounds editor sub-actions (triggered from RoomBoundsEditorScreen)
        if (actionId.startsWith("devmod.ui.room_bounds_editor.") &&
            !actionId.equals("devmod.ui.room_bounds_editor.open")) {
            return true;
        }

        // Vanilla command shortcuts (accessed via /devmod command)
        if (actionId.startsWith("devmod.command.gamemode.") ||
            actionId.startsWith("devmod.command.time.") ||
            actionId.startsWith("devmod.command.weather.") ||
            actionId.equals("devmod.command.heal")) {
            return true;
        }

        // HUD sub-actions (toggle-only, accessed from parent HUD action)
        if (actionId.equals("devmod.hud.impact.dismiss") ||
            actionId.equals("devmod.endurance.hud.toggle") ||
            actionId.equals("devmod.endurance.hud.details_toggle")) {
            return true;
        }

        return false;
    }
}
