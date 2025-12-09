package com.frenkvs.devmod.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton che gestisce tutti i pannelli Impact 3D attivi.
 *
 * Responsabilità:
 * - Mantiene la lista dei pannelli attivi
 * - Spawna nuovi pannelli quando avviene un impatto
 * - Aggiorna i pannelli ogni tick client
 * - Renderizza tutti i pannelli
 * - Rimuove i pannelli scaduti
 * - Gestisce il limite massimo di pannelli
 */
@SuppressWarnings("null") // Minecraft API methods are not annotated but never return null in practice
public class Impact3DPanelManager {

    // Singleton instance
    public static final Impact3DPanelManager INSTANCE = new Impact3DPanelManager();

    // === Configurazione ===
    private static final int MAX_PANELS = 12;  // Limite massimo pannelli simultanei (performance)
    private static final double MAX_RENDER_DISTANCE = 64.0; // Non renderizzare pannelli oltre questa distanza
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    // === Stato === (thread-safe per evitare ConcurrentModificationException)
    private final List<Impact3DPanel> activePanels = new CopyOnWriteArrayList<>();
    private boolean enabled = true;

    private Impact3DPanelManager() {}

    /**
     * Spawna un nuovo pannello 3D da un impatto.
     *
     * @param data Dati dell'impatto (deve contenere hitPoint)
     */
    public void spawnPanelFromImpact(ImpactData data) {
        if (!enabled) return;
        if (data == null) return;

        // Verifica che ci sia un hit point valido
        Vec3 hitPoint = data.hitPoint;
        if (hitPoint == null) {
            // Se non c'è un hit point, prova a usare la posizione del target
            // NOTA: Per entità che si teletrasportano (Enderman), il target potrebbe
            // essere già null. In questo caso usiamo la posizione del player + direzione
            var target = data.getTarget();
            if (target != null) {
                hitPoint = target.position().add(0, target.getBbHeight() * 0.5, 0);
            } else {
                // Fallback: usa la posizione del player + 3 blocchi nella direzione dello sguardo
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Vec3 look = mc.player.getLookAngle();
                    hitPoint = mc.player.getEyePosition().add(look.scale(3.0));
                } else {
                    // Nessun punto valido, non possiamo spawnare
                    return;
                }
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // Rimuovi pannelli in eccesso (FIFO)
        while (activePanels.size() >= MAX_PANELS) {
            activePanels.remove(0);
        }

        // Crea e aggiungi il nuovo pannello
        Impact3DPanel panel = new Impact3DPanel(hitPoint, data, cameraPos);
        activePanels.add(panel);
    }

    /**
     * Aggiorna tutti i pannelli attivi.
     * Chiamato ogni tick client da ClientTickEvent.
     */
    public void clientTick() {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);

        // Aggiorna tutti i pannelli (CopyOnWriteArrayList è safe per iterazione)
        for (Impact3DPanel panel : activePanels) {
            panel.update(level, player, partialTick);
        }

        // Rimuovi pannelli scaduti (removeIf funziona con CopyOnWriteArrayList)
        activePanels.removeIf(Impact3DPanel::isExpired);
    }

    /**
     * Renderizza tutti i pannelli attivi.
     * Chiamato durante RenderLevelStageEvent (AFTER_ENTITIES).
     *
     * <p>PERFORMANCE OPTIMIZATION: Pannelli oltre MAX_RENDER_DISTANCE vengono
     * saltati per ridurre il carico di rendering.
     *
     * @param poseStack Stack di trasformazioni
     * @param bufferSource Buffer per il rendering
     * @param camera Camera attiva
     * @param partialTick Tick parziale
     */
    public void renderAllPanels(PoseStack poseStack, MultiBufferSource bufferSource,
                                 Camera camera, float partialTick) {
        if (!enabled) return;
        if (activePanels.isEmpty()) return;

        Vec3 cameraPos = camera.getPosition();

        for (Impact3DPanel panel : activePanels) {
            // DISTANCE-GATED RENDERING: Skip pannelli troppo lontani
            Vec3 panelPos = panel.getPanelPosition();
            if (panelPos != null) {
                double distSq = panelPos.distanceToSqr(cameraPos);
                if (distSq > MAX_RENDER_DISTANCE_SQ) {
                    continue; // Skip rendering, pannello troppo lontano
                }
            }

            panel.render(poseStack, bufferSource, camera, partialTick);
        }

        // Flush buffer per assicurare rendering
        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    /**
     * Pulisce tutti i pannelli attivi.
     */
    public void clear() {
        activePanels.clear();
    }

    /**
     * Verifica se ci sono pannelli attivi.
     */
    public boolean hasActivePanels() {
        return !activePanels.isEmpty();
    }

    /**
     * Ottiene il numero di pannelli attivi.
     */
    public int getPanelCount() {
        return activePanels.size();
    }

    /**
     * Abilita/disabilita il sistema di pannelli 3D.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    /**
     * Verifica se il sistema è abilitato.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Toggle abilita/disabilita.
     */
    public void toggle() {
        setEnabled(!enabled);
    }
}
