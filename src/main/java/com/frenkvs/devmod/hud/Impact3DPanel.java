package com.frenkvs.devmod.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Rappresenta una singola istanza di pannello 3D nel mondo.
 * Gestisce posizione, lifecycle (fade in/out), e rendering.
 *
 * Ogni pannello è associato a un impatto specifico e contiene:
 * - Posizione del punto di impatto originale
 * - Posizione del pannello (offset dal hit point)
 * - Dati dell'impatto (ImpactData)
 * - Timestamp per gestione lifecycle
 */
@SuppressWarnings("null") // Minecraft API methods are not annotated but never return null in practice
public class Impact3DPanel {

    // === Configurazione Lifecycle ===
    private static final long LIFETIME_MS = 4000;        // Durata totale: 4 secondi
    private static final long FADE_IN_MS = 500;          // Fade in: 500ms (smoother)
    private static final long FADE_OUT_MS = 1000;        // Fade out: 1000ms (gentler)
    private static final long FADE_OUT_START = LIFETIME_MS - FADE_OUT_MS;

    // === Dati Pannello ===
    private final Vec3 hitPoint;           // Punto di impatto originale
    private Vec3 panelPosition;            // Posizione del pannello nel mondo
    private final ImpactData data;         // Dati dell'impatto
    private final long spawnTime;          // Timestamp di creazione

    // === Stato ===
    private boolean expired = false;

    /**
     * Crea un nuovo pannello 3D.
     *
     * @param hitPoint Punto di impatto nel mondo
     * @param data Dati dell'impatto
     * @param cameraPos Posizione della camera (per calcolare offset pannello)
     */
    public Impact3DPanel(Vec3 hitPoint, ImpactData data, Vec3 cameraPos) {
        this.hitPoint = hitPoint;
        this.data = data;
        this.spawnTime = System.currentTimeMillis();

        // Calcola posizione iniziale del pannello
        this.panelPosition = Impact3DRenderer.INSTANCE.calculatePanelPosition(hitPoint, cameraPos);
    }

    /**
     * Aggiorna lo stato del pannello ogni tick client.
     *
     * @param level Livello client
     * @param player Player locale
     * @param partialTick Tick parziale per interpolazione
     */
    public void update(ClientLevel level, LocalPlayer player, float partialTick) {
        long elapsed = System.currentTimeMillis() - spawnTime;

        // Controlla scadenza
        if (elapsed > LIFETIME_MS) {
            expired = true;
            return;
        }

        // Opzionale: aggiorna posizione pannello se deve seguire qualcosa
        // Per ora la posizione è fissa al momento della creazione
    }

    /**
     * Renderizza il pannello nel mondo.
     *
     * @param poseStack Stack di trasformazioni
     * @param bufferSource Buffer per il rendering
     * @param camera Camera attiva
     * @param partialTick Tick parziale
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       Camera camera, float partialTick) {
        if (expired) return;

        float alpha = calculateAlpha();
        if (alpha <= 0.01f) return;

        Vec3 cameraPos = camera.getPosition();

        // Delega il rendering a Impact3DRenderer
        Impact3DRenderer.INSTANCE.renderPanel(
            poseStack,
            bufferSource,
            cameraPos,
            panelPosition,
            hitPoint,
            data,
            alpha
        );
    }

    /**
     * Calcola l'alpha corrente basata sul lifecycle.
     *
     * Curva di alpha:
     * - 0-200ms: fade in (0 -> 1)
     * - 200-3200ms: alpha piena (1)
     * - 3200-4000ms: fade out (1 -> 0)
     *
     * @return Alpha tra 0.0 e 1.0
     */
    private float calculateAlpha() {
        long elapsed = System.currentTimeMillis() - spawnTime;

        if (elapsed < 0) return 0f;
        if (elapsed > LIFETIME_MS) return 0f;

        // Fade in
        if (elapsed < FADE_IN_MS) {
            return (float) elapsed / FADE_IN_MS;
        }

        // Alpha piena
        if (elapsed < FADE_OUT_START) {
            return 1.0f;
        }

        // Fade out
        float fadeOutProgress = (float) (elapsed - FADE_OUT_START) / FADE_OUT_MS;
        return 1.0f - fadeOutProgress;
    }

    /**
     * Verifica se il pannello è scaduto e deve essere rimosso.
     */
    public boolean isExpired() {
        return expired || (System.currentTimeMillis() - spawnTime > LIFETIME_MS);
    }

    /**
     * Ottiene il punto di impatto originale.
     */
    public Vec3 getHitPoint() {
        return hitPoint;
    }

    /**
     * Ottiene la posizione del pannello.
     */
    public Vec3 getPanelPosition() {
        return panelPosition;
    }

    /**
     * Ottiene i dati dell'impatto.
     */
    public ImpactData getData() {
        return data;
    }

    /**
     * Ottiene il timestamp di spawn.
     */
    public long getSpawnTime() {
        return spawnTime;
    }

    /**
     * Ottiene l'età del pannello in millisecondi.
     */
    public long getAge() {
        return System.currentTimeMillis() - spawnTime;
    }

    /**
     * Calcola la distanza dalla camera.
     */
    public double getDistanceFromCamera(Vec3 cameraPos) {
        return panelPosition.distanceTo(cameraPos);
    }

    @Override
    public String toString() {
        return String.format("Impact3DPanel[hit=%s, pos=%s, age=%dms, expired=%s]",
            hitPoint, panelPosition, getAge(), expired);
    }
}
