package com.frenkvs.devmod.attributes;

import com.frenkvs.devmod.integration.ModIntegrationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;

/**
 * Rappresenta un'entità tracciata dal sistema di monitoraggio attributi.
 * Mantiene uno snapshot degli attributi e traccia i cambiamenti.
 *
 * <p><b>CLIENT-ONLY</b>: This class uses Minecraft.getInstance() for LoS calculations
 * and should never be instantiated or used on a dedicated server.
 */
@OnlyIn(Dist.CLIENT)
public class TrackedEntity {

    // === Riferimento entità ===
    private final WeakReference<LivingEntity> entityRef;
    private final int entityId;
    private final String entityName;
    private final String entityType;

    // === Attributi snapshot ===
    private float currentHealth;
    private float maxHealth;
    private float armorValue;
    private float armorToughness;
    private double movementSpeed;
    private double attackDamage;
    private double attackSpeed;
    private double knockbackResistance;

    // === Pehkui Integration ===
    @Nullable private Float pehkuiScale;
    @Nullable private Float pehkuiHitboxScale;

    // === Stato LoS ===
    private boolean hasLineOfSight;
    private Vec3 lastKnownPosition;
    private Vec3 lastBlockedPoint;

    // === Tracking temporale ===
    private long firstTrackedTime;
    private long lastUpdateTime;
    private float healthDelta; // Cambio vita dall'ultimo update

    // === Telemetry/Drift tracking ===
    private Vec3 previousPosition;
    private double totalDistanceMoved = 0.0;
    private float totalDamageTaken = 0.0f;

    @SuppressWarnings("this-escape")
    public TrackedEntity(LivingEntity entity) {
        this.entityRef = new WeakReference<>(entity);
        this.entityId = entity.getId();
        this.entityName = entity.getName().getString();
        this.entityType = entity.getType().getDescriptionId();
        this.firstTrackedTime = System.currentTimeMillis();
        this.lastKnownPosition = entity.position();
        this.previousPosition = entity.position();

        // Prima lettura attributi (safe: update() only reads entity state)
        update();
    }

    /**
     * Aggiorna tutti gli attributi dell'entità.
     */
    public void update() {
        LivingEntity entity = entityRef.get();
        if (entity == null || !entity.isAlive()) return;

        lastUpdateTime = System.currentTimeMillis();

        // Salva delta vita
        float oldHealth = currentHealth;

        // Attributi base
        currentHealth = entity.getHealth();
        maxHealth = entity.getMaxHealth();
        armorValue = entity.getArmorValue();

        // Attributi avanzati (nullable safe)
        var armorToughnessAttr = entity.getAttribute(Attributes.ARMOR_TOUGHNESS);
        armorToughness = armorToughnessAttr != null ? (float) armorToughnessAttr.getValue() : 0f;

        var moveSpeedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        movementSpeed = moveSpeedAttr != null ? moveSpeedAttr.getValue() : 0;

        var attackDmgAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        attackDamage = attackDmgAttr != null ? attackDmgAttr.getValue() : 0;

        var attackSpeedAttr = entity.getAttribute(Attributes.ATTACK_SPEED);
        attackSpeed = attackSpeedAttr != null ? attackSpeedAttr.getValue() : 0;

        var knockbackAttr = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        knockbackResistance = knockbackAttr != null ? knockbackAttr.getValue() : 0;

        // Pehkui
        pehkuiScale = ModIntegrationManager.getPehkuiScale(entity);
        pehkuiHitboxScale = ModIntegrationManager.getPehkuiHitboxScale(entity);

        // Calcola delta salute
        if (oldHealth > 0) {
            healthDelta = currentHealth - oldHealth;
            // Track cumulative damage for telemetry
            if (healthDelta < 0) {
                totalDamageTaken += Math.abs(healthDelta);
            }
        }

        // Aggiorna posizione e traccia drift
        Vec3 currentPos = entity.position();
        if (previousPosition != null) {
            totalDistanceMoved += previousPosition.distanceTo(currentPos);
        }
        previousPosition = lastKnownPosition;
        lastKnownPosition = currentPos;

        // Aggiorna LoS
        updateLineOfSight(entity);
    }

    private void updateLineOfSight(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            hasLineOfSight = false;
            return;
        }

        Vec3 playerEye = player.getEyePosition();
        Vec3 entityEye = entity.getEyePosition();

        ClipContext context = new ClipContext(
            playerEye,
            entityEye,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        );

        HitResult hit = level.clip(context);

        if (hit.getType() == HitResult.Type.MISS) {
            hasLineOfSight = true;
            lastBlockedPoint = null;
        } else {
            hasLineOfSight = false;
            lastBlockedPoint = hit.getLocation();
        }
    }

    // === Getters ===

    @Nullable
    public LivingEntity getEntity() {
        return entityRef.get();
    }

    public int getEntityId() {
        return entityId;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getEntityType() {
        return entityType;
    }

    public float getCurrentHealth() {
        return currentHealth;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public float getHealthPercent() {
        return maxHealth > 0 ? (currentHealth / maxHealth) * 100f : 0f;
    }

    public float getArmorValue() {
        return armorValue;
    }

    public float getArmorToughness() {
        return armorToughness;
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public double getAttackDamage() {
        return attackDamage;
    }

    public double getAttackSpeed() {
        return attackSpeed;
    }

    public double getKnockbackResistance() {
        return knockbackResistance;
    }

    public boolean hasLineOfSight() {
        return hasLineOfSight;
    }

    public Vec3 getLastKnownPosition() {
        return lastKnownPosition;
    }

    @Nullable
    public Vec3 getLastBlockedPoint() {
        return lastBlockedPoint;
    }

    public float getHealthDelta() {
        return healthDelta;
    }

    public long getTrackingDuration() {
        return System.currentTimeMillis() - firstTrackedTime;
    }

    /**
     * Returns the timestamp of the last update.
     * Useful for detecting stale tracked entities.
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    // === Telemetry Accessors ===

    /**
     * Returns the total distance moved by this entity since tracking began.
     * Useful for analyzing entity drift/movement patterns.
     */
    public double getTotalDistanceMoved() {
        return totalDistanceMoved;
    }

    /**
     * Returns the total damage taken by this entity since tracking began.
     * Useful for DPS calculations and combat analysis.
     */
    public float getTotalDamageTaken() {
        return totalDamageTaken;
    }

    /**
     * Returns the previous position before the last update.
     * Useful for calculating movement vectors.
     */
    @Nullable
    public Vec3 getPreviousPosition() {
        return previousPosition;
    }

    /**
     * Calculates the movement vector from previous to current position.
     * Returns Vec3.ZERO if no previous position is available.
     */
    public Vec3 getMovementVector() {
        Vec3 prev = previousPosition;
        Vec3 current = lastKnownPosition;
        if (prev == null || current == null) {
            return Vec3.ZERO;
        }
        return current.subtract(prev);
    }

    // === Pehkui ===

    public boolean hasPehkuiModification() {
        return pehkuiScale != null && Math.abs(pehkuiScale - 1.0f) > 0.01f;
    }

    @Nullable
    public Float getPehkuiScale() {
        return pehkuiScale;
    }

    @Nullable
    public Float getPehkuiHitboxScale() {
        return pehkuiHitboxScale;
    }

    // === Utility ===

    /**
     * Distanza dal player.
     */
    public double getDistanceToPlayer() {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return -1;

        LivingEntity entity = entityRef.get();
        if (entity == null) return -1;

        return entity.distanceTo(player);
    }

    /**
     * Verifica se l'entità è ancora valida.
     */
    public boolean isValid() {
        LivingEntity entity = entityRef.get();
        return entity != null && entity.isAlive();
    }

    /**
     * Verifica se l'entità è un mob ostile.
     */
    public boolean isHostile() {
        LivingEntity entity = entityRef.get();
        if (entity instanceof Mob mob) {
            return mob.getTarget() != null;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("TrackedEntity[%s, HP:%.1f/%.1f, Armor:%.1f, LoS:%s]",
            entityName, currentHealth, maxHealth, armorValue, hasLineOfSight ? "YES" : "NO");
    }
}
