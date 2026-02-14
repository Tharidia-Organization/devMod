package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.ModConfig;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.client.attributes.AttributeMonitoringSystem;
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
import com.devmod.client.rendering.SpawnabilityOverlay;
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
 * V2 domain registrar for debug actions (overlays, heatmaps, visualizers,
 * HUD toggles, combat diagnostics, native debug, perf monitors, context debug).
 *
 * <p>Parallel to the existing {@link com.devmod.actions.client.ClientDebugActions}
 * registration. Does NOT replace it yet.
 */
public final class DebugDomainRegistrar implements DomainRegistrar {

    private static final HeatmapType[] HEATMAP_CYCLE = {
        HeatmapType.DEATH, HeatmapType.MOVEMENT, HeatmapType.CAMPING,
        HeatmapType.STUCK, HeatmapType.AGGRO_DROP, HeatmapType.KITING
    };
    private static final java.util.concurrent.atomic.AtomicInteger currentHeatmapIndex =
        new java.util.concurrent.atomic.AtomicInteger(-1);
    private static long lastOverlayWarningTime = 0;

    @Override
    public String domainName() {
        return "debug";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        List<ActionSpec> specs = new ArrayList<>();

        // --- Debug Overlays ---
        specs.add(toggle(ActionIds.DEBUG_OVERLAY_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.mob_debug", "devmod.radial.item.mob_debug.desc",
            "minecraft:zombie_head", "Root/Debug/Overlays/Debug Overlay"));
        specs.add(toggle(ActionIds.DEBUG_BODY_PARTS_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.body_parts", "devmod.radial.item.body_parts.desc",
            "minecraft:armor_stand", "Root/Debug/Overlays/Body Parts"));
        specs.add(action(ActionIds.DEBUG_OVERLAYS_ENABLE_ALL, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.debug_overlays.enable_all", "devmod.action.debug_overlays.enable_all.desc",
            "minecraft:lime_dye", "Root/Debug/Overlays/Enable All"));
        specs.add(action(ActionIds.DEBUG_OVERLAYS_DISABLE_ALL, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.debug_overlays.disable_all", "devmod.action.debug_overlays.disable_all.desc",
            "minecraft:gray_dye", "Root/Debug/Overlays/Disable All"));

        // --- Native debug toggles ---
        specs.add(toggle(ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.native_debug.entity_pathing", "devmod.action.native_debug.entity_pathing.desc",
            "minecraft:lead", "Root/Debug/Native/Entity Pathing"));
        specs.add(toggle(ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.native_debug.entity_goals", "devmod.action.native_debug.entity_goals.desc",
            "minecraft:writable_book", "Root/Debug/Native/Entity Goals"));
        specs.add(toggle(ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.native_debug.entity_brains", "devmod.action.native_debug.entity_brains.desc",
            "minecraft:ender_pearl", "Root/Debug/Native/Entity Brains"));
        specs.add(toggle(ActionIds.DEBUG_NATIVE_POI_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.native_debug.poi", "devmod.action.native_debug.poi.desc",
            "minecraft:bell", "Root/Debug/Native/POI"));
        specs.add(toggle(ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.native_debug.raids", "devmod.action.native_debug.raids.desc",
            "minecraft:iron_sword", "Root/Debug/Native/Raids"));
        specs.add(toggle(ActionIds.DEBUG_NATIVE_BEES_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.native_debug.bees", "devmod.action.native_debug.bees.desc",
            "minecraft:honeycomb", "Root/Debug/Native/Bees"));
        specs.add(toggle(ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.native_debug.game_events", "devmod.action.native_debug.game_events.desc",
            "minecraft:sculk_sensor", "Root/Debug/Native/Game Events"));
        specs.add(toggle(ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.native_debug.structures", "devmod.action.native_debug.structures.desc",
            "minecraft:structure_block", "Root/Debug/Native/Structures"));

        // --- Light overlay ---
        specs.add(toggle(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.light_levels", "devmod.radial.item.light_levels.desc",
            "minecraft:torch", "Root/Debug/Light"));

        // --- Heatmaps ---
        specs.add(action(ActionIds.DEBUG_HEATMAP_CYCLE, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.radial.item.heatmaps", "devmod.radial.item.heatmaps.desc",
            "minecraft:blaze_powder", "Root/Combat/Heatmaps/Cycle"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.toggle", "devmod.action.heatmap.toggle.desc",
            "minecraft:lever", "Root/Combat/Heatmaps/Toggle"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.death.toggle", "devmod.action.heatmap.death.toggle.desc",
            "minecraft:skeleton_skull", "Root/Combat/Heatmaps/Types/Death"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.movement.toggle", "devmod.action.heatmap.movement.toggle.desc",
            "minecraft:feather", "Root/Combat/Heatmaps/Types/Movement"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.camping.toggle", "devmod.action.heatmap.camping.toggle.desc",
            "minecraft:campfire", "Root/Combat/Heatmaps/Types/Camping"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.stuck.toggle", "devmod.action.heatmap.stuck.toggle.desc",
            "minecraft:cobweb", "Root/Combat/Heatmaps/Types/Stuck"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.aggro_drop.toggle", "devmod.action.heatmap.aggro_drop.toggle.desc",
            "minecraft:ender_eye", "Root/Combat/Heatmaps/Types/Aggro Drop"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_KITING_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.kiting.toggle", "devmod.action.heatmap.kiting.toggle.desc",
            "minecraft:bow", "Root/Combat/Heatmaps/Types/Kiting"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.light_spawnable.toggle", "devmod.action.heatmap.light_spawnable.toggle.desc",
            "minecraft:redstone_torch", "Root/Combat/Heatmaps/Types/Light Spawnable"));
        specs.add(toggle(ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.heatmap.light_dark.toggle", "devmod.action.heatmap.light_dark.toggle.desc",
            "minecraft:coal", "Root/Combat/Heatmaps/Types/Light Dark"));
        specs.add(action(ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.heatmap.clear_current", "devmod.action.heatmap.clear_current.desc",
            "minecraft:barrier", "Root/Combat/Heatmaps/Clear Current"));
        specs.add(action(ActionIds.DEBUG_HEATMAP_CLEAR_ALL, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.heatmap.clear_all", "devmod.action.heatmap.clear_all.desc",
            "minecraft:flint_and_steel", "Root/Combat/Heatmaps/Clear All"));

        // --- Spatial debug ---
        specs.add(toggle(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.room_bounds", "devmod.radial.item.room_bounds.desc",
            "minecraft:structure_block", "Root/Debug/Spatial/Room Bounds"));
        specs.add(action(ActionIds.DEBUG_ROOM_BOUNDS_RELOAD, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.room_bounds.reload", "devmod.action.room_bounds.reload.desc",
            "minecraft:paper", "Root/Debug/Spatial/Reload"));
        specs.add(toggle(ActionIds.DEBUG_ROOM_BOUNDS_GAPS_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.room_bounds.gaps", "devmod.action.room_bounds.gaps.desc",
            "minecraft:barrier", "Root/Debug/Spatial/Gap Detection"));
        specs.add(action(ActionIds.DEBUG_ROOM_BOUNDS_CLEAR, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.room_bounds.clear", "devmod.action.room_bounds.clear.desc",
            "minecraft:tnt", "Root/Debug/Spatial/Clear Rooms"));

        // --- AI debug ---
        specs.add(toggle(ActionIds.DEBUG_PATHFINDING_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.pathfinding", "devmod.radial.item.pathfinding.desc",
            "minecraft:rail", "Root/Debug/AI/Pathfinding"));
        specs.add(toggle(ActionIds.DEBUG_LOS_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.line_of_sight", "devmod.radial.item.line_of_sight.desc",
            "minecraft:spyglass", "Root/Debug/AI/Line of Sight"));
        specs.add(toggle(ActionIds.DEBUG_AGGRO_RANGE_TOGGLE, ActionCategory.DEBUG,
            "devmod.action.aggro_range.toggle", "devmod.action.aggro_range.toggle.desc",
            "minecraft:target", "Root/Debug/AI/Aggro Range"));
        specs.add(toggle(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.vertical_levels", "devmod.radial.item.vertical_levels.desc",
            "minecraft:ladder", "Root/Debug/Spatial/Vertical Levels"));
        specs.add(toggle(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.safe_spots", "devmod.radial.item.safe_spots.desc",
            "minecraft:shield", "Root/Debug/Spatial/Safe Spots"));

        // --- Perf monitors ---
        specs.add(toggle(ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.attr_monitor", "devmod.radial.item.attr_monitor.desc",
            "minecraft:experience_bottle", "Root/Debug/Perf/Attribute Monitor"));
        specs.add(toggle(ActionIds.DEBUG_FPS_TRACKER_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.fps_tracker", "devmod.radial.item.fps_tracker.desc",
            "minecraft:clock", "Root/Debug/Perf/FPS Tracker"));
        specs.add(toggle(ActionIds.DEBUG_PROFILER_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.profiler", "devmod.radial.item.profiler.desc",
            "minecraft:redstone", "Root/Debug/Perf/Profiler"));
        specs.add(toggle(ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.entity_density", "devmod.radial.item.entity_density.desc",
            "minecraft:villager_spawn_egg", "Root/Debug/Perf/Entity Density"));
        specs.add(toggle(ActionIds.DEBUG_CHUNK_PERF_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.chunk_perf", "devmod.radial.item.chunk_perf.desc",
            "minecraft:chest", "Root/Debug/Perf/Chunk Performance"));
        specs.add(toggle(ActionIds.DEBUG_SPAWNABILITY_TOGGLE, ActionCategory.DEBUG,
            "devmod.radial.item.spawnability", "devmod.radial.item.spawnability.desc",
            "minecraft:spawner", "Root/Debug/Spatial/Spawnability"));

        // --- Combat diagnostics ---
        specs.add(toggle(ActionIds.DEBUG_BOSS_PHASE_TOGGLE, ActionCategory.COMBAT,
            "devmod.radial.item.boss_phases", "devmod.radial.item.boss_phases.desc",
            "minecraft:wither_skeleton_skull", "Root/Combat/Diagnostics/Boss Phases"));
        specs.add(toggle(ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE, ActionCategory.COMBAT,
            "devmod.radial.item.skill_efficacy", "devmod.radial.item.skill_efficacy.desc",
            "minecraft:enchanted_book", "Root/Combat/Diagnostics/Skill Efficacy"));

        // --- Economy ---
        specs.add(toggle(ActionIds.DEBUG_ECONOMY_TOGGLE, ActionCategory.COMBAT,
            "devmod.radial.item.economy", "devmod.radial.item.economy.desc",
            "minecraft:gold_ingot", "Root/Combat/Diagnostics/Economy"));
        specs.add(action(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE, ActionCategory.COMBAT, ActionType.TRIGGER_EVENT,
            "devmod.action.economy.view_cycle", "devmod.action.economy.view_cycle.desc",
            "minecraft:gold_block", "Root/Combat/Diagnostics/Economy View"));
        specs.add(action(ActionIds.DEBUG_ECONOMY_SORT_CYCLE, ActionCategory.COMBAT, ActionType.TRIGGER_EVENT,
            "devmod.action.economy.sort_cycle", "devmod.action.economy.sort_cycle.desc",
            "minecraft:hopper", "Root/Combat/Diagnostics/Economy Sort"));

        // --- Impact HUD ---
        specs.add(toggle(ActionIds.HUD_IMPACT_TOGGLE, ActionCategory.COMBAT,
            "devmod.action.impact_hud", "devmod.action.impact_hud.desc",
            "minecraft:netherite_sword", "Root/Combat/Impact HUD"));
        specs.add(toggle(ActionIds.HUD_IMPACT_CONTROLLER_TOGGLE, ActionCategory.COMBAT,
            "devmod.action.impact_controller", "devmod.action.impact_controller.desc",
            "minecraft:lever", "Root/Combat/Impact HUD/Quick Toggle"));
        specs.add(toggle(ActionIds.HUD_IMPACT_3D_TOGGLE, ActionCategory.COMBAT,
            "devmod.action.impact_hud_3d", "devmod.action.impact_hud_3d.desc",
            "minecraft:item_frame", "Root/Combat/Impact HUD/3D Panels"));
        specs.add(action(ActionIds.HUD_IMPACT_DISPLAY_MODE_CYCLE, ActionCategory.COMBAT, ActionType.TRIGGER_EVENT,
            "devmod.action.impact_display_mode", "devmod.action.impact_display_mode.desc",
            "minecraft:comparator", "Root/Combat/Impact HUD/Display Mode"));
        specs.add(action(ActionIds.HUD_IMPACT_PRESET_MINIMAL, ActionCategory.COMBAT, ActionType.TRIGGER_EVENT,
            "devmod.action.impact_preset.minimal", "devmod.action.impact_preset.minimal.desc",
            "minecraft:feather", "Root/Combat/Impact HUD/Presets/Minimal"));
        specs.add(action(ActionIds.HUD_IMPACT_PRESET_DETAILED, ActionCategory.COMBAT, ActionType.TRIGGER_EVENT,
            "devmod.action.impact_preset.detailed", "devmod.action.impact_preset.detailed.desc",
            "minecraft:book", "Root/Combat/Impact HUD/Presets/Detailed"));
        specs.add(action(ActionIds.HUD_IMPACT_PRESET_TRAINING, ActionCategory.COMBAT, ActionType.TRIGGER_EVENT,
            "devmod.action.impact_preset.training", "devmod.action.impact_preset.training.desc",
            "minecraft:knowledge_book", "Root/Combat/Impact HUD/Presets/Training"));
        specs.add(action(ActionIds.HUD_IMPACT_SHOW_RECAP, ActionCategory.COMBAT, ActionType.TRIGGER_EVENT,
            "devmod.action.impact_show_recap", "devmod.action.impact_show_recap.desc",
            "minecraft:paper", "Root/Combat/Impact HUD/Show Recap"));
        specs.add(action(ActionIds.DEBUG_IMPACT_DISMISS, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.impact.dismiss", "devmod.action.impact.dismiss.desc",
            "minecraft:barrier", "Root/Combat/Diagnostics/Dismiss Impact"));
        specs.add(action(ActionIds.DEBUG_SCREEN_SHAKE_TEST, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.screen_shake.test", "devmod.action.screen_shake.test.desc",
            "minecraft:note_block", "Root/Debug/VFX/Screen Shake"));

        // --- HUD toggles ---
        specs.add(toggle(ActionIds.HUD_QUICK_HELP_TOGGLE, ActionCategory.TOOLS,
            "devmod.radial.item.quick_help", "devmod.radial.item.quick_help.desc",
            "minecraft:knowledge_book", "Root/Config/Quick Help"));
        specs.add(toggle(ActionIds.HUD_QUEST_TOGGLE, ActionCategory.ENDURANCE,
            "devmod.radial.item.quest_hud", "devmod.radial.item.quest_hud.desc",
            "minecraft:map", "Root/Play/Endurance HUD/Quest"));
        specs.add(toggle(ActionIds.HUD_ENDURANCE_TOGGLE, ActionCategory.ENDURANCE,
            "devmod.radial.item.endurance_hud", "devmod.radial.item.endurance_hud.desc",
            "minecraft:iron_chestplate", "Root/Play/Endurance HUD/Endurance"));
        specs.add(action(ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE, ActionCategory.ENDURANCE, ActionType.TRIGGER_EVENT,
            "devmod.action.endurance_hud.details", "devmod.action.endurance_hud.details.desc",
            "minecraft:paper", "Root/Play/Endurance HUD/Details"));
        specs.add(toggle(ActionIds.HUD_PARTY_TOGGLE, ActionCategory.PARTY,
            "devmod.radial.item.party_hud", "devmod.radial.item.party_hud.desc",
            "minecraft:player_head", "Root/Play/Party/HUD"));

        // --- Context debug ---
        specs.add(action(ActionIds.DEBUG_COMBAT_RESET, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.combat_reset", "devmod.action.combat_reset.desc",
            "minecraft:clock", "Root/Debug/Context/Reset Combat"));
        specs.add(action(ActionIds.DEBUG_CONTEXT_STATUS, ActionCategory.DEBUG, ActionType.TRIGGER_EVENT,
            "devmod.action.context_status", "devmod.action.context_status.desc",
            "minecraft:book", "Root/Debug/Context/Status"));

        return specs;
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        registry.register(ActionIds.DEBUG_OVERLAY_TOGGLE, ctx -> {
            DebugRenderer.INSTANCE.toggle();
            Config.DEBUG_OVERLAY_ENABLED.set(DebugRenderer.INSTANCE.isEnabled());
            SettingsManager.INSTANCE.markDirty();
            String status = DebugRenderer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.debug_overlay_status", status), true);
            if (ctx.getPlayer() != null) checkOverlayCount(ctx.getPlayer());
        });

        registry.register(ActionIds.DEBUG_BODY_PARTS_TOGGLE, ctx -> {
            ModConfig.setShowBodyPartBoxes(!ModConfig.isShowBodyPartBoxes());
            Config.SHOW_BODY_PART_BOXES.set(ModConfig.isShowBodyPartBoxes());
            if (ModConfig.isShowBodyPartBoxes() && !ModConfig.isShowRender()) ModConfig.setShowRender(true);
            SettingsManager.INSTANCE.markDirty();
            String status = ModConfig.isShowBodyPartBoxes() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.body_parts_status", status), true);
        });

        registry.register(ActionIds.DEBUG_OVERLAYS_ENABLE_ALL, ctx -> setDebugOverlaysEnabled(ctx, true));
        registry.register(ActionIds.DEBUG_OVERLAYS_DISABLE_ALL, ctx -> setDebugOverlaysEnabled(ctx, false));

        // Native debug
        registry.register(ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE, ctx ->
            toggleNativeDebug(ctx, DebugFeature.ENTITY_PATHING, DebugRenderBools::isEntityPathing));
        registry.register(ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE, ctx ->
            toggleNativeDebug(ctx, DebugFeature.ENTITY_GOALS, DebugRenderBools::isEntityGoals));
        registry.register(ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE, ctx ->
            toggleNativeDebug(ctx, DebugFeature.ENTITY_BRAINS, DebugRenderBools::isEntityBrains));
        registry.register(ActionIds.DEBUG_NATIVE_POI_TOGGLE, ctx ->
            toggleNativeDebug(ctx, DebugFeature.POI, DebugRenderBools::isPoi));
        registry.register(ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE, ctx ->
            toggleNativeDebug(ctx, DebugFeature.RAIDS, DebugRenderBools::isRaids));
        registry.register(ActionIds.DEBUG_NATIVE_BEES_TOGGLE, ctx ->
            toggleNativeDebug(ctx, DebugFeature.BEES, DebugRenderBools::isBees));
        registry.register(ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE, ctx ->
            toggleNativeDebug(ctx, DebugFeature.GAME_EVENTS, DebugRenderBools::isGameEvents));
        registry.register(ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE, ctx ->
            toggleNativeDebug(ctx, DebugFeature.STRUCTURE_GENERATIONS, DebugRenderBools::isStructures));

        // Light overlay
        registry.register(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE, ctx -> {
            boolean enabled = LightLevelOverlay.INSTANCE.isEnabled();
            var settings = SettingsManager.INSTANCE.getSettings();
            if (!enabled && !settings.debug.lightOverlayPerfWarned) {
                settings.debug.lightOverlayPerfWarned = true;
                SettingsManager.INSTANCE.markDirty();
                Component overlayName = I18n.translate("devmod.radial.item.light_levels");
                Component warning = I18n.translate("devmod.render.overlay_perf_warning", overlayName)
                    .withStyle(SharedColorTokens.Chat.GOLD);
                ctx.sendSuccess(warning, true);
                return;
            }
            LightLevelOverlay.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = LightLevelOverlay.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.light_overlay_status", status), true);
            if (ctx.getPlayer() != null) checkOverlayCount(ctx.getPlayer());
        });

        // Heatmaps
        registry.register(ActionIds.DEBUG_HEATMAP_CYCLE, DebugDomainRegistrar::cycleHeatmaps);
        registry.register(ActionIds.DEBUG_HEATMAP_TOGGLE, DebugDomainRegistrar::toggleHeatmaps);
        registry.register(ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE, ctx -> toggleHeatmapType(ctx, HeatmapType.DEATH));
        registry.register(ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE, ctx -> toggleHeatmapType(ctx, HeatmapType.MOVEMENT));
        registry.register(ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE, ctx -> toggleHeatmapType(ctx, HeatmapType.CAMPING));
        registry.register(ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE, ctx -> toggleHeatmapType(ctx, HeatmapType.STUCK));
        registry.register(ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE, ctx -> toggleHeatmapType(ctx, HeatmapType.AGGRO_DROP));
        registry.register(ActionIds.DEBUG_HEATMAP_KITING_TOGGLE, ctx -> toggleHeatmapType(ctx, HeatmapType.KITING));
        registry.register(ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE, ctx -> toggleHeatmapType(ctx, HeatmapType.LIGHT_SPAWNABLE));
        registry.register(ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE, ctx -> toggleHeatmapType(ctx, HeatmapType.LIGHT_DARK));
        registry.register(ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT, DebugDomainRegistrar::clearCurrentHeatmap);
        registry.register(ActionIds.DEBUG_HEATMAP_CLEAR_ALL, DebugDomainRegistrar::clearAllHeatmaps);

        // Spatial debug
        registry.register(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE, ctx -> {
            RoomBoundsVisualizer.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = RoomBoundsVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            int roomCount = RoomBoundsVisualizer.INSTANCE.getRoomCount();
            int gapCount = RoomBoundsVisualizer.INSTANCE.getGapCount();
            String gapInfo = gapCount > 0 ? " \u00A7c" + gapCount + " gaps!" : " \u00A7a0 gaps";
            ctx.sendSuccess(I18n.translate("devmod.render.room_bounds_status", status, roomCount, gapInfo), true);
        });
        registry.register(ActionIds.DEBUG_ROOM_BOUNDS_RELOAD, ctx -> {
            RoomBoundsVisualizer.INSTANCE.reload();
            int roomCount = RoomBoundsVisualizer.INSTANCE.getRoomCount();
            ctx.sendSuccess(I18n.translate("devmod.render.room_bounds_reloaded", roomCount), true);
        });
        registry.register(ActionIds.DEBUG_ROOM_BOUNDS_GAPS_TOGGLE, ctx -> {
            RoomBoundsVisualizer.INSTANCE.toggleGapDetection();
            SettingsManager.INSTANCE.markDirty();
            String status = RoomBoundsVisualizer.INSTANCE.isShowingGaps() ? "\u00A7aON" : "\u00A7cOFF";
            int gapCount = RoomBoundsVisualizer.INSTANCE.getGapCount();
            ctx.sendSuccess(I18n.translate("devmod.render.room_gaps_status", status, gapCount), true);
        });
        registry.register(ActionIds.DEBUG_ROOM_BOUNDS_CLEAR, ctx -> {
            RoomBoundsVisualizer.INSTANCE.clearRooms();
            ctx.sendSuccess(I18n.translate("devmod.render.room_bounds_cleared"), true);
        });

        // AI debug
        registry.register(ActionIds.DEBUG_PATHFINDING_TOGGLE, ctx -> {
            PathfindingDebugger.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = PathfindingDebugger.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.pathfinding_status", status), true);
        });
        registry.register(ActionIds.DEBUG_LOS_TOGGLE, ctx -> {
            LineOfSightVisualizer.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = LineOfSightVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.los_status", status), true);
        });
        registry.register(ActionIds.DEBUG_AGGRO_RANGE_TOGGLE, ctx -> {
            AggroRangeVisualizer.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = AggroRangeVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.aggro_range_status", status), true);
        });
        registry.register(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE, ctx -> {
            VerticalLevelsVisualizer.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = VerticalLevelsVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.vertical_status", status), true);
        });
        registry.register(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE, ctx -> {
            SafeSpotVisualizer.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = SafeSpotVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            int spotCount = SafeSpotVisualizer.INSTANCE.getSafeSpotCount();
            ctx.sendSuccess(I18n.translate("devmod.render.safe_spots_status", status, spotCount), true);
        });

        // Perf monitors
        registry.register(ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE, ctx -> {
            AttributeMonitoringSystem.INSTANCE.toggle();
            String status = AttributeMonitoringSystem.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            int tracked = AttributeMonitoringSystem.INSTANCE.getTrackedCount();
            ctx.sendSuccess(I18n.translate("devmod.render.attr_monitor_status", status, tracked), true);
        });
        registry.register(ActionIds.DEBUG_FPS_TRACKER_TOGGLE, ctx -> {
            FpsTracker.INSTANCE.toggle();
            String status = FpsTracker.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.fps_tracker_status", status), true);
        });
        registry.register(ActionIds.DEBUG_PROFILER_TOGGLE, ctx -> {
            PerformanceProfiler.INSTANCE.toggle();
            String status = PerformanceProfiler.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.profiler_status", status), true);
        });
        registry.register(ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE, ctx -> {
            EntityDensityOverlay.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = EntityDensityOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.entity_density_status", status), true);
        });
        registry.register(ActionIds.DEBUG_CHUNK_PERF_TOGGLE, ctx -> {
            ChunkPerformanceVisualizer.INSTANCE.toggle();
            String status = ChunkPerformanceVisualizer.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.chunk_perf_status", status), true);
        });
        registry.register(ActionIds.DEBUG_SPAWNABILITY_TOGGLE, ctx -> {
            boolean enabled = SpawnabilityOverlay.INSTANCE.isEnabled();
            var settings = SettingsManager.INSTANCE.getSettings();
            if (!enabled && !settings.debug.spawnabilityOverlayPerfWarned) {
                settings.debug.spawnabilityOverlayPerfWarned = true;
                SettingsManager.INSTANCE.markDirty();
                Component overlayName = I18n.translate("devmod.radial.item.spawnability");
                Component warning = I18n.translate("devmod.render.overlay_perf_warning", overlayName)
                    .withStyle(SharedColorTokens.Chat.GOLD);
                ctx.sendSuccess(warning, true);
                return;
            }
            SpawnabilityOverlay.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = SpawnabilityOverlay.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            var stats = SpawnabilityOverlay.INSTANCE.getStats();
            ctx.sendSuccess(I18n.translate("devmod.render.spawnability_status", status, stats.hostileSpawnBlocks()), true);
        });

        // Combat diagnostics
        registry.register(ActionIds.DEBUG_BOSS_PHASE_TOGGLE, ctx -> {
            BossPhaseOverlay.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = BossPhaseOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.boss_phase_status", status), true);
        });
        registry.register(ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE, ctx -> {
            SkillEfficacyOverlay.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = SkillEfficacyOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.skill_efficacy_status", status), true);
        });

        // Economy
        registry.register(ActionIds.DEBUG_ECONOMY_TOGGLE, ctx -> {
            EconomyOverlay.toggle();
            String status = EconomyOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.economy_status", status), true);
        });
        registry.register(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE, ctx -> {
            if (EconomyOverlay.isEnabled()) {
                EconomyOverlay.cycleView();
                ctx.sendSuccess(I18n.translate("devmod.render.economy_view", EconomyOverlay.getViewModeName()), true);
            }
        });
        registry.register(ActionIds.DEBUG_ECONOMY_SORT_CYCLE, ctx -> {
            if (EconomyOverlay.isEnabled()) {
                EconomyOverlay.cycleSortMode();
                ctx.sendSuccess(I18n.translate("devmod.render.economy_sort", EconomyOverlay.getSortModeName()), true);
            }
        });

        // Impact HUD
        registry.register(ActionIds.HUD_IMPACT_TOGGLE, ctx -> {
            ImpactHudOverlay.toggle();
            Config.IMPACT_HUD_ENABLED.set(ImpactHudOverlay.isEnabled());
            String status = ImpactHudOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.impact_hud_status", status), true);
        });
        registry.register(ActionIds.HUD_IMPACT_CONTROLLER_TOGGLE, ctx -> {
            ImpactHudController.INSTANCE.toggle();
            String status = ImpactHudController.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.impact.controller_status", status), true);
        });
        registry.register(ActionIds.HUD_IMPACT_3D_TOGGLE, ctx -> {
            ImpactHudController.INSTANCE.toggle3dPanels();
            String status = Impact3DPanelManager.INSTANCE.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.impact_3d_status", status), true);
        });
        registry.register(ActionIds.HUD_IMPACT_DISPLAY_MODE_CYCLE, ctx -> {
            ImpactHudController.INSTANCE.cycleDisplayMode();
            ImpactDisplayMode mode = ImpactHudController.INSTANCE.getDisplayMode();
            ctx.sendSuccess(I18n.translate("devmod.impact.display_mode", mode.getDisplayName()), true);
        });
        registry.register(ActionIds.HUD_IMPACT_PRESET_MINIMAL, ctx -> {
            ImpactHudController.INSTANCE.applyPreset("minimal");
            ctx.sendSuccess(I18n.translate("devmod.impact.preset_applied", "Minimal"), true);
        });
        registry.register(ActionIds.HUD_IMPACT_PRESET_DETAILED, ctx -> {
            ImpactHudController.INSTANCE.applyPreset("detailed");
            ctx.sendSuccess(I18n.translate("devmod.impact.preset_applied", "Detailed"), true);
        });
        registry.register(ActionIds.HUD_IMPACT_PRESET_TRAINING, ctx -> {
            ImpactHudController.INSTANCE.applyPreset("training");
            ctx.sendSuccess(I18n.translate("devmod.impact.preset_applied", "Training"), true);
        });
        registry.register(ActionIds.HUD_IMPACT_SHOW_RECAP, ctx -> {
            if (CombatSessionTracker.INSTANCE.hasData()) {
                CombatRecapScreen.open();
            } else {
                ctx.sendSuccess(I18n.translate("devmod.impact.no_combat_data"), true);
            }
        });
        registry.register(ActionIds.DEBUG_IMPACT_DISMISS, ctx -> {
            ImpactData.clear();
            Impact3DPanelManager.INSTANCE.clear();
            ImpactVFX.clear();
        });
        registry.register(ActionIds.DEBUG_SCREEN_SHAKE_TEST, ctx -> {
            Player player = ctx.getPlayer();
            if (player == null) return;
            com.devmod.client.effects.ShakeEffect testShake =
                com.devmod.client.effects.ShakeManager.createMediumHit(player.position());
            com.devmod.client.effects.ShakeManager.INSTANCE.addShake(testShake);
            ctx.sendSuccess(Component.literal("\u00A7e[DevMod] \u00A7fScreen shake test triggered! Active shakes: \u00A7a" +
                com.devmod.client.effects.ShakeManager.INSTANCE.getActiveCount()), true);
        });

        // HUD toggles
        registry.register(ActionIds.HUD_QUICK_HELP_TOGGLE, ctx -> {
            com.devmod.client.overlay.QuickHelpOverlay.toggle();
            String status = com.devmod.client.overlay.QuickHelpOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.quick_help_status", status), true);
        });
        registry.register(ActionIds.HUD_QUEST_TOGGLE, ctx -> {
            com.devmod.client.quest.QuestHudOverlay.toggle();
            String status = com.devmod.client.quest.QuestHudOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.quest_hud_status", status), true);
        });
        registry.register(ActionIds.HUD_ENDURANCE_TOGGLE, ctx -> {
            com.devmod.client.overlay.EnduranceQuestOverlay.toggle();
            String status = com.devmod.client.overlay.EnduranceQuestOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.endurance_hud_status", status), true);
        });
        registry.register(ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE, ctx -> {
            com.devmod.client.overlay.EnduranceQuestOverlay.toggleDetails();
            String detailStatus = com.devmod.client.overlay.EnduranceQuestOverlay.isShowingDetails() ? "\u00A7aDetailed" : "\u00A77Compact";
            ctx.sendSuccess(I18n.translate("devmod.render.endurance_details", detailStatus), true);
        });
        registry.register(ActionIds.HUD_PARTY_TOGGLE, ctx -> {
            com.devmod.client.overlay.PartyHudOverlay.toggle();
            String status = com.devmod.client.overlay.PartyHudOverlay.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
            ctx.sendSuccess(I18n.translate("devmod.render.party_hud_status", status), true);
        });

        // Context debug
        registry.register(ActionIds.DEBUG_COMBAT_RESET, ctx -> {
            ContextDetector.INSTANCE.reset();
            ctx.sendSuccess(I18n.translate("devmod.context.reset_success"), true);
        });
        registry.register(ActionIds.DEBUG_CONTEXT_STATUS, ctx -> {
            String debugInfo = java.util.Objects.requireNonNull(ContextDetector.INSTANCE.getDebugInfo());
            ctx.sendSuccess(Component.literal(debugInfo), false);
        });
    }

    // ── Spec helpers ──

    private static ActionSpec toggle(String id, ActionCategory category,
                                      String labelKey, String descKey,
                                      String icon, String menuPath) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(category)
            .actionType(ActionType.TOGGLE_SETTING)
            .ui(labelKey, descKey, icon, menuPath, true)
            .handlerRef(id)
            .build();
    }

    private static ActionSpec action(String id, ActionCategory category, ActionType type,
                                      String labelKey, String descKey,
                                      String icon, String menuPath) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(category)
            .actionType(type)
            .ui(labelKey, descKey, icon, menuPath, false)
            .handlerRef(id)
            .build();
    }

    // ── Handler helpers (replicate ClientDebugActions private methods) ──

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
        if (current == target) return;
        PacketDistributor.sendToServer(new DebugTogglePayload(feature.getId()));
    }

    private static void cycleHeatmaps(ActionContext context) {
        boolean shiftHeld = context.isShiftDown();
        boolean ctrlHeld = context.isCtrlDown();
        if (shiftHeld) { clearAllHeatmaps(context); return; }
        if (ctrlHeld) { clearCurrentHeatmap(context); return; }
        cycleHeatmapType();
        SettingsManager.INSTANCE.markDirty();
        int idx = currentHeatmapIndex.get();
        if (idx >= 0 && idx < HEATMAP_CYCLE.length) {
            HeatmapType currentType = HEATMAP_CYCLE[idx];
            int loaded = HeatmapVisualizer.INSTANCE.loadDataFromService(currentType);
            int total = HeatmapVisualizer.INSTANCE.getDataCount(currentType);
            String activeTypes = HeatmapVisualizer.INSTANCE.getActiveTypesString();
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
        if (!enable) currentHeatmapIndex.set(-1);
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
        for (HeatmapType type : HEATMAP_CYCLE) HeatmapVisualizer.INSTANCE.setEnabled(type, false);
        context.sendSuccess(I18n.translate("devmod.render.heatmaps_cleared"), true);
    }

    private static int heatmapIndex(HeatmapType type) {
        for (int i = 0; i < HEATMAP_CYCLE.length; i++) {
            if (HEATMAP_CYCLE[i] == type) return i;
        }
        return -1;
    }

    private static void cycleHeatmapType() {
        int idx = currentHeatmapIndex.get();
        if (idx >= 0 && idx < HEATMAP_CYCLE.length) HeatmapVisualizer.INSTANCE.setEnabled(HEATMAP_CYCLE[idx], false);
        idx++;
        if (idx >= HEATMAP_CYCLE.length) idx = -1;
        currentHeatmapIndex.set(idx);
        if (idx >= 0) HeatmapVisualizer.INSTANCE.setEnabled(HEATMAP_CYCLE[idx], true);
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
