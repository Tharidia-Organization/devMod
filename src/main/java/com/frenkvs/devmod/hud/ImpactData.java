package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.HitHelper.BodyPart;
import com.frenkvs.devmod.integration.ModIntegrationManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contiene i dati dell'ultimo impatto per l'HUD.
 * Thread-safe e con auto-expire dopo DISPLAY_DURATION_MS.
 *
 * MULTIPLAYER-SAFE: I dati sono isolati per UUID dell'attaccante.
 * Ogni client vede solo i propri impatti, non quelli di altri giocatori.
 */
public class ImpactData {

    // Map thread-safe: UUID attaccante -> ultimo impatto di quel giocatore
    // Questo isola i dati per ogni giocatore in multiplayer
    private static final Map<UUID, ImpactData> IMPACTS_BY_PLAYER = new ConcurrentHashMap<>();

    // Cleanup automatico per evitare memory leak (rimuove entries scadute)
    private static long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL_MS = 10000; // Cleanup ogni 10 secondi

    // Durata visualizzazione HUD dopo aver smesso di guardare il pannello
    public static final long DISPLAY_DURATION_MS = 3000; // 3 secondi dopo aver distolto lo sguardo
    private static final long FADE_DURATION_MS = 500;    // Fade out ultimi 500ms

    // Timestamp di quando il player ha smesso di guardare il pannello HUD
    private volatile long stoppedLookingTimestamp = -1;
    // Flag se il giocatore sta guardando il pannello
    private volatile boolean isBeingObserved = false;

    // === Dati impatto ===
    public final long timestamp;
    public final UUID attackerUUID; // UUID dell'attaccante per isolamento multiplayer
    public final WeakReference<LivingEntity> targetRef; // WeakRef per evitare memory leak
    public final String targetName;
    public final BodyPart bodyPart;
    public final float bodyPartMultiplier;
    public final DamageBreakdown breakdown;
    public final String attackSource;
    public final boolean isRanged;

    // === Posizione impatto 3D ===
    @Nullable public final Vec3 hitPoint;       // Punto esatto di collisione
    @Nullable public final Vec3 slashDirection; // Direzione dello slash (per animazione)

    // === Dati Pehkui (nullable se non presente) ===
    @Nullable public final Float pehkuiVisualScale;
    @Nullable public final Float pehkuiHitboxScale;

    // === Dati Better Combat (nullable se non presente) ===
    @Nullable public final String betterCombatAttackName;

    // === Danno Reale (aggiornato post-armor/enchants) ===
    private volatile float actualDamageDealt = -1f;  // -1 = non ancora disponibile
    private volatile float healthBefore = -1f;
    private volatile float healthAfter = -1f;

    /**
     * Costruttore completo per uso interno.
     *
     * @param attackerUUID UUID dell'attaccante (per isolamento multiplayer)
     */
    public ImpactData(UUID attackerUUID, LivingEntity target, BodyPart part, float multiplier,
                      DamageBreakdown breakdown, String attackSource, boolean isRanged,
                      @Nullable Vec3 hitPoint, @Nullable Vec3 slashDirection,
                      @Nullable String bcAttackName) {
        this.timestamp = System.currentTimeMillis();
        this.attackerUUID = attackerUUID;
        this.targetRef = new WeakReference<>(target);
        this.targetName = target.getName().getString();
        this.bodyPart = part;
        this.bodyPartMultiplier = multiplier;
        this.breakdown = breakdown;
        this.attackSource = attackSource;
        this.isRanged = isRanged;

        // Posizione impatto 3D
        this.hitPoint = hitPoint;
        this.slashDirection = slashDirection;

        // Pehkui integration
        this.pehkuiVisualScale = ModIntegrationManager.getPehkuiScale(target);
        this.pehkuiHitboxScale = ModIntegrationManager.getPehkuiHitboxScale(target);

        // Better Combat
        this.betterCombatAttackName = bcAttackName;
    }

    /**
     * Costruttore con hit point (senza BC).
     */
    public ImpactData(UUID attackerUUID, LivingEntity target, BodyPart part, float multiplier,
                      DamageBreakdown breakdown, String attackSource, boolean isRanged,
                      @Nullable Vec3 hitPoint, @Nullable Vec3 slashDirection) {
        this(attackerUUID, target, part, multiplier, breakdown, attackSource, isRanged, hitPoint, slashDirection, null);
    }

    /**
     * Costruttore semplificato (senza posizione e BC).
     */
    public ImpactData(UUID attackerUUID, LivingEntity target, BodyPart part, float multiplier,
                      DamageBreakdown breakdown, String attackSource, boolean isRanged) {
        this(attackerUUID, target, part, multiplier, breakdown, attackSource, isRanged, null, null, null);
    }

    // === Static methods per accesso globale (multiplayer-safe) ===

    /**
     * Salva un nuovo impatto per l'attaccante specificato.
     * In multiplayer, ogni giocatore ha i propri dati isolati.
     */
    public static void store(ImpactData data) {
        if (data == null || data.attackerUUID == null) return;
        IMPACTS_BY_PLAYER.put(data.attackerUUID, data);
        maybeCleanup();
    }

    /**
     * Ottiene l'ultimo impatto del giocatore locale, o null se scaduto/non presente.
     * Questo metodo è MULTIPLAYER-SAFE: ogni client vede solo i propri impatti.
     *
     * NOTA: Questo metodo è safe da chiamare su server dedicati (ritorna null).
     */
    @Nullable
    public static ImpactData get() {
        // SERVER-SAFE: Non caricare Minecraft.class su dedicated server
        if (!FMLEnvironment.dist.isClient()) return null;
        return getForLocalPlayer();
    }

    /**
     * Client-only helper per ottenere l'UUID del player locale.
     * Isolato in metodo separato per evitare classloading di Minecraft su server.
     */
    @Nullable
    private static ImpactData getForLocalPlayer() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return null;
        return getForPlayer(mc.player.getUUID());
    }

    /**
     * Ottiene l'ultimo impatto per uno specifico giocatore.
     */
    @Nullable
    public static ImpactData getForPlayer(UUID playerUUID) {
        if (playerUUID == null) return null;

        ImpactData data = IMPACTS_BY_PLAYER.get(playerUUID);
        if (data == null) return null;

        // Controlla scadenza
        if (data.isExpired()) {
            IMPACTS_BY_PLAYER.remove(playerUUID, data);
            return null;
        }
        return data;
    }

    /**
     * Pulisce l'impatto del giocatore locale.
     *
     * NOTA: Questo metodo è safe da chiamare su server dedicati (no-op).
     */
    public static void clear() {
        // SERVER-SAFE: Non caricare Minecraft.class su dedicated server
        if (!FMLEnvironment.dist.isClient()) return;
        clearForLocalPlayer();
    }

    /**
     * Client-only helper per pulire l'impatto del player locale.
     * Isolato in metodo separato per evitare classloading di Minecraft su server.
     */
    private static void clearForLocalPlayer() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            clearForPlayer(mc.player.getUUID());
        }
    }

    /**
     * Pulisce l'impatto per uno specifico giocatore.
     */
    public static void clearForPlayer(UUID playerUUID) {
        if (playerUUID != null) {
            IMPACTS_BY_PLAYER.remove(playerUUID);
        }
    }

    /**
     * Pulisce tutti gli impatti (es. al cambio mondo/disconnessione).
     */
    public static void clearAll() {
        IMPACTS_BY_PLAYER.clear();
    }

    /**
     * Rimuove periodicamente entries scadute per evitare memory leak.
     */
    private static void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) return;
        lastCleanup = now;

        IMPACTS_BY_PLAYER.entrySet().removeIf(entry ->
            entry.getValue() == null || entry.getValue().isExpired()
        );
    }

    // === Instance methods ===

    /**
     * Aggiorna lo stato di osservazione del pannello HUD.
     * Chiamato ogni frame dal renderer.
     * @param observed true se il crosshair è sopra il pannello
     */
    public void setObserved(boolean observed) {
        if (this.isBeingObserved && !observed) {
            // Il giocatore ha appena distolto lo sguardo - inizia il timer
            this.stoppedLookingTimestamp = System.currentTimeMillis();
        } else if (observed) {
            // Il giocatore sta guardando - resetta il timer
            this.stoppedLookingTimestamp = -1;
        }
        this.isBeingObserved = observed;
    }

    /**
     * Verifica se il giocatore sta osservando il pannello.
     */
    public boolean isBeingObserved() {
        return isBeingObserved;
    }

    /**
     * Controlla se l'impatto è scaduto.
     * Non scade mai finché il giocatore guarda il pannello.
     */
    public boolean isExpired() {
        // Se sta guardando, non scade mai
        if (isBeingObserved) {
            return false;
        }

        // Se non ha mai smesso di guardare (primo frame), usa timestamp originale
        if (stoppedLookingTimestamp < 0) {
            return System.currentTimeMillis() - timestamp > DISPLAY_DURATION_MS;
        }

        // Altrimenti, conta da quando ha smesso di guardare
        return System.currentTimeMillis() - stoppedLookingTimestamp > DISPLAY_DURATION_MS;
    }

    /**
     * Calcola l'alpha per il fade-out.
     * Rimane a 1.0 finché il giocatore guarda il pannello.
     * @return 1.0 = opaco, 0.0 = trasparente
     */
    public float getRemainingAlpha() {
        // Se sta guardando, sempre opaco
        if (isBeingObserved) {
            return 1.0f;
        }

        // Calcola elapsed da quando ha smesso di guardare
        long referenceTime;
        if (stoppedLookingTimestamp > 0) {
            referenceTime = stoppedLookingTimestamp;
        } else {
            referenceTime = timestamp;
        }

        long elapsed = System.currentTimeMillis() - referenceTime;

        if (elapsed > DISPLAY_DURATION_MS) {
            return 0f;
        }

        // Fade out negli ultimi FADE_DURATION_MS
        long fadeStart = DISPLAY_DURATION_MS - FADE_DURATION_MS;
        if (elapsed > fadeStart) {
            float fadeProgress = (elapsed - fadeStart) / (float) FADE_DURATION_MS;
            return 1.0f - fadeProgress;
        }

        return 1.0f;
    }

    /**
     * Ottiene il target se ancora valido (non garbage collected).
     */
    @Nullable
    public LivingEntity getTarget() {
        return targetRef.get();
    }

    /**
     * Verifica se Pehkui ha modificato l'entità.
     */
    public boolean hasPehkuiModification() {
        return pehkuiVisualScale != null && Math.abs(pehkuiVisualScale - 1.0f) > 0.01f;
    }

    /**
     * Verifica se l'attacco proviene da Better Combat.
     */
    public boolean isBetterCombatAttack() {
        return betterCombatAttackName != null && !betterCombatAttackName.isEmpty();
    }

    /**
     * Ottiene una descrizione formattata dell'attacco.
     */
    public String getFormattedAttackSource() {
        if (isBetterCombatAttack()) {
            return "Better Combat '" + betterCombatAttackName + "'";
        }
        return attackSource;
    }

    /**
     * Ottiene il colore del body part per la UI.
     */
    public int getBodyPartColor() {
        return switch (bodyPart) {
            case HEAD -> 0xFF00FFFF;  // Cyan
            case BODY -> 0xFF00FF00;  // Green
            case ARMS -> 0xFFFFFF00;  // Yellow
            case LEGS -> 0xFFFF0000;  // Red
        };
    }

    /**
     * Ottiene il codice colore Minecraft per il body part.
     */
    public String getBodyPartColorCode() {
        return switch (bodyPart) {
            case HEAD -> "§b";  // Aqua/Cyan
            case BODY -> "§a";  // Green
            case ARMS -> "§e";  // Yellow
            case LEGS -> "§c";  // Red
        };
    }

    // === Metodi per danno reale ===

    /**
     * Imposta i dati del danno reale (chiamato da LivingDamageEvent.Post).
     */
    public void setActualDamage(float healthBefore, float healthAfter, float actualDamage) {
        this.healthBefore = healthBefore;
        this.healthAfter = healthAfter;
        this.actualDamageDealt = actualDamage;
    }

    /**
     * Ottiene il danno reale effettivamente inflitto.
     * @return danno reale, o -1 se non ancora disponibile
     */
    public float getActualDamageDealt() {
        return actualDamageDealt;
    }

    /**
     * Verifica se il danno reale è stato registrato.
     */
    public boolean hasActualDamage() {
        return actualDamageDealt >= 0;
    }

    /**
     * Ottiene la vita del target prima del colpo.
     */
    public float getHealthBefore() {
        return healthBefore;
    }

    /**
     * Ottiene la vita del target dopo il colpo.
     */
    public float getHealthAfter() {
        return healthAfter;
    }

    /**
     * Calcola la differenza tra danno calcolato e danno reale.
     * Positivo = armatura/effetti hanno ridotto, Negativo = danno amplificato
     */
    public float getDamageReduction() {
        if (!hasActualDamage()) return 0;
        return breakdown.finalDamage - actualDamageDealt;
    }

    @Override
    public String toString() {
        String actualStr = hasActualDamage() ? String.format(", actual=%.1f", actualDamageDealt) : "";
        return String.format("ImpactData[target=%s, part=%s, mult=%.2f, dmg=%.1f%s, source=%s]",
            targetName, bodyPart, bodyPartMultiplier, breakdown.finalDamage, actualStr, attackSource);
    }
}
