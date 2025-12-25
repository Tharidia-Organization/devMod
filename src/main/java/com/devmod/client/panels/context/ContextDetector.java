package com.devmod.client.panels.context;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.client.testing.TestingSession;

/**
 * Rileva automaticamente il contesto di gioco e notifica i listener.
 *
 * Il detector analizza:
 * - Tempo dall'ultimo evento di combattimento
 * - Stato della sessione di testing
 * - Entita' target del player
 * - Override manuale dell'utente
 */
public class ContextDetector {

    public static final ContextDetector INSTANCE = new ContextDetector();

    // === State ===
    private ContextMode currentMode = ContextMode.EXPLORE;
    @Nullable
    private ContextMode forcedMode = null; // Override manuale

    // === Combat Detection ===
    private long lastCombatEventTime = 0;
    private static final long COMBAT_TIMEOUT_MS = 5000; // 5 secondi senza combat = esci

    // === Target Tracking ===
    @Nullable
    private LivingEntity currentTarget = null;
    private long targetAcquiredTime = 0;

    // === Listeners ===
    private final List<Consumer<ContextMode>> modeChangeListeners = new ArrayList<>();

    private ContextDetector() {
        // Singleton
    }

    /**
     * Aggiorna il detector ogni tick client.
     */
    public void tick() {
        ContextMode forced = forcedMode;
        if (forced != null) {
            // Modo forzato - non cambiare automaticamente
            if (currentMode != forced) {
                setMode(forced);
            }
            return;
        }

        ContextMode detected = detectMode();
        if (detected != currentMode) {
            setMode(detected);
        }
    }

    /**
     * Rileva il modo appropriato basato sullo stato corrente.
     */
    private ContextMode detectMode() {
        // Priorita' 1: Testing attivo
        if (isTestingActive()) {
            return ContextMode.TEST;
        }

        // Priorita' 2: Combat recente
        if (isInCombat()) {
            return ContextMode.COMBAT;
        }

        // Default: Explore
        return ContextMode.EXPLORE;
    }

    /**
     * Verifica se c'e' una sessione di testing attiva.
     */
    private boolean isTestingActive() {
        return TestingSession.INSTANCE.isSessionActive();
    }

    /**
     * Verifica se il player e' in combattimento (eventi recenti).
     */
    private boolean isInCombat() {
        long now = System.currentTimeMillis();
        return (now - lastCombatEventTime) < COMBAT_TIMEOUT_MS;
    }

    /**
     * Imposta il modo corrente e notifica i listener.
     */
    private void setMode(ContextMode mode) {
        ContextMode oldMode = this.currentMode;
        this.currentMode = mode;

        // Notifica listeners
        for (Consumer<ContextMode> listener : modeChangeListeners) {
            listener.accept(mode);
        }

        // Log per debug
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            com.devmod.DevMod.LOGGER.debug("Context changed: {} -> {}", oldMode, mode);
        }
    }

    // === Event Callbacks ===

    /**
     * Chiamato quando il player infligge o riceve danno.
     */
    public void onCombatEvent() {
        lastCombatEventTime = System.currentTimeMillis();
    }

    /**
     * Chiamato quando il player infligge danno a un'entita'.
     */
    public void onDamageDealt(LivingEntity target, float damage) {
        onCombatEvent();
        updateTarget(target);
    }

    /**
     * Chiamato quando il player riceve danno.
     */
    public void onDamageReceived(LivingEntity attacker, float damage) {
        onCombatEvent();
        LivingEntity target = currentTarget;
        if (target == null) {
            updateTarget(attacker);
        }
    }

    /**
     * Aggiorna il target corrente.
     */
    public void updateTarget(@Nullable LivingEntity target) {
        if (target != currentTarget) {
            this.currentTarget = target;
            this.targetAcquiredTime = System.currentTimeMillis();
        }
    }

    /**
     * Ottiene il target corrente se ancora valido.
     */
    @Nullable
    public LivingEntity getCurrentTarget() {
        LivingEntity target = currentTarget;
        if (target != null && !target.isAlive()) {
            currentTarget = null;
            return null;
        }
        return target;
    }

    /**
     * Clears il target corrente.
     */
    public void clearTarget() {
        this.currentTarget = null;
    }

    // === Manual Override ===

    /**
     * Forza un modo specifico (override automatico).
     */
    public void forceMode(ContextMode mode) {
        this.forcedMode = mode;
        setMode(mode);
    }

    /**
     * Rimuove l'override e torna al rilevamento automatico.
     */
    public void clearForcedMode() {
        this.forcedMode = null;
    }

    /**
     * Verifica se c'e' un modo forzato.
     */
    public boolean hasForcedMode() {
        return forcedMode != null;
    }

    // === Listeners ===

    /**
     * Registra un listener per cambi di modo.
     */
    public void addModeChangeListener(Consumer<ContextMode> listener) {
        modeChangeListeners.add(listener);
    }

    /**
     * Rimuove un listener.
     */
    public void removeModeChangeListener(Consumer<ContextMode> listener) {
        modeChangeListeners.remove(listener);
    }

    // === Getters ===

    public ContextMode getCurrentMode() {
        return currentMode;
    }

    @Nullable
    public ContextMode getForcedMode() {
        return forcedMode;
    }

    /**
     * Tempo trascorso dall'ultimo evento di combattimento (ms).
     */
    public long getTimeSinceLastCombat() {
        return System.currentTimeMillis() - lastCombatEventTime;
    }

    /**
     * Tempo trascorso dall'acquisizione del target (ms).
     */
    public long getTimeSinceTargetAcquired() {
        return System.currentTimeMillis() - targetAcquiredTime;
    }

    /**
     * Verifica se il player e' attualmente in un contesto di combattimento.
     */
    public boolean isCurrentlyCombat() {
        return currentMode == ContextMode.COMBAT;
    }

    /**
     * Verifica se il player e' in un contesto di testing.
     */
    public boolean isCurrentlyTesting() {
        return currentMode == ContextMode.TEST;
    }

    /**
     * Debug info.
     */
    public String getDebugInfo() {
        ContextMode forced = forcedMode;
        LivingEntity target = currentTarget;
        return String.format("Context: %s%s | Combat: %dms ago | Target: %s",
            currentMode.getDisplayName(),
            forced != null ? " (forced)" : "",
            getTimeSinceLastCombat(),
            target != null ? target.getName().getString() : "none"
        );
    }

    /**
     * Reset completo dello stato.
     */
    public void reset() {
        currentMode = ContextMode.EXPLORE;
        forcedMode = null;
        lastCombatEventTime = 0;
        currentTarget = null;
        targetAcquiredTime = 0;
    }
}
