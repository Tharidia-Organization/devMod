package com.frenkvs.devmod.gametest;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.HitHelper;
import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.actions.ActionCategory;
import com.frenkvs.devmod.actions.ActionCommandInvoker;
import com.frenkvs.devmod.actions.ActionContext;
import com.frenkvs.devmod.actions.ActionIds;
import com.frenkvs.devmod.actions.ActionOrigin;
import com.frenkvs.devmod.actions.ActionPreconditions;
import com.frenkvs.devmod.actions.ActionRegistry;
import com.frenkvs.devmod.actions.RadialAction;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactHudOverlay;
import com.frenkvs.devmod.hud.ImpactHudPresets;
import com.frenkvs.devmod.rendering.DebugRenderer;
import com.frenkvs.devmod.ui.hub.TestingHub;
import com.frenkvs.devmod.util.I18n;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.frenkvs.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.frenkvs.devmod.telemetry.duckdb.DuckDBQueryAPI;
import com.frenkvs.devmod.telemetry.duckdb.DuckDBQueryAPI.EnduranceStats;
import com.frenkvs.devmod.telemetry.duckdb.DuckDBMigrationService;
import com.frenkvs.devmod.endurance.EnduranceQuestManager;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Test harness commands for manual testing of complex UI/HUD features.
 *
 * These commands are intended for:
 * - Manual testing of features that are difficult to automate
 * - Debugging during development
 * - Demonstrations
 *
 * Commands:
 * - /devtest hud <on|off> - Toggles Impact HUD visibility
 * - /devtest panel <on|off> - Toggles 3D panel visibility
 * - /devtest debug <on|off> - Toggles debug renderer
 * - /devtest debugbox <size> - Adds a debug box at player position
 * - /devtest debugclear - Clears all debug shapes
 * - /devtest panelclear - Clears all 3D panels
 * - /devtest info - Shows current system status
 * - /devtest bodypart <part> - Shows body part multiplier info
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class TestHarnessCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("devtest")
            .requires(source -> source.hasPermission(2)) // Requires OP level 2

            // /devtest hud <on|off>
            .then(Commands.literal("hud")
                .then(Commands.literal("on")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_HUD_ON, ctx)))
                .then(Commands.literal("off")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_HUD_OFF, ctx)))
                .then(Commands.literal("toggle")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_HUD_TOGGLE, ctx)))
                .then(Commands.literal("export")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_HUD_EXPORT, ctx)))
                .then(Commands.literal("import")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_HUD_IMPORT, ctx))))

            // /devtest panel <on|off>
            .then(Commands.literal("panel")
                .then(Commands.literal("on")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_PANEL_ON, ctx)))
                .then(Commands.literal("off")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_PANEL_OFF, ctx)))
                .then(Commands.literal("toggle")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_PANEL_TOGGLE, ctx))))

            // /devtest debug <on|off>
            .then(Commands.literal("debug")
                .then(Commands.literal("on")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_DEBUG_ON, ctx)))
                .then(Commands.literal("off")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_DEBUG_OFF, ctx)))
                .then(Commands.literal("toggle")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_DEBUG_TOGGLE, ctx))))

            // /devtest endurance ...
            .then(Commands.literal("endurance")
                .then(Commands.literal("stats")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_STATS, ctx)))
                .then(Commands.literal("perks")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_PERKS, ctx)))
                .then(Commands.literal("smoke")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_SMOKE, ctx)))
                .then(Commands.literal("export")
                    .then(Commands.argument("table", Objects.requireNonNull(StringArgumentType.word()))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_EXPORT_TABLE, ctx)))
                    .then(Commands.literal("all")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_EXPORT_ALL, ctx))))
                .then(Commands.literal("autosmoke")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_AUTOSMOKE, ctx))))

            // /devtest debugbox <size> - Adds a debug box at player position
            .then(Commands.literal("debugbox")
                .then(Commands.argument("size", Objects.requireNonNull(FloatArgumentType.floatArg(0.1f, 10.0f)))
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_DEBUGBOX, ctx))))

            // /devtest debugclear - Clears all debug shapes
            .then(Commands.literal("debugclear")
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_DEBUGCLEAR, ctx)))

            // /devtest panelclear - Clears all 3D panels
            .then(Commands.literal("panelclear")
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_PANELCLEAR, ctx)))

            // /devtest info - Shows system status
            .then(Commands.literal("info")
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_INFO, ctx)))

            // /devtest qa - Opens Testing Hub (client-side only)
            .then(Commands.literal("qa")
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_QA_OPEN, ctx)))

            // /devtest bodypart <part> - Shows body part multiplier info
            .then(Commands.literal("bodypart")
                .then(Commands.argument("part", Objects.requireNonNull(StringArgumentType.word()))
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_BODYPART_INFO, ctx))))
        );

        DevMod.LOGGER.info("[DevMod] Registered test harness commands");
    }

    public static void registerActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_HUD_ON)
            .labelKey("devmod.action.test.hud.on")
            .descriptionKey("devmod.action.test.hud.on.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/HUD/On")
            .icon(Items.GREEN_DYE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest hud on")
            .handler(context -> handleCommand(context, "devtest hud on", TestHarnessCommands::setHudOn))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_HUD_OFF)
            .labelKey("devmod.action.test.hud.off")
            .descriptionKey("devmod.action.test.hud.off.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/HUD/Off")
            .icon(Items.RED_DYE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest hud off")
            .handler(context -> handleCommand(context, "devtest hud off", TestHarnessCommands::setHudOff))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_HUD_TOGGLE)
            .labelKey("devmod.action.test.hud.toggle")
            .descriptionKey("devmod.action.test.hud.toggle.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/HUD/Toggle")
            .icon(Items.LEVER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest hud toggle")
            .handler(context -> handleCommand(context, "devtest hud toggle", TestHarnessCommands::toggleHud))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_HUD_EXPORT)
            .labelKey("devmod.action.test.hud.export")
            .descriptionKey("devmod.action.test.hud.export.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/HUD/Export")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest hud export")
            .handler(context -> handleCommand(context, "devtest hud export", TestHarnessCommands::exportHudPreset))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_HUD_IMPORT)
            .labelKey("devmod.action.test.hud.import")
            .descriptionKey("devmod.action.test.hud.import.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/HUD/Import")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest hud import")
            .handler(context -> handleCommand(context, "devtest hud import", TestHarnessCommands::importHudPreset))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_PANEL_ON)
            .labelKey("devmod.action.test.panel.on")
            .descriptionKey("devmod.action.test.panel.on.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Panels/On")
            .icon(Items.LIGHT_BLUE_DYE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest panel on")
            .handler(context -> handleCommand(context, "devtest panel on", TestHarnessCommands::setPanelOn))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_PANEL_OFF)
            .labelKey("devmod.action.test.panel.off")
            .descriptionKey("devmod.action.test.panel.off.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Panels/Off")
            .icon(Items.GRAY_DYE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest panel off")
            .handler(context -> handleCommand(context, "devtest panel off", TestHarnessCommands::setPanelOff))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_PANEL_TOGGLE)
            .labelKey("devmod.action.test.panel.toggle")
            .descriptionKey("devmod.action.test.panel.toggle.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Panels/Toggle")
            .icon(Items.REPEATER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest panel toggle")
            .handler(context -> handleCommand(context, "devtest panel toggle", TestHarnessCommands::togglePanel))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_DEBUG_ON)
            .labelKey("devmod.action.test.debug.on")
            .descriptionKey("devmod.action.test.debug.on.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Debug/On")
            .icon(Items.LIME_DYE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest debug on")
            .handler(context -> handleCommand(context, "devtest debug on", TestHarnessCommands::setDebugOn))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_DEBUG_OFF)
            .labelKey("devmod.action.test.debug.off")
            .descriptionKey("devmod.action.test.debug.off.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Debug/Off")
            .icon(Items.REDSTONE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest debug off")
            .handler(context -> handleCommand(context, "devtest debug off", TestHarnessCommands::setDebugOff))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_DEBUG_TOGGLE)
            .labelKey("devmod.action.test.debug.toggle")
            .descriptionKey("devmod.action.test.debug.toggle.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Debug/Toggle")
            .icon(Items.COMMAND_BLOCK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest debug toggle")
            .handler(context -> handleCommand(context, "devtest debug toggle", TestHarnessCommands::toggleDebug))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_STATS)
            .labelKey("devmod.action.test.endurance.stats")
            .descriptionKey("devmod.action.test.endurance.stats.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/Stats")
            .icon(Items.IRON_SWORD)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance stats")
            .handler(context -> handleCommand(context, "devtest endurance stats", TestHarnessCommands::enduranceStats))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_PERKS)
            .labelKey("devmod.action.test.endurance.perks")
            .descriptionKey("devmod.action.test.endurance.perks.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/Perks")
            .icon(Items.EMERALD)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance perks")
            .handler(context -> handleCommand(context, "devtest endurance perks", TestHarnessCommands::endurancePerks))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_SMOKE)
            .labelKey("devmod.action.test.endurance.smoke")
            .descriptionKey("devmod.action.test.endurance.smoke.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/Smoke")
            .icon(Items.FIRE_CHARGE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance smoke")
            .handler(context -> handleCommand(context, "devtest endurance smoke", TestHarnessCommands::enduranceSmoke))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_EXPORT_TABLE)
            .labelKey("devmod.action.test.endurance.export.table")
            .descriptionKey("devmod.action.test.endurance.export.table.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/Export Table")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance export <table>")
            .handler(context -> handleCommandPrompt(context, "devtest endurance export ", TestHarnessCommands::exportTableFromContext))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_EXPORT_ALL)
            .labelKey("devmod.action.test.endurance.export.all")
            .descriptionKey("devmod.action.test.endurance.export.all.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/Export All")
            .icon(Items.CHEST)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance export all")
            .handler(context -> handleCommand(context, "devtest endurance export all", TestHarnessCommands::exportAllTablesFromContext))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_AUTOSMOKE)
            .labelKey("devmod.action.test.endurance.autosmoke")
            .descriptionKey("devmod.action.test.endurance.autosmoke.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/AutoSmoke")
            .icon(Items.CAMPFIRE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance autosmoke")
            .handler(context -> handleCommand(context, "devtest endurance autosmoke", TestHarnessCommands::enduranceAutoSmoke))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_DEBUGBOX)
            .labelKey("devmod.action.test.debugbox")
            .descriptionKey("devmod.action.test.debugbox.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Debug/Box")
            .icon(Items.GLASS)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest debugbox <size>")
            .handler(context -> handleCommandPrompt(context, "devtest debugbox ", TestHarnessCommands::debugBoxFromContext))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_DEBUGCLEAR)
            .labelKey("devmod.action.test.debugclear")
            .descriptionKey("devmod.action.test.debugclear.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Debug/Clear")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest debugclear")
            .handler(context -> handleCommand(context, "devtest debugclear", TestHarnessCommands::debugClear))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_PANELCLEAR)
            .labelKey("devmod.action.test.panelclear")
            .descriptionKey("devmod.action.test.panelclear.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Panels/Clear")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest panelclear")
            .handler(context -> handleCommand(context, "devtest panelclear", TestHarnessCommands::panelClear))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_INFO)
            .labelKey("devmod.action.test.info")
            .descriptionKey("devmod.action.test.info.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Status")
            .icon(Items.CLOCK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest info")
            .handler(context -> handleCommand(context, "devtest info", TestHarnessCommands::info))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_QA_OPEN)
            .labelKey("devmod.action.test.qa.open")
            .descriptionKey("devmod.action.test.qa.open.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/QA/Open")
            .icon(Items.BREWING_STAND)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest qa")
            .handler(context -> handleCommand(context, "devtest qa", TestHarnessCommands::qaOpen))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_BODYPART_INFO)
            .labelKey("devmod.action.test.bodypart")
            .descriptionKey("devmod.action.test.bodypart.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Debug/Body Part")
            .icon(Items.BONE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest bodypart <part>")
            .handler(context -> handleCommandPrompt(context, "devtest bodypart ", TestHarnessCommands::bodyPartFromContext))
            .build());
    }

    private static void handleCommand(ActionContext context, String command,
                                      CommandHandler handler) {
        CommandContext<CommandSourceStack> cmd = context.getCommandContext();
        if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
            handler.run(cmd);
            return;
        }
        context.executeCommand(command);
    }

    private static void handleCommandPrompt(ActionContext context, String command,
                                            CommandHandler handler) {
        CommandContext<CommandSourceStack> cmd = context.getCommandContext();
        if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
            handler.run(cmd);
            return;
        }
        if (!context.openCommandPrompt(command)) {
            context.executeCommand(command);
        }
    }

    @FunctionalInterface
    private interface CommandHandler {
        void run(CommandContext<CommandSourceStack> context);
    }

    private static String getBodyPartColorName(HitHelper.BodyPart part) {
        return switch (part) {
            case HEAD -> "Cyan (0x00FFFF)";
            case BODY -> "Green (0x00FF00)";
            case ARMS -> "Yellow (0xFFFF00)";
            case LEGS -> "Red (0xFF0000)";
        };
    }

    private static int setHudOn(CommandContext<CommandSourceStack> ctx) {
        ImpactHudOverlay.setEnabled(true);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.impact_hud_enabled"), false);
        return 1;
    }

    private static int setHudOff(CommandContext<CommandSourceStack> ctx) {
        ImpactHudOverlay.setEnabled(false);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.impact_hud_disabled"), false);
        return 1;
    }

    private static int toggleHud(CommandContext<CommandSourceStack> ctx) {
        ImpactHudOverlay.toggle();
        boolean enabled = ImpactHudOverlay.isEnabled();
        ctx.getSource().sendSuccess(() -> I18n.translate(
            enabled ? "devmod.testing.impact_hud_enabled" : "devmod.testing.impact_hud_disabled"), false);
        return 1;
    }

    private static int exportHudPreset(CommandContext<CommandSourceStack> ctx) {
        Path path = ImpactHudPresets.getDefaultPresetFile();
        boolean exported = ImpactHudPresets.exportToFile(path);
        if (exported) {
            ctx.getSource().sendSuccess(() -> I18n.translate(
                "devmod.testing.impact_hud_preset_exported", path.toString()), false);
            return 1;
        }
        ctx.getSource().sendFailure(I18n.translate(
            "devmod.testing.impact_hud_preset_export_failed", path.toString()));
        return 0;
    }

    private static int importHudPreset(CommandContext<CommandSourceStack> ctx) {
        Path path = ImpactHudPresets.getDefaultPresetFile();
        boolean imported = ImpactHudPresets.importFromFile(path);
        if (imported) {
            ctx.getSource().sendSuccess(() -> I18n.translate(
                "devmod.testing.impact_hud_preset_imported", path.toString()), false);
            return 1;
        }
        ctx.getSource().sendFailure(I18n.translate(
            "devmod.testing.impact_hud_preset_import_failed", path.toString()));
        return 0;
    }

    private static int setPanelOn(CommandContext<CommandSourceStack> ctx) {
        Impact3DPanelManager.INSTANCE.setEnabled(true);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.panels_3d_enabled"), false);
        return 1;
    }

    private static int setPanelOff(CommandContext<CommandSourceStack> ctx) {
        Impact3DPanelManager.INSTANCE.setEnabled(false);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.panels_3d_disabled"), false);
        return 1;
    }

    private static int togglePanel(CommandContext<CommandSourceStack> ctx) {
        Impact3DPanelManager.INSTANCE.toggle();
        boolean enabled = Impact3DPanelManager.INSTANCE.isEnabled();
        ctx.getSource().sendSuccess(() -> I18n.translate(
            enabled ? "devmod.testing.panels_3d_enabled" : "devmod.testing.panels_3d_disabled"), false);
        return 1;
    }

    private static int setDebugOn(CommandContext<CommandSourceStack> ctx) {
        DebugRenderer.INSTANCE.enable();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.debug_renderer_enabled"), false);
        return 1;
    }

    private static int setDebugOff(CommandContext<CommandSourceStack> ctx) {
        DebugRenderer.INSTANCE.disable();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.debug_renderer_disabled"), false);
        return 1;
    }

    private static int toggleDebug(CommandContext<CommandSourceStack> ctx) {
        DebugRenderer.INSTANCE.toggle();
        boolean enabled = DebugRenderer.INSTANCE.isEnabled();
        ctx.getSource().sendSuccess(() -> I18n.translate(
            enabled ? "devmod.testing.debug_renderer_enabled" : "devmod.testing.debug_renderer_disabled"), false);
        return 1;
    }

    private static int enduranceStats(CommandContext<CommandSourceStack> ctx) {
        return sendEnduranceStats(ctx.getSource());
    }

    private static int endurancePerks(CommandContext<CommandSourceStack> ctx) {
        return sendEndurancePerkUsage(ctx.getSource());
    }

    private static int enduranceSmoke(CommandContext<CommandSourceStack> ctx) {
        return runEnduranceSmoke(ctx.getSource());
    }

    private static int exportTableFromContext(CommandContext<CommandSourceStack> ctx) {
        return exportTable(ctx.getSource(), StringArgumentType.getString(ctx, "table"));
    }

    private static int exportAllTablesFromContext(CommandContext<CommandSourceStack> ctx) {
        return exportAllTables(ctx.getSource());
    }

    private static int enduranceAutoSmoke(CommandContext<CommandSourceStack> ctx) {
        return runEnduranceAutoSmoke(ctx.getSource());
    }

    private static int debugBoxFromContext(CommandContext<CommandSourceStack> ctx) {
        float size = FloatArgumentType.getFloat(ctx, "size");
        Vec3 pos = ctx.getSource().getPosition();

        AABB box = new AABB(
            pos.x - size / 2, pos.y, pos.z - size / 2,
            pos.x + size / 2, pos.y + size, pos.z + size / 2
        );
        DebugRenderer.INSTANCE.addBox(box, 0xFF00FF00, true, 10000L);

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.added_debug_box",
            String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)), false);
        return 1;
    }

    private static int debugClear(CommandContext<CommandSourceStack> ctx) {
        DebugRenderer.INSTANCE.clear();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.cleared_shapes"), false);
        return 1;
    }

    private static int panelClear(CommandContext<CommandSourceStack> ctx) {
        Impact3DPanelManager.INSTANCE.clear();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.cleared_panels"), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        boolean hudEnabled = ImpactHudOverlay.isEnabled();
        boolean panelEnabled = Impact3DPanelManager.INSTANCE.isEnabled();
        boolean debugEnabled = DebugRenderer.INSTANCE.isEnabled();
        int panelCount = Impact3DPanelManager.INSTANCE.getPanelCount();

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.status_info",
            hudEnabled ? "ON" : "OFF",
            panelEnabled ? "ON" : "OFF",
            panelCount,
            debugEnabled ? "ON" : "OFF"
        ), false);
        return 1;
    }

    private static int qaOpen(CommandContext<CommandSourceStack> ctx) {
        if (FMLEnvironment.dist.isClient()) {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new TestingHub()));
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.opening_hub"), false);
        } else {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
        }
        return 1;
    }

    private static int bodyPartFromContext(CommandContext<CommandSourceStack> ctx) {
        String partName = StringArgumentType.getString(ctx, "part").toUpperCase();

        HitHelper.BodyPart bodyPart;
        try {
            bodyPart = HitHelper.BodyPart.valueOf(partName);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.invalid_bodypart", partName));
            return 0;
        }

        WeaponStats stats = new WeaponStats();
        float mult = switch (bodyPart) {
            case HEAD -> stats.headMult;
            case BODY -> stats.bodyMult;
            case ARMS -> stats.armsMult;
            case LEGS -> stats.legsMult;
        };

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.bodypart_info",
            bodyPart.toString(),
            String.format("%.2f", mult),
            getBodyPartColorName(bodyPart)
        ), false);
        return 1;
    }

    // =========================================================================
    // ENDURANCE HELPERS
    // =========================================================================
    private static int sendEnduranceStats(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(I18n.translate("devmod.testing.no_player"));
            return 0;
        }
        DuckDBQueryAPI query = DuckDBTelemetryService.INSTANCE.getQueryAPI();
        if (query == null) {
            source.sendFailure(I18n.translate("devmod.testing.duckdb_not_ready"));
            return 0;
        }
        EnduranceStats stats = query.getEnduranceStats(player.getUUID());
        if (stats == null) {
            source.sendFailure(I18n.translate("devmod.testing.endurance.stats_unavailable"));
            return 0;
        }
        source.sendSuccess(() -> Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
            Objects.requireNonNull(String.format("Endurance: attempts=%d completed=%d failed=%d abandoned=%d best_wave=%d avg_waves=%.1f tokens=%d kills=%d dmg=%.0f/%.0f",
                stats.totalQuests(), stats.completed(), stats.failed(), stats.abandoned(),
                stats.bestWave(), stats.avgWaves(), stats.totalTokens(),
                stats.totalKills(), stats.totalDamageDealt(), stats.totalDamageTaken())))), false);
        return 1;
    }

    private static int sendEndurancePerkUsage(CommandSourceStack source) {
        DuckDBQueryAPI query = DuckDBTelemetryService.INSTANCE.getQueryAPI();
        if (query == null) {
            source.sendFailure(I18n.translate("devmod.testing.duckdb_not_ready"));
            return 0;
        }
        var perks = query.getPerkUsageStats();
        int limit = Math.min(5, perks.size());
        for (int i = 0; i < limit; i++) {
            final int index = i;
            var p = perks.get(i);
            source.sendSuccess(() -> Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                Objects.requireNonNull(String.format("#%d %s (%s/%s): %d selections, %d players",
                    index + 1, p.perkName(), p.tier(), p.category(), p.timesSelected(), p.uniquePlayers())))), false);
        }
        if (perks.isEmpty()) {
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("No perk data yet"), false);
        }
        return 1;
    }

    private static int runEnduranceSmoke(CommandSourceStack source) {
        var conn = DuckDBTelemetryService.INSTANCE.getConnection();
        if (conn == null) {
            source.sendFailure(I18n.translate("devmod.testing.duckdb_not_ready"));
            return 0;
        }
        String[] tables = {
            "endurance_sessions", "endurance_waves", "endurance_wave_kills",
            "endurance_combos", "endurance_perks", "endurance_mutators", "endurance_rewards"
        };
        try (var stmt = conn.createStatement()) {
            for (String table : tables) {
                try (var rs = stmt.executeQuery("SELECT COUNT(*) AS c FROM " + table)) {
                    if (rs.next()) {
                        int cnt = rs.getInt("c");
                        source.sendSuccess(() -> Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                            Objects.requireNonNull(String.format("%s: %d rows", table, cnt)))), false);
                    }
                } catch (Exception e) {
                    source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                        Objects.requireNonNull(String.format("Table %s not reachable: %s", table, e.getMessage())))));
                }
            }
        } catch (Exception e) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("DuckDB query failed: " + e.getMessage())));
            return 0;
        }
        return 1;
    }

    private static int exportTable(CommandSourceStack source, String table) {
        var conn = DuckDBTelemetryService.INSTANCE.getConnection();
        if (conn == null) {
            source.sendFailure(I18n.translate("devmod.testing.duckdb_not_ready"));
            return 0;
        }
        try {
            Path outDir = source.getServer().getServerDirectory().resolve("telemetry_exports");
            java.nio.file.Files.createDirectories(outDir);
            Path outFile = outDir.resolve(table + ".ndjson");
            DuckDBMigrationService.exportToNDJSON(conn, table, outFile);
            source.sendSuccess(() -> Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                Objects.requireNonNull(String.format("Exported %s to %s", table, outFile)))), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                Objects.requireNonNull(String.format("Export failed for %s: %s", table, e.getMessage())))));
            return 0;
        }
    }

    private static int exportAllTables(CommandSourceStack source) {
        String[] tables = {
            "endurance_sessions", "endurance_waves", "endurance_wave_kills",
            "endurance_combos", "endurance_perks", "endurance_mutators", "endurance_rewards"
        };
        int ok = 0;
        for (String table : tables) {
            ok += exportTable(source, table);
        }
        return ok > 0 ? 1 : 0;
    }

    private static int runEnduranceAutoSmoke(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(I18n.translate("devmod.testing.no_player"));
            return 0;
        }
        // Ensure manager initialized
        if (!EnduranceQuestManager.INSTANCE.isInitialized()) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("Endurance manager not initialized")));
            return 0;
        }

        // Start a 2-wave quest with default zombie
        var settings = new EnduranceQuestManager.QuestSettings();
        settings.totalWaves = 2;
        settings.endlessMode = false;
        settings.arenaSize = 64;

        var mobId = net.minecraft.resources.ResourceLocation.withDefaultNamespace("zombie");
        var result = EnduranceQuestManager.INSTANCE.startQuest(player, mobId, settings);
        if (!result.success()) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("Auto-smoke start failed: " + result.message())));
            return 0;
        }

        // Force complete wave 1 to reach checkpoint
        EnduranceQuestManager.INSTANCE.completeWave(player);
        // Exit at checkpoint to finish flow
        EnduranceQuestManager.INSTANCE.exitAtCheckpoint(player);

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Endurance auto-smoke run executed"), false);
        return 1;
    }
}
