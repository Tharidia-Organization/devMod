package com.devmod.gametest;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionCommandInvoker;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.collision.bodypart.BodyPartColors;
import com.devmod.clone.network.TelepadOpenScreenPayload;
import com.devmod.combat.HitHelper;
import com.devmod.endurance.CombatFlowSyncPayload;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.PartyStatsSyncPayload;
import com.devmod.stats.WeaponStats;
import com.devmod.telemetry.duckdb.DuckDBMigrationService;
import com.devmod.telemetry.duckdb.DuckDBQueryAPI;
import com.devmod.telemetry.duckdb.DuckDBQueryAPI.EnduranceStats;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.devmod.util.ColorMasks;
import com.devmod.util.I18n;
import com.devmod.area.network.AreaNetworkHandler;
import com.devmod.area.network.AreaPreviewPayload;
import com.devmod.zone.network.ZoneNetworkHandler;

@EventBusSubscriber(modid = DevMod.MODID)
public class TestHarnessCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestHarnessCommands.class);

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
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_AUTOSMOKE, ctx)))
                .then(Commands.literal("killwave")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_KILLWAVE, ctx)))
                .then(Commands.literal("die")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_DIE, ctx)))
                .then(Commands.literal("skipwave")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TEST_ENDURANCE_SKIP_WAVE, ctx))))

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

            // /devtest payloadsmoke - Sends a small set of payloads for smoke testing
            .then(Commands.literal("payloadsmoke")
                .then(Commands.literal("brief")
                    .executes(TestHarnessCommands::payloadSmokeBrief))
                .executes(TestHarnessCommands::payloadSmoke))

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

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_KILLWAVE)
            .labelKey("devmod.action.test.endurance.killwave")
            .descriptionKey("devmod.action.test.endurance.killwave.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/Kill Wave")
            .icon(Items.DIAMOND_SWORD)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance killwave")
            .handler(context -> handleCommand(context, "devtest endurance killwave", TestHarnessCommands::enduranceKillWave))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_DIE)
            .labelKey("devmod.action.test.endurance.die")
            .descriptionKey("devmod.action.test.endurance.die.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/Simulate Death")
            .icon(Items.SKELETON_SKULL)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance die")
            .handler(context -> handleCommand(context, "devtest endurance die", TestHarnessCommands::enduranceDie))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TEST_ENDURANCE_SKIP_WAVE)
            .labelKey("devmod.action.test.endurance.skipwave")
            .descriptionKey("devmod.action.test.endurance.skipwave.desc")
            .category(ActionCategory.TESTING)
            .menuPath("Root/Testing/Endurance/Skip Wave")
            .icon(Items.ENDER_PEARL)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devtest endurance skipwave")
            .handler(context -> handleCommand(context, "devtest endurance skipwave", TestHarnessCommands::enduranceSkipWave))
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
            case HEAD -> formatColorName("Cyan", BodyPartColors.HEAD);
            case BODY -> formatColorName("Green", BodyPartColors.BODY);
            case ARMS -> formatColorName("Yellow", BodyPartColors.ARMS);
            case LEGS -> formatColorName("Red", BodyPartColors.LEGS);
        };
    }

    private static String formatColorName(String name, int argb) {
        return name + " (" + formatRgb(argb) + ")";
    }

    private static String formatRgb(int argb) {
        return String.format("0x%06X", argb & ColorMasks.RGB);
    }

    private static int setHudOn(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.setHudEnabled(true);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.impact_hud_enabled"), false);
        return 1;
    }

    private static int setHudOff(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.setHudEnabled(false);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.impact_hud_disabled"), false);
        return 1;
    }

    private static int toggleHud(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.toggleHud();
        boolean enabled = com.devmod.client.gametest.TestHarnessClientDelegate.isHudEnabled();
        ctx.getSource().sendSuccess(() -> I18n.translate(
            enabled ? "devmod.testing.impact_hud_enabled" : "devmod.testing.impact_hud_disabled"), false);
        return 1;
    }

    private static int exportHudPreset(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        try {
            Path path = getDefaultPresetFileClientSafe();
            if (path == null) {
                ctx.getSource().sendFailure(I18n.translate("devmod.testing.impact_hud_preset_export_failed", "unknown"));
                return 0;
            }
            boolean exported = exportToFileClientSafe(path);
            if (exported) {
                ctx.getSource().sendSuccess(() -> I18n.translate(
                    "devmod.testing.impact_hud_preset_exported", path.toString()), false);
                return 1;
            }
            ctx.getSource().sendFailure(I18n.translate(
                "devmod.testing.impact_hud_preset_export_failed", path.toString()));
        } catch (Exception e) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.impact_hud_preset_export_failed", e.getMessage()));
        }
        return 0;
    }

    private static int importHudPreset(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        try {
            Path path = getDefaultPresetFileClientSafe();
            if (path == null) {
                ctx.getSource().sendFailure(I18n.translate("devmod.testing.impact_hud_preset_import_failed", "unknown"));
                return 0;
            }
            boolean imported = importFromFileClientSafe(path);
            if (imported) {
                ctx.getSource().sendSuccess(() -> I18n.translate(
                    "devmod.testing.impact_hud_preset_imported", path.toString()), false);
                return 1;
            }
            ctx.getSource().sendFailure(I18n.translate(
                "devmod.testing.impact_hud_preset_import_failed", path.toString()));
        } catch (Exception e) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.impact_hud_preset_import_failed", e.getMessage()));
        }
        return 0;
    }

    private static int setPanelOn(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.setPanelEnabled(true);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.panels_3d_enabled"), false);
        return 1;
    }

    private static int setPanelOff(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.setPanelEnabled(false);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.panels_3d_disabled"), false);
        return 1;
    }

    private static int togglePanel(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.togglePanel();
        boolean enabled = com.devmod.client.gametest.TestHarnessClientDelegate.isPanelEnabled();
        ctx.getSource().sendSuccess(() -> I18n.translate(
            enabled ? "devmod.testing.panels_3d_enabled" : "devmod.testing.panels_3d_disabled"), false);
        return 1;
    }

    private static int setDebugOn(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.setDebugEnabled(true);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.debug_renderer_enabled"), false);
        return 1;
    }

    private static int setDebugOff(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.setDebugEnabled(false);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.debug_renderer_disabled"), false);
        return 1;
    }

    private static int toggleDebug(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.toggleDebug();
        boolean enabled = com.devmod.client.gametest.TestHarnessClientDelegate.isDebugEnabled();
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

    private static int enduranceKillWave(CommandContext<CommandSourceStack> ctx) {
        return runEnduranceKillWave(ctx.getSource());
    }

    private static int enduranceDie(CommandContext<CommandSourceStack> ctx) {
        return runEnduranceDie(ctx.getSource());
    }

    private static int enduranceSkipWave(CommandContext<CommandSourceStack> ctx) {
        return runEnduranceSkipWave(ctx.getSource());
    }

    private static int debugBoxFromContext(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        float size = FloatArgumentType.getFloat(ctx, "size");
        Vec3 pos = ctx.getSource().getPosition();

        AABB box = new AABB(
            pos.x - size / 2, pos.y, pos.z - size / 2,
            pos.x + size / 2, pos.y + size, pos.z + size / 2
        );
        com.devmod.client.gametest.TestHarnessClientDelegate.addDebugBox(box, BodyPartColors.BODY, true, 10000L);

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.added_debug_box",
            String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)), false);
        return 1;
    }

    private static int debugClear(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.clearDebugShapes();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.cleared_shapes"), false);
        return 1;
    }

    private static int panelClear(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.clearPanels();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.cleared_panels"), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        String[] status = com.devmod.client.gametest.TestHarnessClientDelegate.getSystemStatus();

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.status_info",
            status[0], // hudEnabled
            status[1], // panelEnabled
            Integer.parseInt(status[2]), // panelCount
            status[3] // debugEnabled
        ), false);
        return 1;
    }

    private static int qaOpen(CommandContext<CommandSourceStack> ctx) {
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
            return 0;
        }
        com.devmod.client.gametest.TestHarnessClientDelegate.openTestingHub();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.opening_hub"), false);
        return 1;
    }

    private static int payloadSmoke(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(I18n.translate("devmod.testing.no_player"));
            return 0;
        }

        // Server -> Client payloads
        PacketDistributor.sendToPlayer(player, CombatFlowSyncPayload.EMPTY);
        PartyStatsSyncPayload.PlayerEntry entry = new PartyStatsSyncPayload.PlayerEntry(
            player.getUUID().toString(),
            player.getName().getString(),
            0, 0, 0, 0, 0, 0,
            0.0f, 0.0f, false
        );
        PartyStatsSyncPayload partyStats = new PartyStatsSyncPayload(
            1, 1, 0L,
            java.util.List.of(entry),
            0, 0, 0, 0, 0, 0,
            0.0f, false,
            entry.playerId(), "payload smoke"
        );
        PacketDistributor.sendToPlayer(player, partyStats);
        ZoneNetworkHandler.syncZones(player);
        AreaNetworkHandler.openEditorCentralScreen(player);
        AreaPreviewPayload preview = AreaPreviewPayload.boundingBox(
            player.blockPosition(), 3, 3, 3, "BOX", false, "", "NATURAL", 0L);
        AreaNetworkHandler.sendAreaPreview(player, preview);
        PacketDistributor.sendToPlayer(player,
            new TelepadOpenScreenPayload(player.blockPosition(), "SmokeTelepad"));

        // Client -> Server payloads
        if (FMLEnvironment.dist.isClient()) {
            com.devmod.client.gametest.TestHarnessClientDelegate.runPayloadSmoke();
        } else {
            source.sendFailure(I18n.translate("devmod.testing.client_only"));
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "Payload smoke sent (S2C + C2S). Check logs for errors."), false);
        return 1;
    }

    private static int payloadSmokeBrief(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(I18n.translate("devmod.testing.no_player"));
            return 0;
        }

        // Server -> Client payloads (no UI open)
        PacketDistributor.sendToPlayer(player, CombatFlowSyncPayload.EMPTY);
        ZoneNetworkHandler.syncZones(player);

        // Client -> Server payloads
        if (FMLEnvironment.dist.isClient()) {
            com.devmod.client.gametest.TestHarnessClientDelegate.runPayloadSmoke();
        } else {
            source.sendFailure(I18n.translate("devmod.testing.client_only"));
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "Payload smoke brief sent (no UI). Check logs for errors."), false);
        return 1;
    }

    private static int bodyPartFromContext(CommandContext<CommandSourceStack> ctx) {
        String partName = StringArgumentType.getString(ctx, "part").toUpperCase(Locale.ROOT);

        HitHelper.BodyPart bodyPart;
        try {
            bodyPart = HitHelper.BodyPart.valueOf(partName);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(I18n.translate("devmod.testing.invalid_bodypart", partName));
            return 0;
        }

        WeaponStats stats = new WeaponStats();
        float mult = switch (bodyPart) {
            case HEAD -> stats.getHeadMult();
            case BODY -> stats.getBodyMult();
            case ARMS -> stats.getArmsMult();
            case LEGS -> stats.getLegsMult();
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

        // Quest start is ASYNC - schedule wave completion after session is ready
        // The instance dimension creation happens asynchronously, so we need to poll
        // for the session to be fully initialized (arena != null, state == IN_PROGRESS)
        // Use CompletableFuture.delayedExecutor for proper delayed execution
        var server = player.getServer();
        if (server != null) {
            scheduleAutoSmokeCompletion(server, player.getUUID(), 0);
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Endurance auto-smoke run started (async completion pending)"), false);
        return 1;
    }

    /**
     * Poll for session readiness and complete the auto-smoke test.
     * Uses CompletableFuture.delayedExecutor for proper delayed execution.
     * @param attempt Current attempt number (for timeout)
     */
    private static void scheduleAutoSmokeCompletion(net.minecraft.server.MinecraftServer server, UUID playerId, int attempt) {
        if (attempt > 50) {
            // Timeout after 50 attempts * 200ms = 10 seconds
            DevMod.LOGGER.warn("[AutoSmoke] Timeout waiting for session to be ready for player {}", playerId);
            return;
        }

        // Use delayed executor: wait 200ms between attempts (about 4 ticks at 20 TPS)
        java.util.concurrent.CompletableFuture.delayedExecutor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            .execute(() -> {
                // Execute the check on the server thread
                server.execute(() -> {
                    boolean inQuest = EnduranceQuestManager.INSTANCE.isPlayerInQuest(playerId);
                    var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(playerId);

                    DevMod.LOGGER.info("[AutoSmoke] attempt={}, inQuest={}, sessionPresent={}",
                        attempt, inQuest, sessionOpt.isPresent());

                    // First check if session exists at all (using isPlayerInQuest which doesn't filter initializing)
                    if (!inQuest) {
                        // Session truly doesn't exist - quest was cancelled
                        DevMod.LOGGER.warn("[AutoSmoke] Session not found for player {} (quest may have been cancelled)", playerId);
                        return;
                    }

                    // Session exists - check if it's fully initialized (getActiveSession filters initializing sessions)
                    if (sessionOpt.isEmpty()) {
                        // Session exists but is still initializing - retry
                        scheduleAutoSmokeCompletion(server, playerId, attempt + 1);
                        return;
                    }

                    var session = sessionOpt.get();
                    var quest = session.getQuest();

                    // Check if session is fully ready (arena set, quest in progress)
                    if (session.getArena() == null || quest.getState() != com.devmod.endurance.EnduranceQuestState.IN_PROGRESS) {
                        // Not ready yet, retry
                        scheduleAutoSmokeCompletion(server, playerId, attempt + 1);
                        return;
                    }

                    // Session is ready - get player and complete
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) {
                        DevMod.LOGGER.warn("[AutoSmoke] Player {} disconnected", playerId);
                        return;
                    }

                    // Force complete wave 1 to reach checkpoint
                    EnduranceQuestManager.INSTANCE.completeWave(player);
                    // Exit at checkpoint to finish flow
                    EnduranceQuestManager.INSTANCE.exitAtCheckpoint(player);

                    DevMod.LOGGER.info("[AutoSmoke] Completed auto-smoke test for player {} after {} attempts", player.getName().getString(), attempt);
                });
            });
    }

    private static int runEnduranceKillWave(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(I18n.translate("devmod.testing.no_player"));
            return 0;
        }
        if (!EnduranceQuestManager.INSTANCE.isPlayerInQuest(player.getUUID())) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("Not in an active Endurance quest")));
            return 0;
        }

        // Get the arena for this player and kill all quest mobs
        var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player.getUUID());
        if (sessionOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("No active session found")));
            return 0;
        }

        var session = sessionOpt.get();
        var arena = session.getArena();
        if (arena == null) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("No arena found")));
            return 0;
        }

        // Kill all mobs in the arena
        int killed = 0;
        net.minecraft.server.level.ServerLevel level = arena.getLevel();
        AABB bounds = arena.getBounds();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof net.minecraft.world.entity.Mob mob && bounds.contains(entity.position())) {
                // Check if this is a quest mob (not a player or other special entity)
                if (mob.isAlive() && !mob.isRemoved()) {
                    mob.kill();
                    killed++;
                }
            }
        }

        final int finalKilled = killed;
        source.sendSuccess(() -> Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
            String.format("Killed %d mobs in arena", finalKilled))), false);
        return 1;
    }

    private static int runEnduranceDie(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(I18n.translate("devmod.testing.no_player"));
            return 0;
        }
        if (!EnduranceQuestManager.INSTANCE.isPlayerInQuest(player.getUUID())) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("Not in an active Endurance quest")));
            return 0;
        }

        // Kill the player to trigger death handling
        player.kill();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Player death simulated"), false);
        return 1;
    }

    private static int runEnduranceSkipWave(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(I18n.translate("devmod.testing.no_player"));
            return 0;
        }
        if (!EnduranceQuestManager.INSTANCE.isPlayerInQuest(player.getUUID())) {
            source.sendFailure(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("Not in an active Endurance quest")));
            return 0;
        }

        // Force complete the current wave
        EnduranceQuestManager.INSTANCE.completeWave(player);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Wave skipped - checkpoint reached"), false);
        return 1;
    }

    // ========== Client-safe ImpactHudPresets helpers ==========

    private static Method getDefaultPresetFileMethod;
    private static Method exportToFileMethod;
    private static Method importFromFileMethod;
    private static boolean hudPresetsMethodsInitialized = false;

    private static void initHudPresetsMethods() {
        hudPresetsMethodsInitialized = true;
        try {
            Class<?> presetsClass = Class.forName("com.devmod.client.overlay.ImpactHudPresets");
            getDefaultPresetFileMethod = presetsClass.getMethod("getDefaultPresetFile");
            exportToFileMethod = presetsClass.getMethod("exportToFile", Path.class);
            importFromFileMethod = presetsClass.getMethod("importFromFile", Path.class);
        } catch (Exception e) {
            LOGGER.debug("[TestHarness] ImpactHudPresets client hooks unavailable", e);
        }
    }

    private static Path getDefaultPresetFileClientSafe() {
        try {
            if (!hudPresetsMethodsInitialized) {
                initHudPresetsMethods();
            }
            if (getDefaultPresetFileMethod != null) {
                return (Path) getDefaultPresetFileMethod.invoke(null);
            }
        } catch (Exception e) {
            LOGGER.debug("[TestHarness] Failed to read default HUD preset path", e);
        }
        return null;
    }

    private static boolean exportToFileClientSafe(Path path) {
        try {
            if (!hudPresetsMethodsInitialized) {
                initHudPresetsMethods();
            }
            if (exportToFileMethod != null) {
                return (Boolean) exportToFileMethod.invoke(null, path);
            }
        } catch (Exception e) {
            LOGGER.debug("[TestHarness] Failed to export HUD preset", e);
        }
        return false;
    }

    private static boolean importFromFileClientSafe(Path path) {
        try {
            if (!hudPresetsMethodsInitialized) {
                initHudPresetsMethods();
            }
            if (importFromFileMethod != null) {
                return (Boolean) importFromFileMethod.invoke(null, path);
            }
        } catch (Exception e) {
            LOGGER.debug("[TestHarness] Failed to import HUD preset", e);
        }
        return false;
    }
}
