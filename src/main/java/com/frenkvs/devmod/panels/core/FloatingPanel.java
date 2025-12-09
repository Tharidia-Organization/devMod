package com.frenkvs.devmod.panels.core;

import com.frenkvs.devmod.panels.tracking.EntityTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Classe base astratta per tutti i pannelli flottanti nel mondo 3D.
 *
 * Un FloatingPanel e' un elemento UI che:
 * - Esiste nel mondo 3D (non sullo schermo 2D)
 * - Puo' tracciare un'entita' o avere posizione fissa
 * - Ha un lifecycle con animazioni di spawn/despawn
 * - Puo' essere pinnato, minimizzato, trascinato
 * - Si orienta sempre verso la camera (billboard)
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
     * Crea un pannello con tracking di entita'.
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
     * Crea un pannello con posizione fissa.
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
     * Renderizza il contenuto interno del pannello (versione 2D per GUI).
     * Chiamato con coordinate locali (0,0 e' l'angolo top-left del contenuto).
     *
     * @param graphics Context di rendering
     * @param contentWidth Larghezza disponibile per il contenuto
     * @param contentHeight Altezza disponibile per il contenuto
     */
    public abstract void renderContent(GuiGraphics graphics, int contentWidth, int contentHeight);

    /**
     * Renderizza il contenuto interno del pannello nel mondo 3D.
     * Override questo metodo per rendering 3D custom.
     * Default: usa il font per renderizzare testo basico.
     *
     * @param poseStack Stack di trasformazioni
     * @param bufferSource Buffer per rendering
     * @param font Font per il testo
     * @param contentWidth Larghezza disponibile
     * @param contentHeight Altezza disponibile
     * @param alpha Alpha corrente del pannello
     */
    public void renderContent3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        // Default implementation - pannelli possono override per contenuti custom
        // Renderizza info basiche come testo
        renderText3D(poseStack, bufferSource, font, getTitle(), 0, 0, applyAlpha(0xFFFFFFFF, alpha));
    }

    /**
     * Ottiene il titolo del pannello (mostrato nell'header).
     */
    public abstract String getTitle();

    // === 3D Rendering Helpers ===

    /**
     * Helper per renderizzare testo 3D.
     */
    protected void renderText3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                 String text, float x, float y, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        font.drawInBatch(
            text, 0, 0, color, false,
            poseStack.last().pose(), bufferSource,
            Font.DisplayMode.SEE_THROUGH, 0, 15728880
        );
        poseStack.popPose();
    }

    /**
     * Applica alpha a un colore ARGB.
     */
    protected int applyAlpha(int argb, float alphaMultiplier) {
        int originalAlpha = (argb >> 24) & 0xFF;
        if (originalAlpha == 0) originalAlpha = 255;
        int newAlpha = (int) (originalAlpha * alphaMultiplier);
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Aggiorna lo stato interno del pannello.
     * Chiamato ogni tick client.
     */
    public void tick() {
        // Aggiorna tracker se presente
        if (tracker != null) {
            tracker.tick();

            // Se il target non e' piu' valido, inizia despawn
            if (!tracker.isValid() && state != PanelState.DESPAWNING && state != PanelState.REMOVED) {
                startDespawn();
            }
        }

        // Gestione lifecycle
        updateLifecycle();

        // Auto-expire se configurato
        if (type.hasAutoExpire() && !pinned) {
            long age = System.currentTimeMillis() - spawnTime;
            if (age > type.getAutoExpireMs() && state == PanelState.VISIBLE) {
                startDespawn();
            }
        }
    }

    /**
     * Aggiorna le animazioni e le transizioni di stato.
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
     * Transizione a un nuovo stato.
     */
    protected void transitionTo(PanelState newState) {
        if (this.state != newState) {
            this.state = newState;
            this.stateStartTime = System.currentTimeMillis();
            onStateChanged(newState);
        }
    }

    /**
     * Callback per cambio stato (override per comportamenti custom).
     */
    protected void onStateChanged(PanelState newState) {
        // Override in subclasses se necessario
    }

    /**
     * Inizia la sequenza di despawn.
     */
    public void startDespawn() {
        if (state != PanelState.DESPAWNING && state != PanelState.REMOVED) {
            transitionTo(PanelState.DESPAWNING);
        }
    }

    /**
     * Chiude immediatamente il pannello.
     */
    public void close() {
        transitionTo(PanelState.REMOVED);
    }

    // === Position ===

    /**
     * Ottiene la posizione corrente del pannello nel mondo.
     */
    public Vec3 getWorldPosition() {
        if (tracker != null && tracker.isValid()) {
            return tracker.getSmoothedPosition();
        }
        return fixedPosition != null ? fixedPosition : Vec3.ZERO;
    }

    /**
     * Imposta una nuova posizione fissa (rimuove il tracking).
     */
    public void setFixedPosition(Vec3 position) {
        this.fixedPosition = position;
        this.tracker = null;
    }

    // === Pin/Minimize ===

    /**
     * Alterna lo stato di pin.
     */
    public void togglePin() {
        if (type.canPin()) {
            this.pinned = !this.pinned;
        }
    }

    /**
     * Alterna lo stato minimizzato.
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
     * Gestisce un click sul pannello.
     *
     * @param localX Coordinata X relativa al pannello
     * @param localY Coordinata Y relativa al pannello
     * @param button Pulsante del mouse (0=left, 1=right, 2=middle)
     * @return true se il click e' stato gestito
     */
    public boolean handleClick(int localX, int localY, int button) {
        if (!state.isInteractive()) return false;

        // Click destro = toggle pin
        if (button == 1 && type.canPin()) {
            togglePin();
            return true;
        }

        // Click sinistro sull'header = toggle minimize
        if (button == 0 && localY < 16) {
            toggleMinimize();
            return true;
        }

        return false;
    }

    /**
     * Aggiorna lo stato di hover.
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
