package com.devmod.actions.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devmod.actions.ActionCategory;

/**
 * Ground truth baseline of all action IDs declared in ActionIds.java.
 *
 * <p>This class provides a manually verified mapping of every action ID constant
 * to its expected category and registration source file. The data is derived from
 * source-level analysis of ActionIds.java and all registration files.
 *
 * <p>Used by ActionBaselineValidationTest to detect regressions, orphans, and
 * inconsistencies in the action registration landscape.
 */
public final class BaselineActionInventory {

    private BaselineActionInventory() {}

    /**
     * Metadata for a single action in the ground-truth inventory.
     *
     * @param actionId         the action ID string value
     * @param fieldName        the constant field name in ActionIds.java
     * @param expectedCategory the expected ActionCategory
     * @param registrationSource which file registers this action
     */
    public record InventoryEntry(
        String actionId,
        String fieldName,
        ActionCategory expectedCategory,
        RegistrationSource registrationSource
    ) {}

    /**
     * Which file/method is responsible for registering a given action.
     */
    public enum RegistrationSource {
        /** DevModActions.registerCommandActions() - server-side command shortcuts */
        DEVMOD_ACTIONS_COMMANDS,
        /** DevModClientActions.registerUiActions() - client UI screens */
        CLIENT_UI_ACTIONS,
        /** DevModClientActions.registerDebugActions() - client debug toggles */
        CLIENT_DEBUG_ACTIONS,
        /** DevModClientActions.registerConfigActions() - client config toggles */
        CLIENT_CONFIG_ACTIONS,
        /** DevModClientActions.registerTelemetryActions() - client telemetry/heatmap */
        CLIENT_TELEMETRY_ACTIONS,
        /** DevModClientActions.registerQuestActions() - quest completion */
        CLIENT_QUEST_ACTIONS,
        /** DevModClientActions.registerEnduranceActions() - endurance quest/HUD */
        CLIENT_ENDURANCE_ACTIONS,
        /** DevModClientActions.registerAbilityActions() - ability dash/dodge */
        CLIENT_ABILITY_ACTIONS,
        /** ArenaActionRegistry.registerCommonActions() - server-side arena */
        ARENA_COMMON,
        /** ArenaActionRegistry.registerClientActions() - client-side arena */
        ARENA_CLIENT,
        /** DebugCommand.registerActions() - debug command system */
        DEBUG_COMMAND,
        /** TelemetryReloadCommand.registerActions() - telemetry admin */
        TELEMETRY_RELOAD,
        /** DashboardCommand.registerActions() - dashboard server */
        DASHBOARD_COMMAND,
        /** DungeonCommand.registerActions() - dungeon telemetry */
        DUNGEON_COMMAND,
        /** TestHarnessCommands.registerActions() - test harness */
        TEST_HARNESS,
        /** MailboxCommands.registerActions() - mailbox + news admin */
        MAILBOX_COMMANDS,
        /** LeaderboardCommandEvents.registerActions() - leaderboard */
        LEADERBOARD_COMMANDS
    }

    // =========================================================================
    // Category mapping by action ID prefix
    // =========================================================================

    /**
     * Returns the expected category for an action ID based on its prefix and
     * known registration patterns. This encodes the ground-truth mapping
     * discovered by reading all registration source files.
     */
    static ActionCategory inferCategory(String actionId) {
        // UI screens -> varies by registration
        if (actionId.startsWith("devmod.ui.arena")) return ActionCategory.ARENA;
        if (actionId.startsWith("devmod.ui.endurance")
            || actionId.startsWith("devmod.ui.quest_death")
            || actionId.startsWith("devmod.ui.perk_selection")
            || actionId.startsWith("devmod.ui.quest_completion")
            || actionId.startsWith("devmod.ui.wave_checkpoint")
            || actionId.startsWith("devmod.ui.endurance_shop")) return ActionCategory.ENDURANCE;
        if (actionId.startsWith("devmod.ui.party")
            || actionId.equals("devmod.ui.party_invite_popup.open")) return ActionCategory.PARTY;
        if (actionId.startsWith("devmod.ui.testing")
            || actionId.startsWith("devmod.ui.quick_test_wizard")
            || actionId.startsWith("devmod.ui.badge_tests")
            || actionId.startsWith("devmod.ui.voxellab_ui_tests")
            || actionId.startsWith("devmod.ui.qa_testing")
            || actionId.startsWith("devmod.ui.responsiveness_test")
            || actionId.startsWith("devmod.ui.tester_tasks")) return ActionCategory.TESTING;
        if (actionId.startsWith("devmod.ui.telemetry")) return ActionCategory.TELEMETRY;
        if (actionId.startsWith("devmod.ui.notification")) return ActionCategory.UI;
        if (actionId.startsWith("devmod.ui.mailbox")) return ActionCategory.UI;
        if (actionId.startsWith("devmod.ui.voxellab")) return ActionCategory.TESTING;
        if (actionId.startsWith("devmod.ui.welcome")
            || actionId.startsWith("devmod.ui.onboarding")
            || actionId.startsWith("devmod.ui.season_pass")) return ActionCategory.UI;
        if (actionId.startsWith("devmod.ui.keybinds")) return ActionCategory.UI;
        if (actionId.startsWith("devmod.ui.")) return ActionCategory.UI;

        // Debug / HUD
        if (actionId.startsWith("devmod.debug.")) return ActionCategory.DEBUG;
        if (actionId.startsWith("devmod.hud.impact")) return ActionCategory.COMBAT;
        if (actionId.startsWith("devmod.hud.quest")) return ActionCategory.ENDURANCE;
        if (actionId.startsWith("devmod.hud.quick_help")) return ActionCategory.UI;
        if (actionId.startsWith("devmod.hud.party")) return ActionCategory.PARTY;
        if (actionId.startsWith("devmod.endurance.hud")) return ActionCategory.ENDURANCE;

        // Config toggles
        if (actionId.startsWith("devmod.config.")) return ActionCategory.CONFIG;

        // Endurance / Quest
        if (actionId.startsWith("devmod.quest.")) return ActionCategory.ENDURANCE;
        if (actionId.startsWith("devmod.endurance.")) return ActionCategory.ENDURANCE;

        // Abilities
        if (actionId.startsWith("devmod.ability.")) return ActionCategory.COMBAT;

        // Commands
        if (actionId.startsWith("devmod.command.")) return ActionCategory.TOOLS;

        // Arena
        if (actionId.startsWith("devmod.arena.")) return ActionCategory.ARENA;

        // Telemetry
        if (actionId.startsWith("devmod.telemetry.")) return ActionCategory.TELEMETRY;

        // Test harness
        if (actionId.startsWith("devmod.testing.")) return ActionCategory.TESTING;

        // Game design config
        if (actionId.startsWith("devmod.gamedesign.")) return ActionCategory.CONFIG;

        // Mailbox / News
        if (actionId.startsWith("devmod.mailbox.")) return ActionCategory.DEBUG;
        if (actionId.startsWith("devmod.news.")) return ActionCategory.DEBUG;

        // Leaderboard
        if (actionId.startsWith("devmod.leaderboard.")) return ActionCategory.ENDURANCE;

        return ActionCategory.MISC;
    }

    /**
     * Returns the expected registration source for an action ID.
     */
    static RegistrationSource inferRegistrationSource(String actionId) {
        // Client UI screens
        if (actionId.startsWith("devmod.ui.arena")
            || actionId.equals("devmod.arena.quick_test_wizard.open")) {
            return RegistrationSource.ARENA_CLIENT;
        }
        if (actionId.startsWith("devmod.ui.")) return RegistrationSource.CLIENT_UI_ACTIONS;

        // Debug overlays / HUD toggles registered on client
        if (actionId.startsWith("devmod.debug.overlay")
            || actionId.startsWith("devmod.debug.body_parts")
            || actionId.startsWith("devmod.debug.overlays")
            || actionId.startsWith("devmod.debug.native")
            || actionId.startsWith("devmod.debug.light_overlay")
            || actionId.startsWith("devmod.debug.heatmap")
            || actionId.startsWith("devmod.debug.room_bounds")
            || actionId.startsWith("devmod.debug.pathfinding")
            || actionId.startsWith("devmod.debug.los")
            || actionId.startsWith("devmod.debug.aggro_range")
            || actionId.startsWith("devmod.debug.vertical_levels")
            || actionId.startsWith("devmod.debug.safe_spots")
            || actionId.startsWith("devmod.debug.attribute_monitor")
            || actionId.startsWith("devmod.debug.fps_tracker")
            || actionId.startsWith("devmod.debug.profiler")
            || actionId.startsWith("devmod.debug.entity_density")
            || actionId.startsWith("devmod.debug.boss_phase")
            || actionId.startsWith("devmod.debug.skill_efficacy")
            || actionId.startsWith("devmod.debug.spawnability")
            || actionId.startsWith("devmod.debug.chunk_perf")
            || actionId.startsWith("devmod.debug.economy")
            || actionId.startsWith("devmod.debug.screen_shake")
            || actionId.startsWith("devmod.debug.combat")
            || actionId.startsWith("devmod.debug.context")) {
            return RegistrationSource.CLIENT_DEBUG_ACTIONS;
        }
        if (actionId.startsWith("devmod.hud.impact")
            || actionId.equals("devmod.hud.quick_help.toggle")
            || actionId.equals("devmod.hud.quest.toggle")
            || actionId.equals("devmod.hud.party.toggle")) {
            return RegistrationSource.CLIENT_DEBUG_ACTIONS;
        }

        // Debug command system (server-side)
        if (actionId.startsWith("devmod.debug.command")) return RegistrationSource.DEBUG_COMMAND;

        // Config toggles
        if (actionId.startsWith("devmod.config.")) return RegistrationSource.CLIENT_CONFIG_ACTIONS;
        if (actionId.startsWith("devmod.gamedesign.")) return RegistrationSource.CLIENT_CONFIG_ACTIONS;

        // Telemetry client-side heatmap exports
        if (actionId.startsWith("devmod.telemetry.export.heatmap.")
            || actionId.equals("devmod.telemetry.export.damage_stats")) {
            return RegistrationSource.CLIENT_TELEMETRY_ACTIONS;
        }

        // Quest
        if (actionId.equals("devmod.quest.task.complete")) return RegistrationSource.CLIENT_QUEST_ACTIONS;

        // Endurance
        if (actionId.startsWith("devmod.endurance.")) return RegistrationSource.CLIENT_ENDURANCE_ACTIONS;

        // Abilities
        if (actionId.startsWith("devmod.ability.")) return RegistrationSource.CLIENT_ABILITY_ACTIONS;

        // Commands (server-side)
        if (actionId.startsWith("devmod.command.")) return RegistrationSource.DEVMOD_ACTIONS_COMMANDS;

        // Arena common (server-side)
        if (actionId.startsWith("devmod.arena.")) return RegistrationSource.ARENA_COMMON;

        // Telemetry admin (server-side)
        if (actionId.startsWith("devmod.telemetry.reload")
            || actionId.startsWith("devmod.telemetry.dump")
            || actionId.startsWith("devmod.telemetry.export.")
            || actionId.startsWith("devmod.telemetry.scan")
            || actionId.startsWith("devmod.telemetry.spawnability")
            || actionId.startsWith("devmod.telemetry.desirelines")
            || actionId.startsWith("devmod.telemetry.dungeons")
            || actionId.startsWith("devmod.telemetry.backtracking")) {
            return RegistrationSource.TELEMETRY_RELOAD;
        }

        // Dashboard
        if (actionId.startsWith("devmod.telemetry.dashboard")) return RegistrationSource.DASHBOARD_COMMAND;

        // Dungeon telemetry
        if (actionId.startsWith("devmod.telemetry.dungeon")) return RegistrationSource.DUNGEON_COMMAND;

        // Test harness
        if (actionId.startsWith("devmod.testing.")) return RegistrationSource.TEST_HARNESS;

        // Mailbox / News
        if (actionId.startsWith("devmod.mailbox.")) return RegistrationSource.MAILBOX_COMMANDS;
        if (actionId.startsWith("devmod.news.")) return RegistrationSource.MAILBOX_COMMANDS;

        // Leaderboard
        if (actionId.startsWith("devmod.leaderboard.")) return RegistrationSource.LEADERBOARD_COMMANDS;

        return RegistrationSource.CLIENT_UI_ACTIONS; // fallback
    }

    // =========================================================================
    // Ground truth extraction from source
    // =========================================================================

    /** Regex to extract field name and string value from ActionIds.java */
    private static final Pattern ACTION_ID_PATTERN = Pattern.compile(
        "public\\s+static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"");

    /**
     * Reads ActionIds.java source and builds a ground-truth inventory map.
     * Keys are action ID string values, values are InventoryEntry records.
     *
     * @return unmodifiable map of action ID -> InventoryEntry
     * @throws IOException if ActionIds.java cannot be read
     */
    public static Map<String, InventoryEntry> loadInventory() throws IOException {
        Path actionIdsPath = Paths.get("src/main/java/com/devmod/actions/ActionIds.java");
        return loadInventory(actionIdsPath);
    }

    /**
     * Reads ActionIds.java source from the given path and builds a ground-truth inventory map.
     *
     * @param actionIdsPath path to ActionIds.java
     * @return unmodifiable map of action ID -> InventoryEntry
     * @throws IOException if the file cannot be read
     */
    public static Map<String, InventoryEntry> loadInventory(Path actionIdsPath) throws IOException {
        String source = Files.readString(actionIdsPath);
        Matcher matcher = ACTION_ID_PATTERN.matcher(source);

        Map<String, InventoryEntry> inventory = new LinkedHashMap<>();
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String actionId = matcher.group(2);
            ActionCategory category = inferCategory(actionId);
            RegistrationSource regSource = inferRegistrationSource(actionId);
            inventory.put(actionId, new InventoryEntry(actionId, fieldName, category, regSource));
        }

        return Collections.unmodifiableMap(inventory);
    }

    /**
     * Returns the set of action IDs that are in the CLIENT_SAFE_ACTIONS whitelist.
     * Manually maintained to match ActionRegistry.CLIENT_SAFE_ACTIONS.
     */
    public static Set<String> getExpectedClientSafeActionIds() {
        return Set.of(
            "devmod.endurance.quest.start",
            "devmod.endurance.quest.continue",
            "devmod.endurance.quest.exit",
            "devmod.quest.task.complete",
            "devmod.ability.dash",
            "devmod.ability.dodge",
            "devmod.ui.radial.open",
            "devmod.ui.settings.open",
            "devmod.ui.radial_settings.open",
            "devmod.ui.editor_hub.open",
            "devmod.ui.endurance_screen.open",
            "devmod.ui.endurance_shop.open",
            "devmod.ui.party.open",
            "devmod.ui.notification_center.open",
            "devmod.ui.mailbox.open",
            "devmod.ui.quest_death.open",
            "devmod.ui.perk_selection.open",
            "devmod.ui.quest_completion.open",
            "devmod.ui.wave_checkpoint.open",
            "devmod.ui.season_pass.open",
            "devmod.hud.impact.toggle",
            "devmod.hud.quest.toggle",
            "devmod.endurance.hud.toggle",
            "devmod.endurance.hud.details_toggle",
            "devmod.ui.onboarding.start",
            "devmod.ui.onboarding.skip"
        );
    }

    /**
     * Known total count of action ID constants in ActionIds.java.
     * Update this when new actions are added.
     */
    public static final int EXPECTED_ACTION_ID_COUNT = 319;
}
