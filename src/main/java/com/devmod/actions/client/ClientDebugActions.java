package com.devmod.actions.client;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.ModConfig;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.client.attributes.AttributeMonitoringSystem;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.overlay.BossPhaseOverlay;
import com.devmod.client.overlay.CombatRecapScreen;
import com.devmod.client.overlay.CombatSessionTracker;
import com.devmod.client.overlay.EconomyOverlay;
import com.devmod.client.overlay.EntityDensityOverlay;
import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.ImpactDisplayMode;
import com.devmod.client.overlay.ImpactHudController;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.overlay.ImpactVFX;
import com.devmod.client.overlay.SkillEfficacyOverlay;
import com.devmod.client.rendering.SpawnabilityOverlay;
import com.devmod.client.panels.context.ContextDetector;
import com.devmod.client.rendering.AggroRangeVisualizer;
import com.devmod.client.rendering.ChunkPerformanceVisualizer;
import com.devmod.client.rendering.DebugRenderer;
import com.devmod.client.rendering.HeatmapVisualizer;
import com.devmod.client.rendering.LightLevelOverlay;
import com.devmod.client.rendering.LineOfSightVisualizer;
import com.devmod.client.rendering.PathfindingDebugger;
import com.devmod.client.rendering.RoomBoundsVisualizer;
import com.devmod.client.rendering.SafeSpotVisualizer;
import com.devmod.client.rendering.VerticalLevelsVisualizer;
import com.devmod.client.telemetry.FpsTracker;
import com.devmod.client.telemetry.PerformanceProfiler;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.config.Config;
import com.devmod.debug.DebugFeature;
import com.devmod.debug.DebugTogglePayload;
import com.devmod.debug.client.DebugRenderBools;
import com.devmod.rendering.HeatmapType;
import com.devmod.shared.SharedColorTokens;
import com.devmod.util.I18n;

/**
 * Debug-related client actions: overlays, heatmaps, visualizers, HUD toggles,
 * combat diagnostics, native debug, perf monitors, context debug.
 */
public final class ClientDebugActions {

    private static final HeatmapType[] HEATMAP_CYCLE = {
        HeatmapType.DEATH,
        HeatmapType.MOVEMENT,
        HeatmapType.CAMPING,
        HeatmapType.STUCK,
        HeatmapType.AGGRO_DROP,
        HeatmapType.KITING
    };
    private static final java.util.concurrent.atomic.AtomicInteger currentHeatmapIndex =
        new java.util.concurrent.atomic.AtomicInteger(-1);
    private static long lastOverlayWarningTime = 0;

    private ClientDebugActions() {}

    // ── Registration ──

    static void registerActions() {
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
                String status = DebugRenderer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.debug_overlay_status", status), true);
                if (context.getPlayer() != null) {
                    checkOverlayCount(context.getPlayer());
                }
            })
            .build());

        // Impact HUD
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
                String status = ImpactHudOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.impact_hud_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_IMPACT_CONTROLLER_TOGGLE)
            .labelKey("devmod.action.impact_controller")
            .descriptionKey("devmod.action.impact_controller.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Impact HUD/Quick Toggle")
            .icon(Items.LEVER)
            .toggle(context -> ImpactHudController.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ImpactHudController.INSTANCE.toggle();
                String status = ImpactHudController.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.impact.controller_status", status), true);
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
                ImpactHudController.INSTANCE.toggle3dPanels();
                String status = Impact3DPanelManager.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.impact_3d_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_IMPACT_DISPLAY_MODE_CYCLE)
            .labelKey("devmod.action.impact_display_mode")
            .descriptionKey("devmod.action.impact_display_mode.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Impact HUD/Display Mode")
            .icon(Items.COMPARATOR)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ImpactHudController.INSTANCE.cycleDisplayMode();
                ImpactDisplayMode mode = ImpactHudController.INSTANCE.getDisplayMode();
                context.sendSuccess(I18n.translate("devmod.impact.display_mode", mode.getDisplayName()), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_IMPACT_PRESET_MINIMAL)
            .labelKey("devmod.action.impact_preset.minimal")
            .descriptionKey("devmod.action.impact_preset.minimal.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Impact HUD/Presets/Minimal")
            .icon(Items.FEATHER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ImpactHudController.INSTANCE.applyPreset("minimal");
                context.sendSuccess(I18n.translate("devmod.impact.preset_applied", "Minimal"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_IMPACT_PRESET_DETAILED)
            .labelKey("devmod.action.impact_preset.detailed")
            .descriptionKey("devmod.action.impact_preset.detailed.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Impact HUD/Presets/Detailed")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ImpactHudController.INSTANCE.applyPreset("detailed");
                context.sendSuccess(I18n.translate("devmod.impact.preset_applied", "Detailed"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_IMPACT_PRESET_TRAINING)
            .labelKey("devmod.action.impact_preset.training")
            .descriptionKey("devmod.action.impact_preset.training.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Impact HUD/Presets/Training")
            .icon(Items.KNOWLEDGE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ImpactHudController.INSTANCE.applyPreset("training");
                context.sendSuccess(I18n.translate("devmod.impact.preset_applied", "Training"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_IMPACT_SHOW_RECAP)
            .labelKey("devmod.action.impact_show_recap")
            .descriptionKey("devmod.action.impact_show_recap.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Impact HUD/Show Recap")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                if (CombatSessionTracker.INSTANCE.hasData()) {
                    CombatRecapScreen.open();
                } else {
                    context.sendSuccess(I18n.translate("devmod.impact.no_combat_data"), true);
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_BODY_PARTS_TOGGLE)
            .labelKey("devmod.radial.item.body_parts")
            .descriptionKey("devmod.radial.item.body_parts.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Overlays/Body Parts")
            .icon(Items.ARMOR_STAND)
            .toggle(context -> ModConfig.isShowBodyPartBoxes())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ModConfig.setShowBodyPartBoxes(!ModConfig.isShowBodyPartBoxes());
                Config.SHOW_BODY_PART_BOXES.set(ModConfig.isShowBodyPartBoxes());
                if (ModConfig.isShowBodyPartBoxes() && !ModConfig.isShowRender()) {
                    ModConfig.setShowRender(true);
                }
                SettingsManager.INSTANCE.markDirty();
                String status = ModConfig.isShowBodyPartBoxes() ? "\u00A7aON" : "\u00A7cOFF";
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
            .handler(context -> setDebugOverlaysEnabled(context, true))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_OVERLAYS_DISABLE_ALL)
            .labelKey("devmod.action.debug_overlays.disable_all")
            .descriptionKey("devmod.action.debug_overlays.disable_all.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Overlays/Disable All")
            .icon(Items.GRAY_DYE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setDebugOverlaysEnabled(context, false))
            .build());

        // Native debug toggles
        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE)
            .labelKey("devmod.action.native_debug.entity_pathing")
            .descriptionKey("devmod.action.native_debug.entity_pathing.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Entity Pathing")
            .icon(Items.LEAD)
            .toggle(context -> DebugRenderBools.isEntityPathing())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleNativeDebug(context, DebugFeature.ENTITY_PATHING,
                DebugRenderBools::isEntityPathing))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE)
            .labelKey("devmod.action.native_debug.entity_goals")
            .descriptionKey("devmod.action.native_debug.entity_goals.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Entity Goals")
            .icon(Items.WRITABLE_BOOK)
            .toggle(context -> DebugRenderBools.isEntityGoals())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleNativeDebug(context, DebugFeature.ENTITY_GOALS,
                DebugRenderBools::isEntityGoals))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE)
            .labelKey("devmod.action.native_debug.entity_brains")
            .descriptionKey("devmod.action.native_debug.entity_brains.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Entity Brains")
            .icon(Items.ENDER_PEARL)
            .toggle(context -> DebugRenderBools.isEntityBrains())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleNativeDebug(context, DebugFeature.ENTITY_BRAINS,
                DebugRenderBools::isEntityBrains))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_POI_TOGGLE)
            .labelKey("devmod.action.native_debug.poi")
            .descriptionKey("devmod.action.native_debug.poi.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/POI")
            .icon(Items.BELL)
            .toggle(context -> DebugRenderBools.isPoi())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleNativeDebug(context, DebugFeature.POI,
                DebugRenderBools::isPoi))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE)
            .labelKey("devmod.action.native_debug.raids")
            .descriptionKey("devmod.action.native_debug.raids.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Raids")
            .icon(Items.IRON_SWORD)
            .toggle(context -> DebugRenderBools.isRaids())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleNativeDebug(context, DebugFeature.RAIDS,
                DebugRenderBools::isRaids))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_BEES_TOGGLE)
            .labelKey("devmod.action.native_debug.bees")
            .descriptionKey("devmod.action.native_debug.bees.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Bees")
            .icon(Items.HONEYCOMB)
            .toggle(context -> DebugRenderBools.isBees())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleNativeDebug(context, DebugFeature.BEES,
                DebugRenderBools::isBees))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE)
            .labelKey("devmod.action.native_debug.game_events")
            .descriptionKey("devmod.action.native_debug.game_events.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Game Events")
            .icon(Items.SCULK_SENSOR)
            .toggle(context -> DebugRenderBools.isGameEvents())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleNativeDebug(context, DebugFeature.GAME_EVENTS,
                DebugRenderBools::isGameEvents))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE)
            .labelKey("devmod.action.native_debug.structures")
            .descriptionKey("devmod.action.native_debug.structures.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Structures")
            .icon(Items.STRUCTURE_BLOCK)
            .toggle(context -> DebugRenderBools.isStructures())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleNativeDebug(context, DebugFeature.STRUCTURE_GENERATIONS,
                DebugRenderBools::isStructures))
            .build());

        // Light overlay
        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE)
            .labelKey("devmod.radial.item.light_levels")
            .descriptionKey("devmod.radial.item.light_levels.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Light")
            .icon(Items.TORCH)
            .toggle(context -> LightLevelOverlay.INSTANCE.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                boolean enabled = LightLevelOverlay.INSTANCE.isEnabled();
                var settings = SettingsManager.INSTANCE.getSettings();
                if (!enabled && !settings.debug.lightOverlayPerfWarned) {
                    settings.debug.lightOverlayPerfWarned = true;
                    SettingsManager.INSTANCE.markDirty();
                    Component overlayName = I18n.translate("devmod.radial.item.light_levels");
                    Component warning = I18n.translate("devmod.render.overlay_perf_warning", overlayName)
                        .withStyle(SharedColorTokens.Chat.GOLD);
                    context.sendSuccess(warning, true);
                    return;
                }
                LightLevelOverlay.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = LightLevelOverlay.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.light_overlay_status", status), true);
                if (context.getPlayer() != null) {
                    checkOverlayCount(context.getPlayer());
                }
            })
            .build());

        // Heatmaps
        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_CYCLE)
            .labelKey("devmod.radial.item.heatmaps")
            .descriptionKey("devmod.radial.item.heatmaps.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Cycle")
            .icon(Items.BLAZE_POWDER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(ClientDebugActions::cycleHeatmaps)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_TOGGLE)
            .labelKey("devmod.action.heatmap.toggle")
            .descriptionKey("devmod.action.heatmap.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Toggle")
            .icon(Items.LEVER)
            .toggle(context -> HeatmapVisualizer.INSTANCE.hasActiveHeatmaps())
            .precondition(ActionPreconditions.clientOnly())
            .handler(ClientDebugActions::toggleHeatmaps)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE)
            .labelKey("devmod.action.heatmap.death.toggle")
            .descriptionKey("devmod.action.heatmap.death.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Types/Death")
            .icon(Items.SKELETON_SKULL)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapType.DEATH))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapType.DEATH))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE)
            .labelKey("devmod.action.heatmap.movement.toggle")
            .descriptionKey("devmod.action.heatmap.movement.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Types/Movement")
            .icon(Items.FEATHER)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapType.MOVEMENT))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapType.MOVEMENT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE)
            .labelKey("devmod.action.heatmap.camping.toggle")
            .descriptionKey("devmod.action.heatmap.camping.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Types/Camping")
            .icon(Items.CAMPFIRE)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapType.CAMPING))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapType.CAMPING))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE)
            .labelKey("devmod.action.heatmap.stuck.toggle")
            .descriptionKey("devmod.action.heatmap.stuck.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Types/Stuck")
            .icon(Items.COBWEB)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapType.STUCK))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapType.STUCK))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE)
            .labelKey("devmod.action.heatmap.aggro_drop.toggle")
            .descriptionKey("devmod.action.heatmap.aggro_drop.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Types/Aggro Drop")
            .icon(Items.ENDER_EYE)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapType.AGGRO_DROP))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapType.AGGRO_DROP))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_KITING_TOGGLE)
            .labelKey("devmod.action.heatmap.kiting.toggle")
            .descriptionKey("devmod.action.heatmap.kiting.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Types/Kiting")
            .icon(Items.BOW)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapType.KITING))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapType.KITING))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE)
            .labelKey("devmod.action.heatmap.light_spawnable.toggle")
            .descriptionKey("devmod.action.heatmap.light_spawnable.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Types/Light Spawnable")
            .icon(Items.REDSTONE_TORCH)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapType.LIGHT_SPAWNABLE))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapType.LIGHT_SPAWNABLE))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE)
            .labelKey("devmod.action.heatmap.light_dark.toggle")
            .descriptionKey("devmod.action.heatmap.light_dark.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Types/Light Dark")
            .icon(Items.COAL)
            .toggle(context -> HeatmapVisualizer.INSTANCE.isEnabled(HeatmapType.LIGHT_DARK))
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> toggleHeatmapType(context, HeatmapType.LIGHT_DARK))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT)
            .labelKey("devmod.action.heatmap.clear_current")
            .descriptionKey("devmod.action.heatmap.clear_current.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Clear Current")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(ClientDebugActions::clearCurrentHeatmap)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_HEATMAP_CLEAR_ALL)
            .labelKey("devmod.action.heatmap.clear_all")
            .descriptionKey("devmod.action.heatmap.clear_all.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Combat/Heatmaps/Clear All")
            .icon(Items.FLINT_AND_STEEL)
            .precondition(ActionPreconditions.clientOnly())
            .handler(ClientDebugActions::clearAllHeatmaps)
            .build());

        // Spatial debug
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
                String status = RoomBoundsVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                int roomCount = RoomBoundsVisualizer.INSTANCE.getRoomCount();
                int gapCount = RoomBoundsVisualizer.INSTANCE.getGapCount();
                String gapInfo = gapCount > 0 ? " \u00A7c" + gapCount + " gaps!" : " \u00A7a0 gaps";
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

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ROOM_BOUNDS_GAPS_TOGGLE)
            .labelKey("devmod.action.room_bounds.gaps")
            .descriptionKey("devmod.action.room_bounds.gaps.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Gap Detection")
            .icon(Items.BARRIER)
            .toggle(context -> RoomBoundsVisualizer.INSTANCE.isShowingGaps())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                RoomBoundsVisualizer.INSTANCE.toggleGapDetection();
                SettingsManager.INSTANCE.markDirty();
                String status = RoomBoundsVisualizer.INSTANCE.isShowingGaps() ? "\u00A7aON" : "\u00A7cOFF";
                int gapCount = RoomBoundsVisualizer.INSTANCE.getGapCount();
                context.sendSuccess(I18n.translate("devmod.render.room_gaps_status", status, gapCount), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ROOM_BOUNDS_CLEAR)
            .labelKey("devmod.action.room_bounds.clear")
            .descriptionKey("devmod.action.room_bounds.clear.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Spatial/Clear Rooms")
            .icon(Items.TNT)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                RoomBoundsVisualizer.INSTANCE.clearRooms();
                context.sendSuccess(I18n.translate("devmod.render.room_bounds_cleared"), true);
            })
            .build());

        // AI debug
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
                String status = PathfindingDebugger.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                String status = LineOfSightVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                String status = AggroRangeVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                String status = VerticalLevelsVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                String status = SafeSpotVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                int spotCount = SafeSpotVisualizer.INSTANCE.getSafeSpotCount();
                context.sendSuccess(I18n.translate("devmod.render.safe_spots_status", status, spotCount), true);
            })
            .build());

        // Perf monitors
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
                String status = AttributeMonitoringSystem.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                String status = FpsTracker.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                String status = PerformanceProfiler.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                String status = EntityDensityOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.entity_density_status", status), true);
            })
            .build());

        // Combat diagnostics
        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_BOSS_PHASE_TOGGLE)
            .labelKey("devmod.radial.item.boss_phases")
            .descriptionKey("devmod.radial.item.boss_phases.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Diagnostics/Boss Phases")
            .icon(Items.WITHER_SKELETON_SKULL)
            .toggle(context -> BossPhaseOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                BossPhaseOverlay.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = BossPhaseOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.boss_phase_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE)
            .labelKey("devmod.radial.item.skill_efficacy")
            .descriptionKey("devmod.radial.item.skill_efficacy.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Diagnostics/Skill Efficacy")
            .icon(Items.ENCHANTED_BOOK)
            .toggle(context -> SkillEfficacyOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                SkillEfficacyOverlay.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = SkillEfficacyOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                boolean enabled = SpawnabilityOverlay.INSTANCE.isEnabled();
                var settings = SettingsManager.INSTANCE.getSettings();
                if (!enabled && !settings.debug.spawnabilityOverlayPerfWarned) {
                    settings.debug.spawnabilityOverlayPerfWarned = true;
                    SettingsManager.INSTANCE.markDirty();
                    Component overlayName = I18n.translate("devmod.radial.item.spawnability");
                    Component warning = I18n.translate("devmod.render.overlay_perf_warning", overlayName)
                        .withStyle(SharedColorTokens.Chat.GOLD);
                    context.sendSuccess(warning, true);
                    return;
                }
                SpawnabilityOverlay.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = SpawnabilityOverlay.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
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
                String status = ChunkPerformanceVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.chunk_perf_status", status), true);
            })
            .build());

        // Economy
        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ECONOMY_TOGGLE)
            .labelKey("devmod.radial.item.economy")
            .descriptionKey("devmod.radial.item.economy.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Diagnostics/Economy")
            .icon(Items.GOLD_INGOT)
            .toggle(context -> EconomyOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                EconomyOverlay.toggle();
                String status = EconomyOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.economy_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE)
            .labelKey("devmod.action.economy.view_cycle")
            .descriptionKey("devmod.action.economy.view_cycle.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Diagnostics/Economy View")
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
            .menuPath("Root/Combat/Diagnostics/Economy Sort")
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
            .menuPath("Root/Combat/Diagnostics/Dismiss Impact")
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
                com.devmod.client.effects.ShakeEffect testShake =
                    com.devmod.client.effects.ShakeManager.createMediumHit(player.position());
                com.devmod.client.effects.ShakeManager.INSTANCE.addShake(testShake);
                context.sendSuccess(Component.literal("\u00A7e[DevMod] \u00A7fScreen shake test triggered! Active shakes: \u00A7a" +
                    com.devmod.client.effects.ShakeManager.INSTANCE.getActiveCount()), true);
            })
            .build());

        // HUD toggles
        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_QUICK_HELP_TOGGLE)
            .labelKey("devmod.radial.item.quick_help")
            .descriptionKey("devmod.radial.item.quick_help.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Config/Quick Help")
            .icon(Items.KNOWLEDGE_BOOK)
            .toggle(context -> com.devmod.client.overlay.QuickHelpOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.overlay.QuickHelpOverlay.toggle();
                String status = com.devmod.client.overlay.QuickHelpOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.quick_help_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_QUEST_TOGGLE)
            .labelKey("devmod.radial.item.quest_hud")
            .descriptionKey("devmod.radial.item.quest_hud.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance HUD/Quest")
            .icon(Items.MAP)
            .toggle(context -> com.devmod.client.quest.QuestHudOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.quest.QuestHudOverlay.toggle();
                String status = com.devmod.client.quest.QuestHudOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.quest_hud_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_ENDURANCE_TOGGLE)
            .labelKey("devmod.radial.item.endurance_hud")
            .descriptionKey("devmod.radial.item.endurance_hud.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance HUD/Endurance")
            .icon(Items.IRON_CHESTPLATE)
            .toggle(context -> com.devmod.client.overlay.EnduranceQuestOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.overlay.EnduranceQuestOverlay.toggle();
                String status = com.devmod.client.overlay.EnduranceQuestOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.endurance_hud_status", status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE)
            .labelKey("devmod.action.endurance_hud.details")
            .descriptionKey("devmod.action.endurance_hud.details.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance HUD/Details")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.overlay.EnduranceQuestOverlay.toggleDetails();
                String detailStatus = com.devmod.client.overlay.EnduranceQuestOverlay.isShowingDetails() ? "\u00A7aDetailed" : "\u00A77Compact";
                context.sendSuccess(I18n.translate("devmod.render.endurance_details", detailStatus), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.HUD_PARTY_TOGGLE)
            .labelKey("devmod.radial.item.party_hud")
            .descriptionKey("devmod.radial.item.party_hud.desc")
            .category(ActionCategory.PARTY)
            .menuPath("Root/Play/Party/HUD")
            .icon(Items.PLAYER_HEAD)
            .toggle(context -> com.devmod.client.overlay.PartyHudOverlay.isEnabled())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                com.devmod.client.overlay.PartyHudOverlay.toggle();
                String status = com.devmod.client.overlay.PartyHudOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
                context.sendSuccess(I18n.translate("devmod.render.party_hud_status", status), true);
            })
            .build());

        // Context debug
        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_COMBAT_RESET)
            .labelKey("devmod.action.combat_reset")
            .descriptionKey("devmod.action.combat_reset.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Context/Reset Combat")
            .icon(Items.CLOCK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                ContextDetector.INSTANCE.reset();
                context.sendSuccess(I18n.translate("devmod.context.reset_success"), true);
                org.slf4j.LoggerFactory.getLogger(ClientDebugActions.class).info(
                    "[ContextDetector] Manual reset performed - mode now: {}",
                    ContextDetector.INSTANCE.getCurrentMode());
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_CONTEXT_STATUS)
            .labelKey("devmod.action.context_status")
            .descriptionKey("devmod.action.context_status.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Context/Status")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                String debugInfo = Objects.requireNonNull(ContextDetector.INSTANCE.getDebugInfo());
                context.sendSuccess(Component.literal(debugInfo), false);
                org.slf4j.LoggerFactory.getLogger(ClientDebugActions.class).info(
                    "[ContextDetector] Status: {}", debugInfo);
            })
            .build());
    }

    // ── Keybind hints ──

    static void registerKeybindHints() {
        ActionKeybindRegistry.register(ActionIds.DEBUG_OVERLAY_TOGGLE, KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_BODY_PARTS_TOGGLE, KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE, KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_HEATMAP_CYCLE, KeyInputHandler.TOGGLE_HEATMAP_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT, KeyInputHandler.TOGGLE_HEATMAP_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.DEBUG_HEATMAP_CLEAR_ALL, KeyInputHandler.TOGGLE_HEATMAP_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.DEBUG_IMPACT_DISMISS, KeyInputHandler.DISMISS_IMPACT_HUD_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE, KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ROOM_BOUNDS_RELOAD, KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.DEBUG_PATHFINDING_TOGGLE, KeyInputHandler.TOGGLE_PATHFINDING_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_LOS_TOGGLE, KeyInputHandler.TOGGLE_LOS_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE, KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE, KeyInputHandler.TOGGLE_SAFE_SPOT_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE, KeyInputHandler.TOGGLE_ATTRIBUTE_MONITOR_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_FPS_TRACKER_TOGGLE, KeyInputHandler.TOGGLE_FPS_TRACKER_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_PROFILER_TOGGLE, KeyInputHandler.TOGGLE_PROFILER_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_BOSS_PHASE_TOGGLE, KeyInputHandler.TOGGLE_BOSS_PHASE_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE, KeyInputHandler.TOGGLE_ENTITY_DENSITY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE, KeyInputHandler.TOGGLE_SKILL_EFFICACY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_SPAWNABILITY_TOGGLE, KeyInputHandler.TOGGLE_SPAWNABILITY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_CHUNK_PERF_TOGGLE, KeyInputHandler.TOGGLE_CHUNK_PERF_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ECONOMY_TOGGLE, KeyInputHandler.TOGGLE_ECONOMY_KEY);
        ActionKeybindRegistry.register(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE, KeyInputHandler.TOGGLE_ECONOMY_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.DEBUG_ECONOMY_SORT_CYCLE, KeyInputHandler.TOGGLE_ECONOMY_KEY, "Ctrl");
        ActionKeybindRegistry.register(ActionIds.DEBUG_SCREEN_SHAKE_TEST, KeyInputHandler.TEST_SCREEN_SHAKE_KEY);
        ActionKeybindRegistry.register(ActionIds.HUD_QUICK_HELP_TOGGLE, KeyInputHandler.TOGGLE_HELP_KEY);
        ActionKeybindRegistry.register(ActionIds.HUD_QUEST_TOGGLE, KeyInputHandler.TOGGLE_QUEST_HUD_KEY);
        ActionKeybindRegistry.register(ActionIds.HUD_ENDURANCE_TOGGLE, KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY, "Shift");
        ActionKeybindRegistry.register(ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE, KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY, "Ctrl");
    }

    // ── Private helpers ──

    private static void toggleNativeDebug(ActionContext context, DebugFeature feature, BooleanSupplier currentState) {
        boolean wasEnabled = currentState.getAsBoolean();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PacketDistributor.sendToServer(new DebugTogglePayload(feature.getId()));
        }
        boolean enabled = !wasEnabled;
        String status = enabled ? "\u00A7aON" : "\u00A7cOFF";
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
                toggleNativeIfNeeded(DebugFeature.ENTITY_PATHING, DebugRenderBools.isEntityPathing(), true);
                toggleNativeIfNeeded(DebugFeature.ENTITY_GOALS, DebugRenderBools.isEntityGoals(), true);
            } else {
                toggleNativeIfNeeded(DebugFeature.ENTITY_PATHING, DebugRenderBools.isEntityPathing(), false);
                toggleNativeIfNeeded(DebugFeature.ENTITY_GOALS, DebugRenderBools.isEntityGoals(), false);
                toggleNativeIfNeeded(DebugFeature.ENTITY_BRAINS, DebugRenderBools.isEntityBrains(), false);
                toggleNativeIfNeeded(DebugFeature.POI, DebugRenderBools.isPoi(), false);
                toggleNativeIfNeeded(DebugFeature.RAIDS, DebugRenderBools.isRaids(), false);
                toggleNativeIfNeeded(DebugFeature.BEES, DebugRenderBools.isBees(), false);
                toggleNativeIfNeeded(DebugFeature.GAME_EVENTS, DebugRenderBools.isGameEvents(), false);
                toggleNativeIfNeeded(DebugFeature.STRUCTURE_GENERATIONS, DebugRenderBools.isStructures(), false);
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

        int idx = currentHeatmapIndex.get();
        if (idx >= 0 && idx < HEATMAP_CYCLE.length) {
            HeatmapType currentType = HEATMAP_CYCLE[idx];
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
        for (HeatmapType type : HeatmapType.values()) {
            HeatmapVisualizer.INSTANCE.setEnabled(type, enable);
        }
        if (!enable) {
            currentHeatmapIndex.set(-1);
        }
        String status = enable ? "\u00A7aON" : "\u00A7cOFF";
        context.sendSuccess(I18n.translate("devmod.render.heatmap_toggle_status", status), true);
    }

    private static void toggleHeatmapType(ActionContext context, HeatmapType type) {
        boolean enable = !HeatmapVisualizer.INSTANCE.isEnabled(type);
        HeatmapVisualizer.INSTANCE.setEnabled(type, enable);
        int index = heatmapIndex(type);
        if (enable) {
            currentHeatmapIndex.set(index);
        } else if (currentHeatmapIndex.get() == index) {
            currentHeatmapIndex.set(-1);
        }
        String status = enable ? "\u00A7aON" : "\u00A7cOFF";
        context.sendSuccess(I18n.translate("devmod.render.heatmap_type_toggle_status", type.name(), status), true);
    }

    private static void clearCurrentHeatmap(ActionContext context) {
        int idx = currentHeatmapIndex.get();
        if (idx >= 0 && idx < HEATMAP_CYCLE.length) {
            HeatmapType currentType = HEATMAP_CYCLE[idx];
            HeatmapVisualizer.INSTANCE.clear(currentType);
            int remaining = HeatmapVisualizer.INSTANCE.getDataCount(currentType);
            context.sendSuccess(I18n.translate("devmod.render.heatmap_type_cleared", currentType.name(), remaining), true);
        } else {
            context.sendSuccess(I18n.translate("devmod.render.no_heatmap_selected"), true);
        }
    }

    private static void clearAllHeatmaps(ActionContext context) {
        HeatmapVisualizer.INSTANCE.clearAll();
        currentHeatmapIndex.set(-1);
        for (HeatmapType type : HEATMAP_CYCLE) {
            HeatmapVisualizer.INSTANCE.setEnabled(type, false);
        }
        context.sendSuccess(I18n.translate("devmod.render.heatmaps_cleared"), true);
    }

    private static int heatmapIndex(HeatmapType type) {
        for (int i = 0; i < HEATMAP_CYCLE.length; i++) {
            if (HEATMAP_CYCLE[i] == type) {
                return i;
            }
        }
        return -1;
    }

    private static void cycleHeatmapType() {
        int idx = currentHeatmapIndex.get();
        if (idx >= 0 && idx < HEATMAP_CYCLE.length) {
            HeatmapVisualizer.INSTANCE.setEnabled(HEATMAP_CYCLE[idx], false);
        }
        idx++;
        if (idx >= HEATMAP_CYCLE.length) {
            idx = -1;
        }
        currentHeatmapIndex.set(idx);
        if (idx >= 0) {
            HeatmapVisualizer.INSTANCE.setEnabled(HEATMAP_CYCLE[idx], true);
        }
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
}
