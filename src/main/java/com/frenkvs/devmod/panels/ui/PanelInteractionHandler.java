package com.frenkvs.devmod.panels.ui;

import com.frenkvs.devmod.panels.core.FloatingPanel;
import com.frenkvs.devmod.panels.core.FloatingPanelManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Gestisce le interazioni mouse con i pannelli flottanti nel mondo 3D.
 *
 * Funzionalita':
 * - Ray casting dal mouse per trovare pannelli sotto il cursore
 * - Gestione click (sinistro/destro/medio)
 * - Hover detection
 * - Drag support (futuro)
 */
public class PanelInteractionHandler {

    public static final PanelInteractionHandler INSTANCE = new PanelInteractionHandler();

    // Stato interazione
    @Nullable
    private FloatingPanel hoveredPanel = null;
    @Nullable
    private FloatingPanel draggedPanel = null;
    private boolean isDragging = false;

    // Feedback per tentativo drag su pannello non pinnato
    @Nullable
    private String feedbackMessage = null;
    private long feedbackDisplayTime = 0;
    private static final long FEEDBACK_DURATION_MS = 2000;

    // Configurazione
    private static final double MAX_INTERACTION_DISTANCE = 20.0;
    private static final float PANEL_HITBOX_SCALE = 1.2f; // Hitbox leggermente piu' grande

    private PanelInteractionHandler() {}

    /**
     * Aggiorna lo stato di hover basato sulla posizione del mouse.
     * Chiamare ogni frame.
     *
     * @param mouseX Posizione X del mouse su schermo
     * @param mouseY Posizione Y del mouse su schermo
     * @param screenWidth Larghezza schermo
     * @param screenHeight Altezza schermo
     */
    public void updateHover(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clearHover();
            return;
        }

        // Se stiamo draggando, non cambiare hover
        if (isDragging) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        // Trova il pannello sotto il mouse
        FloatingPanel newHovered = findPanelUnderMouse(mouseX, mouseY, screenWidth, screenHeight, camera);

        // Aggiorna stato hover
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
     * Trova il pannello sotto la posizione del mouse.
     */
    @Nullable
    private FloatingPanel findPanelUnderMouse(double mouseX, double mouseY,
                                               int screenWidth, int screenHeight,
                                               Camera camera) {
        Vec3 cameraPos = camera.getPosition();
        List<FloatingPanel> panels = FloatingPanelManager.INSTANCE.getAllPanels();

        // Converti posizione mouse in ray direction
        Vec3 rayDir = screenToWorldRay(mouseX, mouseY, screenWidth, screenHeight, camera);
        if (rayDir == null) return null;

        FloatingPanel closest = null;
        double closestDist = Double.MAX_VALUE;

        for (FloatingPanel panel : panels) {
            if (!panel.shouldRender() || !panel.getState().isInteractive()) continue;

            Vec3 panelPos = panel.getWorldPosition();
            double distance = panelPos.distanceTo(cameraPos);

            if (distance > MAX_INTERACTION_DISTANCE) continue;

            // Test intersezione semplificato (sfera attorno al pannello)
            double hitRadius = estimatePanelRadius(panel, distance) * PANEL_HITBOX_SCALE;

            if (rayIntersectsSphere(cameraPos, rayDir, panelPos, hitRadius)) {
                if (distance < closestDist) {
                    closestDist = distance;
                    closest = panel;
                }
            }
        }

        return closest;
    }

    /**
     * Converte posizione mouse in ray direction nel mondo.
     * Semplificazione: usa la direzione della camera.
     */
    @Nullable
    private Vec3 screenToWorldRay(double mouseX, double mouseY,
                                   int screenWidth, int screenHeight,
                                   Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        // Normalizza coordinate mouse (-1 to 1)
        float normalizedX = (float) (2.0 * mouseX / screenWidth - 1.0);
        float normalizedY = (float) (1.0 - 2.0 * mouseY / screenHeight);

        // Semplificazione: usa la direzione di look del player con offset
        // Per interazione precisa servirebbe projection matrix inversa
        Vec3 lookDir = mc.player.getLookAngle();

        // Calcola right e up vectors
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = lookDir.cross(worldUp).normalize();
        Vec3 up = right.cross(lookDir).normalize();

        // FOV adjustment
        float fov = (float) Math.toRadians(mc.options.fov().get());
        float aspectRatio = (float) screenWidth / screenHeight;

        float tanHalfFov = (float) Math.tan(fov / 2);
        float offsetX = normalizedX * tanHalfFov * aspectRatio;
        float offsetY = normalizedY * tanHalfFov;

        // Ray direction
        return lookDir.add(right.scale(offsetX)).add(up.scale(offsetY)).normalize();
    }

    /**
     * Stima il raggio del pannello in unita' mondo basato sulla distanza.
     */
    private double estimatePanelRadius(FloatingPanel panel, double distance) {
        // Il pannello scala con la distanza, quindi il raggio effettivo
        // dipende dalle dimensioni e dalla scala
        float baseScale = 0.015f; // Da PanelRenderer
        int width = panel.getWidth();
        int height = panel.getHeight();

        // Usa la diagonale come raggio approssimativo
        double diagonal = Math.sqrt(width * width + height * height);
        return diagonal * baseScale * 0.5;
    }

    /**
     * Test intersezione ray-sphere.
     */
    private boolean rayIntersectsSphere(Vec3 rayOrigin, Vec3 rayDir, Vec3 sphereCenter, double radius) {
        Vec3 oc = rayOrigin.subtract(sphereCenter);

        double a = rayDir.dot(rayDir);
        double b = 2.0 * oc.dot(rayDir);
        double c = oc.dot(oc) - radius * radius;

        double discriminant = b * b - 4 * a * c;
        return discriminant >= 0;
    }

    /**
     * Gestisce un click del mouse.
     *
     * @param button Pulsante (0=sinistro, 1=destro, 2=medio)
     * @return true se il click e' stato gestito
     */
    public boolean handleClick(int button) {
        if (hoveredPanel == null) return false;

        // Calcola coordinate locali approssimative (centro del pannello)
        int localX = hoveredPanel.getWidth() / 2;
        int localY = hoveredPanel.getHeight() / 2;

        return hoveredPanel.handleClick(localX, localY, button);
    }

    // Drag state
    private Vec3 dragStartPanelPos = null;
    private double dragStartMouseX = 0;
    private double dragStartMouseY = 0;

    /**
     * Gestisce l'inizio di un drag.
     */
    public boolean handleDragStart(int button) {
        if (button != 0 || hoveredPanel == null) return false;

        // Solo pannelli pinnati possono essere trascinati
        if (!hoveredPanel.isPinned()) {
            // Provide feedback to user explaining why drag doesn't work
            showFeedback("Pin panel first (right-click) to enable dragging");
            return false;
        }

        draggedPanel = hoveredPanel;
        isDragging = true;
        dragStartPanelPos = draggedPanel.getWorldPosition();

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
     * Gestisce il movimento durante un drag.
     * Sposta il pannello nel mondo 3D basandosi sul movimento del mouse.
     */
    public void handleDragMove(double mouseX, double mouseY) {
        if (!isDragging || draggedPanel == null || dragStartPanelPos == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Calcola delta mouse in coordinate normalizzate
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        double deltaX = (mouseX - dragStartMouseX) / screenWidth;
        double deltaY = (mouseY - dragStartMouseY) / screenHeight;

        // Scala il movimento basandosi sulla distanza del pannello
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        double distance = dragStartPanelPos.distanceTo(cameraPos);

        // Movimento proporzionale alla distanza (pannelli lontani si muovono di piu')
        double movementScale = distance * 0.5;

        // Calcola right e up vectors dalla camera
        Vec3 lookDir = mc.player.getLookAngle();
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = lookDir.cross(worldUp).normalize();
        Vec3 up = right.cross(lookDir).normalize();

        // Nuova posizione = posizione iniziale + offset
        Vec3 offset = right.scale(deltaX * movementScale).add(up.scale(-deltaY * movementScale));
        Vec3 newPos = dragStartPanelPos.add(offset);

        // Applica la nuova posizione
        draggedPanel.setFixedPosition(newPos);
    }

    /**
     * Gestisce la fine di un drag.
     */
    public void handleDragEnd() {
        isDragging = false;
        draggedPanel = null;
        dragStartPanelPos = null;
    }

    /**
     * Pulisce lo stato di hover.
     */
    public void clearHover() {
        if (hoveredPanel != null) {
            hoveredPanel.setHovered(false);
            hoveredPanel = null;
        }
    }

    /**
     * Reset completo dello stato.
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
