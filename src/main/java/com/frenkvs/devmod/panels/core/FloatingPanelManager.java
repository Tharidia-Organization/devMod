package com.frenkvs.devmod.panels.core;

import com.frenkvs.devmod.panels.tracking.EntityTracker;
import com.frenkvs.devmod.panels.ui.PanelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Manager singleton per tutti i FloatingPanel nel mondo.
 *
 * Responsabilita':
 * - Gestione lifecycle dei pannelli (spawn, tick, despawn)
 * - Limite massimo pannelli attivi
 * - Rendering coordinato di tutti i pannelli
 * - Gestione interazioni mouse
 * - Rimozione pannelli scaduti
 */
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
     * Spawna un nuovo pannello.
     * Se il limite e' raggiunto, rimuove il pannello piu' vecchio non pinnato.
     *
     * @param panel Pannello da aggiungere
     * @return true se il pannello e' stato aggiunto
     */
    public boolean spawnPanel(FloatingPanel panel) {
        if (!enabled) return false;

        // Rimuovi pannelli in eccesso
        while (panels.size() >= MAX_PANELS) {
            FloatingPanel oldest = findOldestUnpinnedPanel();
            if (oldest != null) {
                oldest.startDespawn();
            } else {
                // Tutti pinnati, non possiamo aggiungere
                return false;
            }
        }

        panels.add(panel);
        return true;
    }

    /**
     * Trova il pannello piu' vecchio non pinnato.
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
     * Cerca un pannello esistente per un'entita'.
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
     * Cerca un pannello per ID.
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
     * Cerca pannelli per tipo.
     */
    public List<FloatingPanel> findPanelsByType(PanelType type) {
        return panels.stream()
            .filter(p -> p.getType() == type)
            .toList();
    }

    // === Lifecycle ===

    /**
     * Aggiorna tutti i pannelli.
     * Chiamare ogni tick client.
     */
    public void tick() {
        if (!enabled) return;

        // Tick tutti i pannelli
        for (FloatingPanel panel : panels) {
            panel.tick();
        }

        // Rimuovi pannelli scaduti
        panels.removeIf(FloatingPanel::isExpired);
    }

    /**
     * Renderizza tutti i pannelli visibili.
     *
     * @param poseStack Stack di trasformazioni
     * @param bufferSource Buffer per rendering
     * @param camera Camera attiva
     * @param partialTick Tick parziale
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       Camera camera, float partialTick) {
        if (!enabled || panels.isEmpty()) return;

        Vec3 cameraPos = camera.getPosition();

        // Ordina per distanza (piu' lontani prima per Z-ordering corretto)
        List<FloatingPanel> sortedPanels = panels.stream()
            .filter(FloatingPanel::shouldRender)
            .filter(p -> {
                double dist = p.getWorldPosition().distanceTo(cameraPos);
                return dist >= MIN_RENDER_DISTANCE && dist <= MAX_RENDER_DISTANCE;
            })
            .sorted((a, b) -> {
                double distA = a.getWorldPosition().distanceTo(cameraPos);
                double distB = b.getWorldPosition().distanceTo(cameraPos);
                return Double.compare(distB, distA); // Lontani prima
            })
            .toList();

        for (FloatingPanel panel : sortedPanels) {
            renderPanel(poseStack, bufferSource, camera, panel, partialTick);
        }
    }

    /**
     * Renderizza un singolo pannello.
     * Delegato a PanelRenderer.
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
     * Gestisce un click del mouse.
     *
     * @param button Pulsante (0=left, 1=right, 2=middle)
     * @return true se un pannello ha gestito il click
     */
    public boolean handleMouseClick(int button) {
        if (!enabled || hoveredPanel == null) return false;

        // Calcola coordinate locali del click (semplificato)
        // In implementazione reale, proiettare il mouse ray sul pannello
        return hoveredPanel.handleClick(0, 0, button);
    }

    /**
     * Aggiorna quale pannello e' sotto il mouse.
     * Chiamato dal sistema di input.
     */
    public void updateHoveredPanel(Minecraft mc) {
        if (!enabled) {
            hoveredPanel = null;
            return;
        }

        // Reset hover state di tutti
        for (FloatingPanel panel : panels) {
            panel.setHovered(false);
        }

        // Trova pannello sotto il mouse (ray casting semplificato)
        // Implementazione completa in PanelInteractionHandler
        hoveredPanel = null;

        if (hoveredPanel != null) {
            hoveredPanel.setHovered(true);
        }
    }

    // === Bulk Operations ===

    /**
     * Chiude tutti i pannelli di un tipo specifico.
     */
    public void closeAllOfType(PanelType type) {
        for (FloatingPanel panel : panels) {
            if (panel.getType() == type) {
                panel.startDespawn();
            }
        }
    }

    /**
     * Chiude tutti i pannelli non pinnati.
     */
    public void closeAllUnpinned() {
        for (FloatingPanel panel : panels) {
            if (!panel.isPinned()) {
                panel.startDespawn();
            }
        }
    }

    /**
     * Chiude tutti i pannelli.
     */
    public void closeAll() {
        for (FloatingPanel panel : panels) {
            panel.startDespawn();
        }
    }

    /**
     * Rimuove immediatamente tutti i pannelli (senza animazione).
     */
    public void clearAll() {
        panels.clear();
        hoveredPanel = null;
    }

    /**
     * Rimuove pannelli che matchano un predicato.
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
     * Debug: ottiene info su tutti i pannelli.
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
