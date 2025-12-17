package com.frenkvs.devmod.gametest;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.HitHelper;
import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactHudOverlay;
import com.frenkvs.devmod.hud.ImpactHudPresets;
import com.frenkvs.devmod.rendering.DebugRenderer;
import com.frenkvs.devmod.ui.hub.TestingHub;
import com.frenkvs.devmod.util.I18n;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
                    .executes(ctx -> {
                        ImpactHudOverlay.setEnabled(true);
                        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.impact_hud_enabled"), false);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        ImpactHudOverlay.setEnabled(false);
                        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.impact_hud_disabled"), false);
                        return 1;
                    }))
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        ImpactHudOverlay.toggle();
                        boolean enabled = ImpactHudOverlay.isEnabled();
                        ctx.getSource().sendSuccess(() -> I18n.translate(enabled ? "devmod.testing.impact_hud_enabled" : "devmod.testing.impact_hud_disabled"), false);
                        return 1;
                    }))
                .then(Commands.literal("export")
                    .executes(ctx -> {
                        Path path = ImpactHudPresets.getDefaultPresetFile();
                        boolean exported = ImpactHudPresets.exportToFile(path);
                        if (exported) {
                            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.impact_hud_preset_exported", path.toString()), false);
                            return 1;
                        }
                        ctx.getSource().sendFailure(I18n.translate("devmod.testing.impact_hud_preset_export_failed", path.toString()));
                        return 0;
                    }))
                .then(Commands.literal("import")
                    .executes(ctx -> {
                        Path path = ImpactHudPresets.getDefaultPresetFile();
                        boolean imported = ImpactHudPresets.importFromFile(path);
                        if (imported) {
                            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.impact_hud_preset_imported", path.toString()), false);
                            return 1;
                        }
                        ctx.getSource().sendFailure(I18n.translate("devmod.testing.impact_hud_preset_import_failed", path.toString()));
                        return 0;
                    })))

            // /devtest panel <on|off>
            .then(Commands.literal("panel")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        Impact3DPanelManager.INSTANCE.setEnabled(true);
                        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.panels_3d_enabled"), false);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        Impact3DPanelManager.INSTANCE.setEnabled(false);
                        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.panels_3d_disabled"), false);
                        return 1;
                    }))
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        Impact3DPanelManager.INSTANCE.toggle();
                        boolean enabled = Impact3DPanelManager.INSTANCE.isEnabled();
                        ctx.getSource().sendSuccess(() -> I18n.translate(enabled ? "devmod.testing.panels_3d_enabled" : "devmod.testing.panels_3d_disabled"), false);
                        return 1;
                    })))

            // /devtest debug <on|off>
            .then(Commands.literal("debug")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        DebugRenderer.INSTANCE.enable();
                        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.debug_renderer_enabled"), false);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        DebugRenderer.INSTANCE.disable();
                        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.debug_renderer_disabled"), false);
                        return 1;
                    }))
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        DebugRenderer.INSTANCE.toggle();
                        boolean enabled = DebugRenderer.INSTANCE.isEnabled();
                        ctx.getSource().sendSuccess(() -> I18n.translate(enabled ? "devmod.testing.debug_renderer_enabled" : "devmod.testing.debug_renderer_disabled"), false);
                        return 1;
                    })))

            // /devtest debugbox <size> - Adds a debug box at player position
            .then(Commands.literal("debugbox")
                .then(Commands.argument("size", Objects.requireNonNull(FloatArgumentType.floatArg(0.1f, 10.0f)))
                    .executes(ctx -> {
                        float size = FloatArgumentType.getFloat(ctx, "size");
                        Vec3 pos = ctx.getSource().getPosition();

                        // Add colored debug box
                        AABB box = new AABB(
                            pos.x - size/2, pos.y, pos.z - size/2,
                            pos.x + size/2, pos.y + size, pos.z + size/2
                        );
                        DebugRenderer.INSTANCE.addBox(box, 0xFF00FF00, true, 10000L); // Green, 10 seconds

                        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.added_debug_box",
                            String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)), false);
                        return 1;
                    })))

            // /devtest debugclear - Clears all debug shapes
            .then(Commands.literal("debugclear")
                .executes(ctx -> {
                    DebugRenderer.INSTANCE.clear();
                    ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.cleared_shapes"), false);
                    return 1;
                }))

            // /devtest panelclear - Clears all 3D panels
            .then(Commands.literal("panelclear")
                .executes(ctx -> {
                    Impact3DPanelManager.INSTANCE.clear();
                    ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.cleared_panels"), false);
                    return 1;
                }))

            // /devtest info - Shows system status
            .then(Commands.literal("info")
                .executes(ctx -> {
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
                }))

            // /devtest qa - Opens Testing Hub (client-side only)
            .then(Commands.literal("qa")
                .executes(ctx -> {
                    if (FMLEnvironment.dist.isClient()) {
                        // Schedule screen opening on next client tick
                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().setScreen(new TestingHub());
                        });
                        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.testing.opening_hub"), false);
                    } else {
                        ctx.getSource().sendFailure(I18n.translate("devmod.testing.client_only"));
                    }
                    return 1;
                }))

            // /devtest bodypart <part> - Shows body part multiplier info
            .then(Commands.literal("bodypart")
                .then(Commands.argument("part", Objects.requireNonNull(StringArgumentType.word()))
                    .executes(ctx -> {
                        String partName = StringArgumentType.getString(ctx, "part").toUpperCase();

                        HitHelper.BodyPart bodyPart;
                        try {
                            bodyPart = HitHelper.BodyPart.valueOf(partName);
                        } catch (IllegalArgumentException e) {
                            ctx.getSource().sendFailure(I18n.translate("devmod.testing.invalid_bodypart", partName));
                            return 0;
                        }

                        // Get default multiplier from WeaponStats
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
                    })))
        );

        DevMod.LOGGER.info("[DevMod] Registered test harness commands");
    }

    private static String getBodyPartColorName(HitHelper.BodyPart part) {
        return switch (part) {
            case HEAD -> "Cyan (0x00FFFF)";
            case BODY -> "Green (0x00FF00)";
            case ARMS -> "Yellow (0xFFFF00)";
            case LEGS -> "Red (0xFF0000)";
        };
    }
}
