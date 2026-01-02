package com.devmod.client.debug;

import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneLayout;
import com.devmod.arena.zone.ZoneTransition;
import com.devmod.client.debug.effects.GapTransitionEffect;
import com.devmod.client.debug.effects.GradientTransitionEffect;
import com.devmod.client.debug.effects.HardTransitionEffect;
import com.devmod.client.debug.effects.TransitionEffect;
import com.devmod.client.debug.effects.WallTransitionEffect;

/**
 * Renders zone transition effects for arena boundaries.
 * Coordinates different visual effects based on transition type.
 *
 * Supports "Full Effects" mode with:
 * - Fog gradients at boundaries
 * - Ambient particles
 * - Pulsing glow effects
 *
 * Usage:
 * - Enable via /devmod zone transitions command
 * - Automatically syncs with ZoneDebugRenderer state
 */
public final class ZoneTransitionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneTransitionRenderer.class);

    private ZoneTransitionRenderer() {} // Utility class

    // Effects registry - all four transition types
    private static final Map<ZoneTransition.TransitionType, TransitionEffect> EFFECTS = Map.of(
        ZoneTransition.TransitionType.HARD, new HardTransitionEffect(),
        ZoneTransition.TransitionType.GRADIENT, new GradientTransitionEffect(),
        ZoneTransition.TransitionType.WALL, new WallTransitionEffect(),
        ZoneTransition.TransitionType.GAP, new GapTransitionEffect()
    );

    // Current state
    private static boolean enabled = false;
    private static @Nullable ZoneLayout currentLayout = null;
    private static int baseY = 64;
    private static ZoneTransition.TransitionType currentTransitionType = ZoneTransition.TransitionType.HARD;

    // Render time tracking for animations
    private static float renderTime = 0f;

    // ============== Public API ==============

    /**
     * Enables or disables zone transition rendering.
     */
    public static void setEnabled(boolean value) {
        enabled = value;
        LOGGER.info("Zone transition rendering: {}", value ? "ENABLED" : "DISABLED");
    }

    /**
     * Returns whether transition rendering is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the current zone layout for rendering.
     */
    public static void setZoneLayout(@Nullable ZoneLayout layout, int floorY) {
        currentLayout = layout;
        baseY = floorY;

        // Update transition type from layout
        if (layout != null && layout.defaultTransition() != null) {
            currentTransitionType = layout.defaultTransition().type();
        }
    }

    /**
     * Sets the transition type to use for rendering.
     */
    public static void setTransitionType(ZoneTransition.TransitionType type) {
        currentTransitionType = type;
        LOGGER.debug("Transition type set to: {}", type);
    }

    /**
     * Clears the current zone layout.
     */
    public static void clearZoneLayout() {
        currentLayout = null;
    }

    /**
     * Main render method - called from level render event after ZoneDebugRenderer.
     *
     * @param poseStack The pose stack for transforms
     * @param bufferSource Buffer source for rendering
     * @param cameraPos Camera position for offset
     * @param partialTick Partial tick for smooth animation
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                               Vec3 cameraPos, float partialTick) {
        ZoneLayout layout = currentLayout;
        if (!enabled || layout == null) {
            return;
        }

        // Update render time for animations
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            renderTime = (mc.level.getGameTime() + partialTick) * 0.05f;
        }

        TransitionEffect effect = EFFECTS.get(currentTransitionType);
        if (effect == null) {
            effect = EFFECTS.get(ZoneTransition.TransitionType.HARD); // Default fallback
        }
        if (effect == null) {
            return; // No effects available
        }

        try {
            final TransitionEffect effectToUse = effect;
            for (ArenaZone zone : layout.zones()) {
                effectToUse.render(poseStack, bufferSource, cameraPos, zone, baseY, renderTime);
            }
        } catch (Exception e) {
            LOGGER.warn("Error rendering zone transitions: {}", e.getMessage());
        }
    }

    /**
     * Syncs state from ZoneDebugRenderer.
     * Called when debug rendering state changes.
     */
    public static void syncFromDebugRenderer() {
        // Automatically enable transitions when debug rendering is enabled
        if (ZoneDebugRenderer.isEnabled()) {
            enabled = true;
        }
    }

    /**
     * Gets the current transition effect.
     */
    @Nullable
    public static TransitionEffect getCurrentEffect() {
        return EFFECTS.get(currentTransitionType);
    }

    /**
     * Gets the available transition types.
     */
    public static ZoneTransition.TransitionType[] getAvailableTypes() {
        return EFFECTS.keySet().toArray(new ZoneTransition.TransitionType[0]);
    }
}
