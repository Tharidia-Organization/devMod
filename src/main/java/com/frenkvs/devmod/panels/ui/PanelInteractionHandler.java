package com.frenkvs.devmod.panels.ui;

import com.frenkvs.devmod.panels.core.FloatingPanel;
import com.frenkvs.devmod.panels.core.FloatingPanelManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Handles mouse interactions with floating panels in the 3D world.
 *
 * Features:
 * - Ray casting from mouse to find panels under cursor
 * - Click handling (left/right/middle)
 * - Hover detection
 * - Drag support (future)
 */
public class PanelInteractionHandler {

    public static final PanelInteractionHandler INSTANCE = new PanelInteractionHandler();

    // Interaction state
    @Nullable
    private FloatingPanel hoveredPanel = null;
    @Nullable
    private FloatingPanel draggedPanel = null;
    private boolean isDragging = false;

    // Feedback for drag attempt on unpinned panel
    @Nullable
    private String feedbackMessage = null;
    private long feedbackDisplayTime = 0;
    private static final long FEEDBACK_DURATION_MS = 2000;

    // Configuration
    private static final double MAX_INTERACTION_DISTANCE = 20.0;
    private static final float PANEL_HITBOX_SCALE = 1.2f; // Hitbox slightly larger

    private PanelInteractionHandler() {}

    /**
     * Updates hover state based on mouse position.
     * Call every frame.
     *
     * @param mouseX Mouse X position on screen
     * @param mouseY Mouse Y position on screen
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     */
    public void updateHover(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clearHover();
            return;
        }

        // If dragging, don't change hover
        if (isDragging) return;

        Camera camera = mc.gameRenderer.getMainCamera();

        // Find the panel under the mouse
        FloatingPanel newHovered = findPanelUnderMouse(mouseX, mouseY, screenWidth, screenHeight, camera);

        // Update hover state
        if (newHovered != hoveredPanel) {
            if (hoveredPanel != null) {
                hoveredPanel.setHovered(false);
            }
            hoveredPanel = newHovered;
            if (hoveredPanel != null) {
                hoveredPanel.setHovered(true);
            }
        }
    }

    /**
     * Finds the panel under the mouse position.
     */
    @Nullable
    private FloatingPanel findPanelUnderMouse(double mouseX, double mouseY,
                                               int screenWidth, int screenHeight,
                                               Camera camera) {
        Vec3 cameraPos = Objects.requireNonNull(camera.getPosition());
        List<FloatingPanel> panels = FloatingPanelManager.INSTANCE.getAllPanels();

        // Convert mouse position to ray direction
        Vec3 rayDir = screenToWorldRay(mouseX, mouseY, screenWidth, screenHeight, camera);
        if (rayDir == null) return null;

        FloatingPanel closest = null;
        double closestDist = Double.MAX_VALUE;

        for (FloatingPanel panel : panels) {
            if (!panel.shouldRender() || !panel.getState().isInteractive()) continue;

            Vec3 panelPos = panel.getWorldPosition();
            double distance = panelPos.distanceTo(cameraPos);

            if (distance > MAX_INTERACTION_DISTANCE) continue;

            // Simplified intersection test (sphere around panel)
            double hitRadius = estimatePanelRadius(panel, distance) * PANEL_HITBOX_SCALE;

            if (rayIntersectsSphere(cameraPos, rayDir, Objects.requireNonNull(panelPos), hitRadius)) {
                if (distance < closestDist) {
                    closestDist = distance;
                    closest = panel;
                }
            }
        }

        return closest;
    }

    /**
     * Converts mouse position to ray direction in world.
     * Simplification: uses camera direction.
     */
    @Nullable
    private Vec3 screenToWorldRay(double mouseX, double mouseY,
                                   int screenWidth, int screenHeight,
                                   Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return null;

        // Normalize mouse coordinates (-1 to 1)
        float normalizedX = (float) (2.0 * mouseX / screenWidth - 1.0);
        float normalizedY = (float) (1.0 - 2.0 * mouseY / screenHeight);

        // Simplification: uses player's look direction with offset
        // For precise interaction, would need inverse projection matrix
        Vec3 lookDir = Objects.requireNonNull(player.getLookAngle());

        // Calculate right and up vectors
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = Objects.requireNonNull(lookDir.cross(worldUp).normalize());
        Vec3 up = Objects.requireNonNull(right.cross(lookDir).normalize());

        // FOV adjustment
        float fov = (float) Math.toRadians(mc.options.fov().get());
        float aspectRatio = (float) screenWidth / screenHeight;

        float tanHalfFov = (float) Math.tan(fov / 2);
        float offsetX = normalizedX * tanHalfFov * aspectRatio;
        float offsetY = normalizedY * tanHalfFov;

        // Ray direction
        return Objects.requireNonNull(lookDir.add(Objects.requireNonNull(right.scale(offsetX))).add(Objects.requireNonNull(up.scale(offsetY))).normalize());
    }

    /**
     * Estimates the panel radius in world units based on distance.
     */
    private double estimatePanelRadius(FloatingPanel panel, double distance) {
        // The panel scales with distance, so the effective radius
        // depends on dimensions and scale
        float baseScale = 0.015f; // From PanelRenderer
        int width = panel.getWidth();
        int height = panel.getHeight();

        // Use diagonal as approximate radius
        double diagonal = Math.sqrt(width * width + height * height);
        return diagonal * baseScale * 0.5;
    }

    /**
     * Ray-sphere intersection test.
     */
    private boolean rayIntersectsSphere(@Nonnull Vec3 rayOrigin, @Nonnull Vec3 rayDir, @Nonnull Vec3 sphereCenter, double radius) {
        Vec3 oc = Objects.requireNonNull(rayOrigin.subtract(sphereCenter));

        double a = rayDir.dot(rayDir);
        double b = 2.0 * oc.dot(rayDir);
        double c = oc.dot(oc) - radius * radius;

        double discriminant = b * b - 4 * a * c;
        return discriminant >= 0;
    }

    /**
     * Handles a mouse click.
     *
     * @param button Button (0=left, 1=right, 2=middle)
     * @return true if the click was handled
     */
    public boolean handleClick(int button) {
        FloatingPanel localHovered = this.hoveredPanel;
        if (localHovered == null) return false;

        // Calculate approximate local coordinates (center of panel)
        int localX = localHovered.getWidth() / 2;
        int localY = localHovered.getHeight() / 2;

        return localHovered.handleClick(localX, localY, button);
    }

    // Drag state
    private Vec3 dragStartPanelPos = null;
    private double dragStartMouseX = 0;
    private double dragStartMouseY = 0;

    /**
     * Handles the start of a drag.
     */
    public boolean handleDragStart(int button) {
        FloatingPanel localHovered = this.hoveredPanel;
        if (button != 0 || localHovered == null) return false;

        // Only pinned panels can be dragged
        if (!localHovered.isPinned()) {
            // Provide feedback to user explaining why drag doesn't work
            showFeedback("Pin panel first (right-click) to enable dragging");
            return false;
        }

        draggedPanel = localHovered;
        isDragging = true;
        dragStartPanelPos = localHovered.getWorldPosition();

        Minecraft mc = Minecraft.getInstance();
        dragStartMouseX = mc.mouseHandler.xpos();
        dragStartMouseY = mc.mouseHandler.ypos();

        return true;
    }

    /**
     * Shows a temporary feedback message to the user.
     */
    private void showFeedback(String message) {
        this.feedbackMessage = message;
        this.feedbackDisplayTime = System.currentTimeMillis();
    }

    /**
     * Handles movement during a drag.
     * Moves the panel in 3D world based on mouse movement.
     */
    public void handleDragMove(double mouseX, double mouseY) {
        FloatingPanel localDraggedPanel = this.draggedPanel;
        Vec3 localDragStartPos = this.dragStartPanelPos;
        if (!isDragging || localDraggedPanel == null || localDragStartPos == null) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        // Calculate mouse delta in normalized coordinates
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        double deltaX = (mouseX - dragStartMouseX) / screenWidth;
        double deltaY = (mouseY - dragStartMouseY) / screenHeight;

        // Scale movement based on panel distance
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = Objects.requireNonNull(camera.getPosition());
        double distance = localDragStartPos.distanceTo(cameraPos);

        // Movement proportional to distance (far panels move more)
        double movementScale = distance * 0.5;

        // Calculate right and up vectors from camera
        Vec3 lookDir = Objects.requireNonNull(player.getLookAngle());
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = Objects.requireNonNull(lookDir.cross(worldUp).normalize());
        Vec3 up = Objects.requireNonNull(right.cross(lookDir).normalize());

        // New position = initial position + offset
        Vec3 offset = Objects.requireNonNull(Objects.requireNonNull(right.scale(deltaX * movementScale)).add(Objects.requireNonNull(up.scale(-deltaY * movementScale))));
        Vec3 newPos = Objects.requireNonNull(localDragStartPos.add(offset));

        // Apply the new position
        localDraggedPanel.setFixedPosition(newPos);
    }

    /**
     * Handles the end of a drag.
     */
    public void handleDragEnd() {
        isDragging = false;
        draggedPanel = null;
        dragStartPanelPos = null;
    }

    /**
     * Clears the hover state.
     */
    public void clearHover() {
        if (hoveredPanel != null) {
            hoveredPanel.setHovered(false);
            hoveredPanel = null;
        }
    }

    /**
     * Complete state reset.
     */
    public void reset() {
        clearHover();
        handleDragEnd();
    }

    // === Getters ===

    @Nullable
    public FloatingPanel getHoveredPanel() {
        return hoveredPanel;
    }

    public boolean isHoveringPanel() {
        return hoveredPanel != null;
    }

    public boolean isDragging() {
        return isDragging;
    }

    @Nullable
    public FloatingPanel getDraggedPanel() {
        return draggedPanel;
    }

    /**
     * Gets the current feedback message, if any and still valid.
     * Returns null if no message or if the message has expired.
     */
    @Nullable
    public String getFeedbackMessage() {
        if (feedbackMessage == null) return null;

        long elapsed = System.currentTimeMillis() - feedbackDisplayTime;
        if (elapsed > FEEDBACK_DURATION_MS) {
            feedbackMessage = null;
            return null;
        }
        return feedbackMessage;
    }

    /**
     * Gets the opacity for the feedback message (fades out over time).
     * Returns 0.0 to 1.0.
     */
    public float getFeedbackOpacity() {
        if (feedbackMessage == null) return 0f;

        long elapsed = System.currentTimeMillis() - feedbackDisplayTime;
        if (elapsed > FEEDBACK_DURATION_MS) return 0f;

        // Fade out during last 500ms
        long fadeStart = FEEDBACK_DURATION_MS - 500;
        if (elapsed > fadeStart) {
            return 1f - (float)(elapsed - fadeStart) / 500f;
        }
        return 1f;
    }

    /**
     * Clears any active feedback message.
     */
    public void clearFeedback() {
        feedbackMessage = null;
    }
}
