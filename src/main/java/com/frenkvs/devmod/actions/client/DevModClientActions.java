package com.frenkvs.devmod.actions.client;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.client.input.KeyInputHandler;
import com.frenkvs.devmod.ModConfig;
import com.frenkvs.devmod.actions.ActionCategory;
import com.frenkvs.devmod.actions.ActionContext;
import com.frenkvs.devmod.actions.ActionIds;
import com.frenkvs.devmod.actions.ActionPrecondition;
import com.frenkvs.devmod.actions.ActionPreconditions;
import com.frenkvs.devmod.actions.ActionRegistry;
import com.frenkvs.devmod.actions.RadialAction;
import com.frenkvs.devmod.abilities.AbilityActionPayload;
import com.frenkvs.devmod.abilities.DodgeAbilitySystem;
import com.frenkvs.devmod.attributes.AttributeMonitoringSystem;
import com.frenkvs.devmod.debug.DebugFeature;
import com.frenkvs.devmod.debug.DebugTogglePayload;
import com.frenkvs.devmod.debug.client.DebugRenderBools;
import com.frenkvs.devmod.hud.EnduranceQuestOverlay;
import com.frenkvs.devmod.endurance.ClientQuestCache;
import com.frenkvs.devmod.endurance.EnduranceShopScreen;
import com.frenkvs.devmod.endurance.EnduranceQuestScreen;
import com.frenkvs.devmod.endurance.EnduranceUiCache;
import com.frenkvs.devmod.endurance.PerkChoicesPayload;
import com.frenkvs.devmod.endurance.PerkSelectionScreen;
import com.frenkvs.devmod.endurance.QuestCompletionPayload;
import com.frenkvs.devmod.endurance.QuestCompletionScreen;
import com.frenkvs.devmod.endurance.QuestDeathScreen;
import com.frenkvs.devmod.endurance.QuestActionPayload;
import com.frenkvs.devmod.endurance.QuestExitConfirmScreen;
import com.frenkvs.devmod.endurance.StartQuestPayload;
import com.frenkvs.devmod.endurance.WaveCheckpointScreen;
import com.frenkvs.devmod.hud.BossPhaseOverlay;
import com.frenkvs.devmod.hud.EconomyOverlay;
import com.frenkvs.devmod.hud.EntityDensityOverlay;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactData;
import com.frenkvs.devmod.hud.ImpactHudOverlay;
import com.frenkvs.devmod.hud.ImpactHudPresets;
import com.frenkvs.devmod.hud.ImpactVFX;
import com.frenkvs.devmod.hud.OnboardingOverlay;
import com.frenkvs.devmod.hud.QuickHelpOverlay;
import com.frenkvs.devmod.hud.SkillEfficacyOverlay;
import com.frenkvs.devmod.panels.context.ContextDetector;
import com.frenkvs.devmod.party.PartyScreen;
import com.frenkvs.devmod.party.InvitePopupScreen;
import com.frenkvs.devmod.party.PartyNotificationPayload;
import com.frenkvs.devmod.party.PartyUiCache;
import com.frenkvs.devmod.quest.QuestEditorScreen;
import com.frenkvs.devmod.quest.QuestHudOverlay;
import com.frenkvs.devmod.quest.QuestManager;
import com.frenkvs.devmod.quest.QuestTask;
import com.frenkvs.devmod.rendering.AggroRangeVisualizer;
import com.frenkvs.devmod.rendering.ChunkPerformanceVisualizer;
import com.frenkvs.devmod.rendering.DebugRenderer;
import com.frenkvs.devmod.rendering.HeatmapVisualizer;
import com.frenkvs.devmod.rendering.LightLevelOverlay;
import com.frenkvs.devmod.rendering.LineOfSightVisualizer;
import com.frenkvs.devmod.rendering.PathfindingDebugger;
import com.frenkvs.devmod.rendering.RoomBoundsVisualizer;
import com.frenkvs.devmod.rendering.SafeSpotVisualizer;
import com.frenkvs.devmod.rendering.SpawnabilityOverlay;
import com.frenkvs.devmod.rendering.VerticalLevelsVisualizer;
import com.frenkvs.devmod.telemetry.FpsTracker;
import com.frenkvs.devmod.telemetry.PerformanceProfiler;
import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.MobConfigScreen;
import com.frenkvs.devmod.MobEquipmentScreen;
import com.frenkvs.devmod.ui.RoomBoundsEditorScreen;
import com.frenkvs.devmod.ui.WelcomeScreen;
import com.frenkvs.devmod.ui.editor.EditorStartTab;
import com.frenkvs.devmod.ui.editor.ItemEditorScreen;
import com.frenkvs.devmod.ui.editor.StaminaSystemEditor;
import com.frenkvs.devmod.ui.hub.TestingHub;
import com.frenkvs.devmod.ui.hub.TestingHubState;
import com.frenkvs.devmod.testing.QATestingScreen;
import com.frenkvs.devmod.testing.TestingSession;
import com.frenkvs.devmod.testing.TutorialManager;
import com.frenkvs.devmod.ui.testing.VoxelLabScreen;
import com.frenkvs.devmod.ui.testing.VoxelLabUiTestScreen;
import com.frenkvs.devmod.ui.unified.UnifiedSettingsScreen;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.ui.wizard.QuickTestWizard;
import com.devmod.arena.command.ArenaActionRegistry;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.BooleanSupplier;

public final class DevModClientActions {
    private static final HeatmapVisualizer.HeatmapType[] HEATMAP_CYCLE = {
        HeatmapVisualizer.HeatmapType.DEATH,
        HeatmapVisualizer.HeatmapType.MOVEMENT,
        HeatmapVisualizer.HeatmapType.CAMPING,
        HeatmapVisualizer.HeatmapType.STUCK,
        HeatmapVisualizer.HeatmapType.AGGRO_DROP,
        HeatmapVisualizer.HeatmapType.KITING
    };
    private static int currentHeatmapIndex = -1;

    private DevModClientActions() {}

    public static void register() {
        registerUiActions();
        registerDebugActions();
        registerConfigActions();
        registerTelemetryActions();
        registerQuestActions();
        registerEnduranceActions();
        registerAbilityActions();
        registerKeybindHints();
    }

    private static ActionPrecondition screenPrecondition() {
        return ActionPreconditions.clientOnly()
            .and(ActionPreconditions.screenClosed())
            .and(ActionPreconditions.withMessage(
                context -> !ContextDetector.INSTANCE.isCurrentlyCombat(),
                "devmod.action.requires_not_in_combat"
            ));
    }

    private static ActionPrecondition qaSessionActivePrecondition() {
        return ActionPreconditions.clientOnly()
            .and(ActionPreconditions.withMessage(
                context -> TestingSession.INSTANCE.isSessionActive(),
                "devmod.action.testing.session_inactive"
            ));
    }

    private static ActionPrecondition qaSessionExistsPrecondition() {
        return ActionPreconditions.clientOnly()
            .and(ActionPreconditions.withMessage(
                context -> TestingSession.INSTANCE.hasExistingSession(),
                "devmod.action.testing.session_missing"
            ));
    }

    private static ActionPrecondition qaActiveTestPrecondition() {
        return qaSessionActivePrecondition()
            .and(ActionPreconditions.withMessage(
                QATestingScreen.Actions::hasActiveTest,
                "devmod.action.testing.requires_active_test"
            ));
    }

    private static void registerUiActions() {
        ArenaActionRegistry.registerClientActions();
        ActionRegistry.register(RadialAction.builder(ActionIds.UI_RADIAL_OPEN)
            .labelKey("devmod.action.radial_open")
            .descriptionKey("devmod.action.radial_open.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Home")
            .icon(Items.COMPASS)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.screenClosed()))
            .handler(context -> Minecraft.getInstance().setScreen(new com.frenkvs.devmod.ui.radial.RadialMenuScreenV3()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_SETTINGS_OPEN)
            .labelKey("devmod.radial.item.settings")
            .descriptionKey("devmod.radial.item.settings.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Settings")
            .icon(Items.COMPARATOR)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new UnifiedSettingsScreen(null)))
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
                mc.setScreen(new KeyBindsScreen(java.util.Objects.requireNonNull(parent, "parent"), options));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO)
            .labelKey("devmod.radial.item.item_editor")
            .descriptionKey("devmod.radial.item.item_editor.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Item Editor/Auto")
            .icon(Items.BOOK)
            .precondition(screenPrecondition())
            .handler(DevModClientActions::openItemEditorAuto)
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
                Minecraft.getInstance().setScreen(new com.frenkvs.devmod.TelemetryDashboardScreen(parent));
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_MOB_CONFIG_OPEN)
            .labelKey("devmod.radial.item.mob_editor")
            .descriptionKey("devmod.radial.item.mob_editor.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Mob Config")
            .icon(Items.LEAD)
            .precondition(screenPrecondition())
            .handler(DevModClientActions::openMobConfig)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_MOB_EQUIPMENT_OPEN)
            .labelKey("devmod.action.mob_equipment.open")
            .descriptionKey("devmod.action.mob_equipment.open.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Mob Config/Equipment")
            .icon(Items.IRON_HELMET)
            .precondition(screenPrecondition())
            .handler(DevModClientActions::openMobEquipment)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN)
            .labelKey("devmod.action.room_bounds_editor")
            .descriptionKey("devmod.action.room_bounds_editor.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Room Bounds Editor")
            .icon(Items.STRUCTURE_BLOCK)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new RoomBoundsEditorScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_POINT_A)
            .labelKey("devmod.action.room_bounds.point_a")
            .descriptionKey("devmod.action.room_bounds.point_a.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Room Bounds/Set Point A")
            .icon(Items.LIME_WOOL)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.requiresPlayer()))
            .handler(RoomBoundsEditorScreen.Actions::setPointA)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_POINT_B)
            .labelKey("devmod.action.room_bounds.point_b")
            .descriptionKey("devmod.action.room_bounds.point_b.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Room Bounds/Set Point B")
            .icon(Items.RED_WOOL)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.requiresPlayer()))
            .handler(RoomBoundsEditorScreen.Actions::setPointB)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_SAVE)
            .labelKey("devmod.action.room_bounds.save")
            .descriptionKey("devmod.action.room_bounds.save.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Room Bounds/Save")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly().and(ActionPreconditions.requiresPlayer()))
            .handler(RoomBoundsEditorScreen.Actions::saveRoom)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ROOM_BOUNDS_DELETE_LAST)
            .labelKey("devmod.action.room_bounds.delete_last")
            .descriptionKey("devmod.action.room_bounds.delete_last.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Room Bounds/Delete Last")
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
            .handler(DevModClientActions::openTestingHub)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QA_TESTING_OPEN)
            .labelKey("devmod.action.qa_testing")
            .descriptionKey("devmod.action.qa_testing.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA Suite")
            .icon(Items.BOOK)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new QATestingScreen()))
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
            .precondition(qaActiveTestPrecondition().and(ActionPreconditions.withMessage(
                QATestingScreen.Actions::hasAutoTest,
                "devmod.action.testing.requires_auto_test"
            )))
            .handler(QATestingScreen.Actions::autoCheckTest)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUICK_TEST_WIZARD_OPEN)
            .labelKey("devmod.radial.item.quick_test")
            .descriptionKey("devmod.radial.item.quick_test.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Quick Test Wizard")
            .icon(Items.LIGHTNING_ROD)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new QuickTestWizard()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_QUICK_TEST_WIZARD_OPEN)
            .labelKey("devmod.action.arena.quick_test_wizard")
            .descriptionKey("devmod.action.arena.quick_test_wizard.desc")
            .category(ActionCategory.ARENA)
            .menuPath("Root/Arena/Quick Test Wizard")
            .icon(Items.TARGET)
            .precondition(screenPrecondition())
            .handler(DevModClientActions::openArenaQuickTestWizard)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_BADGE_TESTS_OPEN)
            .labelKey("devmod.action.badge_tests")
            .descriptionKey("devmod.action.badge_tests.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Badge Tests")
            .icon(Items.NETHER_STAR)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new com.frenkvs.devmod.testing.BadgeTestScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_VOXELLAB_UI_TESTS_OPEN)
            .labelKey("devmod.action.voxellab_ui_tests")
            .descriptionKey("devmod.action.voxellab_ui_tests.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/VoxelLab UI")
            .icon(Items.GLOW_ITEM_FRAME)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new VoxelLabUiTestScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_VOXELLAB_OPEN)
            .labelKey("devmod.action.voxellab")
            .descriptionKey("devmod.action.voxellab.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/VoxelLab")
            .icon(Items.PINK_CONCRETE)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new VoxelLabScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_PARTY_OPEN)
            .labelKey("devmod.action.party")
            .descriptionKey("devmod.action.party.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Party")
            .icon(Items.PLAYER_HEAD)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new PartyScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_PARTY_INVITE_POPUP_OPEN)
            .labelKey("devmod.action.party_invite")
            .descriptionKey("devmod.action.party_invite.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Party/Invites")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> PartyUiCache.getActiveInvite() != null
                        || context.getPayload(PartyNotificationPayload.class) != null,
                    "devmod.action.party_invite.missing"
                )))
            .handler(DevModClientActions::openPartyInvite)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUEST_EDITOR_OPEN)
            .labelKey("devmod.radial.item.quest_editor")
            .descriptionKey("devmod.radial.item.quest_editor.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Quest Editor")
            .icon(Items.FEATHER)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new QuestEditorScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ENDURANCE_EDITOR_OPEN)
            .labelKey("devmod.action.endurance_editor")
            .descriptionKey("devmod.action.endurance_editor.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Quest Editor")
            .icon(Items.GOLDEN_HELMET)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new QuestEditorScreen(true)))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ENDURANCE_SCREEN_OPEN)
            .labelKey("devmod.radial.item.endurance")
            .descriptionKey("devmod.radial.item.endurance.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Start")
            .icon(Items.GOLDEN_HELMET)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new EnduranceQuestScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUEST_DEATH_OPEN)
            .labelKey("devmod.action.quest_death")
            .descriptionKey("devmod.action.quest_death.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Death")
            .icon(Items.SKELETON_SKULL)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> ClientQuestCache.isAwaitingRespawn(),
                    "devmod.action.requires_respawn"
                )))
            .handler(context -> Minecraft.getInstance().setScreen(new QuestDeathScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_PERK_SELECTION_OPEN)
            .labelKey("devmod.action.perk_selection")
            .descriptionKey("devmod.action.perk_selection.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Perks/Choose")
            .icon(Items.ENCHANTED_BOOK)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> context.getPayload(PerkChoicesPayload.class) != null
                        || EnduranceUiCache.getLastPerkChoices() != null,
                    "devmod.action.perk_selection.missing"
                )))
            .handler(DevModClientActions::openPerkSelection)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_WAVE_CHECKPOINT_OPEN)
            .labelKey("devmod.action.wave_checkpoint")
            .descriptionKey("devmod.action.wave_checkpoint.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Checkpoints")
            .icon(Items.CLOCK)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> ClientQuestCache.hasActiveQuest(),
                    "devmod.action.requires_active_quest"
                )))
            .handler(context -> Minecraft.getInstance().setScreen(new WaveCheckpointScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_QUEST_COMPLETION_OPEN)
            .labelKey("devmod.action.quest_completion")
            .descriptionKey("devmod.action.quest_completion.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Results")
            .icon(Items.NETHER_STAR)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> context.getPayload(QuestCompletionPayload.class) != null
                        || EnduranceUiCache.getLastQuestCompletion() != null,
                    "devmod.action.quest_completion.missing"
                )))
            .handler(DevModClientActions::openQuestCompletion)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ENDURANCE_SHOP_OPEN)
            .labelKey("devmod.action.endurance.shop")
            .descriptionKey("devmod.action.endurance.shop.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Shop")
            .icon(Items.EMERALD)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new EnduranceShopScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_STAMINA_EDITOR_OPEN)
            .labelKey("devmod.action.stamina_editor")
            .descriptionKey("devmod.action.stamina_editor.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Stamina Editor")
            .icon(Items.HONEY_BOTTLE)
            .precondition(screenPrecondition())
            .handler(context -> Minecraft.getInstance().setScreen(new StaminaSystemEditor()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_WELCOME_OPEN)
            .labelKey("devmod.action.welcome")
            .descriptionKey("devmod.action.welcome.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Help/Welcome")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Minecraft.getInstance().setScreen(new WelcomeScreen()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ONBOARDING_START)
            .labelKey("devmod.action.onboarding.start")
            .descriptionKey("devmod.action.onboarding.start.desc")
            .category(ActionCategory.UI)
            .menuPath("Root/Help/Onboarding/Start")
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
            .menuPath("Root/Help/Onboarding/Skip")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> OnboardingOverlay.isActive()
                        || context.getPayload(OnboardingActionPayload.class) != null,
                    "devmod.action.onboarding.inactive"
                )))
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

    private static void registerDebugActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_OVERLAY_TOGGLE)
            .labelKey("devmod.radial.item.mob_debug")
            .descriptionKey("devmod.radial.item.mob_debug.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Overlays/Debug Overlay")
            .icon(Items.ZOMBIE_HEAD)
            .toggle(context -> DebugRenderer.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                DebugRenderer.INSTANCE.toggle();
                Config.DEBUG_OVERLAY_ENABLED.set(DebugRenderer.INSTANCE.isEnabled());
                SettingsManager.INSTANCE.markDirty();
                String status = DebugRenderer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.debug_overlay_status", status), true);
                if (context.getPlayer() != null) {
                    checkOverlayCount(context.getPlayer());
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_IMPACT_TOGGLE)
            .labelKey("devmod.action.impact_hud")
            .descriptionKey("devmod.action.impact_hud.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Impact HUD")
            .icon(Items.NETHERITE_SWORD)
            .toggle(context -> ImpactHudOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ImpactHudOverlay.toggle();
                Config.IMPACT_HUD_ENABLED.set(ImpactHudOverlay.isEnabled());
                String status = ImpactHudOverlay.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.impact_hud_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_IMPACT_3D_TOGGLE)
            .labelKey("devmod.action.impact_hud_3d")
            .descriptionKey("devmod.action.impact_hud_3d.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Impact HUD/3D Panels")
            .icon(Items.ITEM_FRAME)
            .toggle(context -> Impact3DPanelManager.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Impact3DPanelManager.INSTANCE.toggle();
                String status = Impact3DPanelManager.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.impact_3d_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_BODY_PARTS_TOGGLE)
            .labelKey("devmod.radial.item.body_parts")
            .descriptionKey("devmod.radial.item.body_parts.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Overlays/Body Parts")
            .icon(Items.ARMOR_STAND)
            .toggle(context -> ModConfig.showBodyPartBoxes)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ModConfig.showBodyPartBoxes = !ModConfig.showBodyPartBoxes;
                Config.SHOW_BODY_PART_BOXES.set(ModConfig.showBodyPartBoxes);
                if (ModConfig.showBodyPartBoxes && !ModConfig.showRender) {
                    ModConfig.showRender = true;
                }
                SettingsManager.INSTANCE.markDirty();
                String status = ModConfig.showBodyPartBoxes ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.body_parts_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_OVERLAYS_ENABLE_ALL)
            .labelKey("devmod.action.debug_overlays.enable_all")
            .descriptionKey("devmod.action.debug_overlays.enable_all.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Overlays/Enable All")
            .icon(Items.LIME_DYE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.setDebugOverlaysEnabled(context, true))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_OVERLAYS_DISABLE_ALL)
            .labelKey("devmod.action.debug_overlays.disable_all")
            .descriptionKey("devmod.action.debug_overlays.disable_all.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Overlays/Disable All")
            .icon(Items.GRAY_DYE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.setDebugOverlaysEnabled(context, false))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE)
            .labelKey("devmod.action.native_debug.entity_pathing")
            .descriptionKey("devmod.action.native_debug.entity_pathing.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Entity Pathing")
            .icon(Items.LEAD)
            .toggle(context -> DebugRenderBools.ENTITY_PATHING)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.toggleNativeDebug(context, DebugFeature.ENTITY_PATHING,
                () -> DebugRenderBools.ENTITY_PATHING))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE)
            .labelKey("devmod.action.native_debug.entity_goals")
            .descriptionKey("devmod.action.native_debug.entity_goals.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Entity Goals")
            .icon(Items.WRITABLE_BOOK)
            .toggle(context -> DebugRenderBools.ENTITY_GOALS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.toggleNativeDebug(context, DebugFeature.ENTITY_GOALS,
                () -> DebugRenderBools.ENTITY_GOALS))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE)
            .labelKey("devmod.action.native_debug.entity_brains")
            .descriptionKey("devmod.action.native_debug.entity_brains.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Entity Brains")
            .icon(Items.ENDER_PEARL)
            .toggle(context -> DebugRenderBools.ENTITY_BRAINS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.toggleNativeDebug(context, DebugFeature.ENTITY_BRAINS,
                () -> DebugRenderBools.ENTITY_BRAINS))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_POI_TOGGLE)
            .labelKey("devmod.action.native_debug.poi")
            .descriptionKey("devmod.action.native_debug.poi.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/POI")
            .icon(Items.BELL)
            .toggle(context -> DebugRenderBools.POI)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.toggleNativeDebug(context, DebugFeature.POI,
                () -> DebugRenderBools.POI))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE)
            .labelKey("devmod.action.native_debug.raids")
            .descriptionKey("devmod.action.native_debug.raids.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Raids")
            .icon(Items.IRON_SWORD)
            .toggle(context -> DebugRenderBools.RAIDS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.toggleNativeDebug(context, DebugFeature.RAIDS,
                () -> DebugRenderBools.RAIDS))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_BEES_TOGGLE)
            .labelKey("devmod.action.native_debug.bees")
            .descriptionKey("devmod.action.native_debug.bees.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Bees")
            .icon(Items.HONEYCOMB)
            .toggle(context -> DebugRenderBools.BEES)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.toggleNativeDebug(context, DebugFeature.BEES,
                () -> DebugRenderBools.BEES))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE)
            .labelKey("devmod.action.native_debug.game_events")
            .descriptionKey("devmod.action.native_debug.game_events.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Game Events")
            .icon(Items.SCULK_SENSOR)
            .toggle(context -> DebugRenderBools.GAME_EVENTS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.toggleNativeDebug(context, DebugFeature.GAME_EVENTS,
                () -> DebugRenderBools.GAME_EVENTS))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE)
            .labelKey("devmod.action.native_debug.structures")
            .descriptionKey("devmod.action.native_debug.structures.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Structures")
            .icon(Items.STRUCTURE_BLOCK)
            .toggle(context -> DebugRenderBools.STRUCTURES)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> DevModClientActions.toggleNativeDebug(context, DebugFeature.STRUCTURE_GENERATIONS,
                () -> DebugRenderBools.STRUCTURES))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE)
            .labelKey("devmod.radial.item.light_levels")
            .descriptionKey("devmod.radial.item.light_levels.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Light")
            .icon(Items.TORCH)
            .toggle(context -> LightLevelOverlay.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                LightLevelOverlay.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = LightLevelOverlay.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.light_overlay_status", status), true);
                if (context.getPlayer() != null) {
                    checkOverlayCount(context.getPlayer());
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_CYCLE)
            .labelKey("devmod.radial.item.heatmaps")
            .descriptionKey("devmod.radial.item.heatmaps.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Cycle")
            .icon(Items.BLAZE_POWDER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(DevModClientActions::cycleHeatmaps)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_TOGGLE)
            .labelKey("devmod.action.heatmap.toggle")
            .descriptionKey("devmod.action.heatmap.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Toggle")
            .icon(Items.LEVER)
            .toggle(context -> HeatmapVisualizer.INSTANCE.hasActiveHeatmaps())
            .precondition(ActionPreconditions.clientOnly())
            .handler(DevModClientActions::toggleHeatmaps)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE)
            .labelKey("devmod.action.heatmap.death.toggle")
            .descriptionKey("devmod.action.heatmap.death.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Types/Death")
            .icon(Items.SKELETON_SKULL)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapVisualizer.HeatmapType.DEATH))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapVisualizer.HeatmapType.DEATH))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE)
            .labelKey("devmod.action.heatmap.movement.toggle")
            .descriptionKey("devmod.action.heatmap.movement.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Types/Movement")
            .icon(Items.FEATHER)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapVisualizer.HeatmapType.MOVEMENT))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapVisualizer.HeatmapType.MOVEMENT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE)
            .labelKey("devmod.action.heatmap.camping.toggle")
            .descriptionKey("devmod.action.heatmap.camping.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Types/Camping")
            .icon(Items.CAMPFIRE)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapVisualizer.HeatmapType.CAMPING))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapVisualizer.HeatmapType.CAMPING))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE)
            .labelKey("devmod.action.heatmap.stuck.toggle")
            .descriptionKey("devmod.action.heatmap.stuck.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Types/Stuck")
            .icon(Items.COBWEB)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapVisualizer.HeatmapType.STUCK))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapVisualizer.HeatmapType.STUCK))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE)
            .labelKey("devmod.action.heatmap.aggro_drop.toggle")
            .descriptionKey("devmod.action.heatmap.aggro_drop.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Types/Aggro Drop")
            .icon(Items.ENDER_EYE)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapVisualizer.HeatmapType.AGGRO_DROP))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapVisualizer.HeatmapType.AGGRO_DROP))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_KITING_TOGGLE)
            .labelKey("devmod.action.heatmap.kiting.toggle")
            .descriptionKey("devmod.action.heatmap.kiting.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Types/Kiting")
            .icon(Items.BOW)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapVisualizer.HeatmapType.KITING))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapVisualizer.HeatmapType.KITING))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE)
            .labelKey("devmod.action.heatmap.light_spawnable.toggle")
            .descriptionKey("devmod.action.heatmap.light_spawnable.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Types/Light Spawnable")
            .icon(Items.REDSTONE_TORCH)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapVisualizer.HeatmapType.LIGHT_SPAWNABLE))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapVisualizer.HeatmapType.LIGHT_SPAWNABLE))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE)
            .labelKey("devmod.action.heatmap.light_dark.toggle")
            .descriptionKey("devmod.action.heatmap.light_dark.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Types/Light Dark")
            .icon(Items.COAL)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapVisualizer.HeatmapType.LIGHT_DARK))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapVisualizer.HeatmapType.LIGHT_DARK))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT)
            .labelKey("devmod.action.heatmap.clear_current")
            .descriptionKey("devmod.action.heatmap.clear_current.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Clear Current")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(DevModClientActions::clearCurrentHeatmap)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_CLEAR_ALL)
            .labelKey("devmod.action.heatmap.clear_all")
            .descriptionKey("devmod.action.heatmap.clear_all.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Heatmaps/Clear All")
            .icon(Items.FLINT_AND_STEEL)
            .precondition(ActionPreconditions.clientOnly())
            .handler(DevModClientActions::clearAllHeatmaps)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE)
            .labelKey("devmod.radial.item.room_bounds")
            .descriptionKey("devmod.radial.item.room_bounds.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Room Bounds")
            .icon(Items.STRUCTURE_BLOCK)
            .toggle(context -> RoomBoundsVisualizer.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                RoomBoundsVisualizer.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = RoomBoundsVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                int roomCount = RoomBoundsVisualizer.INSTANCE.getRoomCount();
                int gapCount = RoomBoundsVisualizer.INSTANCE.getGapCount();
                String gapInfo = gapCount > 0 ? " §c" + gapCount + " gaps!" : " §a0 gaps";
                context.sendSuccess(I18n.translate("devmod.render.room_bounds_status", status, roomCount, gapInfo), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ROOM_BOUNDS_RELOAD)
            .labelKey("devmod.action.room_bounds.reload")
            .descriptionKey("devmod.action.room_bounds.reload.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Reload")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                RoomBoundsVisualizer.INSTANCE.reload();
                int roomCount = RoomBoundsVisualizer.INSTANCE.getRoomCount();
                context.sendSuccess(I18n.translate("devmod.render.room_bounds_reloaded", roomCount), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_PATHFINDING_TOGGLE)
            .labelKey("devmod.radial.item.pathfinding")
            .descriptionKey("devmod.radial.item.pathfinding.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/AI/Pathfinding")
            .icon(Items.RAIL)
            .toggle(context -> PathfindingDebugger.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                PathfindingDebugger.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = PathfindingDebugger.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.pathfinding_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_LOS_TOGGLE)
            .labelKey("devmod.radial.item.line_of_sight")
            .descriptionKey("devmod.radial.item.line_of_sight.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/AI/Line of Sight")
            .icon(Items.SPYGLASS)
            .toggle(context -> LineOfSightVisualizer.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                LineOfSightVisualizer.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = LineOfSightVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.los_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_AGGRO_RANGE_TOGGLE)
            .labelKey("devmod.action.aggro_range.toggle")
            .descriptionKey("devmod.action.aggro_range.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/AI/Aggro Range")
            .icon(Items.TARGET)
            .toggle(context -> AggroRangeVisualizer.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                AggroRangeVisualizer.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = AggroRangeVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.aggro_range_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE)
            .labelKey("devmod.radial.item.vertical_levels")
            .descriptionKey("devmod.radial.item.vertical_levels.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Vertical Levels")
            .icon(Items.LADDER)
            .toggle(context -> VerticalLevelsVisualizer.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                VerticalLevelsVisualizer.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = VerticalLevelsVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.vertical_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE)
            .labelKey("devmod.radial.item.safe_spots")
            .descriptionKey("devmod.radial.item.safe_spots.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Safe Spots")
            .icon(Items.SHIELD)
            .toggle(context -> SafeSpotVisualizer.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                SafeSpotVisualizer.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = SafeSpotVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                int spotCount = SafeSpotVisualizer.INSTANCE.getSafeSpotCount();
                context.sendSuccess(I18n.translate("devmod.render.safe_spots_status", status, spotCount), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE)
            .labelKey("devmod.radial.item.attr_monitor")
            .descriptionKey("devmod.radial.item.attr_monitor.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Perf/Attribute Monitor")
            .icon(Items.EXPERIENCE_BOTTLE)
            .toggle(context -> AttributeMonitoringSystem.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                AttributeMonitoringSystem.INSTANCE.toggle();
                String status = AttributeMonitoringSystem.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                int trackedCount = AttributeMonitoringSystem.INSTANCE.getTrackedCount();
                context.sendSuccess(I18n.translate("devmod.render.attr_monitor_status", status, trackedCount), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_FPS_TRACKER_TOGGLE)
            .labelKey("devmod.radial.item.fps_tracker")
            .descriptionKey("devmod.radial.item.fps_tracker.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Perf/FPS Tracker")
            .icon(Items.CLOCK)
            .toggle(context -> FpsTracker.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                FpsTracker.INSTANCE.toggle();
                String status = FpsTracker.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.fps_tracker_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_PROFILER_TOGGLE)
            .labelKey("devmod.radial.item.profiler")
            .descriptionKey("devmod.radial.item.profiler.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Perf/Profiler")
            .icon(Items.REDSTONE)
            .toggle(context -> PerformanceProfiler.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                PerformanceProfiler.INSTANCE.toggle();
                String status = PerformanceProfiler.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.profiler_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE)
            .labelKey("devmod.radial.item.entity_density")
            .descriptionKey("devmod.radial.item.entity_density.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Perf/Entity Density")
            .icon(Items.VILLAGER_SPAWN_EGG)
            .toggle(context -> EntityDensityOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                EntityDensityOverlay.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = EntityDensityOverlay.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.entity_density_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_BOSS_PHASE_TOGGLE)
            .labelKey("devmod.radial.item.boss_phases")
            .descriptionKey("devmod.radial.item.boss_phases.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Boss Phases")
            .icon(Items.WITHER_SKELETON_SKULL)
            .toggle(context -> BossPhaseOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                BossPhaseOverlay.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = BossPhaseOverlay.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.boss_phase_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE)
            .labelKey("devmod.radial.item.skill_efficacy")
            .descriptionKey("devmod.radial.item.skill_efficacy.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Skill Efficacy")
            .icon(Items.ENCHANTED_BOOK)
            .toggle(context -> SkillEfficacyOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                SkillEfficacyOverlay.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = SkillEfficacyOverlay.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.skill_efficacy_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_SPAWNABILITY_TOGGLE)
            .labelKey("devmod.radial.item.spawnability")
            .descriptionKey("devmod.radial.item.spawnability.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Spawnability")
            .icon(Items.SPAWNER)
            .toggle(context -> SpawnabilityOverlay.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                SpawnabilityOverlay.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = SpawnabilityOverlay.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                var stats = SpawnabilityOverlay.INSTANCE.getStats();
                context.sendSuccess(I18n.translate("devmod.render.spawnability_status", status, stats.hostileSpawnBlocks()), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_CHUNK_PERF_TOGGLE)
            .labelKey("devmod.radial.item.chunk_perf")
            .descriptionKey("devmod.radial.item.chunk_perf.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Perf/Chunk Performance")
            .icon(Items.CHEST)
            .toggle(context -> ChunkPerformanceVisualizer.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ChunkPerformanceVisualizer.INSTANCE.toggle();
                String status = ChunkPerformanceVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.chunk_perf_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ECONOMY_TOGGLE)
            .labelKey("devmod.radial.item.economy")
            .descriptionKey("devmod.radial.item.economy.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Debug/Economy")
            .icon(Items.GOLD_INGOT)
            .toggle(context -> EconomyOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                EconomyOverlay.toggle();
                String status = EconomyOverlay.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.economy_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE)
            .labelKey("devmod.action.economy.view_cycle")
            .descriptionKey("devmod.action.economy.view_cycle.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Debug/Economy/View")
            .icon(Items.GOLD_BLOCK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                if (EconomyOverlay.isEnabled()) {
                    EconomyOverlay.cycleView();
                    String viewName = EconomyOverlay.getViewModeName();
                    context.sendSuccess(I18n.translate("devmod.render.economy_view", viewName), true);
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ECONOMY_SORT_CYCLE)
            .labelKey("devmod.action.economy.sort_cycle")
            .descriptionKey("devmod.action.economy.sort_cycle.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Debug/Economy/Sort")
            .icon(Items.HOPPER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                if (EconomyOverlay.isEnabled()) {
                    EconomyOverlay.cycleSortMode();
                    String sortName = EconomyOverlay.getSortModeName();
                    context.sendSuccess(I18n.translate("devmod.render.economy_sort", sortName), true);
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_IMPACT_DISMISS)
            .labelKey("devmod.action.impact.dismiss")
            .descriptionKey("devmod.action.impact.dismiss.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/HUD/Dismiss Impact")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ImpactData.clear();
                Impact3DPanelManager.INSTANCE.clear();
                ImpactVFX.clear();
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_SCREEN_SHAKE_TEST)
            .labelKey("devmod.action.screen_shake.test")
            .descriptionKey("devmod.action.screen_shake.test.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/VFX/Screen Shake")
            .icon(Items.NOTE_BLOCK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Player player = context.getPlayer();
                if (player == null) {
                    return;
                }
                com.frenkvs.devmod.effects.ShakeEffect testShake =
                    com.frenkvs.devmod.effects.ShakeManager.createMediumHit(player.position());
                com.frenkvs.devmod.effects.ShakeManager.INSTANCE.addShake(testShake);
                context.sendSuccess(Component.literal("§e[DevMod] §fScreen shake test triggered! Active shakes: §a" +
                    com.frenkvs.devmod.effects.ShakeManager.INSTANCE.getActiveCount()), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_QUICK_HELP_TOGGLE)
            .labelKey("devmod.radial.item.quick_help")
            .descriptionKey("devmod.radial.item.quick_help.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Help/Quick Help")
            .icon(Items.KNOWLEDGE_BOOK)
            .toggle(context -> QuickHelpOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                QuickHelpOverlay.toggle();
                String status = QuickHelpOverlay.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.quick_help_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_QUEST_TOGGLE)
            .labelKey("devmod.radial.item.quest_hud")
            .descriptionKey("devmod.radial.item.quest_hud.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/HUD")
            .icon(Items.MAP)
            .toggle(context -> QuestHudOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                QuestHudOverlay.toggle();
                String status = QuestHudOverlay.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.quest_hud_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_ENDURANCE_TOGGLE)
            .labelKey("devmod.radial.item.endurance_hud")
            .descriptionKey("devmod.radial.item.endurance_hud.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/HUD")
            .icon(Items.IRON_CHESTPLATE)
            .toggle(context -> EnduranceQuestOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                EnduranceQuestOverlay.toggle();
                String status = EnduranceQuestOverlay.isEnabled() ? "§aON" : "§cOFF";
                context.sendSuccess(I18n.translate("devmod.render.endurance_hud_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE)
            .labelKey("devmod.action.endurance_hud.details")
            .descriptionKey("devmod.action.endurance_hud.details.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/HUD/Details")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                EnduranceQuestOverlay.toggleDetails();
                String detailStatus = EnduranceQuestOverlay.isShowingDetails() ? "§aDetailed" : "§7Compact";
                context.sendSuccess(I18n.translate("devmod.render.endurance_details", detailStatus), true);
            })
            .build());
    }

    private static void registerConfigActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_BODY_PART_DETECTION_TOGGLE)
            .labelKey("devmod.configuration.combat.bodyPartDetection")
            .descriptionKey("devmod.configuration.combat.bodyPartDetection.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Combat/Body Part Detection")
            .icon(Items.TARGET)
            .toggle(context -> Config.BODY_PART_DETECTION_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.BODY_PART_DETECTION_ENABLED.set(!Config.BODY_PART_DETECTION_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TELEMETRY_TOGGLE)
            .labelKey("devmod.configuration.telemetry.enabled")
            .descriptionKey("devmod.configuration.telemetry.enabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Telemetry/Recording")
            .icon(Items.WRITABLE_BOOK)
            .toggle(context -> Config.TELEMETRY_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.TELEMETRY_ENABLED.set(!Config.TELEMETRY_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TELEMETRY_HITS_TOGGLE)
            .labelKey("devmod.configuration.telemetry.logHits")
            .descriptionKey("devmod.configuration.telemetry.logHits.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Telemetry/Hits")
            .icon(Items.IRON_SWORD)
            .toggle(context -> Config.TELEMETRY_HITS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.TELEMETRY_HITS_ENABLED.set(!Config.TELEMETRY_HITS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TELEMETRY_DEATHS_TOGGLE)
            .labelKey("devmod.configuration.telemetry.logDeaths")
            .descriptionKey("devmod.configuration.telemetry.logDeaths.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Telemetry/Deaths")
            .icon(Items.SKELETON_SKULL)
            .toggle(context -> Config.TELEMETRY_DEATHS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.TELEMETRY_DEATHS_ENABLED.set(!Config.TELEMETRY_DEATHS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TELEMETRY_SPAWNS_TOGGLE)
            .labelKey("devmod.configuration.telemetry.logSpawns")
            .descriptionKey("devmod.configuration.telemetry.logSpawns.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Telemetry/Spawns")
            .icon(Items.EGG)
            .toggle(context -> Config.TELEMETRY_SPAWNS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.TELEMETRY_SPAWNS_ENABLED.set(!Config.TELEMETRY_SPAWNS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_HISTORY_TOGGLE)
            .labelKey("devmod.configuration.debug.impactHudHistoryEnabled")
            .descriptionKey("devmod.configuration.debug.impactHudHistoryEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/History")
            .icon(Items.CLOCK)
            .toggle(context -> Config.IMPACT_HUD_HISTORY_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_HUD_HISTORY_ENABLED.set(!Config.IMPACT_HUD_HISTORY_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_DPS_TOGGLE)
            .labelKey("devmod.configuration.debug.impactHudDpsEnabled")
            .descriptionKey("devmod.configuration.debug.impactHudDpsEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/DPS")
            .icon(Items.REDSTONE)
            .toggle(context -> Config.IMPACT_HUD_DPS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_HUD_DPS_ENABLED.set(!Config.IMPACT_HUD_DPS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_LEFT)
            .labelKey("devmod.config.impact_hud.position.top_left")
            .descriptionKey("devmod.config.impact_hud.position.top_left.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Top Left")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.TOP_LEFT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_RIGHT)
            .labelKey("devmod.config.impact_hud.position.top_right")
            .descriptionKey("devmod.config.impact_hud.position.top_right.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Top Right")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.TOP_RIGHT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_LEFT)
            .labelKey("devmod.config.impact_hud.position.center_left")
            .descriptionKey("devmod.config.impact_hud.position.center_left.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Center Left")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.CENTER_LEFT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_RIGHT)
            .labelKey("devmod.config.impact_hud.position.center_right")
            .descriptionKey("devmod.config.impact_hud.position.center_right.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Center Right")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.CENTER_RIGHT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_LEFT)
            .labelKey("devmod.config.impact_hud.position.bottom_left")
            .descriptionKey("devmod.config.impact_hud.position.bottom_left.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Bottom Left")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.BOTTOM_LEFT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_RIGHT)
            .labelKey("devmod.config.impact_hud.position.bottom_right")
            .descriptionKey("devmod.config.impact_hud.position.bottom_right.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Bottom Right")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.BOTTOM_RIGHT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_MINUS)
            .labelKey("devmod.config.impact_hud.offset_x.minus")
            .descriptionKey("devmod.config.impact_hud.offset_x.minus.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Offset/X-")
            .icon(Items.ARROW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> adjustHudOffset(true, -10))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_PLUS)
            .labelKey("devmod.config.impact_hud.offset_x.plus")
            .descriptionKey("devmod.config.impact_hud.offset_x.plus.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Offset/X+")
            .icon(Items.ARROW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> adjustHudOffset(true, 10))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_MINUS)
            .labelKey("devmod.config.impact_hud.offset_y.minus")
            .descriptionKey("devmod.config.impact_hud.offset_y.minus.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Offset/Y-")
            .icon(Items.ARROW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> adjustHudOffset(false, -10))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_PLUS)
            .labelKey("devmod.config.impact_hud.offset_y.plus")
            .descriptionKey("devmod.config.impact_hud.offset_y.plus.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Offset/Y+")
            .icon(Items.ARROW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> adjustHudOffset(false, 10))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_PRESET_EXPORT)
            .labelKey("devmod.action.config.impact_hud.export")
            .descriptionKey("devmod.action.config.impact_hud.export.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Presets/Export")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var path = ImpactHudPresets.getDefaultPresetFile();
                boolean success = ImpactHudPresets.exportToDefault();
                if (success) {
                    context.sendSuccess(I18n.translate("devmod.testing.impact_hud_preset_exported", path.toString()), true);
                } else {
                    context.sendFailure(I18n.translate("devmod.testing.impact_hud_preset_export_failed", path.toString()));
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_PRESET_IMPORT)
            .labelKey("devmod.action.config.impact_hud.import")
            .descriptionKey("devmod.action.config.impact_hud.import.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Presets/Import")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var path = ImpactHudPresets.getDefaultPresetFile();
                boolean success = ImpactHudPresets.importFromDefault();
                if (success) {
                    ImpactHudOverlay.setEnabled(Config.IMPACT_HUD_ENABLED.get());
                    context.sendSuccess(I18n.translate("devmod.testing.impact_hud_preset_imported", path.toString()), true);
                } else {
                    context.sendFailure(I18n.translate("devmod.testing.impact_hud_preset_import_failed", path.toString()));
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_RESET_DEFAULTS)
            .labelKey("devmod.action.config.impact_hud.reset")
            .descriptionKey("devmod.action.config.impact_hud.reset.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Reset Defaults")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> resetImpactHudDefaults())
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Enabled")
            .icon(Items.BLAZE_POWDER)
            .toggle(context -> Config.IMPACT_VFX_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_ENABLED.set(!Config.IMPACT_VFX_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_VORTEX_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxVortexEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxVortexEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Vortex")
            .icon(Items.ENDER_EYE)
            .toggle(context -> Config.IMPACT_VFX_VORTEX_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_VORTEX_ENABLED.set(!Config.IMPACT_VFX_VORTEX_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_SLASH_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxSlashEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxSlashEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Slash")
            .icon(Items.IRON_SWORD)
            .toggle(context -> Config.IMPACT_VFX_SLASH_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_SLASH_ENABLED.set(!Config.IMPACT_VFX_SLASH_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_LINES_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxLinesEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxLinesEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Lines")
            .icon(Items.STRING)
            .toggle(context -> Config.IMPACT_VFX_LINES_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_LINES_ENABLED.set(!Config.IMPACT_VFX_LINES_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_LOW)
            .labelKey("devmod.config.impact_vfx.intensity.low")
            .descriptionKey("devmod.config.impact_vfx.intensity.low.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Intensity/Low")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setVfxIntensity(0.5))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MED)
            .labelKey("devmod.config.impact_vfx.intensity.med")
            .descriptionKey("devmod.config.impact_vfx.intensity.med.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Intensity/Normal")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setVfxIntensity(1.0))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_HIGH)
            .labelKey("devmod.config.impact_vfx.intensity.high")
            .descriptionKey("devmod.config.impact_vfx.intensity.high.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Intensity/High")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setVfxIntensity(1.5))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MAX)
            .labelKey("devmod.config.impact_vfx.intensity.max")
            .descriptionKey("devmod.config.impact_vfx.intensity.max.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Intensity/Max")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setVfxIntensity(2.0))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_RESET_DEFAULTS)
            .labelKey("devmod.action.config.impact_vfx.reset")
            .descriptionKey("devmod.action.config.impact_vfx.reset.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Reset Defaults")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> resetImpactVfxDefaults())
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE)
            .labelKey("devmod.config.screen_shake.toggle")
            .descriptionKey("devmod.config.screen_shake.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Screen Shake")
            .icon(Items.NOTE_BLOCK)
            .toggle(context -> Config.SCREEN_SHAKE_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.SCREEN_SHAKE_ENABLED.set(!Config.SCREEN_SHAKE_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_PROJECTILE_TRAILS_TOGGLE)
            .labelKey("devmod.config.projectile_trails.toggle")
            .descriptionKey("devmod.config.projectile_trails.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Projectile Trails")
            .icon(Items.SPECTRAL_ARROW)
            .toggle(context -> Config.PROJECTILE_TRAILS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.PROJECTILE_TRAILS_ENABLED.set(!Config.PROJECTILE_TRAILS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_BADGE_POPUPS_TOGGLE)
            .labelKey("devmod.config.badge_popups.toggle")
            .descriptionKey("devmod.config.badge_popups.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Badge Popups")
            .icon(Items.NAME_TAG)
            .toggle(context -> Config.BADGE_POPUP_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.BADGE_POPUP_ENABLED.set(!Config.BADGE_POPUP_ENABLED.get()))
            .build());
    }

    private static void registerTelemetryActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_DEATH)
            .labelKey("devmod.action.telemetry.export.heatmap.death")
            .descriptionKey("devmod.action.telemetry.export.heatmap.death.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Death")
            .icon(Items.SKELETON_SKULL)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportDeathHeatmap,
                "devmod.telemetry.exported_heatmap.death"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_MOVEMENT)
            .labelKey("devmod.action.telemetry.export.heatmap.movement")
            .descriptionKey("devmod.action.telemetry.export.heatmap.movement.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Movement")
            .icon(Items.FEATHER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportMovementHeatmap,
                "devmod.telemetry.exported_heatmap.movement"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_CAMPING)
            .labelKey("devmod.action.telemetry.export.heatmap.camping")
            .descriptionKey("devmod.action.telemetry.export.heatmap.camping.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Camping")
            .icon(Items.CAMPFIRE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportCampingHeatmap,
                "devmod.telemetry.exported_heatmap.camping"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_STUCK)
            .labelKey("devmod.action.telemetry.export.heatmap.stuck")
            .descriptionKey("devmod.action.telemetry.export.heatmap.stuck.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Stuck")
            .icon(Items.COBWEB)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportStuckHeatmap,
                "devmod.telemetry.exported_heatmap.stuck"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP)
            .labelKey("devmod.action.telemetry.export.heatmap.aggro_drop")
            .descriptionKey("devmod.action.telemetry.export.heatmap.aggro_drop.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Aggro Drop")
            .icon(Items.ENDER_EYE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportAggroDropHeatmap,
                "devmod.telemetry.exported_heatmap.aggro_drop"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_KITING)
            .labelKey("devmod.action.telemetry.export.heatmap.kiting")
            .descriptionKey("devmod.action.telemetry.export.heatmap.kiting.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Kiting")
            .icon(Items.BOW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportKitingHeatmap,
                "devmod.telemetry.exported_heatmap.kiting"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_CHOKE_POINTS)
            .labelKey("devmod.action.telemetry.export.heatmap.choke_points")
            .descriptionKey("devmod.action.telemetry.export.heatmap.choke_points.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Choke Points")
            .icon(Items.CHAIN)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportChokePointHeatmap,
                "devmod.telemetry.exported_heatmap.choke_points"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_PARKOUR_FALLS)
            .labelKey("devmod.action.telemetry.export.heatmap.parkour_falls")
            .descriptionKey("devmod.action.telemetry.export.heatmap.parkour_falls.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Parkour Falls")
            .icon(Items.HONEY_BOTTLE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportParkourFallHeatmap,
                "devmod.telemetry.exported_heatmap.parkour_falls"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_DAMAGE_STATS)
            .labelKey("devmod.action.telemetry.export.damage_stats")
            .descriptionKey("devmod.action.telemetry.export.damage_stats.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Damage Stats")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportDamageStats,
                "devmod.telemetry.exported_damage_stats"))
            .build());
    }

    private static void registerQuestActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.QUEST_TASK_COMPLETE)
            .labelKey("devmod.action.quest.task.complete")
            .descriptionKey("devmod.action.quest.task.complete.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Task/Complete")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> QuestManager.INSTANCE.getCurrentTask() != null,
                    "devmod.action.requires_active_task"
                )))
            .handler(context -> {
                QuestTask task = QuestManager.INSTANCE.getCurrentTask();
                if (task != null) {
                    String taskName = task.getDescription();
                    QuestManager.INSTANCE.completeCurrentTask();
                    context.sendSuccess(
                        I18n.translate("devmod.message.task_completed")
                            .append(I18n.translate("devmod.ui.colon_value", taskName)),
                        true
                    );
                } else {
                    context.sendSuccess(Component.translatable("devmod.message.no_active_task"), true);
                }
            })
            .build());
    }

    private static void registerEnduranceActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.ENDURANCE_QUEST_START)
            .labelKey("devmod.action.endurance.start")
            .descriptionKey("devmod.action.endurance.start.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Start Quest")
            .icon(Items.COMPASS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                StartQuestPayload payload = context.getPayload(StartQuestPayload.class);
                if (payload == null) {
                    return;
                }
                PacketDistributor.sendToServer(payload);
                Minecraft.getInstance().setScreen(null);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ENDURANCE_QUEST_CONTINUE)
            .labelKey("devmod.action.endurance.continue")
            .descriptionKey("devmod.action.endurance.continue.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Continue")
            .icon(Items.TOTEM_OF_UNDYING)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> ClientQuestCache.isAwaitingRespawn()
                        || ClientQuestCache.isAtCheckpoint()
                        || context.getPayload(QuestActionPayload.Action.class) != null,
                    "devmod.action.requires_respawn_or_checkpoint"
                )))
            .handler(context -> {
                QuestActionPayload.Action action = resolveContinueAction(context);
                PacketDistributor.sendToServer(new QuestActionPayload(action));
                context.sendSuccess(I18n.translate("devmod.network.continuing_quest"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ENDURANCE_QUEST_EXIT)
            .labelKey("devmod.action.endurance.exit")
            .descriptionKey("devmod.action.endurance.exit.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Endurance/Exit")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly().and(
                ActionPreconditions.withMessage(
                    context -> ClientQuestCache.hasActiveQuest(),
                    "devmod.action.requires_active_quest"
                )))
            .handler(context -> {
                if (!context.isConfirmed()) {
                    Minecraft.getInstance().setScreen(new QuestExitConfirmScreen(Minecraft.getInstance().screen));
                    return;
                }
                QuestActionPayload.Action action = resolveExitAction(context);
                PacketDistributor.sendToServer(new QuestActionPayload(action));
            })
            .build());
    }

    private static void registerAbilityActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.ABILITY_DASH)
            .labelKey("devmod.action.ability.dash")
            .descriptionKey("devmod.action.ability.dash.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Abilities/Dash")
            .icon(Items.FEATHER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> PacketDistributor.sendToServer(
                java.util.Objects.requireNonNull(AbilityActionPayload.dash(), "dashPayload")))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ABILITY_DODGE)
            .labelKey("devmod.action.ability.dodge")
            .descriptionKey("devmod.action.ability.dodge.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Abilities/Dodge")
            .icon(Items.RABBIT_FOOT)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Minecraft mc = Minecraft.getInstance();
                DodgeAbilitySystem.DodgeDirection direction = determineDodgeDirection(mc);
                PacketDistributor.sendToServer(
                    java.util.Objects.requireNonNull(AbilityActionPayload.dodge(direction), "dodgePayload"));
            })
            .build());
    }

    private static void registerKeybindHints() {
        ActionKeybindRegistry.register(ActionIds.UI_RADIAL_OPEN, KeyInputHandler.OPEN_RADIAL_MENU_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_SETTINGS_OPEN, KeyInputHandler.OPEN_SETTINGS_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO, KeyInputHandler.OPEN_WEAPON_EDITOR_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON, KeyInputHandler.OPEN_WEAPON_EDITOR_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR, KeyInputHandler.OPEN_WEAPON_EDITOR_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.DEBUG_OVERLAY_TOGGLE, KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_BODY_PARTS_TOGGLE, KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE, KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_HEATMAP_CYCLE, KeyInputHandler.TOGGLE_HEATMAP_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT, KeyInputHandler.TOGGLE_HEATMAP_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.DEBUG_HEATMAP_CLEAR_ALL, KeyInputHandler.TOGGLE_HEATMAP_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.DEBUG_IMPACT_DISMISS, KeyInputHandler.DISMISS_IMPACT_HUD_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE, KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN, KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.DEBUG_ROOM_BOUNDS_RELOAD, KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.DEBUG_PATHFINDING_TOGGLE, KeyInputHandler.TOGGLE_PATHFINDING_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_LOS_TOGGLE, KeyInputHandler.TOGGLE_LOS_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE, KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE, KeyInputHandler.TOGGLE_SAFE_SPOT_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN, KeyInputHandler.OPEN_DASHBOARD_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE, KeyInputHandler.TOGGLE_ATTRIBUTE_MONITOR_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_FPS_TRACKER_TOGGLE, KeyInputHandler.TOGGLE_FPS_TRACKER_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_PROFILER_TOGGLE, KeyInputHandler.TOGGLE_PROFILER_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_QA_TESTING_OPEN, KeyInputHandler.OPEN_QA_TESTING_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_TESTING_HUB_OPEN, KeyInputHandler.OPEN_TESTING_HUB_KEY);
        ActionKeybindRegistry.register(ActionIds.ARENA_HUD_TOGGLE, KeyInputHandler.OPEN_TESTING_HUB_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.DEBUG_BOSS_PHASE_TOGGLE, KeyInputHandler.TOGGLE_BOSS_PHASE_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE, KeyInputHandler.TOGGLE_ENTITY_DENSITY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE, KeyInputHandler.TOGGLE_SKILL_EFFICACY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_SPAWNABILITY_TOGGLE, KeyInputHandler.TOGGLE_SPAWNABILITY_KEY);
        ActionKeybindRegistry.register(ActionIds.HUD_QUEST_TOGGLE, KeyInputHandler.TOGGLE_QUEST_HUD_KEY);
        ActionKeybindRegistry.register(ActionIds.QUEST_TASK_COMPLETE, KeyInputHandler.QUEST_COMPLETE_TASK_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_QUEST_EDITOR_OPEN, KeyInputHandler.OPEN_QUEST_EDITOR_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ECONOMY_TOGGLE, KeyInputHandler.TOGGLE_ECONOMY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE, KeyInputHandler.TOGGLE_ECONOMY_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.DEBUG_ECONOMY_SORT_CYCLE, KeyInputHandler.TOGGLE_ECONOMY_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.DEBUG_CHUNK_PERF_TOGGLE, KeyInputHandler.TOGGLE_CHUNK_PERF_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_ENDURANCE_EDITOR_OPEN, KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY);
        ActionKeybindRegistry.register(ActionIds.HUD_ENDURANCE_TOGGLE, KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE, KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.ENDURANCE_QUEST_CONTINUE, KeyInputHandler.QUEST_CONTINUE_KEY);
        ActionKeybindRegistry.register(ActionIds.ENDURANCE_QUEST_EXIT, KeyInputHandler.QUEST_EXIT_KEY);
        ActionKeybindRegistry.register(ActionIds.HUD_QUICK_HELP_TOGGLE, KeyInputHandler.TOGGLE_HELP_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_MOB_CONFIG_OPEN, KeyInputHandler.INSPECT_MOB_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_SCREEN_SHAKE_TEST, KeyInputHandler.TEST_SCREEN_SHAKE_KEY);
        ActionKeybindRegistry.register(ActionIds.ABILITY_DASH, KeyInputHandler.DASH_KEY);
        ActionKeybindRegistry.register(ActionIds.ABILITY_DODGE, KeyInputHandler.DODGE_KEY);
        ActionKeybindRegistry.register(ActionIds.UI_PARTY_OPEN, KeyInputHandler.OPEN_PARTY_KEY);
    }

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
        net.minecraft.sounds.SoundEvent soundEvent = net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value();
        if (soundEvent == null) {
            return;
        }
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
            Minecraft.getInstance().setScreen(new ItemEditorScreen(heldItem, EditorStartTab.ARMOR));
        } else {
            Minecraft.getInstance().setScreen(new ItemEditorScreen(heldItem, EditorStartTab.WEAPON));
        }
    }

    private static void openItemEditorTab(ActionContext context, EditorStartTab tab) {
        ItemStack payloadItem = context.getPayload(ItemStack.class);
        if (payloadItem != null && !payloadItem.isEmpty()) {
            Minecraft.getInstance().setScreen(new ItemEditorScreen(payloadItem.copy(), tab));
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
        Minecraft.getInstance().setScreen(new ItemEditorScreen(heldItem, tab));
    }

    private static void openMobConfig(ActionContext context) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.Mob payloadMob = context.getPayload(net.minecraft.world.entity.Mob.class);
        if (payloadMob != null) {
            mc.setScreen(new MobConfigScreen(payloadMob));
            return;
        }

        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
                mc.setScreen(new MobConfigScreen(mob));
            } else {
                context.sendSuccess(I18n.translate("devmod.render.target_not_mob"), true);
            }
        } else {
            context.sendSuccess(I18n.translate("devmod.render.no_entity_targeted"), true);
        }
    }

    private static void openMobEquipment(ActionContext context) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen parent = mc.screen;
        net.minecraft.world.entity.Mob mob = null;

        net.minecraft.world.entity.Mob payloadMob = context.getPayload(net.minecraft.world.entity.Mob.class);
        if (payloadMob != null) {
            mob = payloadMob;
        } else if (mc.screen instanceof MobConfigScreen screen) {
            mob = screen.getMob();
            parent = screen;
        } else if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
            && entityHit.getEntity() instanceof net.minecraft.world.entity.Mob targetMob) {
            mob = targetMob;
        }

        if (mob == null) {
            context.sendFailure(Component.translatable("devmod.action.mob_equipment.requires_target"));
            return;
        }

        mc.setScreen(new MobEquipmentScreen(mob, parent));
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
        Minecraft.getInstance().setScreen(new PerkSelectionScreen(payload.waveNumber(), payload.choices()));
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
        Minecraft.getInstance().setScreen(new QuestCompletionScreen(payload));
    }

    private static void openPartyInvite(ActionContext context) {
        PartyNotificationPayload payload = context.getPayload(PartyNotificationPayload.class);
        if (payload == null) {
            payload = PartyUiCache.getActiveInvite();
        }
        if (payload == null) {
            context.sendFailure(Component.translatable("devmod.action.party_invite.missing"));
            return;
        }
        Minecraft.getInstance().setScreen(InvitePopupScreen.fromNotification(payload));
    }

    private static void openArenaQuickTestWizard(ActionContext context) {
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        if (registry == null) {
            context.sendFailure(Component.translatable("devmod.action.arena.quick_test_wizard.missing_registry"));
            return;
        }
        com.devmod.arena.ui.QuickTestWizard.open(registry, config -> {
            if (config.playerCountOverride() != null && config.playerCountOverride() > 0) {
                context.sendSuccess(Component.translatable("devmod.action.arena.quick_test_wizard.player_count_ignored"), true);
            }
            if (config.forceTemplate()) {
                context.executeCommand("arena force " + config.templateId() + " 5");
            }
            if (config.dryRun()) {
                context.executeCommand("arena validate " + config.templateId());
            } else {
                context.executeCommand("arena create " + config.templateId());
            }
        });
    }

    private static void openTestingHub(ActionContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (TestingHubState.INSTANCE.isMinimized()) {
            TestingHub.restoreFromHud();
            return;
        }
        try {
            mc.setScreen(new TestingHub());
        } catch (Exception e) {
            DevMod.LOGGER.error("[DevMod] Error opening Testing Hub: {}", e.getMessage(), e);
            context.sendFailure(I18n.translate("devmod.message.error_opening_hub", e.getMessage()));
        }
    }

    private static QuestActionPayload.Action resolveContinueAction(ActionContext context) {
        QuestActionPayload.Action override = context.getPayload(QuestActionPayload.Action.class);
        if (override == QuestActionPayload.Action.CONTINUE_AFTER_DEATH
            || override == QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE) {
            return override;
        }
        if (ClientQuestCache.isAtCheckpoint()) {
            return QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE;
        }
        return QuestActionPayload.Action.CONTINUE_AFTER_DEATH;
    }

    private static QuestActionPayload.Action resolveExitAction(ActionContext context) {
        QuestActionPayload.Action override = context.getPayload(QuestActionPayload.Action.class);
        if (override == QuestActionPayload.Action.GIVE_UP_AFTER_DEATH
            || override == QuestActionPayload.Action.EXIT_AT_CHECKPOINT
            || override == QuestActionPayload.Action.ABANDON_QUEST) {
            return override;
        }
        if (ClientQuestCache.isAtCheckpoint()) {
            return QuestActionPayload.Action.EXIT_AT_CHECKPOINT;
        }
        if (ClientQuestCache.isAwaitingRespawn()) {
            return QuestActionPayload.Action.GIVE_UP_AFTER_DEATH;
        }
        return QuestActionPayload.Action.ABANDON_QUEST;
    }

    private static void toggleNativeDebug(ActionContext context, DebugFeature feature, BooleanSupplier currentState) {
        boolean wasEnabled = currentState.getAsBoolean();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PacketDistributor.sendToServer(new DebugTogglePayload(feature.getId()));
        }
        boolean enabled = !wasEnabled;
        String status = enabled ? "§aON" : "§cOFF";
        context.sendSuccess(I18n.translate("devmod.render.native_debug_status", feature.getDisplayName(), status), true);
    }

    private static void setDebugOverlaysEnabled(ActionContext context, boolean enabled) {
        DebugRenderer.INSTANCE.setEnabled(enabled);
        LightLevelOverlay.INSTANCE.setEnabled(enabled);
        LineOfSightVisualizer.INSTANCE.setEnabled(enabled);
        PathfindingDebugger.INSTANCE.setEnabled(enabled);
        RoomBoundsVisualizer.INSTANCE.setEnabled(enabled);
        SettingsManager.INSTANCE.markDirty();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            if (enabled) {
                toggleNativeIfNeeded(DebugFeature.ENTITY_PATHING, DebugRenderBools.ENTITY_PATHING, true);
                toggleNativeIfNeeded(DebugFeature.ENTITY_GOALS, DebugRenderBools.ENTITY_GOALS, true);
            } else {
                toggleNativeIfNeeded(DebugFeature.ENTITY_PATHING, DebugRenderBools.ENTITY_PATHING, false);
                toggleNativeIfNeeded(DebugFeature.ENTITY_GOALS, DebugRenderBools.ENTITY_GOALS, false);
                toggleNativeIfNeeded(DebugFeature.ENTITY_BRAINS, DebugRenderBools.ENTITY_BRAINS, false);
                toggleNativeIfNeeded(DebugFeature.POI, DebugRenderBools.POI, false);
                toggleNativeIfNeeded(DebugFeature.RAIDS, DebugRenderBools.RAIDS, false);
                toggleNativeIfNeeded(DebugFeature.BEES, DebugRenderBools.BEES, false);
                toggleNativeIfNeeded(DebugFeature.GAME_EVENTS, DebugRenderBools.GAME_EVENTS, false);
                toggleNativeIfNeeded(DebugFeature.STRUCTURE_GENERATIONS, DebugRenderBools.STRUCTURES, false);
            }
        }

        String key = enabled ? "devmod.render.debug_overlays_enabled" : "devmod.render.debug_overlays_disabled";
        context.sendSuccess(I18n.translate(key), true);
    }

    private static void toggleNativeIfNeeded(DebugFeature feature, boolean current, boolean target) {
        if (current == target) {
            return;
        }
        PacketDistributor.sendToServer(new DebugTogglePayload(feature.getId()));
    }

    private static void setHudPosition(Config.HudPosition position) {
        if (position == null) {
            return;
        }
        try {
            Config.IMPACT_HUD_POSITION.set(position);
        } catch (Exception ignored) {}
    }

    private static void adjustHudOffset(boolean isX, int delta) {
        try {
            if (isX) {
                int current = Config.IMPACT_HUD_OFFSET_X.get();
                Config.IMPACT_HUD_OFFSET_X.set(Math.max(0, Math.min(200, current + delta)));
            } else {
                int current = Config.IMPACT_HUD_OFFSET_Y.get();
                Config.IMPACT_HUD_OFFSET_Y.set(Math.max(0, Math.min(200, current + delta)));
            }
        } catch (Exception ignored) {}
    }

    private static void setVfxIntensity(double value) {
        try {
            Config.IMPACT_VFX_INTENSITY.set(value);
        } catch (Exception ignored) {}
    }

    private static void resetImpactHudDefaults() {
        Config.IMPACT_HUD_ENABLED.set(true);
        Config.IMPACT_HUD_POSITION.set(Config.HudPosition.TOP_RIGHT);
        Config.IMPACT_HUD_OFFSET_X.set(10);
        Config.IMPACT_HUD_OFFSET_Y.set(10);
        Config.IMPACT_HUD_HISTORY_ENABLED.set(true);
        Config.IMPACT_HUD_DPS_ENABLED.set(true);

        ImpactHudOverlay.setEnabled(true);
        Impact3DPanelManager.INSTANCE.setEnabled(true);
    }

    private static void resetImpactVfxDefaults() {
        Config.IMPACT_VFX_ENABLED.set(true);
        Config.IMPACT_VFX_VORTEX_ENABLED.set(true);
        Config.IMPACT_VFX_SLASH_ENABLED.set(true);
        Config.IMPACT_VFX_LINES_ENABLED.set(true);
        Config.IMPACT_VFX_INTENSITY.set(1.0);
    }

    private static void cycleHeatmaps(ActionContext context) {
        boolean shiftHeld = context.isShiftDown();
        boolean ctrlHeld = context.isCtrlDown();

        if (shiftHeld) {
            clearAllHeatmaps(context);
            return;
        }

        if (ctrlHeld) {
            clearCurrentHeatmap(context);
            return;
        }

        cycleHeatmapType();
        SettingsManager.INSTANCE.markDirty();

        if (currentHeatmapIndex >= 0 && currentHeatmapIndex < HEATMAP_CYCLE.length) {
            HeatmapVisualizer.HeatmapType currentType = HEATMAP_CYCLE[currentHeatmapIndex];
            int loaded = HeatmapVisualizer.INSTANCE.loadDataFromService(currentType);
            int total = HeatmapVisualizer.INSTANCE.getDataCount(currentType);
            String activeTypes = HeatmapVisualizer.INSTANCE.getActiveTypesString();
            DevMod.LOGGER.debug("Heatmap loaded {} new points, total: {}", loaded, total);
            context.sendSuccess(I18n.translate("devmod.render.heatmap_status", activeTypes, total), true);
        } else {
            context.sendSuccess(I18n.translate("devmod.render.heatmap_off"), true);
        }
    }

    private static void toggleHeatmaps(ActionContext context) {
        boolean enable = !HeatmapVisualizer.INSTANCE.hasActiveHeatmaps();
        for (HeatmapVisualizer.HeatmapType type : HeatmapVisualizer.HeatmapType.values()) {
            HeatmapVisualizer.INSTANCE.setEnabled(type, enable);
        }
        if (!enable) {
            currentHeatmapIndex = -1;
        }
        String status = enable ? "§aON" : "§cOFF";
        context.sendSuccess(I18n.translate("devmod.render.heatmap_toggle_status", status), true);
    }

    private static void toggleHeatmapType(ActionContext context, HeatmapVisualizer.HeatmapType type) {
        boolean enable = !HeatmapVisualizer.INSTANCE.isEnabled(type);
        HeatmapVisualizer.INSTANCE.setEnabled(type, enable);
        int index = heatmapIndex(type);
        if (enable) {
            currentHeatmapIndex = index;
        } else if (currentHeatmapIndex == index) {
            currentHeatmapIndex = -1;
        }
        String status = enable ? "§aON" : "§cOFF";
        context.sendSuccess(I18n.translate("devmod.render.heatmap_type_toggle_status", type.name(), status), true);
    }

    private static void clearCurrentHeatmap(ActionContext context) {
        if (currentHeatmapIndex >= 0 && currentHeatmapIndex < HEATMAP_CYCLE.length) {
            HeatmapVisualizer.HeatmapType currentType = HEATMAP_CYCLE[currentHeatmapIndex];
            HeatmapVisualizer.INSTANCE.clear(currentType);
            int remaining = HeatmapVisualizer.INSTANCE.getDataCount(currentType);
            context.sendSuccess(I18n.translate("devmod.render.heatmap_type_cleared", currentType.name(), remaining), true);
        } else {
            context.sendSuccess(I18n.translate("devmod.render.no_heatmap_selected"), true);
        }
    }

    private static void clearAllHeatmaps(ActionContext context) {
        HeatmapVisualizer.INSTANCE.clearAll();
        currentHeatmapIndex = -1;
        for (HeatmapVisualizer.HeatmapType type : HEATMAP_CYCLE) {
            HeatmapVisualizer.INSTANCE.setEnabled(type, false);
        }
        context.sendSuccess(I18n.translate("devmod.render.heatmaps_cleared"), true);
    }

    private static int heatmapIndex(HeatmapVisualizer.HeatmapType type) {
        for (int i = 0; i < HEATMAP_CYCLE.length; i++) {
            if (HEATMAP_CYCLE[i] == type) {
                return i;
            }
        }
        return -1;
    }

    private static void cycleHeatmapType() {
        if (currentHeatmapIndex >= 0 && currentHeatmapIndex < HEATMAP_CYCLE.length) {
            HeatmapVisualizer.INSTANCE.setEnabled(HEATMAP_CYCLE[currentHeatmapIndex], false);
        }
        currentHeatmapIndex++;
        if (currentHeatmapIndex >= HEATMAP_CYCLE.length) {
            currentHeatmapIndex = -1;
        }
        if (currentHeatmapIndex >= 0) {
            HeatmapVisualizer.INSTANCE.setEnabled(HEATMAP_CYCLE[currentHeatmapIndex], true);
        }
    }

    private static void exportTelemetry(ActionContext context, Runnable export, String messageKey) {
        export.run();
        context.sendSuccess(I18n.translate(messageKey), true);
    }

    private static void checkOverlayCount(Player player) {
        int activeCount = 0;
        if (DebugRenderer.INSTANCE.isEnabled()) activeCount++;
        if (LightLevelOverlay.INSTANCE.isEnabled()) activeCount++;
        if (!HeatmapVisualizer.INSTANCE.getActiveTypesString().equals("None")) activeCount++;
        if (RoomBoundsVisualizer.INSTANCE.isEnabled()) activeCount++;
        if (PathfindingDebugger.INSTANCE.isEnabled()) activeCount++;
        if (LineOfSightVisualizer.INSTANCE.isEnabled()) activeCount++;
        if (VerticalLevelsVisualizer.INSTANCE.isEnabled()) activeCount++;
        if (SafeSpotVisualizer.INSTANCE.isEnabled()) activeCount++;
        if (FpsTracker.INSTANCE.isEnabled()) activeCount++;
        if (EntityDensityOverlay.isEnabled()) activeCount++;
        if (BossPhaseOverlay.isEnabled()) activeCount++;

        long now = System.currentTimeMillis();
        if (activeCount >= 5 && (now - lastOverlayWarningTime) > 30000) {
            lastOverlayWarningTime = now;
            if (activeCount >= 8) {
                player.displayClientMessage(I18n.translate("devmod.render.overlays_warning", activeCount), false);
            } else {
                player.displayClientMessage(I18n.translate("devmod.render.overlays_info", activeCount), true);
            }
        }
    }

    private static long lastOverlayWarningTime = 0;

    private static DodgeAbilitySystem.DodgeDirection determineDodgeDirection(Minecraft mc) {
        var options = mc.options;
        boolean left = options.keyLeft.isDown();
        boolean right = options.keyRight.isDown();
        boolean back = options.keyDown.isDown();
        boolean forward = options.keyUp.isDown();
        if (left && !right) {
            return DodgeAbilitySystem.DodgeDirection.LEFT;
        } else if (right && !left) {
            return DodgeAbilitySystem.DodgeDirection.RIGHT;
        } else if (back) {
            return DodgeAbilitySystem.DodgeDirection.BACK;
        } else if (forward) {
            return DodgeAbilitySystem.DodgeDirection.FORWARD;
        }
        return DodgeAbilitySystem.DodgeDirection.BACK;
    }
}
