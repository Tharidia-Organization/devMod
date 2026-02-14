package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import com.devmod.DevMod;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.catalog.ActionSpec.PolicyMeta;
import com.devmod.actions.catalog.ActionSpec.UiMeta;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.endurance.EnduranceQuestScreen;
import com.devmod.client.endurance.EnduranceShopScreen;
import com.devmod.client.endurance.EnduranceUiCache;
import com.devmod.client.endurance.PerkSelectionScreen;
import com.devmod.client.endurance.QuestCompletionScreen;
import com.devmod.client.endurance.QuestDeathScreen;
import com.devmod.client.endurance.WaveCheckpointScreen;
import com.devmod.client.notification.ui.NotificationCenterScreen;
import com.devmod.client.overlay.OnboardingOverlay;
import com.devmod.client.party.InvitePopupScreen;
import com.devmod.client.party.PartyScreen;
import com.devmod.client.party.PartyUiCache;
import com.devmod.client.quest.QuestEditorScreen;
import com.devmod.client.testing.QATestingScreen;
import com.devmod.client.testing.TestingSession;
import com.devmod.client.testing.TutorialManager;
import com.devmod.client.testing.UIResponsivenessTestScreen;
import com.devmod.client.ui.RoomBoundsEditorScreen;
import com.devmod.client.ui.WelcomeScreen;
import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.ItemEditorScreen;
import com.devmod.client.ui.hub.TestingHub;
import com.devmod.client.ui.hub.TestingHubState;
import com.devmod.client.ui.screens.ArenaHubScreen;
import com.devmod.client.ui.screens.ArenaResultsScreen;
import com.devmod.client.ui.screens.EditorHubScreen;
import com.devmod.client.ui.screens.MobConfigScreen;
import com.devmod.client.ui.screens.MobEquipmentScreen;
import com.devmod.client.ui.screens.NexusHubScreen;
import com.devmod.client.ui.screens.PlayHubScreen;
import com.devmod.client.ui.testing.VoxelLabScreen;
import com.devmod.client.ui.testing.VoxelLabUiTestScreen;
import com.devmod.client.ui.unified.SettingsCategory;
import com.devmod.client.ui.unified.UnifiedSettingsScreen;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.client.ui.wizard.QuickTestWizard;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.notification.PartyInviteActionData;
import com.devmod.util.I18n;

/**
 * V2 domain registrar for UI actions (screens, editors, hubs, testing, QA,
 * party, notifications, onboarding, quest/endurance screens).
 *
 * <p>Parallel to the existing {@link com.devmod.actions.client.ClientUIActions}
 * registration. Does NOT replace it yet.
 */
public final class UiDomainRegistrar implements DomainRegistrar {

    /** Inner record used by onboarding handlers. */
    private record OnboardingActionPayload(boolean dontShowAgain) {}

    @Override
    public String domainName() {
        return "ui";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        List<ActionSpec> specs = new ArrayList<>();

        // --- Radial / Settings / Config screens ---
        specs.add(spec(ActionIds.UI_RADIAL_OPEN, ActionCategory.UI, ActionType.NAVIGATE_SCREEN,
            "devmod.action.radial_open", "devmod.action.radial_open.desc",
            "minecraft:compass", "Root/Home", false, null));
        specs.add(spec(ActionIds.UI_SETTINGS_OPEN, ActionCategory.CONFIG, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.settings", "devmod.radial.item.settings.desc",
            "minecraft:comparator", "Root/Config/Settings", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_RADIAL_SETTINGS_OPEN, ActionCategory.CONFIG, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.radial_settings", "devmod.radial.item.radial_settings.desc",
            "minecraft:compass", "Root/Config/Radial Menu", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_RADIAL_RESTORE_DEFAULTS, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.radial.item.restore_defaults", "devmod.radial.item.restore_defaults.desc",
            "minecraft:sunflower", "Root/Config/Radial Menu", false, null));
        specs.add(spec(ActionIds.UI_KEYBINDS_OPEN, ActionCategory.CONFIG, ActionType.NAVIGATE_SCREEN,
            "devmod.action.keybinds.open", "devmod.action.keybinds.open.desc",
            "minecraft:tripwire_hook", "Root/Config/Keybinds", false, "screenPrecondition"));

        // --- Item Editor ---
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.item_editor", "devmod.radial.item.item_editor.desc",
            "minecraft:book", "Root/Tools/Item Editor/Auto", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.item_editor.weapon", "devmod.action.item_editor.weapon.desc",
            "minecraft:diamond_sword", "Root/Tools/Item Editor/Weapon", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.item_editor.armor", "devmod.action.item_editor.armor.desc",
            "minecraft:diamond_chestplate", "Root/Tools/Item Editor/Armor", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_SHIELD, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.item_editor.shield", "devmod.action.item_editor.shield.desc",
            "minecraft:shield", "Root/Tools/Item Editor/Shield", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_GENERAL, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.item_editor.general", "devmod.action.item_editor.general.desc",
            "minecraft:book", "Root/Tools/Item Editor/General", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_RECIPE, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.item_editor.recipe", "devmod.action.item_editor.recipe.desc",
            "minecraft:crafting_table", "Root/Tools/Item Editor/Recipe", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_FOOD, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.item_editor.food", "devmod.action.item_editor.food.desc",
            "minecraft:cooked_beef", "Root/Tools/Item Editor/Food", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_FUEL, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.item_editor.fuel", "devmod.action.item_editor.fuel.desc",
            "minecraft:coal", "Root/Tools/Item Editor/Fuel", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ITEM_EDITOR_OPEN_USABLE, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.item_editor.usable", "devmod.action.item_editor.usable.desc",
            "minecraft:snowball", "Root/Tools/Item Editor/Usable", false, "screenPrecondition"));

        // --- Telemetry Dashboard ---
        specs.add(spec(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN, ActionCategory.TELEMETRY, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.dashboard", "devmod.radial.item.dashboard.desc",
            "minecraft:writable_book", "Root/Telemetry/Dashboard", false, "screenPrecondition"));

        // --- Hubs ---
        specs.add(spec(ActionIds.UI_PLAY_HUB_OPEN, ActionCategory.PARTY, ActionType.NAVIGATE_SCREEN,
            "devmod.action.play_hub", "devmod.action.play_hub.desc",
            "minecraft:player_head", "Root/Play/Hub", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ARENA_HUB_OPEN, ActionCategory.ARENA, ActionType.NAVIGATE_SCREEN,
            "devmod.action.arena_hub", "devmod.action.arena_hub.desc",
            "minecraft:target", "Root/Arena/Hub", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_NEXUS_HUB_OPEN, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.nexus_hub", "devmod.action.nexus_hub.desc",
            "minecraft:ender_eye", "Root/Nexus/Hub", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_EDITOR_HUB_OPEN, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.editor_hub", "devmod.radial.item.editor_hub.desc",
            "minecraft:bookshelf", "Root/Tools/Editors/Editor Hub", false, "screenPrecondition"));

        // --- Mob Editor ---
        specs.add(spec(ActionIds.UI_MOB_CONFIG_OPEN, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.mob_editor", "devmod.radial.item.mob_editor.desc",
            "minecraft:lead", "Root/Tools/Mob Editor", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_MOB_EQUIPMENT_OPEN, ActionCategory.TOOLS, ActionType.NAVIGATE_SCREEN,
            "devmod.action.mob_equipment.open", "devmod.action.mob_equipment.open.desc",
            "minecraft:iron_helmet", "Root/Tools/Mob Editor/Equipment", false, "screenPrecondition"));

        // --- Room Bounds ---
        specs.add(spec(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN, ActionCategory.ARENA, ActionType.NAVIGATE_SCREEN,
            "devmod.action.room_bounds_editor", "devmod.action.room_bounds_editor.desc",
            "minecraft:structure_block", "Root/Arena/Bounds/Editor", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ROOM_BOUNDS_POINT_A, ActionCategory.ARENA, ActionType.TRIGGER_EVENT,
            "devmod.action.room_bounds.point_a", "devmod.action.room_bounds.point_a.desc",
            "minecraft:lime_wool", "Root/Arena/Bounds/Point A", false, null));
        specs.add(spec(ActionIds.UI_ROOM_BOUNDS_POINT_B, ActionCategory.ARENA, ActionType.TRIGGER_EVENT,
            "devmod.action.room_bounds.point_b", "devmod.action.room_bounds.point_b.desc",
            "minecraft:red_wool", "Root/Arena/Bounds/Point B", false, null));
        specs.add(spec(ActionIds.UI_ROOM_BOUNDS_SAVE, ActionCategory.ARENA, ActionType.TRIGGER_EVENT,
            "devmod.action.room_bounds.save", "devmod.action.room_bounds.save.desc",
            "minecraft:writable_book", "Root/Arena/Bounds/Save", false, null));
        specs.add(specWithConfirm(ActionIds.UI_ROOM_BOUNDS_DELETE_LAST, ActionCategory.ARENA, ActionType.TRIGGER_EVENT,
            "devmod.action.room_bounds.delete_last", "devmod.action.room_bounds.delete_last.desc",
            "minecraft:barrier", "Root/Arena/Bounds/Delete Last", null));

        // --- Testing ---
        specs.add(spec(ActionIds.UI_TESTING_HUB_OPEN, ActionCategory.TESTING, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.testing_hub", "devmod.radial.item.testing_hub.desc",
            "minecraft:brewing_stand", "Root/Testing/Hub", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_QA_TESTING_OPEN, ActionCategory.TESTING, ActionType.NAVIGATE_SCREEN,
            "devmod.action.qa_testing", "devmod.action.qa_testing.desc",
            "minecraft:book", "Root/Testing/QA Suite", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_RESPONSIVENESS_TEST_OPEN, ActionCategory.TESTING, ActionType.NAVIGATE_SCREEN,
            "devmod.action.ui_responsiveness_test", "devmod.action.ui_responsiveness_test.desc",
            "minecraft:map", "Root/Testing/UI Responsiveness", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_QUICK_TEST_WIZARD_OPEN, ActionCategory.TESTING, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.quick_test", "devmod.radial.item.quick_test.desc",
            "minecraft:lightning_rod", "Root/Testing/Quick Test Wizard", false, "screenPrecondition"));
        specs.add(spec(ActionIds.ARENA_QUICK_TEST_WIZARD_OPEN, ActionCategory.ARENA, ActionType.NAVIGATE_SCREEN,
            "devmod.action.arena.quick_test_wizard", "devmod.action.arena.quick_test_wizard.desc",
            "minecraft:target", "Root/Arena/Quick Test Wizard", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_BADGE_TESTS_OPEN, ActionCategory.TESTING, ActionType.NAVIGATE_SCREEN,
            "devmod.action.badge_tests", "devmod.action.badge_tests.desc",
            "minecraft:nether_star", "Root/Testing/Badge Tests", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_VOXELLAB_UI_TESTS_OPEN, ActionCategory.TESTING, ActionType.NAVIGATE_SCREEN,
            "devmod.action.voxellab_ui_tests", "devmod.action.voxellab_ui_tests.desc",
            "minecraft:glow_item_frame", "Root/Developer/VoxelLab UI", false, "developerModePrecondition"));
        specs.add(spec(ActionIds.UI_VOXELLAB_OPEN, ActionCategory.TESTING, ActionType.NAVIGATE_SCREEN,
            "devmod.action.voxellab", "devmod.action.voxellab.desc",
            "minecraft:pink_concrete", "Root/Developer/VoxelLab", false, "developerModePrecondition"));

        // --- QA Session Actions ---
        specs.add(spec(ActionIds.QA_SESSION_START, ActionCategory.TESTING, ActionType.TRIGGER_EVENT,
            "devmod.action.qa.session.start", "devmod.action.qa.session.start.desc",
            "minecraft:lime_dye", "Root/Testing/QA/Session/Start", false, null));
        specs.add(spec(ActionIds.QA_SESSION_RESUME, ActionCategory.TESTING, ActionType.TRIGGER_EVENT,
            "devmod.action.qa.session.resume", "devmod.action.qa.session.resume.desc",
            "minecraft:clock", "Root/Testing/QA/Session/Resume", false, "qaSessionExistsPrecondition"));
        specs.add(spec(ActionIds.QA_REPORT_SAVE, ActionCategory.TESTING, ActionType.TRIGGER_EVENT,
            "devmod.action.qa.report.save", "devmod.action.qa.report.save.desc",
            "minecraft:writable_book", "Root/Testing/QA/Report/Save", false, "qaSessionActivePrecondition"));
        specs.add(spec(ActionIds.QA_REPORT_COPY, ActionCategory.TESTING, ActionType.TRIGGER_EVENT,
            "devmod.action.qa.report.copy", "devmod.action.qa.report.copy.desc",
            "minecraft:feather", "Root/Testing/QA/Report/Copy", false, "qaSessionActivePrecondition"));
        specs.add(spec(ActionIds.QA_TEST_PASS, ActionCategory.TESTING, ActionType.TRIGGER_EVENT,
            "devmod.action.qa.test.pass", "devmod.action.qa.test.pass.desc",
            "minecraft:green_wool", "Root/Testing/QA/Actions/Pass", false, "qaActiveTestPrecondition"));
        specs.add(spec(ActionIds.QA_TEST_FAIL, ActionCategory.TESTING, ActionType.TRIGGER_EVENT,
            "devmod.action.qa.test.fail", "devmod.action.qa.test.fail.desc",
            "minecraft:red_wool", "Root/Testing/QA/Actions/Fail", false, "qaActiveTestPrecondition"));
        specs.add(spec(ActionIds.QA_TEST_SKIP, ActionCategory.TESTING, ActionType.TRIGGER_EVENT,
            "devmod.action.qa.test.skip", "devmod.action.qa.test.skip.desc",
            "minecraft:yellow_wool", "Root/Testing/QA/Actions/Skip", false, "qaActiveTestPrecondition"));
        specs.add(spec(ActionIds.QA_TEST_AUTO, ActionCategory.TESTING, ActionType.TRIGGER_EVENT,
            "devmod.action.qa.test.auto", "devmod.action.qa.test.auto.desc",
            "minecraft:comparator", "Root/Testing/QA/Actions/Auto", false, "qaAutoTestPrecondition"));

        // --- Party ---
        specs.add(spec(ActionIds.UI_PARTY_OPEN, ActionCategory.PARTY, ActionType.NAVIGATE_SCREEN,
            "devmod.action.party", "devmod.action.party.desc",
            "minecraft:player_head", "Root/Play/Party", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_PARTY_INVITE_POPUP_OPEN, ActionCategory.PARTY, ActionType.NAVIGATE_SCREEN,
            "devmod.action.party_invite", "devmod.action.party_invite.desc",
            "minecraft:paper", "Root/Play/Party/Invites", false, "partyInvitePrecondition"));

        // --- Notifications ---
        specs.add(spec(ActionIds.UI_NOTIFICATION_CENTER_OPEN, ActionCategory.UI, ActionType.NAVIGATE_SCREEN,
            "devmod.action.notification_center", "devmod.action.notification_center.desc",
            "minecraft:bell", "Root/Play/Party/Notifications", false, null));
        specs.add(spec(ActionIds.UI_NOTIFICATION_SETTINGS_OPEN, ActionCategory.CONFIG, ActionType.NAVIGATE_SCREEN,
            "devmod.action.notification_settings", "devmod.action.notification_settings.desc",
            "minecraft:bell", "Root/Config/Notifications", false, null));
        specs.add(spec(ActionIds.UI_MAILBOX_OPEN, ActionCategory.UI, ActionType.NAVIGATE_SCREEN,
            "devmod.action.mailbox", "devmod.action.mailbox.desc",
            "minecraft:writable_book", "Root/Play/Party/Mailbox", false, null));
        specs.add(spec(ActionIds.UI_TESTER_TASKS_OPEN, ActionCategory.DEBUG, ActionType.NAVIGATE_SCREEN,
            "devmod.action.tester_tasks", "devmod.action.tester_tasks.desc",
            "minecraft:written_book", "Root/Admin/Tester Tasks", false, "testerPrecondition"));

        // --- Quest / Endurance ---
        specs.add(spec(ActionIds.UI_QUEST_EDITOR_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.quest_editor", "devmod.radial.item.quest_editor.desc",
            "minecraft:feather", "Root/Play/Quest Tools/Quest Editor", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ENDURANCE_EDITOR_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.action.endurance_editor", "devmod.action.endurance_editor.desc",
            "minecraft:golden_helmet", "Root/Play/Quest Tools/Endurance Editor", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ENDURANCE_SCREEN_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.radial.item.endurance", "devmod.radial.item.endurance.desc",
            "minecraft:golden_helmet", "Root/Play/Endurance/Start", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_ENDURANCE_SHOP_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.action.endurance.shop", "devmod.action.endurance.shop.desc",
            "minecraft:emerald", "Root/Play/Endurance/Shop", false, "screenPrecondition"));
        specs.add(spec(ActionIds.UI_QUEST_DEATH_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.action.quest_death", "devmod.action.quest_death.desc",
            "minecraft:skeleton_skull", "Root/Play/Quest Flow/Death", false, "questDeathPrecondition"));
        specs.add(spec(ActionIds.UI_PERK_SELECTION_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.action.perk_selection", "devmod.action.perk_selection.desc",
            "minecraft:enchanted_book", "Root/Play/Quest Flow/Perk Selection", false, "perkSelectionPrecondition"));
        specs.add(spec(ActionIds.UI_WAVE_CHECKPOINT_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.action.wave_checkpoint", "devmod.action.wave_checkpoint.desc",
            "minecraft:clock", "Root/Play/Quest Flow/Wave Checkpoint", false, "activeQuestPrecondition"));
        specs.add(spec(ActionIds.UI_QUEST_COMPLETION_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.action.quest_completion", "devmod.action.quest_completion.desc",
            "minecraft:nether_star", "Root/Play/Quest Flow/Completion", false, "questCompletionPrecondition"));
        specs.add(spec(ActionIds.UI_ARENA_RESULTS_OPEN, ActionCategory.ENDURANCE, ActionType.NAVIGATE_SCREEN,
            "devmod.action.arena_results", "devmod.action.arena_results.desc",
            "minecraft:golden_sword", "Root/Play/Quest Flow/Arena Results", false, null));

        // --- Onboarding / Welcome ---
        specs.add(spec(ActionIds.UI_WELCOME_OPEN, ActionCategory.UI, ActionType.NAVIGATE_SCREEN,
            "devmod.action.welcome", "devmod.action.welcome.desc",
            "minecraft:book", "Root/Config/Welcome", false, null));
        specs.add(spec(ActionIds.UI_ONBOARDING_START, ActionCategory.UI, ActionType.TRIGGER_EVENT,
            "devmod.action.onboarding.start", "devmod.action.onboarding.start.desc",
            "minecraft:writable_book", "Root/Config/Onboarding/Start", false, null));
        specs.add(spec(ActionIds.UI_ONBOARDING_SKIP, ActionCategory.UI, ActionType.TRIGGER_EVENT,
            "devmod.action.onboarding.skip", "devmod.action.onboarding.skip.desc",
            "minecraft:barrier", "Root/Config/Onboarding/Skip", false, "onboardingActivePrecondition"));

        // --- Season Pass / Character Sheet / Leaderboard / Journal / LFG ---
        specs.add(spec(ActionIds.UI_SEASON_PASS_OPEN, ActionCategory.UI, ActionType.NAVIGATE_SCREEN,
            "devmod.action.season_pass", "devmod.action.season_pass.desc",
            "minecraft:gold_ingot", "Root/Play/Endurance/Season Pass", false, null));
        specs.add(spec(ActionIds.UI_CHARACTER_SHEET_OPEN, ActionCategory.UI, ActionType.NAVIGATE_SCREEN,
            "devmod.action.character_sheet", "devmod.action.character_sheet.desc",
            "minecraft:writable_book", "Root/Play/Character Sheet", false, null));
        specs.add(spec(ActionIds.UI_LEADERBOARD_OPEN, ActionCategory.UI, ActionType.NAVIGATE_SCREEN,
            "devmod.action.leaderboard", "devmod.action.leaderboard.desc",
            "minecraft:golden_sword", "Root/Play/Leaderboard", false, null));
        specs.add(spec(ActionIds.UI_QUEST_JOURNAL_OPEN, ActionCategory.UI, ActionType.NAVIGATE_SCREEN,
            "devmod.action.quest_journal", "devmod.action.quest_journal.desc",
            "minecraft:book", "Root/Play/Quest Journal", false, null));
        specs.add(spec(ActionIds.UI_LFG_OPEN, ActionCategory.PARTY, ActionType.NAVIGATE_SCREEN,
            "devmod.action.lfg", "devmod.action.lfg.desc",
            "minecraft:spyglass", "Root/Play/Party/LFG", false, null));

        return specs;
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        registry.register(ActionIds.UI_RADIAL_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("radial_menu",
                () -> new com.devmod.client.ui.radial.RadialMenuScreen()));

        registry.register(ActionIds.UI_SETTINGS_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("settings",
                () -> new UnifiedSettingsScreen(null)));

        registry.register(ActionIds.UI_RADIAL_SETTINGS_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("radial_settings",
                () -> new UnifiedSettingsScreen(null, SettingsCategory.RADIAL)));

        registry.register(ActionIds.UI_RADIAL_RESTORE_DEFAULTS, ctx -> {
            com.devmod.client.ui.radial.RadialMenuConfig.INSTANCE.resetToDefaults();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    java.util.Objects.requireNonNull(
                        Component.translatable("devmod.radial.message.defaults_restored"),
                        "message"), false);
            }
        });

        registry.register(ActionIds.UI_KEYBINDS_OPEN, ctx -> {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.gui.screens.Screen parent =
                java.util.Objects.requireNonNullElseGet(mc.screen,
                    () -> new net.minecraft.client.gui.screens.Screen(
                        java.util.Objects.requireNonNull(Component.empty(), "emptyTitle")) {});
            var options = java.util.Objects.requireNonNull(mc.options, "options");
            com.devmod.client.ui.ScreenSafety.openSafe("keybinds", parent,
                () -> new KeyBindsScreen(java.util.Objects.requireNonNull(parent, "parent"), options));
        });

        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO, UiDomainRegistrar::openItemEditorAuto);
        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON, ctx -> openItemEditorTab(ctx, EditorStartTab.WEAPON));
        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR, ctx -> openItemEditorTab(ctx, EditorStartTab.ARMOR));
        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_SHIELD, ctx -> openItemEditorTab(ctx, EditorStartTab.ARMOR));
        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_GENERAL, ctx -> openItemEditorTab(ctx, EditorStartTab.GENERAL));
        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_RECIPE, ctx -> openItemEditorTab(ctx, EditorStartTab.RECIPE));
        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_FOOD, ctx -> openItemEditorTab(ctx, EditorStartTab.FOOD));
        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_FUEL, ctx -> openItemEditorTab(ctx, EditorStartTab.FUEL));
        registry.register(ActionIds.UI_ITEM_EDITOR_OPEN_USABLE, ctx -> openItemEditorTab(ctx, EditorStartTab.USABLE));

        registry.register(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN, ctx -> {
            net.minecraft.client.gui.screens.Screen parent =
                ctx.getPayload(net.minecraft.client.gui.screens.Screen.class);
            com.devmod.client.ui.ScreenSafety.openSafe("telemetry_dashboard", parent,
                () -> new com.devmod.client.ui.screens.TelemetryDashboardScreen(parent));
        });

        registry.register(ActionIds.UI_PLAY_HUB_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("play_hub", () -> new PlayHubScreen(null)));
        registry.register(ActionIds.UI_ARENA_HUB_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("arena_hub", () -> new ArenaHubScreen(null)));
        registry.register(ActionIds.UI_NEXUS_HUB_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("nexus_hub", () -> new NexusHubScreen(null)));
        registry.register(ActionIds.UI_EDITOR_HUB_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("editor_hub", () -> new EditorHubScreen(null)));

        registry.register(ActionIds.UI_MOB_CONFIG_OPEN, UiDomainRegistrar::openMobConfig);
        registry.register(ActionIds.UI_MOB_EQUIPMENT_OPEN, UiDomainRegistrar::openMobEquipment);

        registry.register(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("room_bounds_editor", () -> new RoomBoundsEditorScreen()));
        registry.register(ActionIds.UI_ROOM_BOUNDS_POINT_A, RoomBoundsEditorScreen.Actions::setPointA);
        registry.register(ActionIds.UI_ROOM_BOUNDS_POINT_B, RoomBoundsEditorScreen.Actions::setPointB);
        registry.register(ActionIds.UI_ROOM_BOUNDS_SAVE, RoomBoundsEditorScreen.Actions::saveRoom);
        registry.register(ActionIds.UI_ROOM_BOUNDS_DELETE_LAST, RoomBoundsEditorScreen.Actions::deleteLastRoom);

        registry.register(ActionIds.UI_TESTING_HUB_OPEN, ctx -> {
            if (TestingHubState.INSTANCE.isMinimized()) {
                TestingHub.restoreFromHud();
                return;
            }
            com.devmod.client.ui.ScreenSafety.openSafe("testing_hub", () -> new TestingHub());
        });
        registry.register(ActionIds.UI_QA_TESTING_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("qa_testing", () -> new QATestingScreen()));
        registry.register(ActionIds.UI_RESPONSIVENESS_TEST_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("ui_responsiveness_test", () -> new UIResponsivenessTestScreen()));
        registry.register(ActionIds.UI_QUICK_TEST_WIZARD_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("quick_test_wizard", () -> new QuickTestWizard()));
        registry.register(ActionIds.ARENA_QUICK_TEST_WIZARD_OPEN, UiDomainRegistrar::openArenaQuickTestWizard);
        registry.register(ActionIds.UI_BADGE_TESTS_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("badge_tests",
                () -> new com.devmod.client.testing.BadgeTestScreen()));
        registry.register(ActionIds.UI_VOXELLAB_UI_TESTS_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("voxellab_ui_tests", () -> new VoxelLabUiTestScreen()));
        registry.register(ActionIds.UI_VOXELLAB_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("voxellab", () -> new VoxelLabScreen()));

        // QA session
        registry.register(ActionIds.QA_SESSION_START, QATestingScreen.Actions::startSession);
        registry.register(ActionIds.QA_SESSION_RESUME, QATestingScreen.Actions::resumeSession);
        registry.register(ActionIds.QA_REPORT_SAVE, QATestingScreen.Actions::saveReport);
        registry.register(ActionIds.QA_REPORT_COPY, QATestingScreen.Actions::copyReport);
        registry.register(ActionIds.QA_TEST_PASS, QATestingScreen.Actions::passTest);
        registry.register(ActionIds.QA_TEST_FAIL, QATestingScreen.Actions::failTest);
        registry.register(ActionIds.QA_TEST_SKIP, QATestingScreen.Actions::skipTest);
        registry.register(ActionIds.QA_TEST_AUTO, QATestingScreen.Actions::autoCheckTest);

        // Party
        registry.register(ActionIds.UI_PARTY_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("party_screen", () -> new PartyScreen()));
        registry.register(ActionIds.UI_PARTY_INVITE_POPUP_OPEN, UiDomainRegistrar::openPartyInvite);

        // Notifications
        registry.register(ActionIds.UI_NOTIFICATION_CENTER_OPEN, ctx ->
            NotificationCenterScreen.open("NOTIFICATIONS", null));
        registry.register(ActionIds.UI_NOTIFICATION_SETTINGS_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("notification_settings",
                () -> new com.devmod.client.notification.ui.NotificationSettingsScreen(null)));
        registry.register(ActionIds.UI_MAILBOX_OPEN, ctx ->
            NotificationCenterScreen.open("MAILBOX", null));
        registry.register(ActionIds.UI_TESTER_TASKS_OPEN, ctx ->
            NotificationCenterScreen.open("TASKS", null));

        // Quest / Endurance
        registry.register(ActionIds.UI_QUEST_EDITOR_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("quest_editor", () -> new QuestEditorScreen()));
        registry.register(ActionIds.UI_ENDURANCE_EDITOR_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("endurance_editor", () -> new QuestEditorScreen(true)));
        registry.register(ActionIds.UI_ENDURANCE_SCREEN_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("endurance_quest", () -> new EnduranceQuestScreen()));
        registry.register(ActionIds.UI_ENDURANCE_SHOP_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("endurance_shop", () -> new EnduranceShopScreen()));
        registry.register(ActionIds.UI_QUEST_DEATH_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("quest_death", () -> new QuestDeathScreen()));
        registry.register(ActionIds.UI_PERK_SELECTION_OPEN, UiDomainRegistrar::openPerkSelection);
        registry.register(ActionIds.UI_WAVE_CHECKPOINT_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("wave_checkpoint", () -> new WaveCheckpointScreen()));
        registry.register(ActionIds.UI_QUEST_COMPLETION_OPEN, UiDomainRegistrar::openQuestCompletion);
        registry.register(ActionIds.UI_ARENA_RESULTS_OPEN, ctx -> {
            ArenaResultsScreen.ArenaRunData demo = new ArenaResultsScreen.ArenaRunData(
                true, 10, 10, 360000, 127, 8500, "S", 42,
                5, 120.5f, 3450.0f, 0, 18, 0.35f,
                java.util.List.of("50-Hit Combo!", "Perfect Dodge x3!", "No Deaths!"));
            com.devmod.client.ui.ScreenSafety.openSafe("arena_results",
                () -> new ArenaResultsScreen(demo));
        });

        // Welcome / Onboarding
        registry.register(ActionIds.UI_WELCOME_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("welcome", () -> new WelcomeScreen()));
        registry.register(ActionIds.UI_ONBOARDING_START, ctx -> {
            var payload = ctx.getPayload(OnboardingActionPayload.class);
            boolean dontShowAgain = payload != null && payload.dontShowAgain();
            playUiClick(1.0f);
            applyWelcomePreference(dontShowAgain);
            TutorialManager.INSTANCE.setPhase(TutorialManager.TutorialPhase.WELCOME);
            TutorialManager.INSTANCE.setOnboardingCompleted(false);
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(null);
            mc.execute(OnboardingOverlay::start);
        });
        registry.register(ActionIds.UI_ONBOARDING_SKIP, ctx -> {
            var payload = ctx.getPayload(OnboardingActionPayload.class);
            if (payload != null) {
                playUiClick(0.8f);
                applyWelcomePreference(payload.dontShowAgain());
                TutorialManager.INSTANCE.setOnboardingCompleted(true);
                Minecraft.getInstance().setScreen(null);
                return;
            }
            OnboardingOverlay.handleEscape();
        });

        // Season Pass / Character Sheet / Leaderboard / Journal / LFG
        registry.register(ActionIds.UI_SEASON_PASS_OPEN, ctx -> {
            Integer tier = ctx.getPayload(Integer.class);
            if (tier != null && tier > 0) {
                com.devmod.client.ui.season.SeasonPassScreen.openAtTier(tier);
            } else {
                com.devmod.client.ui.season.SeasonPassScreen.open();
            }
        });
        registry.register(ActionIds.UI_CHARACTER_SHEET_OPEN, ctx ->
            Minecraft.getInstance().setScreen(new com.devmod.client.ui.screens.CharacterSheetScreen()));
        registry.register(ActionIds.UI_LEADERBOARD_OPEN, ctx ->
            Minecraft.getInstance().setScreen(new com.devmod.client.ui.screens.LeaderboardScreen()));
        registry.register(ActionIds.UI_QUEST_JOURNAL_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("quest_journal",
                () -> new com.devmod.client.ui.screens.QuestJournalScreen(null)));
        registry.register(ActionIds.UI_LFG_OPEN, ctx ->
            com.devmod.client.ui.ScreenSafety.openSafe("lfg",
                () -> new com.devmod.client.ui.screens.LFGScreen(null)));
    }

    // ── Spec helpers ──

    private static ActionSpec spec(String id, ActionCategory category, ActionType type,
                                    String labelKey, String descKey, String icon,
                                    String menuPath, boolean isToggle, String preconditionRef) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(category)
            .actionType(type)
            .ui(labelKey, descKey, icon, menuPath, isToggle)
            .policy(false, 0, preconditionRef)
            .handlerRef(id)
            .build();
    }

    private static ActionSpec specWithConfirm(String id, ActionCategory category, ActionType type,
                                               String labelKey, String descKey, String icon,
                                               String menuPath, String preconditionRef) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(category)
            .actionType(type)
            .ui(labelKey, descKey, icon, menuPath, false)
            .policy(true, 0, preconditionRef)
            .handlerRef(id)
            .build();
    }

    // ── Handler helpers (replicate ClientUIActions private methods) ──

    private static void applyWelcomePreference(boolean dontShowAgain) {
        if (dontShowAgain) {
            SettingsManager.INSTANCE.getSettings().onboarding.hasSeenWelcome = true;
            SettingsManager.INSTANCE.getSettings().onboarding.tutorialCompleted = true;
        }
        SettingsManager.INSTANCE.markDirty();
        SettingsManager.INSTANCE.save();
    }

    private static void playUiClick(float pitch) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.sounds.SoundEvent soundEvent = java.util.Objects.requireNonNull(
            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), "UI_BUTTON_CLICK sound");
        net.minecraft.client.resources.sounds.SoundInstance soundInstance =
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(soundEvent, 1.0f, pitch);
        if (soundInstance != null) {
            mc.getSoundManager().play(soundInstance);
        }
    }

    private static void openItemEditorAuto(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return;
        ItemStack heldItem = player.getMainHandItem().copy();
        if (heldItem.isEmpty()) {
            context.sendSuccess(I18n.translate("devmod.message.item_editor_hint"), true);
            return;
        }
        if (heldItem.getItem() instanceof ArmorItem) {
            com.devmod.client.ui.ScreenSafety.openSafe("item_editor_armor",
                () -> new ItemEditorScreen(heldItem, EditorStartTab.ARMOR));
        } else {
            com.devmod.client.ui.ScreenSafety.openSafe("item_editor_weapon",
                () -> new ItemEditorScreen(heldItem, EditorStartTab.WEAPON));
        }
    }

    private static void openItemEditorTab(ActionContext context, EditorStartTab tab) {
        ItemStack payloadItem = context.getPayload(ItemStack.class);
        if (payloadItem != null && !payloadItem.isEmpty()) {
            com.devmod.client.ui.ScreenSafety.openSafe("item_editor",
                () -> new ItemEditorScreen(payloadItem.copy(), tab));
            return;
        }
        Player player = context.getPlayer();
        if (player == null) return;
        ItemStack heldItem = player.getMainHandItem().copy();
        if (heldItem.isEmpty()) {
            context.sendSuccess(I18n.translate("devmod.message.must_hold_item"), true);
            return;
        }
        com.devmod.client.ui.ScreenSafety.openSafe("item_editor",
            () -> new ItemEditorScreen(heldItem, tab));
    }

    private static void openMobConfig(ActionContext context) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.Mob payloadMob = context.getPayload(net.minecraft.world.entity.Mob.class);
        if (payloadMob != null) {
            com.devmod.client.ui.ScreenSafety.openSafe("mob_config", () -> new MobConfigScreen(payloadMob));
            return;
        }
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
                com.devmod.client.ui.ScreenSafety.openSafe("mob_config", () -> new MobConfigScreen(mob));
            } else {
                context.sendSuccess(I18n.translate("devmod.render.target_not_mob"), true);
            }
        } else {
            context.sendSuccess(I18n.translate("devmod.render.no_entity_targeted"), true);
        }
    }

    private static void openMobEquipment(ActionContext context) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen parentScreen = mc.screen;
        net.minecraft.world.entity.Mob targetMob = null;
        net.minecraft.world.entity.Mob payloadMob = context.getPayload(net.minecraft.world.entity.Mob.class);
        if (payloadMob != null) {
            targetMob = payloadMob;
        } else if (mc.screen instanceof MobConfigScreen screen) {
            targetMob = screen.getMob();
            parentScreen = screen;
        } else if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
            && entityHit.getEntity() instanceof net.minecraft.world.entity.Mob hitMob) {
            targetMob = hitMob;
        }
        if (targetMob == null) {
            context.sendFailure(Component.translatable("devmod.action.mob_equipment.requires_target"));
            return;
        }
        final net.minecraft.world.entity.Mob finalMob = targetMob;
        final net.minecraft.client.gui.screens.Screen finalParent = parentScreen;
        com.devmod.client.ui.ScreenSafety.openSafe("mob_equipment", finalParent,
            () -> new MobEquipmentScreen(finalMob, finalParent));
    }

    private static void openPerkSelection(ActionContext context) {
        PerkChoicesPayload payload = context.getPayload(PerkChoicesPayload.class);
        if (payload == null) payload = EnduranceUiCache.getLastPerkChoices();
        if (payload == null) {
            context.sendFailure(Component.translatable("devmod.action.perk_selection.missing"));
            return;
        }
        final PerkChoicesPayload finalPayload = payload;
        com.devmod.client.ui.ScreenSafety.openSafe("perk_selection",
            () -> new PerkSelectionScreen(finalPayload.waveNumber(), finalPayload.choices(), finalPayload.expiresAt()));
    }

    private static void openQuestCompletion(ActionContext context) {
        QuestCompletionPayload payload = context.getPayload(QuestCompletionPayload.class);
        if (payload == null) payload = EnduranceUiCache.getLastQuestCompletion();
        if (payload == null) {
            context.sendFailure(Component.translatable("devmod.action.quest_completion.missing"));
            return;
        }
        final QuestCompletionPayload finalPayload = payload;
        com.devmod.client.ui.ScreenSafety.openSafe("quest_completion",
            () -> new QuestCompletionScreen(finalPayload));
    }

    private static void openPartyInvite(ActionContext context) {
        PartyInviteActionData payload = context.getPayload(PartyInviteActionData.class);
        if (payload == null) payload = PartyUiCache.getActiveInvite();
        if (payload == null) {
            context.sendFailure(Component.translatable("devmod.action.party_invite.missing"));
            return;
        }
        final PartyInviteActionData finalPayload = payload;
        com.devmod.client.ui.ScreenSafety.openSafe("party_invite",
            () -> InvitePopupScreen.fromActionData(finalPayload));
    }

    private static void openArenaQuickTestWizard(ActionContext context) {
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        com.devmod.client.arena.ui.ArenaTestWizard.open(registry, config -> {
            Integer playerCount = config.playerCountOverride();
            if (playerCount != null && playerCount > 0) {
                context.sendSuccess(Component.translatable("devmod.action.arena.quick_test_wizard.player_count_ignored"), true);
            }
            String templateId = config.templateId();
            if (!com.devmod.actions.CommandSanitizer.isValidTemplateId(templateId)) {
                context.sendFailure(Component.literal("Invalid template ID"));
                return;
            }
            if (config.forceTemplate()) {
                context.executeCommand(com.devmod.actions.CommandSanitizer.buildTemplateCommandWithInt("arena force", templateId, 5));
            }
            if (config.dryRun()) {
                context.executeCommand(com.devmod.actions.CommandSanitizer.buildTemplateCommand("arena validate", templateId));
            } else {
                context.executeCommand(com.devmod.actions.CommandSanitizer.buildTemplateCommand("arena create", templateId));
            }
        });
    }
}
