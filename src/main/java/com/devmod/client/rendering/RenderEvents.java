package com.devmod.client.rendering;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.AABB;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.devmod.DevMod;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.attributes.AttributeMonitoringSystem;
import com.devmod.client.attributes.AttributeRayVisualizer;
import com.devmod.client.collision.rendering.OBBDebugRenderer;
import com.devmod.client.combat.WeaponTrailVFX;
import com.devmod.client.compat.mods.epicfight.ClientEpicFightCache;
import com.devmod.client.effects.TrailManager;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.ImpactVFX;
import com.devmod.client.panels.context.ContextDetector;
import com.devmod.client.panels.core.FloatingPanelManager;
import com.devmod.client.panels.ui.PanelInteractionHandler;
import com.devmod.client.rendering.shield.EnergyShieldRenderer;
import com.devmod.client.telemetry.PerformanceProfiler;
import com.devmod.client.testing.TestingSession;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.config.Config;
import com.devmod.config.handler.impl.ArmorConfigHandler;
import com.devmod.stats.ArmorStats;
import com.devmod.util.I18n;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class RenderEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderEvents.class);

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PerformanceProfiler profiler = PerformanceProfiler.INSTANCE;

        // Update mob info overlay
        long t0 = profiler.startTiming("MobDebugOverlay");
        MobDebugOverlay.renderMobInfo();
        profiler.endTiming("MobDebugOverlay", t0);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Render all debug shapes
        long t1 = profiler.startTiming("DebugRenderer");
        DebugRenderer.INSTANCE.render(
            poseStack,
            bufferSource,
            event.getCamera().getPosition()
        );
        profiler.endTiming("DebugRenderer", t1);

        // Render impact VFX (marker 3D, slash animation, connection lines)
        long t2 = profiler.startTiming("ImpactVFX");
        ImpactVFX.render(
            poseStack,
            bufferSource,
            event.getCamera().getPosition()
        );
        profiler.endTiming("ImpactVFX", t2);

        // === NEW: Render Impact 3D Panels ===
        long t3 = profiler.startTiming("Impact3DPanels");
        Impact3DPanelManager.INSTANCE.renderAllPanels(
            poseStack,
            bufferSource,
            event.getCamera(),
            event.getPartialTick().getGameTimeDeltaPartialTick(true)
        );
        profiler.endTiming("Impact3DPanels", t3);

        // === PHASE 7: Render Attribute Monitoring 3D Rays ===
        long t4 = profiler.startTiming("AttributeRays");
        AttributeRayVisualizer.INSTANCE.render(
            Objects.requireNonNull(poseStack),
            Objects.requireNonNull(bufferSource),
            Objects.requireNonNull(event.getCamera().getPosition())
        );
        profiler.endTiming("AttributeRays", t4);

        // === PHASE 2 UX: Render Floating Panels (Context-Aware) ===
        long t5 = profiler.startTiming("FloatingPanels");
        FloatingPanelManager.INSTANCE.render(
            poseStack,
            bufferSource,
            event.getCamera(),
            event.getPartialTick().getGameTimeDeltaPartialTick(true)
        );
        profiler.endTiming("FloatingPanels", t5);

        // === PHASE 4: Pathfinding Debugger (P key) ===
        if (PathfindingDebugger.INSTANCE.isEnabled()) {
            long t6 = profiler.startTiming("PathfindingDebugger");
            PathfindingDebugger.INSTANCE.render(
                Objects.requireNonNull(poseStack),
                Objects.requireNonNull(bufferSource),
                Objects.requireNonNull(event.getCamera().getPosition())
            );
            profiler.endTiming("PathfindingDebugger", t6);
        }

        // === Projectile Trails (Perception-style effect) ===
        if (TrailManager.INSTANCE.isEnabled()) {
            long t7 = profiler.startTiming("ProjectileTrails");
            TrailManager.INSTANCE.render(
                poseStack,
                bufferSource,
                event.getCamera().getPosition()
            );
            profiler.endTiming("ProjectileTrails", t7);
        }

        // === Energy Shield Rendering (for players using shields) ===
        long t8 = profiler.startTiming("EnergyShields");
        renderEnergyShields(poseStack, bufferSource, event.getCamera().getPosition(),
            event.getPartialTick().getGameTimeDeltaPartialTick(true));
        profiler.endTiming("EnergyShields", t8);

        // === Weapon Trail VFX (sword swing trails) ===
        if (WeaponTrailVFX.INSTANCE.isEnabled()) {
            long t9 = profiler.startTiming("WeaponTrailVFX");
            WeaponTrailVFX.INSTANCE.render(
                poseStack,
                bufferSource,
                event.getCamera().getPosition()
            );
            profiler.endTiming("WeaponTrailVFX", t9);
        }

        // === OBB Debug Rendering (when body part boxes enabled + OBB system active) ===
        if (com.devmod.ModConfig.isShowBodyPartBoxes() && isOBBSystemEnabled()) {
            long t10 = profiler.startTiming("OBBDebugRenderer");
            renderOBBHitboxes(poseStack, bufferSource, event.getCamera().getPosition());
            profiler.endTiming("OBBDebugRenderer", t10);
        }

        // Flush the buffer to ensure all lines are rendered
        bufferSource.endBatch();

        // Update counters for the profiler
        profiler.setCounter("3D Panels", Impact3DPanelManager.INSTANCE.getPanelCount());
        profiler.setCounter("Impact3D Full", Impact3DPanelManager.INSTANCE.getLastRenderedFull());
        profiler.setCounter("Impact3D Compact", Impact3DPanelManager.INSTANCE.getLastRenderedCompact());
        profiler.setCounter("Impact3D Minimal", Impact3DPanelManager.INSTANCE.getLastRenderedMinimal());
        profiler.setCounter("Impact3D Cache Hit", Impact3DPanelManager.INSTANCE.getLastCacheHits());
        profiler.setCounter("Impact3D Cache Miss", Impact3DPanelManager.INSTANCE.getLastCacheMisses());
        profiler.setCounter("Floating Panels", FloatingPanelManager.INSTANCE.getPanelCount());
        profiler.setCounter("Tracked Entities", AttributeMonitoringSystem.INSTANCE.getTrackedCount());
    }

    // Track if testing system initialization is allowed (after world is fully loaded)
    private static boolean testingSystemReady = false;
    private static int testingReadyDelayTicks = 0;
    private static final int TESTING_READY_DELAY = 100; // 5 seconds delay after player exists

    /**
     * Handles all mod keybinds.
     * This is the CORRECT way according to NeoForge documentation:
     * https://docs.neoforged.net/docs/misc/keymappings
     */
    private static void handleKeyBindings(Minecraft mc) {
        if (mc.player == null) return;

        while (KeyInputHandler.OPEN_SETTINGS_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_SETTINGS_OPEN);
        }

        while (KeyInputHandler.OPEN_WEAPON_EDITOR_KEY.consumeClick()) {
            ActionContext context = ClientActionContexts.forKeybind();
            if (context.isCtrlDown()) {
                invokeAction(ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR, context);
            } else if (context.isShiftDown()) {
                invokeAction(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON, context);
            } else {
                invokeAction(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO, context);
            }
        }

        while (KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY.consumeClick()) {
            ActionContext context = ClientActionContexts.forKeybind();
            if (context.isShiftDown()) {
                invokeAction(ActionIds.DEBUG_BODY_PARTS_TOGGLE, context);
            } else {
                invokeAction(ActionIds.DEBUG_OVERLAY_TOGGLE, context);
            }
        }

        while (KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_HEATMAP_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_HEATMAP_CYCLE);
        }

        while (KeyInputHandler.DISMISS_IMPACT_HUD_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_IMPACT_DISMISS);
        }

        while (KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY.consumeClick()) {
            ActionContext context = ClientActionContexts.forKeybind();
            if (context.isShiftDown()) {
                invokeAction(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN, context);
            } else if (context.isCtrlDown()) {
                invokeAction(ActionIds.DEBUG_ROOM_BOUNDS_RELOAD, context);
            } else {
                invokeAction(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE, context);
            }
        }

        while (KeyInputHandler.TOGGLE_PATHFINDING_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_PATHFINDING_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_LOS_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_LOS_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_SAFE_SPOT_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE);
        }

        while (KeyInputHandler.OPEN_DASHBOARD_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN);
        }

        while (KeyInputHandler.TOGGLE_ATTRIBUTE_MONITOR_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_FPS_TRACKER_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_FPS_TRACKER_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_PROFILER_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_PROFILER_TOGGLE);
        }

        while (KeyInputHandler.OPEN_QA_TESTING_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_QA_TESTING_OPEN);
        }

        while (KeyInputHandler.OPEN_TESTING_HUB_KEY.consumeClick()) {
            ActionContext context = ClientActionContexts.forKeybind();
            if (context.isShiftDown()) {
                invokeAction(ActionIds.ARENA_HUD_TOGGLE, context);
            } else {
                invokeAction(ActionIds.UI_TESTING_HUB_OPEN, context);
            }
        }

        while (KeyInputHandler.TOGGLE_BOSS_PHASE_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_BOSS_PHASE_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_ENTITY_DENSITY_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_SKILL_EFFICACY_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_SPAWNABILITY_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_SPAWNABILITY_TOGGLE);
        }

        while (KeyInputHandler.TOGGLE_QUEST_HUD_KEY.consumeClick()) {
            invokeAction(ActionIds.HUD_QUEST_TOGGLE);
        }

        while (KeyInputHandler.QUEST_COMPLETE_TASK_KEY.consumeClick()) {
            invokeAction(ActionIds.QUEST_TASK_COMPLETE);
        }

        while (KeyInputHandler.OPEN_QUEST_EDITOR_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_QUEST_EDITOR_OPEN);
        }

        while (KeyInputHandler.TOGGLE_ECONOMY_KEY.consumeClick()) {
            ActionContext context = ClientActionContexts.forKeybind();
            if (context.isCtrlDown()) {
                invokeAction(ActionIds.DEBUG_ECONOMY_SORT_CYCLE, context);
            } else if (context.isShiftDown()) {
                invokeAction(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE, context);
            } else {
                invokeAction(ActionIds.DEBUG_ECONOMY_TOGGLE, context);
            }
        }

        if (com.devmod.client.overlay.EconomyOverlay.isEnabled()) {
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_PAGEUP)) {
                com.devmod.client.overlay.EconomyOverlay.scrollUp();
            }
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_PAGEDOWN)) {
                com.devmod.client.overlay.EconomyOverlay.scrollDown();
            }
        }

        while (KeyInputHandler.TOGGLE_CHUNK_PERF_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_CHUNK_PERF_TOGGLE);
        }

        while (KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY.consumeClick()) {
            ActionContext context = ClientActionContexts.forKeybind();
            if (context.isShiftDown()) {
                invokeAction(ActionIds.HUD_ENDURANCE_TOGGLE, context);
            } else if (context.isCtrlDown()) {
                invokeAction(ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE, context);
            } else {
                invokeAction(ActionIds.UI_ENDURANCE_EDITOR_OPEN, context);
            }
        }

        while (KeyInputHandler.QUEST_CONTINUE_KEY.consumeClick()) {
            invokeAction(ActionIds.ENDURANCE_QUEST_CONTINUE);
        }

        while (KeyInputHandler.QUEST_EXIT_KEY.consumeClick()) {
            invokeAction(ActionIds.ENDURANCE_QUEST_EXIT);
        }

        while (KeyInputHandler.TOGGLE_HELP_KEY.consumeClick()) {
            invokeAction(ActionIds.HUD_QUICK_HELP_TOGGLE);
        }

        while (KeyInputHandler.OPEN_RADIAL_MENU_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_RADIAL_OPEN);
        }

        while (KeyInputHandler.INSPECT_MOB_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_MOB_CONFIG_OPEN);
        }

        while (KeyInputHandler.TEST_SCREEN_SHAKE_KEY.consumeClick()) {
            invokeAction(ActionIds.DEBUG_SCREEN_SHAKE_TEST);
        }

        while (KeyInputHandler.DASH_KEY.consumeClick()) {
            invokeAction(ActionIds.ABILITY_DASH);
        }

        while (KeyInputHandler.DODGE_KEY.consumeClick()) {
            invokeAction(ActionIds.ABILITY_DODGE);
        }

        while (KeyInputHandler.OPEN_PARTY_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_PARTY_OPEN);
        }

        while (KeyInputHandler.OPEN_NOTIFICATION_CENTER_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_NOTIFICATION_CENTER_OPEN);
        }

        while (KeyInputHandler.OPEN_MAILBOX_KEY.consumeClick()) {
            invokeAction(ActionIds.UI_MAILBOX_OPEN);
        }

        if (com.devmod.client.overlay.OnboardingOverlay.isActive()) {
            long escWindowHandle = mc.getWindow().getWindow();
            boolean escPressed = InputConstants.isKeyDown(escWindowHandle, InputConstants.KEY_ESCAPE);

            if (escPressed && !escWasPressed) {
                invokeAction(ActionIds.UI_ONBOARDING_SKIP);
            }
            escWasPressed = escPressed;
        } else {
            escWasPressed = false;
        }
    }

    private static void invokeAction(String actionId) {
        ActionRegistry.invoke(actionId, ClientActionContexts.forKeybind());
    }

    private static void invokeAction(String actionId, ActionContext context) {
        ActionRegistry.invoke(actionId, context);
    }

    // Debounce state for ESC key in tutorial
    private static boolean escWasPressed = false;

    /**
     * Client tick event to update 3D panels and the monitoring system.
     * NOTE: This is the CORRECT way to handle keybinds according to NeoForge documentation.
     * DO NOT use InputEvent.Key - always use ClientTickEvent.Post with consumeClick().
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // Reset when leaving world
            testingSystemReady = false;
            testingReadyDelayTicks = 0;
            return;
        }

        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen
            && com.devmod.client.endurance.ClientQuestCache.shouldSuppressVanillaDeathScreen()) {
            LOGGER.info("[EnduranceQuest][Client] Replacing vanilla DeathScreen with QuestDeathScreen");
            com.devmod.client.ui.ScreenSafety.openSafe(
                "quest_death",
                () -> new com.devmod.client.endurance.QuestDeathScreen());
        }

        // === KEYBIND HANDLING (correct way for NeoForge) ===
        handleKeyBindings(mc);

        PerformanceProfiler profiler = PerformanceProfiler.INSTANCE;

        // Update the 3D panel manager
        long t0 = profiler.startTiming("PanelManager.tick");
        Impact3DPanelManager.INSTANCE.clientTick();
        profiler.endTiming("PanelManager.tick", t0);

        ImpactData.clientTickCleanup();
        com.devmod.client.collision.transform.ModelPartTransformCapture.clientTick();

        // PHASE 7: Update the attribute monitoring system
        long t1 = profiler.startTiming("AttrMonitor.tick");
        AttributeMonitoringSystem.INSTANCE.tick();
        profiler.endTiming("AttrMonitor.tick", t1);

        // NOTE: PathfindingDebugger tracking is now done automatically in its render() method
        // This prevents duplicate tracking and keeps the code DRY

        // PHASE 9: Update the profiler itself
        profiler.onClientTick();

        // === Projectile Trails (Perception-style effect) ===
        long t3 = profiler.startTiming("TrailManager.tick");
        TrailManager.INSTANCE.tick();
        profiler.endTiming("TrailManager.tick", t3);

        // === Weapon Trail VFX (sword swing trails) ===
        long t4 = profiler.startTiming("WeaponTrailVFX.tick");
        WeaponTrailVFX.INSTANCE.tick();
        profiler.endTiming("WeaponTrailVFX.tick", t4);

        // === PHASE 2 UX: Tick Floating Panels and Context Detector ===
        long t2 = profiler.startTiming("FloatingPanels.tick");
        FloatingPanelManager.INSTANCE.tick();
        ContextDetector.INSTANCE.tick();

        // === Epic Fight Integration: Update client cache ===
        if (ClientEpicFightCache.shouldUpdate()) {
            ClientEpicFightCache.INSTANCE.updateFromLocalPlayer(mc.player);
            ClientEpicFightCache.INSTANCE.tickAnimations(1.0f);
        }

        // Update panel hover based on mouse position
        if (mc.screen == null) { // Only if no GUI is open
            double mouseX = mc.mouseHandler.xpos();
            double mouseY = mc.mouseHandler.ypos();
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            PanelInteractionHandler.INSTANCE.updateHover(mouseX, mouseY, screenW, screenH);
        } else {
            PanelInteractionHandler.INSTANCE.clearHover();
        }

        // Display feedback message from panel interaction (e.g., "Pin panel first to drag")
        String feedbackMsg = PanelInteractionHandler.INSTANCE.getFeedbackMessage();
        if (feedbackMsg != null && mc.player != null) {
            mc.player.displayClientMessage(
                I18n.translate("devmod.panel.feedback", feedbackMsg),
                true  // actionbar (above hotbar)
            );
            PanelInteractionHandler.INSTANCE.clearFeedback(); // Clear after displaying
        }
        profiler.endTiming("FloatingPanels.tick", t2);

        // QA Testing: Background auto-validation tick
        // Delay initialization until world is stable to prevent blocking
        if (!testingSystemReady) {
            testingReadyDelayTicks++;
            if (testingReadyDelayTicks >= TESTING_READY_DELAY) {
                testingSystemReady = true;
            }
        }

        // Only tick testing system after world is stable
        if (testingSystemReady) {
            try {
                TestingSession.INSTANCE.clientTick();
            } catch (Exception e) {
                // Don't let testing system crashes affect the game
            }
        }

        // === FASE 5: Auto-save settings periodically when dirty ===
        tickSettingsAutoSave();

        // === P0-4: Auto-save damage statistics periodically ===
        tickDamageStatsAutoSave();
    }

    // Auto-save settings tracking
    private static long lastSettingsSaveTime = 0;
    private static final long SETTINGS_SAVE_INTERVAL_MS = 30_000; // Save every 30 seconds if dirty

    // Auto-save damage statistics tracking
    private static long lastDamageStatsSaveTime = 0;
    private static final long DAMAGE_STATS_SAVE_INTERVAL_MS = 60_000; // Save every 60 seconds if dirty

    /**
     * Periodically saves settings if they are marked as dirty.
     * This ensures changes from keybind toggles are persisted.
     */
    private static void tickSettingsAutoSave() {
        if (!SettingsManager.INSTANCE.isDirty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSettingsSaveTime >= SETTINGS_SAVE_INTERVAL_MS) {
            SettingsManager.INSTANCE.save();
            lastSettingsSaveTime = now;
        }
    }

    /**
     * Periodically saves damage statistics if they are marked as dirty.
     * This prevents data loss from crashes during long combat sessions.
     */
    private static void tickDamageStatsAutoSave() {
        if (!com.devmod.testing.stats.DamageStatistics.INSTANCE.isDirty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastDamageStatsSaveTime >= DAMAGE_STATS_SAVE_INTERVAL_MS) {
            com.devmod.testing.stats.DamageStatistics.INSTANCE.save();
            lastDamageStatsSaveTime = now;
        }
    }

    /**
     * Renders energy shields for all nearby players who are actively blocking with a shield.
     * Uses ArmorStats to determine shield visual properties (color, opacity, glow).
     */
    private static void renderEnergyShields(PoseStack poseStack, MultiBufferSource bufferSource,
                                             net.minecraft.world.phys.Vec3 cameraPos, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        // Render shields for all players in render distance
        for (Player player : level.players()) {
            // Check if player is blocking with a shield
            if (!player.isBlocking()) continue;

            ItemStack useItem = player.getUseItem();
            if (useItem.isEmpty() || !(useItem.getItem() instanceof ShieldItem)) continue;

            // Get shield visual properties from ArmorStats
            ArmorStats stats = ArmorConfigHandler.INSTANCE.getStats(useItem);

            // Only render if shield has visual properties enabled (opacity > 0)
            if (stats.getShieldOpacity() <= 0.01f) continue;

            // Render the energy shield
            EnergyShieldRenderer.render(
                poseStack,
                bufferSource,
                player,
                stats.getShieldColor(),
                stats.getShieldOpacity(),
                1.2f, // Default shield radius
                partialTick,
                cameraPos
            );
        }
    }

    /**
     * Checks if the OBB hitbox system is enabled in config.
     * Safe method that won't throw if config is not yet loaded.
     */
    private static boolean isOBBSystemEnabled() {
        try {
            return Config.OBB_HITBOX_ENABLED.get();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Renders OBB hitboxes for all nearby living entities.
     * Only called when both showBodyPartBoxes and OBB system are enabled.
     */
    private static void renderOBBHitboxes(PoseStack poseStack, MultiBufferSource bufferSource,
                                          net.minecraft.world.phys.Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) return;

        // Get nearby living entities within render distance
        AABB searchArea = Objects.requireNonNull(player.getBoundingBox().inflate(32.0)); // 32 block radius
        var nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, searchArea,
            entity -> !Objects.equals(entity, player) && entity.isAlive());

        // Render OBB hitboxes for each entity
        OBBDebugRenderer.renderNearbyEntityOBBs(
            Objects.requireNonNull(poseStack),
            Objects.requireNonNull(bufferSource),
            Objects.requireNonNull(cameraPos),
            Objects.requireNonNull(nearbyEntities));
    }
}
