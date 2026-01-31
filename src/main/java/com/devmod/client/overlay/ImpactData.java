package com.devmod.client.overlay;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.loading.FMLEnvironment;

import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.combat.HitHelper.BodyPart;
import com.devmod.damage.DamageBreakdown;
import com.devmod.integration.ModIntegrationManager;
import com.devmod.util.I18n;

public class ImpactData {

    // Thread-safe map: attacker UUID -> last impact for that player
    // This isolates data for each player in multiplayer
    private static final Map<UUID, ImpactData> IMPACTS_BY_PLAYER = new ConcurrentHashMap<>();

    // Automatic cleanup to prevent memory leak (removes expired entries)
    private static long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL_MS = 10000; // Cleanup every 10 seconds

    // HUD display duration after stopping to look at the panel
    public static final long DISPLAY_DURATION_MS = 3000; // 3 seconds after looking away
    private static final long FADE_DURATION_MS = 500;    // Fade out last 500ms
    // BUG-005 FIX: Maximum observation time to prevent HUD from staying forever
    private static final long MAX_OBSERVATION_TIME_MS = 30000; // 30 seconds max even if observed

    // Timestamp of when the player stopped looking at the HUD panel
    private volatile long stoppedLookingTimestamp = -1;
    // Flag if the player is looking at the panel
    private volatile boolean isBeingObserved = false;

    // === Impact data ===
    private final long timestamp;
    private final UUID attackerUUID; // Attacker UUID for multiplayer isolation
    private final WeakReference<LivingEntity> targetRef; // WeakRef to prevent memory leak
    private final String targetName;
    private final BodyPart bodyPart;
    private final float bodyPartMultiplier;
    private final DamageBreakdown breakdown;
    @Nonnull private final String attackSource;
    private final boolean isRanged;

    // === 3D Impact Position ===
    @Nullable private final Vec3 hitPoint;       // Exact collision point
    @Nullable private final Vec3 slashDirection; // Slash direction (for animation)

    // === Pehkui Data (nullable if not present) ===
    @Nullable private final Float pehkuiVisualScale;
    @Nullable private final Float pehkuiHitboxScale;

    // === Epic Fight Data (nullable if not present) ===
    @Nullable private final String epicFightAnimationName;
    private final boolean epicFightCombatActive;

    // Getters
    public long getTimestamp() { return timestamp; }
    public UUID getAttackerUUID() { return attackerUUID; }
    public WeakReference<LivingEntity> getTargetRef() { return targetRef; }
    public String getTargetName() { return targetName; }
    public BodyPart getBodyPart() { return bodyPart; }
    public float getBodyPartMultiplier() { return bodyPartMultiplier; }
    public DamageBreakdown getBreakdown() { return breakdown; }
    @Nonnull public String getAttackSource() { return attackSource; }
    public boolean isRanged() { return isRanged; }
    @Nullable public Vec3 getHitPoint() { return hitPoint; }
    @Nullable public Vec3 getSlashDirection() { return slashDirection; }
    @Nullable public Float getPehkuiVisualScale() { return pehkuiVisualScale; }
    @Nullable public Float getPehkuiHitboxScale() { return pehkuiHitboxScale; }
    public boolean isEpicFightCombatActive() { return epicFightCombatActive; }

    // === Actual Damage (updated post-armor/enchants) ===
    private volatile float actualDamageDealt = -1f;  // -1 = not yet available
    private volatile float healthBefore = -1f;
    private volatile float healthAfter = -1f;

    // === Damage Reduction Breakdown (BUG-003 FIX) ===
    private volatile float armorReduction = 0f;
    private volatile float enchantmentReduction = 0f;
    private volatile float mobEffectReduction = 0f;  // Resistance potion, etc.
    private volatile float absorptionReduction = 0f;
    private volatile float blockedDamage = 0f;
    private final AtomicInteger revision = new AtomicInteger();

    /**
     * Complete constructor for internal use.
     *
     * @param attackerUUID Attacker's UUID (for multiplayer isolation)
     */
    public ImpactData(UUID attackerUUID, LivingEntity target, BodyPart part, float multiplier,
                      DamageBreakdown breakdown, String attackSource, boolean isRanged,
                      @Nullable Vec3 hitPoint, @Nullable Vec3 slashDirection,
                      @Nullable String epicFightAnimation, boolean epicFightActive) {
        this.timestamp = System.currentTimeMillis();
        this.attackerUUID = attackerUUID;
        this.targetRef = new WeakReference<>(target);
        this.targetName = target.getName().getString();
        this.bodyPart = part;
        this.bodyPartMultiplier = multiplier;
        this.breakdown = breakdown;
        this.attackSource = Objects.requireNonNull(attackSource, "attackSource");
        this.isRanged = isRanged;

        // 3D impact position
        this.hitPoint = hitPoint;
        this.slashDirection = slashDirection;

        // Pehkui integration
        this.pehkuiVisualScale = ModIntegrationManager.getPehkuiScale(target);
        this.pehkuiHitboxScale = ModIntegrationManager.getPehkuiHitboxScale(target);

        // Epic Fight integration
        this.epicFightAnimationName = epicFightAnimation;
        this.epicFightCombatActive = epicFightActive;
    }

    /**
     * Constructor with hit point (without Epic Fight data).
     */
    public ImpactData(UUID attackerUUID, LivingEntity target, BodyPart part, float multiplier,
                      DamageBreakdown breakdown, String attackSource, boolean isRanged,
                      @Nullable Vec3 hitPoint, @Nullable Vec3 slashDirection) {
        this(attackerUUID, target, part, multiplier, breakdown, attackSource, isRanged, hitPoint, slashDirection, null, false);
    }

    /**
     * Simplified constructor (without position and Epic Fight data).
     */
    public ImpactData(UUID attackerUUID, LivingEntity target, BodyPart part, float multiplier,
                      DamageBreakdown breakdown, String attackSource, boolean isRanged) {
        this(attackerUUID, target, part, multiplier, breakdown, attackSource, isRanged, null, null, null, false);
    }

    // === Static methods for global access (multiplayer-safe) ===

    /**
     * Stores a new impact for the specified attacker.
     * In multiplayer, each player has their own isolated data.
     */
    public static void store(ImpactData data) {
        if (data == null || data.getAttackerUUID() == null) return;
        IMPACTS_BY_PLAYER.put(data.getAttackerUUID(), data);
        maybeCleanup();
    }

    /**
     * Gets the latest impact of the local player, or null if expired/not present.
     * This method is MULTIPLAYER-SAFE: each client sees only their own impacts.
     *
     * NOTE: This method is safe to call on dedicated servers (returns null).
     */
    @Nullable
    public static ImpactData get() {
        // SERVER-SAFE: Don't load Minecraft.class on dedicated server
        if (!FMLEnvironment.dist.isClient()) return null;
        return getForLocalPlayer();
    }

    /**
     * Client-only helper to get the local player's UUID.
     * Isolated in separate method to avoid classloading Minecraft on server.
     */
    @Nullable
    private static ImpactData getForLocalPlayer() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return null;
        return getForPlayer(player.getUUID());
    }

    /**
     * Gets the last impact for a specific player.
     */
    @Nullable
    public static ImpactData getForPlayer(UUID playerUUID) {
        if (playerUUID == null) return null;

        ImpactData data = IMPACTS_BY_PLAYER.get(playerUUID);
        if (data == null) return null;

        // Check expiration
        if (data.isExpired()) {
            IMPACTS_BY_PLAYER.remove(playerUUID, data);
            return null;
        }
        return data;
    }

    /**
     * Clears the local player's impact.
     *
     * NOTE: This method is safe to call on dedicated servers (no-op).
     */
    public static void clear() {
        // SERVER-SAFE: Don't load Minecraft.class on dedicated server
        if (!FMLEnvironment.dist.isClient()) return;
        clearForLocalPlayer();
    }

    /**
     * Client-only helper to clear the local player's impact.
     * Isolated in separate method to avoid classloading Minecraft on server.
     */
    private static void clearForLocalPlayer() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            UUID playerId = mc.player.getUUID();
            clearForPlayer(playerId);
        }
    }

    /**
     * Clears the impact for a specific player.
     */
    public static void clearForPlayer(UUID playerUUID) {
        if (playerUUID != null) {
            IMPACTS_BY_PLAYER.remove(playerUUID);
            ImpactHistory.clearForPlayer(playerUUID);
            ImpactDpsTracker.clearForPlayer(playerUUID);
        }
    }

    /**
     * Clears all impacts (e.g., on world change/disconnection).
     */
    public static void clearAll() {
        IMPACTS_BY_PLAYER.clear();
        ImpactHistory.clearAll();
        ImpactDpsTracker.clearAll();
    }

    /**
     * Client-side tick hook for deterministic cleanup.
     */
    public static void clientTickCleanup() {
        if (!FMLEnvironment.dist.isClient()) return;
        maybeCleanup();
    }

    /**
     * Periodically removes expired entries to avoid memory leaks.
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
     * Updates the observation state of the HUD panel.
     * Called every frame by the renderer.
     * @param observed true if the crosshair is over the panel
     */
    public void setObserved(boolean observed) {
        if (this.isBeingObserved && !observed) {
            // The player just looked away - start the timer
            this.stoppedLookingTimestamp = System.currentTimeMillis();
        } else if (observed) {
            // The player is looking - reset the timer
            this.stoppedLookingTimestamp = -1;
        }
        this.isBeingObserved = observed;
    }

    /**
     * Checks if the player is observing the panel.
     */
    public boolean isBeingObserved() {
        return isBeingObserved;
    }

    /**
     * Checks if the impact has expired.
     * Expires after DISPLAY_DURATION_MS when not observed, or after MAX_OBSERVATION_TIME_MS regardless.
     */
    public boolean isExpired() {
        long now = System.currentTimeMillis();

        // BUG-005 FIX: Always expire after max observation time, even if being observed
        if (now - timestamp > MAX_OBSERVATION_TIME_MS) {
            return true;
        }

        // If currently observing and within max time, don't expire
        if (isBeingObserved) {
            return false;
        }

        // If never stopped looking (first frame), use original timestamp
        if (stoppedLookingTimestamp < 0) {
            return now - timestamp > DISPLAY_DURATION_MS;
        }

        // Otherwise, count from when they stopped looking
        return now - stoppedLookingTimestamp > DISPLAY_DURATION_MS;
    }

    /**
     * Calculates the alpha for fade-out.
     * Remains at 1.0 as long as the player is looking at the panel.
     * @return 1.0 = opaque, 0.0 = transparent
     */
    public float getRemainingAlpha() {
        // If being observed, always opaque
        if (isBeingObserved) {
            return 1.0f;
        }

        // Calculate elapsed time since stopped looking
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

        // Fade out in the last FADE_DURATION_MS
        long fadeStart = DISPLAY_DURATION_MS - FADE_DURATION_MS;
        if (elapsed > fadeStart) {
            float fadeProgress = (elapsed - fadeStart) / (float) FADE_DURATION_MS;
            return 1.0f - fadeProgress;
        }

        return 1.0f;
    }

    /**
     * Gets the target if still valid (not garbage collected).
     */
    @Nullable
    public LivingEntity getTarget() {
        return targetRef.get();
    }

    /**
     * Checks if Pehkui has modified the entity.
     */
    public boolean hasPehkuiModification() {
        return pehkuiVisualScale != null && Math.abs(pehkuiVisualScale - 1.0f) > 0.01f;
    }

    /**
     * Checks if the attack comes from Epic Fight combat.
     */
    public boolean isEpicFightCombat() {
        return epicFightCombatActive;
    }

    /**
     * Gets the Epic Fight animation name if available.
     */
    @Nullable
    public String getEpicFightAnimationName() {
        return epicFightAnimationName;
    }

    /**
     * Gets a formatted description of the attack.
     */
    public String getFormattedAttackSource() {
        if (isEpicFightCombat() && epicFightAnimationName != null) {
            return "Epic Fight '" + epicFightAnimationName + "'";
        }
        return attackSource;
    }

    /**
     * Gets a formatted description of the attack as a component.
     */
    public Component getFormattedAttackSourceComponent() {
        if (isEpicFightCombat() && epicFightAnimationName != null) {
            return I18n.translate("devmod.hud.attack_source.epic_fight", epicFightAnimationName);
        }

        if (attackSource.startsWith("devmod.hud.attack_source.")) {
            return I18n.translate(attackSource);
        }

        return Component.literal(attackSource);
    }

    /**
     * Gets the body part color for the UI.
     * Uses centralized OverlayTheme.BodyPart tokens.
     */
    public int getBodyPartColor() {
        return switch (bodyPart) {
            case HEAD -> OverlayTheme.BodyPart.HEAD;
            case BODY -> OverlayTheme.BodyPart.BODY;
            case ARMS -> OverlayTheme.BodyPart.ARMS;
            case LEGS -> OverlayTheme.BodyPart.LEGS;
        };
    }

    /**
     * Gets the Minecraft color code for the body part.
     */
    public String getBodyPartColorCode() {
        return switch (bodyPart) {
            case HEAD -> "\u00A7b";  // Aqua/Cyan
            case BODY -> "\u00A7a";  // Green
            case ARMS -> "\u00A7e";  // Yellow
            case LEGS -> "\u00A7c";  // Red
        };
    }

    // === Methods for actual damage ===

    /**
     * Sets the actual damage data (called from LivingDamageEvent.Post).
     */
    public void setActualDamage(float healthBefore, float healthAfter, float actualDamage) {
        this.healthBefore = healthBefore;
        this.healthAfter = healthAfter;
        this.actualDamageDealt = actualDamage;
        revision.incrementAndGet();
    }

    /**
     * Sets the damage reduction breakdown (called from LivingDamageEvent.Post).
     * BUG-003 FIX: Provides detailed breakdown of what reduced the damage.
     */
    public void setDamageReductionBreakdown(float armor, float enchantments, float mobEffects,
                                             float absorption, float blocked) {
        this.armorReduction = armor;
        this.enchantmentReduction = enchantments;
        this.mobEffectReduction = mobEffects;
        this.absorptionReduction = absorption;
        this.blockedDamage = blocked;
        revision.incrementAndGet();
    }

    /** Gets armor reduction amount. */
    public float getArmorReduction() { return armorReduction; }
    /** Gets enchantment (Protection) reduction amount. */
    public float getEnchantmentReduction() { return enchantmentReduction; }
    /** Gets mob effect (Resistance) reduction amount. */
    public float getMobEffectReduction() { return mobEffectReduction; }
    /** Gets absorption reduction amount. */
    public float getAbsorptionReduction() { return absorptionReduction; }
    /** Gets blocked damage amount (shield). */
    public float getBlockedDamage() { return blockedDamage; }

    /** Checks if any damage reduction breakdown data is available. */
    public boolean hasReductionBreakdown() {
        return armorReduction > 0 || enchantmentReduction > 0 || mobEffectReduction > 0
            || absorptionReduction > 0 || blockedDamage > 0;
    }

    /**
     * Gets the actual damage dealt.
     * @return actual damage, or -1 if not yet available
     */
    public float getActualDamageDealt() {
        return actualDamageDealt;
    }

    /**
     * Checks if actual damage has been recorded.
     */
    public boolean hasActualDamage() {
        return actualDamageDealt >= 0;
    }

    /**
     * Gets the target's health before the hit.
     */
    public float getHealthBefore() {
        return healthBefore;
    }

    /**
     * Gets the target's health after the hit.
     */
    public float getHealthAfter() {
        return healthAfter;
    }

    /**
     * Calculates the difference between calculated and actual damage.
     * Positive = armor/effects reduced it, Negative = damage amplified
     */
    public float getDamageReduction() {
        if (!hasActualDamage()) return 0;
        return breakdown.getFinalDamage() - actualDamageDealt;
    }

    public int getRevision() {
        return revision.get();
    }

    @Override
    public String toString() {
        String actualStr = hasActualDamage() ? String.format(", actual=%.1f", actualDamageDealt) : "";
        return String.format("ImpactData[target=%s, part=%s, mult=%.2f, dmg=%.1f%s, source=%s]",
            targetName, bodyPart, bodyPartMultiplier, breakdown.getFinalDamage(), actualStr, attackSource);
    }
}
