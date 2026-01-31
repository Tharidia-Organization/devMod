package com.devmod.client.endurance.wis;
import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.endurance.PerkSelectionScreen;
import com.devmod.client.endurance.WaveDirectiveScreen;
import com.devmod.client.endurance.wis.ui.DebriefScreen;
import com.devmod.client.endurance.wis.ui.MinimalCombatHUD;
import com.devmod.client.endurance.wis.ui.PreWaveBriefOverlay;

/**
 * Handles registration and rendering of Wave Intelligence System overlays.
 * Integrates with NeoForge's GUI layer system.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class WISOverlayHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WISOverlayHandler.class);

    private static final ResourceLocation WIS_LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "wave_intelligence");

    // Track if we need to open debrief screen
    private static boolean pendingDebriefOpen = false;
    private static long pendingDebriefRequestedAt = 0L;

    private static final long DEBRIEF_FORCE_OPEN_DELAY_MS = 1500;

    // Track if checkpoint screen should open after debrief closes
    private static boolean pendingCheckpointAfterDebrief = false;

    /**
     * Register the WIS overlay layer.
     */
    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(Objects.requireNonNull(VanillaGuiLayers.BOSS_OVERLAY),
            Objects.requireNonNull(WIS_LAYER_ID), WISOverlayHandler::renderWISOverlay);
    }

    /**
     * Main render method for WIS overlays.
     */
    private static void renderWISOverlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui || mc.screen != null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        WavePhase phase = WaveIntelligenceManager.INSTANCE.getCurrentPhase();

        switch (phase) {
            case PRE_BRIEF -> {
                if (PreWaveBriefOverlay.shouldRender()) {
                    PreWaveBriefOverlay.render(graphics, screenWidth, screenHeight);
                }
            }
            case COMBAT -> {
                if (MinimalCombatHUD.shouldRender()) {
                    MinimalCombatHUD.render(graphics, screenWidth, screenHeight);
                }
            }
            case DEBRIEF -> {
                // Debrief is a full screen, not an overlay
                // It will be opened via openDebriefScreen()
            }
            case IDLE -> {
                // Nothing to render
            }
        }
    }

    /**
     * Client tick event handler for WIS updates.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Tick the WIS manager
        WaveIntelligenceManager.INSTANCE.tick();

        // Check if we need to open debrief screen
        if (pendingDebriefOpen) {
            openDebriefIfReady(mc);
        }

        // Check if we need to open checkpoint screen after debrief
        // This runs when debrief was shown and user closed it
        if (pendingCheckpointAfterDebrief && mc.screen == null) {
            WavePhase phase = WaveIntelligenceManager.INSTANCE.getCurrentPhase();
            // Only open checkpoint when WIS has transitioned back to IDLE (debrief dismissed)
            if (phase == WavePhase.IDLE) {
                LOGGER.info("[WIS] Opening checkpoint screen after debrief");
                pendingCheckpointAfterDebrief = false;
                ActionRegistry.invoke(ActionIds.UI_WAVE_CHECKPOINT_OPEN,
                    ClientActionContexts.forClient(ActionOrigin.EVENT));
            }
        }
    }

    /**
     * Request opening the debrief screen.
     * Will be opened on next tick when no screen is active.
     */
    public static void requestDebriefScreen() {
        pendingDebriefOpen = true;
        pendingDebriefRequestedAt = System.currentTimeMillis();
    }

    /**
     * Force open the debrief screen immediately.
     */
    public static void openDebriefScreenNow() {
        WaveTelemetryCollector collector = ensureCollectorAvailable();
        if (collector != null) {
            com.devmod.client.ui.ScreenSafety.openSafe(
                "debrief",
                () -> new DebriefScreen(collector));
        }
    }

    /**
     * Check if WIS overlays are currently being rendered.
     */
    public static boolean isRenderingWIS() {
        WavePhase phase = WaveIntelligenceManager.INSTANCE.getCurrentPhase();
        return phase == WavePhase.PRE_BRIEF || phase == WavePhase.COMBAT;
    }

    /**
     * Request opening the checkpoint screen after debrief closes.
     * This enables the Debrief -> Checkpoint sequential flow.
     */
    public static void requestCheckpointAfterDebrief() {
        pendingCheckpointAfterDebrief = true;
    }

    /**
     * Check if checkpoint screen is pending after debrief.
     */
    public static boolean isCheckpointPending() {
        return pendingCheckpointAfterDebrief;
    }

    /**
     * Reset all pending screen state.
     * Called when quest ends or player disconnects.
     */
    public static void reset() {
        pendingDebriefOpen = false;
        pendingCheckpointAfterDebrief = false;
        pendingDebriefRequestedAt = 0L;
    }

    private static void openDebriefIfReady(Minecraft mc) {
        WaveTelemetryCollector collector = ensureCollectorAvailable();
        long elapsed = pendingDebriefRequestedAt > 0L
            ? System.currentTimeMillis() - pendingDebriefRequestedAt
            : 0L;

        Screen current = mc.screen;
        boolean canOpen = current == null;

        if (!canOpen && elapsed >= DEBRIEF_FORCE_OPEN_DELAY_MS && canInterruptScreen(current)) {
            mc.setScreen(null);
            canOpen = mc.screen == null;
        }

        if (canOpen && collector != null) {
            com.devmod.client.ui.ScreenSafety.openSafe(
                "debrief",
                () -> new DebriefScreen(collector));
            pendingDebriefOpen = false;
            pendingDebriefRequestedAt = 0L;
        }
    }

    private static boolean canInterruptScreen(@Nullable Screen screen) {
        if (screen == null) {
            return true;
        }
        return !(screen instanceof ChatScreen)
            && !(screen instanceof PerkSelectionScreen)
            && !(screen instanceof WaveDirectiveScreen);
    }

    @Nullable
    private static WaveTelemetryCollector ensureCollectorAvailable() {
        WaveTelemetryCollector collector = WaveIntelligenceManager.INSTANCE.getCurrentCollector();
        if (collector != null) {
            return collector;
        }

        int waveNumber = WaveIntelligenceManager.INSTANCE.getCurrentWaveNumber();
        if (waveNumber <= 0) {
            waveNumber = ClientQuestCache.getCurrentWave();
        }
        if (waveNumber <= 0) {
            waveNumber = 1;
        }

        WaveIntelligenceManager.INSTANCE.ensureCollectorExists(waveNumber);
        return WaveIntelligenceManager.INSTANCE.getCurrentCollector();
    }
}
