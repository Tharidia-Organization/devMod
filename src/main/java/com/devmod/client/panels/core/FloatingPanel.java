package com.devmod.client.panels.core;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.panels.tracking.EntityTracker;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Abstract base class for all floating panels in the 3D world.
 *
 * A FloatingPanel is a UI element that:
 * - Exists in the 3D world (not on the 2D screen)
 * - Can track an entity or have a fixed position
 * - Has a lifecycle with spawn/despawn animations
 * - Can be pinned, minimized, dragged
 * - Always faces the camera (billboard)
 */
public abstract class FloatingPanel {

    // === Identificazione ===
    protected final UUID id;
    protected final PanelType type;

    // === Tracking ===
    @Nullable
    protected EntityTracker tracker;
    protected Vec3 fixedPosition;

    // === Lifecycle ===
    protected PanelState state = PanelState.SPAWNING;
    protected long stateStartTime;
    protected long spawnTime;

    // === Visual State ===
    protected float currentAlpha = 0f;
    protected float currentScale = 0.8f;
    protected boolean pinned = false;
    protected boolean minimized = false;
    protected boolean hovered = false;

    // === Layout ===
    protected int width;
    protected int height;

    // === Animation Constants ===
    private static final float SPAWN_SCALE_START = 0.8f;
    private static final float SPAWN_SCALE_END = 1.0f;
    private static final float DESPAWN_SCALE_END = 0.9f;

    /**
     * Create a panel with entity tracking.
     */
    protected FloatingPanel(PanelType type, EntityTracker tracker) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.tracker = tracker;
        this.fixedPosition = null;
        this.width = type.getDefaultWidth();
        this.height = type.getDefaultHeight();
        this.spawnTime = System.currentTimeMillis();
        this.stateStartTime = spawnTime;
    }

    /**
     * Create a panel with fixed position.
     */
    protected FloatingPanel(PanelType type, Vec3 fixedPosition) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.tracker = null;
        this.fixedPosition = fixedPosition;
        this.width = type.getDefaultWidth();
        this.height = type.getDefaultHeight();
        this.spawnTime = System.currentTimeMillis();
        this.stateStartTime = spawnTime;
    }

    // === Abstract Methods ===

    /**
     * Render the internal content of the panel (2D version for GUI).
     * Called with local coordinates (0,0 is the top-left corner of content).
     *
     * @param graphics Rendering context
     * @param contentWidth Available width for content
     * @param contentHeight Available height for content
     */
    public abstract void renderContent(GuiGraphics graphics, int contentWidth, int contentHeight);

    /**
     * Render the internal content of the panel in the 3D world.
     * Override this method for custom 3D rendering.
     * Default: uses font to render basic text.
     *
     * @param poseStack Transform stack
     * @param bufferSource Buffer for rendering
     * @param font Font for text
     * @param contentWidth Available width
     * @param contentHeight Available height
     * @param alpha Current panel alpha
     */
    public void renderContent3D(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource bufferSource, @Nonnull Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        // Default implementation - panels can override for custom content
        // Renders basic info as text
        renderText3D(poseStack, bufferSource, font, getTitle(), 0, 0, applyAlpha(0xFFFFFFFF, alpha));
    }

    /**
     * Get the panel title (shown in header).
     */
    @Nonnull
    public abstract String getTitle();

    // === 3D Rendering Helpers ===

    /**
     * Helper per renderizzare testo 3D.
     */
    protected void renderText3D(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource bufferSource, @Nonnull Font font,
                                 @Nonnull String text, float x, float y, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        font.drawInBatch(
            text, 0, 0, color, false,
            Objects.requireNonNull(poseStack.last().pose()), bufferSource,
            Font.DisplayMode.SEE_THROUGH, 0, 15728880
        );
        poseStack.popPose();
    }

    /**
     * Apply alpha to an ARGB color.
     */
    protected int applyAlpha(int argb, float alphaMultiplier) {
        int originalAlpha = (argb >> 24) & 0xFF;
        if (originalAlpha == 0) originalAlpha = 255;
        int newAlpha = (int) (originalAlpha * alphaMultiplier);
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Update the internal state of the panel.
     * Called every client tick.
     */
    public void tick() {
        // Update tracker if present
        EntityTracker localTracker = this.tracker;
        if (localTracker != null) {
            localTracker.tick();

            // If the target is no longer valid, start despawn
            if (!localTracker.isValid() && state != PanelState.DESPAWNING && state != PanelState.REMOVED) {
                startDespawn();
            }
        }

        // Lifecycle management
        updateLifecycle();

        // Auto-expire if configured
        if (type.hasAutoExpire() && !pinned) {
            long age = System.currentTimeMillis() - spawnTime;
            if (age > type.getAutoExpireMs() && state == PanelState.VISIBLE) {
                startDespawn();
            }
        }
    }

    /**
     * Update animations and state transitions.
     */
    protected void updateLifecycle() {
        long stateTime = System.currentTimeMillis() - stateStartTime;
        long duration = state.getTransitionDuration();

        switch (state) {
            case SPAWNING -> {
                if (duration > 0) {
                    float progress = Math.min(1.0f, (float) stateTime / duration);
                    currentAlpha = easeOutCubic(progress);
                    currentScale = lerp(SPAWN_SCALE_START, SPAWN_SCALE_END, easeOutBack(progress));
                } else {
                    currentAlpha = 1.0f;
                    currentScale = 1.0f;
                }

                if (stateTime >= duration) {
                    transitionTo(PanelState.VISIBLE);
                }
            }

            case VISIBLE -> {
                currentAlpha = 1.0f;
                currentScale = 1.0f;
            }

            case MINIMIZED -> {
                currentAlpha = 0.7f;
                currentScale = 1.0f;
            }

            case DESPAWNING -> {
                if (duration > 0) {
                    float progress = Math.min(1.0f, (float) stateTime / duration);
                    currentAlpha = 1.0f - easeInCubic(progress);
                    currentScale = lerp(1.0f, DESPAWN_SCALE_END, progress);
                }

                if (stateTime >= duration) {
                    transitionTo(PanelState.REMOVED);
                }
            }

            case REMOVED -> {
                currentAlpha = 0f;
                currentScale = 0f;
            }
        }
    }

    // === State Transitions ===

    /**
     * Transition to a new state.
     */
    protected void transitionTo(PanelState newState) {
        if (this.state != newState) {
            this.state = newState;
            this.stateStartTime = System.currentTimeMillis();
            onStateChanged(newState);
        }
    }

    /**
     * Callback for state change (override for custom behaviors).
     */
    protected void onStateChanged(PanelState newState) {
        // Override in subclasses if needed
    }

    /**
     * Start the despawn sequence.
     */
    public void startDespawn() {
        if (state != PanelState.DESPAWNING && state != PanelState.REMOVED) {
            transitionTo(PanelState.DESPAWNING);
        }
    }

    /**
     * Immediately close the panel.
     */
    public void close() {
        transitionTo(PanelState.REMOVED);
    }

    // === Position ===

    /**
     * Get the current position of the panel in the world.
     */
    @Nonnull
    public Vec3 getWorldPosition() {
        EntityTracker localTracker = this.tracker;
        if (localTracker != null && localTracker.isValid()) {
            return Objects.requireNonNull(localTracker.getSmoothedPosition(), "smoothedPosition");
        }
        return fixedPosition != null ? fixedPosition : Objects.requireNonNull(Vec3.ZERO, "Vec3.ZERO");
    }

    /**
     * Set a new fixed position (removes tracking).
     */
    public void setFixedPosition(Vec3 position) {
        this.fixedPosition = position;
        this.tracker = null;
    }

    // === Pin/Minimize ===

    /**
     * Toggle pin state.
     */
    public void togglePin() {
        if (type.canPin()) {
            this.pinned = !this.pinned;
        }
    }

    /**
     * Toggle minimized state.
     */
    public void toggleMinimize() {
        if (state == PanelState.VISIBLE) {
            transitionTo(PanelState.MINIMIZED);
            minimized = true;
        } else if (state == PanelState.MINIMIZED) {
            transitionTo(PanelState.VISIBLE);
            minimized = false;
        }
    }

    // === Interaction ===

    /**
     * Handle a click on the panel.
     *
     * @param localX X coordinate relative to panel
     * @param localY Y coordinate relative to panel
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true if the click was handled
     */
    public boolean handleClick(int localX, int localY, int button) {
        if (!state.isInteractive()) return false;

        // Right click = toggle pin
        if (button == 1 && type.canPin()) {
            togglePin();
            return true;
        }

        // Left click on header = toggle minimize
        if (button == 0 && localY < 16) {
            toggleMinimize();
            return true;
        }

        return false;
    }

    /**
     * Update hover state.
     */
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    // === Easing Functions ===

    protected static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    protected static float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    protected static float easeInCubic(float t) {
        return t * t * t;
    }

    protected static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2));
    }

    // === Getters ===

    public UUID getId() {
        return id;
    }

    public PanelType getType() {
        return type;
    }

    public PanelState getState() {
        return state;
    }

    public float getCurrentAlpha() {
        return currentAlpha;
    }

    public float getCurrentScale() {
        return currentScale;
    }

    public boolean isPinned() {
        return pinned;
    }

    public boolean isMinimized() {
        return minimized;
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean isExpired() {
        return state == PanelState.REMOVED;
    }

    public boolean shouldRender() {
        return state.shouldRender() && currentAlpha > 0.01f;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return minimized ? 16 : height;
    }

    public long getAge() {
        return System.currentTimeMillis() - spawnTime;
    }

    @Nullable
    public EntityTracker getTracker() {
        return tracker;
    }

    @Override
    public String toString() {
        return String.format("FloatingPanel[id=%s, type=%s, state=%s, pos=%s]",
            id.toString().substring(0, 8), type, state, getWorldPosition());
    }
}
