package com.frenkvs.devmod.rendering;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.KeyInputHandler;
import com.frenkvs.devmod.util.I18n;
import com.frenkvs.devmod.attributes.AttributeMonitoringSystem;
import com.frenkvs.devmod.attributes.AttributeRayVisualizer;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.hud.BossPhaseOverlay;
import com.frenkvs.devmod.hud.EntityDensityOverlay;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactVFX;
import com.frenkvs.devmod.panels.context.ContextDetector;
import com.frenkvs.devmod.panels.core.FloatingPanelManager;
import com.frenkvs.devmod.panels.ui.PanelInteractionHandler;
import com.frenkvs.devmod.panels.ui.PanelRenderer;
import com.frenkvs.devmod.quest.QuestEditorScreen;
import com.frenkvs.devmod.quest.QuestHudOverlay;
import com.frenkvs.devmod.quest.QuestManager;
import com.frenkvs.devmod.quest.QuestTask;
import com.frenkvs.devmod.telemetry.FpsTracker;
import com.frenkvs.devmod.telemetry.PerformanceProfiler;
import com.frenkvs.devmod.testing.TestingSession;
import com.frenkvs.devmod.effects.TrailManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Objects;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class RenderEvents {

    // === Overlay Limit Warning System ===
    private static final int OVERLAY_WARNING_THRESHOLD = 5;
    private static final int OVERLAY_MAX_RECOMMENDED = 8;
    private static long lastOverlayWarningTime = 0;
    private static final long OVERLAY_WARNING_COOLDOWN_MS = 30000; // 30 seconds between warnings

    /**
     * Count active overlays and warn if too many are enabled.
     * Called after toggling any overlay.
     */
    private static void checkOverlayCount(net.minecraft.world.entity.player.Player player) {
        int activeCount = 0;

        // Count all active overlays
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

        // Show warning if too many active (with cooldown to avoid spam)
        long now = System.currentTimeMillis();
        if (activeCount >= OVERLAY_WARNING_THRESHOLD && (now - lastOverlayWarningTime) > OVERLAY_WARNING_COOLDOWN_MS) {
            lastOverlayWarningTime = now;

            if (activeCount >= OVERLAY_MAX_RECOMMENDED) {
                player.displayClientMessage(
                    I18n.translate("devmod.render.overlays_warning", activeCount),
                    false // Show in chat for visibility
                );
            } else {
                player.displayClientMessage(
                    I18n.translate("devmod.render.overlays_info", activeCount),
                    true // Action bar is fine for info level
                );
            }
        }
    }

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
            poseStack,
            bufferSource,
            event.getCamera().getPosition()
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
                poseStack,
                bufferSource,
                event.getCamera().getPosition()
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

        // Flush the buffer to ensure all lines are rendered
        bufferSource.endBatch();

        // Update counters for the profiler
        profiler.setCounter("3D Panels", Impact3DPanelManager.INSTANCE.getPanelCount());
        profiler.setCounter("Floating Panels", FloatingPanelManager.INSTANCE.getPanelCount());
        profiler.setCounter("Tracked Entities", AttributeMonitoringSystem.INSTANCE.getTrackedCount());
    }

    // Track if testing system initialization is allowed (after world is fully loaded)
    private static boolean testingSystemReady = false;
    private static int testingReadyDelayTicks = 0;
    private static final int TESTING_READY_DELAY = 100; // 5 seconds delay after player exists

    // Index for cycling heatmap types
    private static int currentHeatmapIndex = -1;
    private static final HeatmapVisualizer.HeatmapType[] HEATMAP_CYCLE = {
            HeatmapVisualizer.HeatmapType.DEATH,
            HeatmapVisualizer.HeatmapType.MOVEMENT,
            HeatmapVisualizer.HeatmapType.CAMPING,
            HeatmapVisualizer.HeatmapType.STUCK,
            HeatmapVisualizer.HeatmapType.AGGRO_DROP,
            HeatmapVisualizer.HeatmapType.KITING
    };

    /**
     * Handles all mod keybinds.
     * This is the CORRECT way according to NeoForge documentation:
     * https://docs.neoforged.net/docs/misc/keymappings
     */
    private static void handleKeyBindings(Minecraft mc) {
        var player = mc.player;
        if (player == null) return;

        // IMPORTANT: Don't process keybinds if a screen is already open
        // (except for toggles that don't open GUI)
        boolean screenOpen = mc.screen != null;

        // If you press K - Opens UnifiedSettingsScreen (PHASE 3: Unified Settings Panel)
        while (KeyInputHandler.OPEN_SETTINGS_KEY.consumeClick()) {
            if (!screenOpen) {
                mc.setScreen(new com.frenkvs.devmod.ui.unified.UnifiedSettingsScreen(null));
            }
        }

        // If you press M (and have something in hand) - Opens Weapon Editor (legacy GUI)
        while (KeyInputHandler.OPEN_WEAPON_EDITOR_KEY.consumeClick()) {
            if (!screenOpen) {
                if (!player.getMainHandItem().isEmpty()) {
                    mc.setScreen(new com.frenkvs.devmod.WeaponEditorScreen());
                } else {
                    player.displayClientMessage(
                            Objects.requireNonNull(Component.translatable("devmod.message.must_hold_item").withStyle(s -> s.withColor(0xFF5555))),
                            true
                    );
                }
            }
        }

        // If you press G (Toggle Debug Overlay) or Shift+G (Toggle Body Part Boxes)
        while (KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY.consumeClick()) {
            boolean shiftHeld = Screen.hasShiftDown();

            if (shiftHeld) {
                // Shift+G: Toggle Body Part Hitboxes
                com.frenkvs.devmod.ModConfig.showBodyPartBoxes = !com.frenkvs.devmod.ModConfig.showBodyPartBoxes;
                // Auto-enable showRender if enabling body part boxes
                if (com.frenkvs.devmod.ModConfig.showBodyPartBoxes && !com.frenkvs.devmod.ModConfig.showRender) {
                    com.frenkvs.devmod.ModConfig.showRender = true;
                }
                SettingsManager.INSTANCE.markDirty();
                String status = com.frenkvs.devmod.ModConfig.showBodyPartBoxes ? "§aON" : "§cOFF";
                player.displayClientMessage(
                        I18n.translate("devmod.render.body_parts_status", status),
                        true
                );
            } else {
                // G: Toggle Debug Overlay
                DebugRenderer.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = DebugRenderer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                player.displayClientMessage(
                        I18n.translate("devmod.render.debug_overlay_status", status),
                        true
                );
                checkOverlayCount(player);
            }
        }

        // PHASE 4: If you press L (Toggle Light Level Overlay)
        while (KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY.consumeClick()) {
            LightLevelOverlay.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = LightLevelOverlay.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.light_overlay_status", status),
                    true
            );
            checkOverlayCount(player);
        }

        // PHASE 4: If you press H (Cycle Heatmap Types)
        // Shift+H: Clear ALL heatmaps
        // Ctrl+H: Clear current heatmap type
        while (KeyInputHandler.TOGGLE_HEATMAP_KEY.consumeClick()) {
            boolean shiftHeld = Screen.hasShiftDown();
            boolean ctrlHeld = Screen.hasControlDown();

            if (shiftHeld) {
                // Shift+H: Clear ALL heatmaps
                HeatmapVisualizer.INSTANCE.clearAll();
                currentHeatmapIndex = -1;
                // Disable all visualizations
                for (HeatmapVisualizer.HeatmapType type : HEATMAP_CYCLE) {
                    HeatmapVisualizer.INSTANCE.setEnabled(type, false);
                }
                player.displayClientMessage(
                        I18n.translate("devmod.render.heatmaps_cleared"),
                        true
                );
            } else if (ctrlHeld) {
                // Ctrl+H: Clear current heatmap type
                if (currentHeatmapIndex >= 0 && currentHeatmapIndex < HEATMAP_CYCLE.length) {
                    HeatmapVisualizer.HeatmapType currentType = HEATMAP_CYCLE[currentHeatmapIndex];
                    HeatmapVisualizer.INSTANCE.clear(currentType);
                    int remaining = HeatmapVisualizer.INSTANCE.getDataCount(currentType);
                    player.displayClientMessage(
                            I18n.translate("devmod.render.heatmap_type_cleared", currentType.name(), remaining),
                            true
                    );
                } else {
                    player.displayClientMessage(
                            I18n.translate("devmod.render.no_heatmap_selected"),
                            true
                    );
                }
            } else {
                // H: Cycle heatmap type + load data from service
                cycleHeatmapType();
                SettingsManager.INSTANCE.markDirty();

                // Load data from service for the current type
                if (currentHeatmapIndex >= 0 && currentHeatmapIndex < HEATMAP_CYCLE.length) {
                    HeatmapVisualizer.HeatmapType currentType = HEATMAP_CYCLE[currentHeatmapIndex];
                    int loaded = HeatmapVisualizer.INSTANCE.loadDataFromService(currentType);
                    int total = HeatmapVisualizer.INSTANCE.getDataCount(currentType);
                    String activeTypes = HeatmapVisualizer.INSTANCE.getActiveTypesString();
                    player.displayClientMessage(
                            I18n.translate("devmod.render.heatmap_status", activeTypes, total),
                            true
                    );
                } else {
                    player.displayClientMessage(
                            I18n.translate("devmod.render.heatmap_off"),
                            true
                    );
                }
            }
        }

        // PHASE 4: If you press R (Toggle Room Bounds Visualizer)
        // Shift+R: Open Room Bounds Editor (in-game UI)
        // Ctrl+R: Reload rooms from config file
        while (KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY.consumeClick()) {
            boolean shiftHeld = Screen.hasShiftDown();
            boolean ctrlHeld = Screen.hasControlDown();

            if (shiftHeld && !screenOpen) {
                // Shift+R: Open Room Bounds Editor
                mc.setScreen(new com.frenkvs.devmod.ui.RoomBoundsEditorScreen());
            } else if (ctrlHeld) {
                // Ctrl+R: Reload rooms from config
                RoomBoundsVisualizer.INSTANCE.reload();
                int roomCount = RoomBoundsVisualizer.INSTANCE.getRoomCount();
                player.displayClientMessage(
                        I18n.translate("devmod.render.room_bounds_reloaded", roomCount),
                        true
                );
            } else {
                // R: Toggle Room Bounds Visualizer
                RoomBoundsVisualizer.INSTANCE.toggle();
                SettingsManager.INSTANCE.markDirty();
                String status = RoomBoundsVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
                int roomCount = RoomBoundsVisualizer.INSTANCE.getRoomCount();
                int gapCount = RoomBoundsVisualizer.INSTANCE.getGapCount();
                String gapInfo = gapCount > 0 ? " §c" + gapCount + " gaps!" : " §a0 gaps";
                player.displayClientMessage(
                        I18n.translate("devmod.render.room_bounds_status", status, roomCount, gapInfo),
                        true
                );
            }
        }

        // PHASE 4: If you press P (Toggle Pathfinding Debugger)
        while (KeyInputHandler.TOGGLE_PATHFINDING_KEY.consumeClick()) {
            PathfindingDebugger.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = PathfindingDebugger.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.pathfinding_status", status),
                    true
            );
        }

        // PHASE 4: If you press V (Toggle Line of Sight Visualizer)
        while (KeyInputHandler.TOGGLE_LOS_KEY.consumeClick()) {
            LineOfSightVisualizer.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = LineOfSightVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.los_status", status),
                    true
            );
        }

        // PHASE 4: If you press Y (Toggle Vertical Levels Visualizer)
        while (KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY.consumeClick()) {
            VerticalLevelsVisualizer.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = VerticalLevelsVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.vertical_status", status),
                    true
            );
        }

        // PHASE 4: If you press C (Toggle Safe Spot Visualizer)
        while (KeyInputHandler.TOGGLE_SAFE_SPOT_KEY.consumeClick()) {
            SafeSpotVisualizer.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = SafeSpotVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            int spotCount = SafeSpotVisualizer.INSTANCE.getSafeSpotCount();
            player.displayClientMessage(
                    I18n.translate("devmod.render.safe_spots_status", status, spotCount),
                    true
            );
        }

        // J key - Opens the Telemetry Dashboard
        while (KeyInputHandler.OPEN_DASHBOARD_KEY.consumeClick()) {
            if (!screenOpen) {
                mc.setScreen(new com.frenkvs.devmod.TelemetryDashboardScreen(null));
            }
        }

        // PHASE 7: If you press U (Toggle Attribute Monitoring System)
        while (KeyInputHandler.TOGGLE_ATTRIBUTE_MONITOR_KEY.consumeClick()) {
            AttributeMonitoringSystem.INSTANCE.toggle();
            String status = AttributeMonitoringSystem.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            int trackedCount = AttributeMonitoringSystem.INSTANCE.getTrackedCount();
            player.displayClientMessage(
                    I18n.translate("devmod.render.attr_monitor_status", status, trackedCount),
                    true
            );
        }

        // PHASE 8: If you press F8 (Toggle FPS Tracker)
        while (KeyInputHandler.TOGGLE_FPS_TRACKER_KEY.consumeClick()) {
            FpsTracker.INSTANCE.toggle();
            String status = FpsTracker.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.fps_tracker_status", status),
                    true
            );
        }

        // PHASE 9: If you press F9 (Toggle Performance Profiler)
        while (KeyInputHandler.TOGGLE_PROFILER_KEY.consumeClick()) {
            PerformanceProfiler.INSTANCE.toggle();
            String status = PerformanceProfiler.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.profiler_status", status),
                    true
            );
        }

        // QA TESTING: If you press N (Open QA Testing Screen) - LEGACY, now use F7 for Testing Hub
        while (KeyInputHandler.OPEN_QA_TESTING_KEY.consumeClick()) {
            DevMod.LOGGER.info("[DevMod] N key consumed - screenOpen={}", screenOpen);
            if (!screenOpen) {
                DevMod.LOGGER.info("[DevMod] Opening Testing Hub...");
                try {
                    mc.setScreen(new com.frenkvs.devmod.ui.hub.TestingHub());
                    DevMod.LOGGER.info("[DevMod] Testing Hub opened successfully");
                } catch (Exception e) {
                    DevMod.LOGGER.error("[DevMod] Error opening QA Testing: {}", e.getMessage(), e);
                    player.displayClientMessage(
                            I18n.translate("devmod.message.error_opening_qa").append(": " + e.getMessage()),
                            false
                    );
                }
            }
        }

        // TESTING HUB: If you press F7 (Open Testing Hub - unified interface)
        // If the hub is minimized, it restores it instead of creating a new one
        while (KeyInputHandler.OPEN_TESTING_HUB_KEY.consumeClick()) {
            if (!screenOpen) {
                try {
                    // Check if hub was minimized - if so, restore it
                    if (com.frenkvs.devmod.ui.hub.TestingHubState.INSTANCE.isMinimized()) {
                        com.frenkvs.devmod.ui.hub.TestingHub.restoreFromHud();
                    } else {
                        mc.setScreen(new com.frenkvs.devmod.ui.hub.TestingHub());
                    }
                } catch (Exception e) {
                    DevMod.LOGGER.error("[DevMod] Error opening Testing Hub: {}", e.getMessage(), e);
                    player.displayClientMessage(
                            I18n.translate("devmod.message.error_opening_hub").append(": " + e.getMessage()),
                            false
                    );
                }
            }
        }

        // VOXEL-LAB: If you press B (Toggle Boss Phase Overlay)
        while (KeyInputHandler.TOGGLE_BOSS_PHASE_KEY.consumeClick()) {
            BossPhaseOverlay.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = BossPhaseOverlay.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.boss_phase_status", status),
                    true
            );
        }

        // VOXEL-LAB: If you press F6 (Toggle Entity Density Overlay)
        while (KeyInputHandler.TOGGLE_ENTITY_DENSITY_KEY.consumeClick()) {
            EntityDensityOverlay.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = EntityDensityOverlay.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.entity_density_status", status),
                    true
            );
        }

        // VOXEL-LAB: If you press F5 (Toggle Skill Efficacy Overlay)
        while (KeyInputHandler.TOGGLE_SKILL_EFFICACY_KEY.consumeClick()) {
            com.frenkvs.devmod.hud.SkillEfficacyOverlay.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = com.frenkvs.devmod.hud.SkillEfficacyOverlay.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.skill_efficacy_status", status),
                    true
            );
        }

        // VOXEL-LAB M27: If you press F4 (Toggle Spawnability Map)
        while (KeyInputHandler.TOGGLE_SPAWNABILITY_KEY.consumeClick()) {
            SpawnabilityOverlay.INSTANCE.toggle();
            SettingsManager.INSTANCE.markDirty();
            String status = SpawnabilityOverlay.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            var stats = SpawnabilityOverlay.INSTANCE.getStats();
            player.displayClientMessage(
                    I18n.translate("devmod.render.spawnability_status", status, stats.hostileSpawnBlocks()),
                    true
            );
        }

        // QUEST HUD: If you press \ (Toggle Quest HUD)
        while (KeyInputHandler.TOGGLE_QUEST_HUD_KEY.consumeClick()) {
            QuestHudOverlay.toggle();
            String status = QuestHudOverlay.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.quest_hud_status", status),
                    true
            );
        }

        // QUEST HUD: If you press ] (Complete current task)
        while (KeyInputHandler.QUEST_COMPLETE_TASK_KEY.consumeClick()) {
            QuestTask task = QuestManager.INSTANCE.getCurrentTask();
            if (task != null) {
                String taskName = task.getDescription();
                QuestManager.INSTANCE.completeCurrentTask();
                player.displayClientMessage(
                        Objects.requireNonNull(I18n.translate("devmod.message.task_completed").withStyle(s -> s.withColor(0x55FF55))
                                .append(I18n.translate("devmod.ui.colon_value", taskName).withStyle(s -> s.withColor(0xFFFFFF)))),
                        true
                );
            } else {
                player.displayClientMessage(
                        Objects.requireNonNull(Component.translatable("devmod.message.no_active_task").withStyle(s -> s.withColor(0xAAAAAA))),
                        true
                );
            }
        }

        // QUEST EDITOR: If you press [ (Open Quest Editor)
        while (KeyInputHandler.OPEN_QUEST_EDITOR_KEY.consumeClick()) {
            if (!screenOpen) {
                mc.setScreen(new QuestEditorScreen());
            }
        }

        // ECONOMY: If you press F3 (Toggle Economy Overlay)
        // Shift+F3 cycles the view (economy stats <-> mob loot)
        // Ctrl+F3 cycles the sort mode (kills, drop%, recent)
        while (KeyInputHandler.TOGGLE_ECONOMY_KEY.consumeClick()) {
            boolean shiftHeld = Screen.hasShiftDown();
            boolean ctrlHeld = Screen.hasControlDown();

            if (ctrlHeld && com.frenkvs.devmod.hud.EconomyOverlay.isEnabled()) {
                // Ctrl+F3: cycle the sort mode
                com.frenkvs.devmod.hud.EconomyOverlay.cycleSortMode();
                String sortName = com.frenkvs.devmod.hud.EconomyOverlay.getSortModeName();
                player.displayClientMessage(
                        I18n.translate("devmod.render.economy_sort", sortName),
                        true
                );
            } else if (shiftHeld && com.frenkvs.devmod.hud.EconomyOverlay.isEnabled()) {
                // Shift+F3: cycle the view
                com.frenkvs.devmod.hud.EconomyOverlay.cycleView();
                String viewName = com.frenkvs.devmod.hud.EconomyOverlay.getViewModeName();
                player.displayClientMessage(
                        I18n.translate("devmod.render.economy_view", viewName),
                        true
                );
            } else {
                // F3 normal: toggle overlay
                com.frenkvs.devmod.hud.EconomyOverlay.toggle();
                String status = com.frenkvs.devmod.hud.EconomyOverlay.isEnabled() ? "§aON" : "§cOFF";
                player.displayClientMessage(
                        I18n.translate("devmod.render.economy_status", status),
                        true
                );
            }
        }

        // ECONOMY SCROLL: Page Up/Down to scroll the mob list
        if (com.frenkvs.devmod.hud.EconomyOverlay.isEnabled()) {
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_PAGEUP)) {
                com.frenkvs.devmod.hud.EconomyOverlay.scrollUp();
            }
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_PAGEDOWN)) {
                com.frenkvs.devmod.hud.EconomyOverlay.scrollDown();
            }
        }

        // CHUNK PERF: If you press F2 (Toggle Chunk Performance Visualizer)
        while (KeyInputHandler.TOGGLE_CHUNK_PERF_KEY.consumeClick()) {
            ChunkPerformanceVisualizer.INSTANCE.toggle();
            String status = ChunkPerformanceVisualizer.INSTANCE.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                    I18n.translate("devmod.render.chunk_perf_status", status),
                    true
            );
        }

        // ENDURANCE QUEST: If you press F10 (Open Quest Editor with Endurance Modal)
        // Shift+F10: Toggle Endurance HUD visibility
        // Ctrl+F10: Toggle Endurance HUD details
        while (KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY.consumeClick()) {
            boolean shiftHeld = Screen.hasShiftDown();
            boolean ctrlHeld = Screen.hasControlDown();

            if (shiftHeld) {
                // Shift+F10: Toggle Endurance HUD visibility
                com.frenkvs.devmod.hud.EnduranceQuestOverlay.toggle();
                String status = com.frenkvs.devmod.hud.EnduranceQuestOverlay.isEnabled() ? "§aON" : "§cOFF";
                player.displayClientMessage(
                        I18n.translate("devmod.render.endurance_hud_status", status),
                        true
                );
            } else if (ctrlHeld) {
                // Ctrl+F10: Toggle details view
                com.frenkvs.devmod.hud.EnduranceQuestOverlay.toggleDetails();
                String detailStatus = com.frenkvs.devmod.hud.EnduranceQuestOverlay.isShowingDetails() ? "§aDetailed" : "§7Compact";
                player.displayClientMessage(
                        I18n.translate("devmod.render.endurance_details", detailStatus),
                        true
                );
            } else if (!screenOpen) {
                // F10: Open Quest Editor with Endurance modal
                try {
                    mc.setScreen(new com.frenkvs.devmod.quest.QuestEditorScreen(true));
                } catch (Exception e) {
                    DevMod.LOGGER.error("[DevMod] Error opening Endurance Quest: {}", e.getMessage(), e);
                    player.displayClientMessage(
                            I18n.translate("devmod.render.error_opening_quest", e.getMessage()),
                            false
                    );
                }
            }
        }

        // F11: Continue/Respawn in Endurance Quest
        while (KeyInputHandler.QUEST_CONTINUE_KEY.consumeClick()) {
            // Send action based on quest state
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.frenkvs.devmod.endurance.QuestActionPayload(
                    com.frenkvs.devmod.endurance.QuestActionPayload.Action.CONTINUE_AFTER_DEATH
                )
            );
            player.displayClientMessage(
                    I18n.translate("devmod.network.continuing_quest"),
                    true
            );
        }

        // F12: Exit/Give Up in Endurance Quest (with confirmation dialog)
        while (KeyInputHandler.QUEST_EXIT_KEY.consumeClick()) {
            // Open confirmation screen instead of directly exiting
            mc.setScreen(new com.frenkvs.devmod.endurance.QuestExitConfirmScreen(mc.screen));
        }

        // F1: Toggle Quick Help Overlay
        while (KeyInputHandler.TOGGLE_HELP_KEY.consumeClick()) {
            com.frenkvs.devmod.hud.QuickHelpOverlay.toggle();
            String status = com.frenkvs.devmod.hud.QuickHelpOverlay.isEnabled() ? "§aON" : "§cOFF";
            player.displayClientMessage(
                I18n.translate("devmod.render.quick_help_status", status),
                true
            );
        }

        // ` (BACKTICK): Open Radial Menu - PRIMARY ACCESS to all DevMod tools
        while (KeyInputHandler.OPEN_RADIAL_MENU_KEY.consumeClick()) {
            if (!screenOpen) {
                mc.setScreen(new com.frenkvs.devmod.ui.radial.RadialMenuScreenV3());
            }
        }

        // INSPECT MOB: Open MobConfigScreen for the mob being looked at
        while (KeyInputHandler.INSPECT_MOB_KEY.consumeClick()) {
            if (!screenOpen) {
                if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
                    if (entityHit.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
                        mc.setScreen(new com.frenkvs.devmod.MobConfigScreen(mob));
                    } else {
                        // Looking at entity but not a Mob (e.g., player, item frame)
                        player.displayClientMessage(
                            I18n.translate("devmod.render.target_not_mob"),
                            true
                        );
                    }
                } else {
                    // Not looking at any entity
                    player.displayClientMessage(
                        I18n.translate("devmod.render.no_entity_targeted"),
                        true
                    );
                }
            }
        }

        // TEST SCREEN SHAKE: If you press 0 (Test shake effect)
        // Debug: check if keybind is detected at all using raw GLFW check
        long windowHandle = mc.getWindow().getWindow();
        boolean zeroKeyDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_0);
        if (zeroKeyDown) {
            DevMod.LOGGER.info("[DevMod] KEY_0 is DOWN (raw GLFW check)");
        }

        while (KeyInputHandler.TEST_SCREEN_SHAKE_KEY.consumeClick()) {
            DevMod.LOGGER.info("[DevMod] Screen shake keybind consumed!");
            // Create a test shake at player position
            com.frenkvs.devmod.effects.ShakeEffect testShake =
                com.frenkvs.devmod.effects.ShakeManager.createMediumHit(player.position());
            com.frenkvs.devmod.effects.ShakeManager.INSTANCE.addShake(testShake);
            DevMod.LOGGER.info("[DevMod] Shake added, isEnabled={}, activeCount={}",
                com.frenkvs.devmod.effects.ShakeManager.INSTANCE.isEnabled(),
                com.frenkvs.devmod.effects.ShakeManager.INSTANCE.getActiveCount());
            player.displayClientMessage(
                Component.literal("§e[DevMod] §fScreen shake test triggered! Active shakes: §a" +
                    com.frenkvs.devmod.effects.ShakeManager.INSTANCE.getActiveCount()),
                true
            );
        }

        // ESC handling for onboarding tutorial skip
        // Use InputConstants for reliable key detection
        // NOTE: Check isActive() first, regardless of screenOpen state
        // The tutorial overlay is a HUD, not a screen, so ESC should work even with no screen open
        if (com.frenkvs.devmod.hud.OnboardingOverlay.isActive()) {
            long escWindowHandle = mc.getWindow().getWindow();
            boolean escPressed = InputConstants.isKeyDown(escWindowHandle, InputConstants.KEY_ESCAPE);

            if (escPressed && !escWasPressed) {
                // Use handleEscape() which properly handles the skip logic and returns success state
                com.frenkvs.devmod.hud.OnboardingOverlay.handleEscape();
            }
            escWasPressed = escPressed;
        } else {
            escWasPressed = false;
        }
    }

    // Debounce state for ESC key in tutorial
    private static boolean escWasPressed = false;

    private static void cycleHeatmapType() {
        // Disable the current type
        if (currentHeatmapIndex >= 0 && currentHeatmapIndex < HEATMAP_CYCLE.length) {
            HeatmapVisualizer.INSTANCE.setEnabled(HEATMAP_CYCLE[currentHeatmapIndex], false);
        }

        // Move to the next (or return to -1 = all off)
        currentHeatmapIndex++;
        if (currentHeatmapIndex >= HEATMAP_CYCLE.length) {
            currentHeatmapIndex = -1; // All off
        }

        // Enable the new type
        if (currentHeatmapIndex >= 0) {
            HeatmapVisualizer.INSTANCE.setEnabled(HEATMAP_CYCLE[currentHeatmapIndex], true);
        }
    }

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

        // === KEYBIND HANDLING (correct way for NeoForge) ===
        handleKeyBindings(mc);

        PerformanceProfiler profiler = PerformanceProfiler.INSTANCE;

        // Update the 3D panel manager
        long t0 = profiler.startTiming("PanelManager.tick");
        Impact3DPanelManager.INSTANCE.clientTick();
        profiler.endTiming("PanelManager.tick", t0);

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

        // === PHASE 2 UX: Tick Floating Panels and Context Detector ===
        long t2 = profiler.startTiming("FloatingPanels.tick");
        FloatingPanelManager.INSTANCE.tick();
        ContextDetector.INSTANCE.tick();

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
        if (!com.frenkvs.devmod.testing.stats.DamageStatistics.INSTANCE.isDirty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastDamageStatsSaveTime >= DAMAGE_STATS_SAVE_INTERVAL_MS) {
            com.frenkvs.devmod.testing.stats.DamageStatistics.INSTANCE.save();
            lastDamageStatsSaveTime = now;
        }
    }
}
