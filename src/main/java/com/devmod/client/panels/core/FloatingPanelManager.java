package com.devmod.client.panels.core;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.panels.tracking.EntityTracker;
import com.devmod.client.panels.ui.PanelRenderer;

public class FloatingPanelManager {

    public static final FloatingPanelManager INSTANCE = new FloatingPanelManager();

    // === Panel Storage ===
    private final List<FloatingPanel> panels = new CopyOnWriteArrayList<>();

    // === Configuration ===
    private static final int MAX_PANELS = 12;
    private static final double MAX_RENDER_DISTANCE = 32.0;
    private static final double MIN_RENDER_DISTANCE = 1.0;

    // === State ===
    private boolean enabled = true;
    @Nullable
    private FloatingPanel hoveredPanel = null;

    private FloatingPanelManager() {
        // Singleton
    }

    // === Panel Spawning ===

    /**
     * Spawn a new panel.
     * If the limit is reached, remove the oldest unpinned panel.
     *
     * @param panel Panel to add
     * @return true if the panel was added
     */
    public boolean spawnPanel(FloatingPanel panel) {
        if (!enabled) return false;

        // Remove excess panels
        while (panels.size() >= MAX_PANELS) {
            FloatingPanel oldest = findOldestUnpinnedPanel();
            if (oldest != null) {
                oldest.startDespawn();
            } else {
                // All pinned, can't add
                return false;
            }
        }

        panels.add(panel);
        return true;
    }

    /**
     * Find the oldest unpinned panel.
     */
    @Nullable
    private FloatingPanel findOldestUnpinnedPanel() {
        FloatingPanel oldest = null;
        long oldestAge = 0;

        for (FloatingPanel panel : panels) {
            if (!panel.isPinned() && panel.getState() != PanelState.DESPAWNING) {
                long age = panel.getAge();
                if (oldest == null || age > oldestAge) {
                    oldest = panel;
                    oldestAge = age;
                }
            }
        }

        return oldest;
    }

    /**
     * Search for an existing panel for an entity.
     */
    @Nullable
    public FloatingPanel findPanelForEntity(Entity entity) {
        int entityId = entity.getId();
        for (FloatingPanel panel : panels) {
            EntityTracker tracker = panel.getTracker();
            if (tracker != null && tracker.getTargetId() == entityId) {
                return panel;
            }
        }
        return null;
    }

    /**
     * Search for a panel by ID.
     */
    @Nullable
    public FloatingPanel findPanelById(UUID id) {
        for (FloatingPanel panel : panels) {
            if (panel.getId().equals(id)) {
                return panel;
            }
        }
        return null;
    }

    /**
     * Search for panels by type.
     */
    public List<FloatingPanel> findPanelsByType(PanelType type) {
        return panels.stream()
            .filter(p -> p.getType() == type)
            .toList();
    }

    // === Lifecycle ===

    /**
     * Update all panels.
     * Call every client tick.
     */
    public void tick() {
        if (!enabled) return;

        // Tick all panels
        for (FloatingPanel panel : panels) {
            panel.tick();
        }

        // Remove expired panels
        panels.removeIf(FloatingPanel::isExpired);
    }

    /**
     * Render all visible panels.
     *
     * @param poseStack Transform stack
     * @param bufferSource Buffer for rendering
     * @param camera Active camera
     * @param partialTick Partial tick
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       Camera camera, float partialTick) {
        if (!enabled || panels.isEmpty()) return;

        Vec3 cameraPos = Objects.requireNonNull(camera.getPosition(), "cameraPos");

        // Sort by distance (farthest first for correct Z-ordering)
        List<FloatingPanel> sortedPanels = panels.stream()
            .filter(FloatingPanel::shouldRender)
            .filter(p -> {
                double dist = p.getWorldPosition().distanceTo(cameraPos);
                return dist >= MIN_RENDER_DISTANCE && dist <= MAX_RENDER_DISTANCE;
            })
            .sorted((a, b) -> {
                double distA = a.getWorldPosition().distanceTo(cameraPos);
                double distB = b.getWorldPosition().distanceTo(cameraPos);
                return Double.compare(distB, distA); // Farthest first
            })
            .toList();

        for (FloatingPanel panel : sortedPanels) {
            renderPanel(poseStack, bufferSource, camera, panel, partialTick);
        }
    }

    /**
     * Render a single panel.
     * Delegated to PanelRenderer.
     */
    private void renderPanel(PoseStack poseStack, MultiBufferSource bufferSource,
                             Camera camera, FloatingPanel panel, float partialTick) {
        PanelRenderer.INSTANCE.renderPanel(
            poseStack,
            bufferSource,
            camera.getPosition(),
            panel,
            partialTick
        );
    }

    // === Interaction ===

    /**
     * Handles a mouse click.
     *
     * @param button Button (0=left, 1=right, 2=middle)
     * @return true if a panel handled the click
     */
    public boolean handleMouseClick(int button) {
        FloatingPanel panel = hoveredPanel;
        if (!enabled || panel == null) return false;

        // Calculate local click coordinates (simplified)
        // In real implementation, project the mouse ray onto the panel
        return panel.handleClick(0, 0, button);
    }

    /**
     * Update which panel is under the mouse.
     * Called from the input system.
     */
    public void updateHoveredPanel(Minecraft mc) {
        if (!enabled) {
            hoveredPanel = null;
            return;
        }

        // Reset hover state of all
        for (FloatingPanel panel : panels) {
            panel.setHovered(false);
        }

        // Find panel under mouse (simplified ray casting)
        // Full implementation in PanelInteractionHandler
        hoveredPanel = null;

        if (hoveredPanel != null) {
            hoveredPanel.setHovered(true);
        }
    }

    // === Bulk Operations ===

    /**
     * Close all panels of a specific type.
     */
    public void closeAllOfType(PanelType type) {
        for (FloatingPanel panel : panels) {
            if (panel.getType() == type) {
                panel.startDespawn();
            }
        }
    }

    /**
     * Close all unpinned panels.
     */
    public void closeAllUnpinned() {
        for (FloatingPanel panel : panels) {
            if (!panel.isPinned()) {
                panel.startDespawn();
            }
        }
    }

    /**
     * Close all panels.
     */
    public void closeAll() {
        for (FloatingPanel panel : panels) {
            panel.startDespawn();
        }
    }

    /**
     * Immediately remove all panels (without animation).
     */
    public void clearAll() {
        panels.clear();
        hoveredPanel = null;
    }

    /**
     * Remove panels that match a predicate.
     */
    public void removeWhere(Predicate<FloatingPanel> predicate) {
        for (FloatingPanel panel : panels) {
            if (predicate.test(panel)) {
                panel.startDespawn();
            }
        }
    }

    // === Getters/Setters ===

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            closeAll();
        }
    }

    public int getPanelCount() {
        return panels.size();
    }

    public int getActivePanelCount() {
        return (int) panels.stream()
            .filter(p -> p.getState() != PanelState.DESPAWNING && p.getState() != PanelState.REMOVED)
            .count();
    }

    @Nullable
    public FloatingPanel getHoveredPanel() {
        return hoveredPanel;
    }

    public List<FloatingPanel> getAllPanels() {
        return List.copyOf(panels);
    }

    /**
     * Debug: get info about all panels.
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("FloatingPanels: ").append(panels.size()).append("/").append(MAX_PANELS).append("\n");

        for (FloatingPanel panel : panels) {
            sb.append("  - ").append(panel.toString()).append("\n");
        }

        return sb.toString();
    }
}
