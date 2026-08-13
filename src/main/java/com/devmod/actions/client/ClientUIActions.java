package com.devmod.actions.client;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.DevMod;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionPrecondition;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.arena.command.ArenaActionRegistry;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.endurance.EnduranceQuestScreen;
import com.devmod.client.endurance.EnduranceShopScreen;
import com.devmod.client.endurance.EnduranceUiCache;
import com.devmod.client.endurance.PerkSelectionScreen;
import com.devmod.client.endurance.QuestCompletionScreen;
import com.devmod.client.endurance.QuestDeathScreen;
import com.devmod.client.endurance.QuestExitConfirmScreen;
import com.devmod.client.endurance.WaveCheckpointScreen;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.notification.ui.NotificationCenterScreen;
import com.devmod.client.overlay.OnboardingOverlay;
import com.devmod.client.panels.context.ContextDetector;
import com.devmod.client.party.InvitePopupScreen;
import com.devmod.client.party.PartyScreen;
import com.devmod.client.party.PartyUiCache;
import com.devmod.client.quest.QuestEditorScreen;
import com.devmod.client.testing.QATestingScreen;
import com.devmod.client.testing.UIResponsivenessTestScreen;
import com.devmod.client.testing.TestingSession;
import com.devmod.client.testing.TutorialManager;
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
import com.devmod.config.TesterModality;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.notification.PartyInviteActionData;
import com.devmod.util.I18n;

/**
 * UI-related client actions: screens, editors, hubs, testing, QA, radial menu,
 * party, notifications, onboarding, quest/endurance screens.
 */
public final class ClientUIActions {

    private ClientUIActions() {}

    // ── Precondition helpers (package-visible for facade) ──

    static ActionPrecondition screenPrecondition() {
        return ActionPreconditions.clientOnly()
            .and(ActionPreconditions.screenClosed())
            .and(ActionPreconditions.withMessage(
                context -> !ContextDetector.INSTANCE.isCurrentlyCombat(),
                "devmod.action.requires_not_in_combat"
            ));
    }

    static ActionPrecondition qaAutoTestPrecondition() {
        return qaActiveTestPrecondition().and(ActionPreconditions.withMessage(
            QATestingScreen.Actions::hasAutoTest,
            "devmod.action.testing.requires_auto_test"
        ));
    }

    static ActionPrecondition partyInvitePrecondition() {
        return ActionPreconditions.clientOnly().and(
            ActionPreconditions.withMessage(
                context -> PartyUiCache.getActiveInvite() != null
                    || context.getPayload(PartyInviteActionData.class) != null,
                "devmod.action.party_invite.missing"
            ));
    }

    static ActionPrecondition questDeathPrecondition() {
        return ActionPreconditions.clientOnly().and(
            ActionPreconditions.withMessage(
                context -> ClientQuestCache.isAwaitingRespawn(),
                "devmod.action.requires_respawn"
            ));
    }

    static ActionPrecondition perkSelectionPrecondition() {
        return ActionPreconditions.clientOnly().and(
            ActionPreconditions.withMessage(
                context -> context.getPayload(PerkChoicesPayload.class) != null
                    || EnduranceUiCache.getLastPerkChoices() != null,
                "devmod.action.perk_selection.missing"
            ));
    }

    static ActionPrecondition questCompletionPrecondition() {
        return ActionPreconditions.clientOnly().and(
            ActionPreconditions.withMessage(
                context -> context.getPayload(QuestCompletionPayload.class) != null
                    || EnduranceUiCache.getLastQuestCompletion() != null,
                "devmod.action.quest_completion.missing"
            ));
    }

    static ActionPrecondition onboardingActivePrecondition() {
        return ActionPreconditions.clientOnly().and(
            ActionPreconditions.withMessage(
                context -> OnboardingOverlay.isActive()
                    || context.getPayload(OnboardingActionPayload.class) != null,
                "devmod.action.onboarding.inactive"
            ));
    }

    static ActionPrecondition uiScreenPrecondition() {
        return ActionPreconditions.clientOnly()
            .and(ActionPreconditions.screenClosed());
    }

    static ActionPrecondition qaSessionActivePrecondition() {
        return ActionPreconditions.clientOnly()
            .and(ActionPreconditions.withMessage(
                context -> TestingSession.INSTANCE.isSessionActive(),
                "devmod.action.testing.session_inactive"
            ));
    }

    static ActionPrecondition developerModePrecondition() {
        return screenPrecondition()
            .and(ActionPreconditions.withMessage(
                context -> SettingsManager.INSTANCE.getSettings().debug.developerMode,
                "devmod.action.requires_developer_mode"
            ));
    }

    static ActionPrecondition testerPrecondition() {
        return screenPrecondition()
            .and(ActionPreconditions.withMessage(
                context -> com.devmod.mailbox.client.ClientMailboxAccess.isTester(),
                "devmod.action.requires_tester"
            ));
    }

    static ActionPrecondition qaSessionExistsPrecondition() {
        return ActionPreconditions.clientOnly()
            .and(ActionPreconditions.withMessage(
                context -> TestingSession.INSTANCE.hasExistingSession(),
                "devmod.action.testing.session_missing"
            ));
    }

    static ActionPrecondition qaActiveTestPrecondition() {
        return qaSessionActivePrecondition()
            .and(ActionPreconditions.withMessage(
                QATestingScreen.Actions::hasActiveTest,
                "devmod.action.testing.requires_active_test"
            ));
    }

    // ── Registration ──

    static void registerActions() {
        ArenaActionRegistry.registerClientActions();
        ActionRegistry.register(RadialAction.builder(ActionIds.UI_RADIAL_OPEN)
            .labelKey("devmod.action.radial_open")
            .descriptionKey("devmod.action.radial_open.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Home")
            .icon(Items.COMPASS)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.screenClosed()))
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "radial_menu",
                () -> new com.devmod.client.ui.radial.RadialMenuScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_SETTINGS_OPEN)
            .labelKey("devmod.radial.item.settings")
            .descriptionKey("devmod.radial.item.settings.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Settings")
            .icon(Items.COMPARATOR)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "settings",
                () -> new UnifiedSettingsScreen(null)))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_RADIAL_SETTINGS_OPEN)
            .labelKey("devmod.radial.item.radial_settings")
            .descriptionKey("devmod.radial.item.radial_settings.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Radial Menu")
            .icon(Items.COMPASS)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "radial_settings",
                () -> new UnifiedSettingsScreen(null, SettingsCategory.RADIAL)))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_RADIAL_RESTORE_DEFAULTS)
            .labelKey("devmod.radial.item.restore_defaults")
            .descriptionKey("devmod.radial.item.restore_defaults.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Radial Menu")
            .icon(Items.SUNFLOWER)
            .precondition(ActionPreconditions.always())
            .handler(context -> {
                com.devmod.client.ui.radial.RadialMenuConfig.INSTANCE.resetToDefaults();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        java.util.Objects.requireNonNull(
                            Component.translatable("devmod.radial.message.defaults_restored"),
                            "message"), false);
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_KEYBINDS_OPEN)
            .labelKey("devmod.action.keybinds.open")
            .descriptionKey("devmod.action.keybinds.open.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Keybinds")
            .icon(Items.TRIPWIRE_HOOK)
            .precondition(screenPrecondition())
            .handler(context -> {
                Minecraft mc = Minecraft.getInstance();
                net.minecraft.client.gui.screens.Screen parent =
                    java.util.Objects.requireNonNullElseGet(mc.screen,
                        () -> new net.minecraft.client.gui.screens.Screen(
                            java.util.Objects.requireNonNull(Component.empty(), "emptyTitle")) {});
                var options = java.util.Objects.requireNonNull(mc.options, "options");
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "keybinds",
                    parent,
                    () -> new KeyBindsScreen(java.util.Objects.requireNonNull(parent, "parent"), options));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO)
            .labelKey("devmod.radial.item.item_editor")
            .descriptionKey("devmod.radial.item.item_editor.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Auto")
            .icon(Items.BOOK)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openItemEditorAuto)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON)
            .labelKey("devmod.action.item_editor.weapon")
            .descriptionKey("devmod.action.item_editor.weapon.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Weapon")
            .icon(Items.DIAMOND_SWORD)
            .precondition(screenPrecondition())
            .handler(context -> openItemEditorTab(context, EditorStartTab.WEAPON))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR)
            .labelKey("devmod.action.item_editor.armor")
            .descriptionKey("devmod.action.item_editor.armor.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Armor")
            .icon(Items.DIAMOND_CHESTPLATE)
            .precondition(screenPrecondition())
            .handler(context -> openItemEditorTab(context, EditorStartTab.ARMOR))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_SHIELD)
            .labelKey("devmod.action.item_editor.shield")
            .descriptionKey("devmod.action.item_editor.shield.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Shield")
            .icon(Items.SHIELD)
            .precondition(screenPrecondition())
            .handler(context -> openItemEditorTab(context, EditorStartTab.ARMOR))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_GENERAL)
            .labelKey("devmod.action.item_editor.general")
            .descriptionKey("devmod.action.item_editor.general.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/General")
            .icon(Items.BOOK)
            .precondition(screenPrecondition())
            .handler(context -> openItemEditorTab(context, EditorStartTab.GENERAL))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_RECIPE)
            .labelKey("devmod.action.item_editor.recipe")
            .descriptionKey("devmod.action.item_editor.recipe.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Recipe")
            .icon(Items.CRAFTING_TABLE)
            .precondition(screenPrecondition())
            .handler(context -> openItemEditorTab(context, EditorStartTab.RECIPE))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_FOOD)
            .labelKey("devmod.action.item_editor.food")
            .descriptionKey("devmod.action.item_editor.food.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Food")
            .icon(Items.COOKED_BEEF)
            .precondition(screenPrecondition())
            .handler(context -> openItemEditorTab(context, EditorStartTab.FOOD))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_FUEL)
            .labelKey("devmod.action.item_editor.fuel")
            .descriptionKey("devmod.action.item_editor.fuel.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Fuel")
            .icon(Items.COAL)
            .precondition(screenPrecondition())
            .handler(context -> openItemEditorTab(context, EditorStartTab.FUEL))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_USABLE)
            .labelKey("devmod.action.item_editor.usable")
            .descriptionKey("devmod.action.item_editor.usable.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Usable")
            .icon(Items.SNOWBALL)
            .precondition(screenPrecondition())
            .handler(context -> openItemEditorTab(context, EditorStartTab.USABLE))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN)
            .labelKey("devmod.radial.item.dashboard")
            .descriptionKey("devmod.radial.item.dashboard.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dashboard")
            .icon(Items.WRITABLE_BOOK)
            .precondition(screenPrecondition())
            .handler(context -> {
                net.minecraft.client.gui.screens.Screen parent =
                    context.getPayload(net.minecraft.client.gui.screens.Screen.class);
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "telemetry_dashboard",
                    parent,
                    () -> new com.devmod.client.ui.screens.TelemetryDashboardScreen(parent));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_PLAY_HUB_OPEN)
            .labelKey("devmod.action.play_hub")
            .descriptionKey("devmod.action.play_hub.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Hub")
            .icon(Items.PLAYER_HEAD)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openPlayHub)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ARENA_HUB_OPEN)
            .labelKey("devmod.action.arena_hub")
            .descriptionKey("devmod.action.arena_hub.desc")
            .category(ActionCategory.ARENA)
            .menuPath("Root/Arena/Hub")
            .icon(Items.TARGET)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openArenaHub)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_NEXUS_HUB_OPEN)
            .labelKey("devmod.action.nexus_hub")
            .descriptionKey("devmod.action.nexus_hub.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Nexus/Hub")
            .icon(Items.ENDER_EYE)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openNexusHub)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_EDITOR_HUB_OPEN)
            .labelKey("devmod.radial.item.editor_hub")
            .descriptionKey("devmod.radial.item.editor_hub.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Editors/Editor Hub")
            .icon(Items.BOOKSHELF)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openEditorHub)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_MOB_CONFIG_OPEN)
            .labelKey("devmod.radial.item.mob_editor")
            .descriptionKey("devmod.radial.item.mob_editor.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Mob Editor")
            .icon(Items.LEAD)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openMobConfig)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_MOB_EQUIPMENT_OPEN)
            .labelKey("devmod.action.mob_equipment.open")
            .descriptionKey("devmod.action.mob_equipment.open.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Mob Editor/Equipment")
            .icon(Items.IRON_HELMET)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openMobEquipment)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN)
            .labelKey("devmod.action.room_bounds_editor")
            .descriptionKey("devmod.action.room_bounds_editor.desc")
            .category(ActionCategory.ARENA)
            .menuPath("Root/Arena/Bounds/Editor")
            .icon(Items.STRUCTURE_BLOCK)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "room_bounds_editor",
                () -> new RoomBoundsEditorScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_POINT_A)
            .labelKey("devmod.action.room_bounds.point_a")
            .descriptionKey("devmod.action.room_bounds.point_a.desc")
            .category(ActionCategory.ARENA)
            .menuPath("Root/Arena/Bounds/Point A")
            .icon(Items.LIME_WOOL)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.requiresPlayer()))
            .handler(RoomBoundsEditorScreen.Actions::setPointA)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_POINT_B)
            .labelKey("devmod.action.room_bounds.point_b")
            .descriptionKey("devmod.action.room_bounds.point_b.desc")
            .category(ActionCategory.ARENA)
            .menuPath("Root/Arena/Bounds/Point B")
            .icon(Items.RED_WOOL)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.requiresPlayer()))
            .handler(RoomBoundsEditorScreen.Actions::setPointB)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_SAVE)
            .labelKey("devmod.action.room_bounds.save")
            .descriptionKey("devmod.action.room_bounds.save.desc")
            .category(ActionCategory.ARENA)
            .menuPath("Root/Arena/Bounds/Save")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.requiresPlayer()))
            .handler(RoomBoundsEditorScreen.Actions::saveRoom)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_DELETE_LAST)
            .labelKey("devmod.action.room_bounds.delete_last")
            .descriptionKey("devmod.action.room_bounds.delete_last.desc")
            .category(ActionCategory.ARENA)
            .menuPath("Root/Arena/Bounds/Delete Last")
            .icon(Items.BARRIER)
            .requiresConfirm(true)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.requiresPlayer()))
            .handler(RoomBoundsEditorScreen.Actions::deleteLastRoom)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_TESTING_HUB_OPEN)
            .labelKey("devmod.radial.item.testing_hub")
            .descriptionKey("devmod.radial.item.testing_hub.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Hub")
            .icon(Items.BREWING_STAND)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openTestingHub)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QA_TESTING_OPEN)
            .labelKey("devmod.action.qa_testing")
            .descriptionKey("devmod.action.qa_testing.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA Suite")
            .icon(Items.BOOK)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "qa_testing",
                () -> new QATestingScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_RESPONSIVENESS_TEST_OPEN)
            .labelKey("devmod.action.ui_responsiveness_test")
            .descriptionKey("devmod.action.ui_responsiveness_test.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/UI Responsiveness")
            .icon(Items.MAP)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "ui_responsiveness_test",
                () -> new UIResponsivenessTestScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.QA_SESSION_START)
            .labelKey("devmod.action.qa.session.start")
            .descriptionKey("devmod.action.qa.session.start.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Session/Start")
            .icon(Items.LIME_DYE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(QATestingScreen.Actions::startSession)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.QA_SESSION_RESUME)
            .labelKey("devmod.action.qa.session.resume")
            .descriptionKey("devmod.action.qa.session.resume.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Session/Resume")
            .icon(Items.CLOCK)
            .precondition(qaSessionExistsPrecondition())
            .handler(QATestingScreen.Actions::resumeSession)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.QA_REPORT_SAVE)
            .labelKey("devmod.action.qa.report.save")
            .descriptionKey("devmod.action.qa.report.save.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Report/Save")
            .icon(Items.WRITABLE_BOOK)
            .precondition(qaSessionActivePrecondition())
            .handler(QATestingScreen.Actions::saveReport)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.QA_REPORT_COPY)
            .labelKey("devmod.action.qa.report.copy")
            .descriptionKey("devmod.action.qa.report.copy.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Report/Copy")
            .icon(Items.FEATHER)
            .precondition(qaSessionActivePrecondition())
            .handler(QATestingScreen.Actions::copyReport)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.QA_TEST_PASS)
            .labelKey("devmod.action.qa.test.pass")
            .descriptionKey("devmod.action.qa.test.pass.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Actions/Pass")
            .icon(Items.GREEN_WOOL)
            .precondition(qaActiveTestPrecondition())
            .handler(QATestingScreen.Actions::passTest)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.QA_TEST_FAIL)
            .labelKey("devmod.action.qa.test.fail")
            .descriptionKey("devmod.action.qa.test.fail.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Actions/Fail")
            .icon(Items.RED_WOOL)
            .precondition(qaActiveTestPrecondition())
            .handler(QATestingScreen.Actions::failTest)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.QA_TEST_SKIP)
            .labelKey("devmod.action.qa.test.skip")
            .descriptionKey("devmod.action.qa.test.skip.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Actions/Skip")
            .icon(Items.YELLOW_WOOL)
            .precondition(qaActiveTestPrecondition())
            .handler(QATestingScreen.Actions::skipTest)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.QA_TEST_AUTO)
            .labelKey("devmod.action.qa.test.auto")
            .descriptionKey("devmod.action.qa.test.auto.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Actions/Auto")
            .icon(Items.COMPARATOR)
            .precondition(qaAutoTestPrecondition())
            .handler(QATestingScreen.Actions::autoCheckTest)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUICK_TEST_WIZARD_OPEN)
            .labelKey("devmod.radial.item.quick_test")
            .descriptionKey("devmod.radial.item.quick_test.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Quick Test Wizard")
            .icon(Items.LIGHTNING_ROD)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "quick_test_wizard",
                () -> new QuickTestWizard()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_QUICK_TEST_WIZARD_OPEN)
            .labelKey("devmod.action.arena.quick_test_wizard")
            .descriptionKey("devmod.action.arena.quick_test_wizard.desc")
            .category(ActionCategory.ARENA)
            .menuPath("Root/Arena/Quick Test Wizard")
            .icon(Items.TARGET)
            .precondition(screenPrecondition())
            .handler(ClientUIActions::openArenaQuickTestWizard)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_BADGE_TESTS_OPEN)
            .labelKey("devmod.action.badge_tests")
            .descriptionKey("devmod.action.badge_tests.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Badge Tests")
            .icon(Items.NETHER_STAR)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "badge_tests",
                () -> new com.devmod.client.testing.BadgeTestScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_VOXELLAB_UI_TESTS_OPEN)
            .labelKey("devmod.action.voxellab_ui_tests")
            .descriptionKey("devmod.action.voxellab_ui_tests.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Developer/VoxelLab UI")
            .icon(Items.GLOW_ITEM_FRAME)
            .precondition(developerModePrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "voxellab_ui_tests",
                () -> new VoxelLabUiTestScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_VOXELLAB_OPEN)
            .labelKey("devmod.action.voxellab")
            .descriptionKey("devmod.action.voxellab.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Developer/VoxelLab")
            .icon(Items.PINK_CONCRETE)
            .precondition(developerModePrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "voxellab",
                () -> new VoxelLabScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_PARTY_OPEN)
            .labelKey("devmod.action.party")
            .descriptionKey("devmod.action.party.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Party")
            .icon(Items.PLAYER_HEAD)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "party_screen",
                () -> new PartyScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_NOTIFICATION_CENTER_OPEN)
            .labelKey("devmod.action.notification_center")
            .descriptionKey("devmod.action.notification_center.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Party/Notifications")
            .icon(Items.BELL)
            .precondition(uiScreenPrecondition())
            .handler(context -> NotificationCenterScreen.open("NOTIFICATIONS", null))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_NOTIFICATION_SETTINGS_OPEN)
            .labelKey("devmod.action.notification_settings")
            .descriptionKey("devmod.action.notification_settings.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Notifications")
            .icon(Items.BELL)
            .precondition(uiScreenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "notification_settings",
                () -> new com.devmod.client.notification.ui.NotificationSettingsScreen(null)))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_MAILBOX_OPEN)
            .labelKey("devmod.action.mailbox")
            .descriptionKey("devmod.action.mailbox.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Party/Mailbox")
            .icon(Items.WRITABLE_BOOK)
            .precondition(uiScreenPrecondition())
            .handler(context -> NotificationCenterScreen.open("MAILBOX", null))
            .build());

        if (TesterModality.isEnabled()) {
            ActionRegistry.register(RadialAction.builder(ActionIds.UI_TESTER_TASKS_OPEN)
                .labelKey("devmod.action.tester_tasks")
                .descriptionKey("devmod.action.tester_tasks.desc")
                .category(ActionCategory.DEBUG)
                .menuPath("Root/Admin/Tester Tasks")
                .icon(Items.WRITTEN_BOOK)
                .precondition(testerPrecondition())
                .handler(context -> NotificationCenterScreen.open("TASKS", null))
                .build());
        }

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_PARTY_INVITE_POPUP_OPEN)
            .labelKey("devmod.action.party_invite")
            .descriptionKey("devmod.action.party_invite.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Party/Invites")
            .icon(Items.PAPER)
            .precondition(partyInvitePrecondition())
            .handler(ClientUIActions::openPartyInvite)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUEST_EDITOR_OPEN)
            .labelKey("devmod.radial.item.quest_editor")
            .descriptionKey("devmod.radial.item.quest_editor.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Quest Tools/Quest Editor")
            .icon(Items.FEATHER)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "quest_editor",
                () -> new QuestEditorScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ENDURANCE_EDITOR_OPEN)
            .labelKey("devmod.action.endurance_editor")
            .descriptionKey("devmod.action.endurance_editor.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Quest Tools/Endurance Editor")
            .icon(Items.GOLDEN_HELMET)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "endurance_editor",
                () -> new QuestEditorScreen(true)))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ENDURANCE_SCREEN_OPEN)
            .labelKey("devmod.radial.item.endurance")
            .descriptionKey("devmod.radial.item.endurance.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance/Start")
            .icon(Items.GOLDEN_HELMET)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "endurance_quest",
                () -> new EnduranceQuestScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUEST_DEATH_OPEN)
            .labelKey("devmod.action.quest_death")
            .descriptionKey("devmod.action.quest_death.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Quest Flow/Death")
            .icon(Items.SKELETON_SKULL)
            .precondition(questDeathPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "quest_death",
                () -> new QuestDeathScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_PERK_SELECTION_OPEN)
            .labelKey("devmod.action.perk_selection")
            .descriptionKey("devmod.action.perk_selection.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Quest Flow/Perk Selection")
            .icon(Items.ENCHANTED_BOOK)
            .precondition(perkSelectionPrecondition())
            .handler(ClientUIActions::openPerkSelection)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_WAVE_CHECKPOINT_OPEN)
            .labelKey("devmod.action.wave_checkpoint")
            .descriptionKey("devmod.action.wave_checkpoint.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Quest Flow/Wave Checkpoint")
            .icon(Items.CLOCK)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> ClientQuestCache.hasActiveQuest(),
                    "devmod.action.requires_active_quest"
                )))
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "wave_checkpoint",
                () -> new WaveCheckpointScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUEST_COMPLETION_OPEN)
            .labelKey("devmod.action.quest_completion")
            .descriptionKey("devmod.action.quest_completion.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Quest Flow/Completion")
            .icon(Items.NETHER_STAR)
            .precondition(questCompletionPrecondition())
            .handler(ClientUIActions::openQuestCompletion)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ARENA_RESULTS_OPEN)
            .labelKey("devmod.action.arena_results")
            .descriptionKey("devmod.action.arena_results.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Quest Flow/Arena Results")
            .icon(Items.GOLDEN_SWORD)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                // Demo data for testing - in production, data comes from quest completion
                ArenaResultsScreen.ArenaRunData demo = new ArenaResultsScreen.ArenaRunData(
                    true, 10, 10, 360000,
                    127, 8500, "S", 42,
                    5, 120.5f, 3450.0f, 0, 18, 0.35f,
                    java.util.List.of("50-Hit Combo!", "Perfect Dodge x3!", "No Deaths!")
                );
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "arena_results",
                    () -> new ArenaResultsScreen(demo));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ENDURANCE_SHOP_OPEN)
            .labelKey("devmod.action.endurance.shop")
            .descriptionKey("devmod.action.endurance.shop.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance/Shop")
            .icon(Items.EMERALD)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "endurance_shop",
                () -> new EnduranceShopScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_WELCOME_OPEN)
            .labelKey("devmod.action.welcome")
            .descriptionKey("devmod.action.welcome.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Config/Welcome")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "welcome",
                () -> new WelcomeScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_SEASON_PASS_OPEN)
            .labelKey("devmod.action.season_pass")
            .descriptionKey("devmod.action.season_pass.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Endurance/Season Pass")
            .icon(Items.GOLD_INGOT)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Integer tier = context.getPayload(Integer.class);
                if (tier != null && tier > 0) {
                    com.devmod.client.ui.season.SeasonPassScreen.openAtTier(tier);
                } else {
                    com.devmod.client.ui.season.SeasonPassScreen.open();
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_CHARACTER_SHEET_OPEN)
            .labelKey("devmod.action.character_sheet")
            .descriptionKey("devmod.action.character_sheet.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Character Sheet")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Minecraft.getInstance().setScreen(new com.devmod.client.ui.screens.CharacterSheetScreen());
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_LEADERBOARD_OPEN)
            .labelKey("devmod.action.leaderboard")
            .descriptionKey("devmod.action.leaderboard.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Leaderboard")
            .icon(Items.GOLDEN_SWORD)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Minecraft.getInstance().setScreen(new com.devmod.client.ui.screens.LeaderboardScreen());
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUEST_JOURNAL_OPEN)
            .labelKey("devmod.action.quest_journal")
            .descriptionKey("devmod.action.quest_journal.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Quest Journal")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "quest_journal",
                    () -> new com.devmod.client.ui.screens.QuestJournalScreen(null));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_LFG_OPEN)
            .labelKey("devmod.action.lfg")
            .descriptionKey("devmod.action.lfg.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Party/LFG")
            .icon(Items.SPYGLASS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "lfg",
                    () -> new com.devmod.client.ui.screens.LFGScreen(null));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ONBOARDING_START)
            .labelKey("devmod.action.onboarding.start")
            .descriptionKey("devmod.action.onboarding.start.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Config/Onboarding/Start")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                OnboardingActionPayload payload = context.getPayload(OnboardingActionPayload.class);
                boolean dontShowAgain = payload != null && payload.dontShowAgain();
                playUiClick(1.0f);
                applyWelcomePreference(dontShowAgain);
                TutorialManager.INSTANCE.setPhase(TutorialManager.TutorialPhase.WELCOME);
                TutorialManager.INSTANCE.setOnboardingCompleted(false);
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(null);
                mc.execute(OnboardingOverlay::start);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ONBOARDING_SKIP)
            .labelKey("devmod.action.onboarding.skip")
            .descriptionKey("devmod.action.onboarding.skip.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Config/Onboarding/Skip")
            .icon(Items.BARRIER)
            .precondition(onboardingActivePrecondition())
            .handler(context -> {
                OnboardingActionPayload payload = context.getPayload(OnboardingActionPayload.class);
                if (payload != null) {
                    playUiClick(0.8f);
                    applyWelcomePreference(payload.dontShowAgain());
                    TutorialManager.INSTANCE.setOnboardingCompleted(true);
                    Minecraft.getInstance().setScreen(null);
                    return;
                }
                OnboardingOverlay.handleEscape();
            })
            .build());
    }

    static void registerCoreOnlyActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.UI_RADIAL_OPEN)
            .labelKey("devmod.action.radial_open")
            .descriptionKey("devmod.action.radial_open.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Home")
            .icon(Items.COMPASS)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.screenClosed()))
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "radial_menu",
                () -> new com.devmod.client.ui.radial.RadialMenuScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_SETTINGS_OPEN)
            .labelKey("devmod.radial.item.settings")
            .descriptionKey("devmod.radial.item.settings.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config")
            .icon(Items.COMPARATOR)
            .precondition(uiScreenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "settings",
                () -> new UnifiedSettingsScreen(null, SettingsCategory.GENERAL)))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_CHARACTER_SHEET_OPEN)
            .labelKey("devmod.action.character_sheet")
            .descriptionKey("devmod.action.character_sheet.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Character Sheet")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Minecraft.getInstance().setScreen(new com.devmod.client.ui.screens.CharacterSheetScreen());
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_LEADERBOARD_OPEN)
            .labelKey("devmod.action.leaderboard")
            .descriptionKey("devmod.action.leaderboard.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Leaderboard")
            .icon(Items.GOLDEN_SWORD)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Minecraft.getInstance().setScreen(new com.devmod.client.ui.screens.LeaderboardScreen());
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUEST_JOURNAL_OPEN)
            .labelKey("devmod.action.quest_journal")
            .descriptionKey("devmod.action.quest_journal.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Quest Journal")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "quest_journal",
                    () -> new com.devmod.client.ui.screens.QuestJournalScreen(null));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_LFG_OPEN)
            .labelKey("devmod.action.lfg")
            .descriptionKey("devmod.action.lfg.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Party/LFG")
            .icon(Items.SPYGLASS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "lfg",
                    () -> new com.devmod.client.ui.screens.LFGScreen(null));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ENDURANCE_SCREEN_OPEN)
            .labelKey("devmod.radial.item.endurance")
            .descriptionKey("devmod.radial.item.endurance.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance/Start")
            .icon(Items.GOLDEN_HELMET)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "endurance_quest",
                () -> new EnduranceQuestScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_SEASON_PASS_OPEN)
            .labelKey("devmod.action.season_pass")
            .descriptionKey("devmod.action.season_pass.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Play/Endurance/Season Pass")
            .icon(Items.GOLD_INGOT)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Integer tier = context.getPayload(Integer.class);
                if (tier != null && tier > 0) {
                    com.devmod.client.ui.season.SeasonPassScreen.openAtTier(tier);
                } else {
                    com.devmod.client.ui.season.SeasonPassScreen.open();
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_PARTY_OPEN)
            .labelKey("devmod.action.party")
            .descriptionKey("devmod.action.party.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Party")
            .icon(Items.PLAYER_HEAD)
            .precondition(screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "party_screen",
                () -> new PartyScreen()))
            .build());
    }

    // ── Keybind hints ──

    static void registerKeybindHints() {
        ActionKeybindRegistry.register(ActionIds.UI_RADIAL_OPEN, KeyInputHandler.OPEN_RADIAL_MENU_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_SETTINGS_OPEN, KeyInputHandler.OPEN_SETTINGS_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO, KeyInputHandler.OPEN_WEAPON_EDITOR_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON, KeyInputHandler.OPEN_WEAPON_EDITOR_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR, KeyInputHandler.OPEN_WEAPON_EDITOR_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN, KeyInputHandler.OPEN_DASHBOARD_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_QA_TESTING_OPEN, KeyInputHandler.OPEN_QA_TESTING_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_TESTING_HUB_OPEN, KeyInputHandler.OPEN_TESTING_HUB_KEY);
        ActionKeybindRegistry.register(ActionIds.ARENA_HUD_TOGGLE, KeyInputHandler.OPEN_TESTING_HUB_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.UI_VOXELLAB_OPEN, KeyInputHandler.OPEN_VOXELLAB_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_QUEST_EDITOR_OPEN, KeyInputHandler.OPEN_QUEST_EDITOR_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ENDURANCE_EDITOR_OPEN, KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN, KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.UI_MOB_CONFIG_OPEN, KeyInputHandler.INSPECT_MOB_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_PARTY_OPEN, KeyInputHandler.OPEN_PARTY_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_LFG_OPEN, KeyInputHandler.OPEN_LFG_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_NOTIFICATION_CENTER_OPEN, KeyInputHandler.OPEN_NOTIFICATION_CENTER_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_MAILBOX_OPEN, KeyInputHandler.OPEN_MAILBOX_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_QUEST_JOURNAL_OPEN, KeyInputHandler.OPEN_QUEST_JOURNAL_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_CHARACTER_SHEET_OPEN, KeyInputHandler.OPEN_CHARACTER_SHEET_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_LEADERBOARD_OPEN, KeyInputHandler.OPEN_LEADERBOARD_KEY);
        if (TesterModality.isEnabled()) {
            ActionKeybindRegistry.register(ActionIds.UI_TESTER_TASKS_OPEN, KeyInputHandler.OPEN_TESTER_TASKS_KEY);
        }
    }

    static void registerCoreKeybindHints() {
        ActionKeybindRegistry.register(ActionIds.UI_RADIAL_OPEN, KeyInputHandler.OPEN_RADIAL_MENU_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_QUEST_JOURNAL_OPEN, KeyInputHandler.OPEN_QUEST_JOURNAL_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_CHARACTER_SHEET_OPEN, KeyInputHandler.OPEN_CHARACTER_SHEET_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_LEADERBOARD_OPEN, KeyInputHandler.OPEN_LEADERBOARD_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_LFG_OPEN, KeyInputHandler.OPEN_LFG_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ENDURANCE_SCREEN_OPEN, KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_PARTY_OPEN, KeyInputHandler.OPEN_PARTY_KEY);
    }

    // ── Private helpers ──

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
        if (player == null) {
            return;
        }
        ItemStack heldItem = player.getMainHandItem().copy();
        if (heldItem.isEmpty()) {
            context.sendSuccess(I18n.translate("devmod.message.item_editor_hint"), true);
            return;
        }
        if (heldItem.getItem() instanceof ArmorItem) {
            com.devmod.client.ui.ScreenSafety.openSafe(
                "item_editor_armor",
                () -> new ItemEditorScreen(heldItem, EditorStartTab.ARMOR));
        } else {
            com.devmod.client.ui.ScreenSafety.openSafe(
                "item_editor_weapon",
                () -> new ItemEditorScreen(heldItem, EditorStartTab.WEAPON));
        }
    }

    private static void openItemEditorTab(ActionContext context, EditorStartTab tab) {
        ItemStack payloadItem = context.getPayload(ItemStack.class);
        if (payloadItem != null && !payloadItem.isEmpty()) {
            com.devmod.client.ui.ScreenSafety.openSafe(
                "item_editor",
                () -> new ItemEditorScreen(payloadItem.copy(), tab));
            return;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        ItemStack heldItem = player.getMainHandItem().copy();
        if (heldItem.isEmpty()) {
            context.sendSuccess(I18n.translate("devmod.message.must_hold_item"), true);
            return;
        }
        com.devmod.client.ui.ScreenSafety.openSafe(
            "item_editor",
            () -> new ItemEditorScreen(heldItem, tab));
    }

    private static void openMobConfig(ActionContext context) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.Mob payloadMob = context.getPayload(net.minecraft.world.entity.Mob.class);
        if (payloadMob != null) {
            com.devmod.client.ui.ScreenSafety.openSafe(
                "mob_config",
                () -> new MobConfigScreen(payloadMob));
            return;
        }

        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "mob_config",
                    () -> new MobConfigScreen(mob));
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

        com.devmod.client.ui.ScreenSafety.openSafe(
            "mob_equipment",
            finalParent,
            () -> new MobEquipmentScreen(finalMob, finalParent));
    }

    private static void openPerkSelection(ActionContext context) {
        PerkChoicesPayload payload = context.getPayload(PerkChoicesPayload.class);
        if (payload == null) {
            payload = EnduranceUiCache.getLastPerkChoices();
        }
        if (payload == null) {
            context.sendFailure(Component.translatable("devmod.action.perk_selection.missing"));
            return;
        }
        final PerkChoicesPayload finalPayload = payload;
        com.devmod.client.ui.ScreenSafety.openSafe(
            "perk_selection",
            () -> new PerkSelectionScreen(finalPayload.waveNumber(), finalPayload.choices(), finalPayload.expiresAt()));
    }

    private static void openQuestCompletion(ActionContext context) {
        QuestCompletionPayload payload = context.getPayload(QuestCompletionPayload.class);
        if (payload == null) {
            payload = EnduranceUiCache.getLastQuestCompletion();
        }
        if (payload == null) {
            context.sendFailure(Component.translatable("devmod.action.quest_completion.missing"));
            return;
        }
        final QuestCompletionPayload finalPayload = payload;
        com.devmod.client.ui.ScreenSafety.openSafe(
            "quest_completion",
            () -> new QuestCompletionScreen(finalPayload));
    }

    private static void openPartyInvite(ActionContext context) {
        PartyInviteActionData payload = context.getPayload(PartyInviteActionData.class);
        if (payload == null) {
            payload = PartyUiCache.getActiveInvite();
        }
        if (payload == null) {
            context.sendFailure(Component.translatable("devmod.action.party_invite.missing"));
            return;
        }
        final PartyInviteActionData finalPayload = payload;
        com.devmod.client.ui.ScreenSafety.openSafe(
            "party_invite",
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
                context.sendFailure(net.minecraft.network.chat.Component.literal("Invalid template ID"));
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

    private static void openEditorHub(ActionContext context) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "editor_hub",
            () -> new EditorHubScreen(null));
    }

    private static void openPlayHub(ActionContext context) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "play_hub",
            () -> new PlayHubScreen(null));
    }

    private static void openArenaHub(ActionContext context) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "arena_hub",
            () -> new ArenaHubScreen(null));
    }

    private static void openNexusHub(ActionContext context) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "nexus_hub",
            () -> new NexusHubScreen(null));
    }

    private static void openTestingHub(ActionContext context) {
        if (TestingHubState.INSTANCE.isMinimized()) {
            TestingHub.restoreFromHud();
            return;
        }
        com.devmod.client.ui.ScreenSafety.openSafe(
            "testing_hub",
            () -> new TestingHub());
    }
}
