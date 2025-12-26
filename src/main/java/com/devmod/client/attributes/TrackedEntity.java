package com.devmod.client.attributes;

import java.lang.ref.WeakReference;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.integration.ModIntegrationManager;
@OnlyIn(Dist.CLIENT)
public class TrackedEntity {

    // === Entity Reference ===
    private final WeakReference<LivingEntity> entityRef;
    private final int entityId;
    private final String entityName;
    private final String entityType;

    // === Attribute Snapshot ===
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

    // === Line of Sight State ===
    private boolean hasLineOfSight;
    private Vec3 lastKnownPosition;
    private Vec3 lastBlockedPoint;

    // === Temporal Tracking ===
    private long firstTrackedTime;
    private long lastUpdateTime;
    private float healthDelta; // Health change since last update

    // === Telemetry/Drift tracking ===
    private Vec3 previousPosition;
    private double totalDistanceMoved = 0.0;
    private float totalDamageTaken = 0.0f;


    public TrackedEntity(LivingEntity entity) {
        this.entityRef = new WeakReference<>(entity);
        this.entityId = entity.getId();
        this.entityName = entity.getName().getString();
        this.entityType = entity.getType().getDescriptionId();
        this.firstTrackedTime = System.currentTimeMillis();
        this.lastUpdateTime = this.firstTrackedTime;
        this.lastKnownPosition = entity.position();
        this.previousPosition = entity.position();

        // Initialize base attributes directly to avoid this-escape
        this.currentHealth = entity.getHealth();
        this.maxHealth = entity.getMaxHealth();
        this.armorValue = entity.getArmorValue();
        initializeAttributes(entity);
    }

    /** Initialize advanced attributes (called from constructor). */
    private void initializeAttributes(LivingEntity entity) {
        var armorToughnessAttr = entity.getAttribute(Objects.requireNonNull(Attributes.ARMOR_TOUGHNESS));
        this.armorToughness = armorToughnessAttr != null ? (float) armorToughnessAttr.getValue() : 0f;

        var moveSpeedAttr = entity.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
        this.movementSpeed = moveSpeedAttr != null ? moveSpeedAttr.getValue() : 0;

        var attackDmgAttr = entity.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        this.attackDamage = attackDmgAttr != null ? attackDmgAttr.getValue() : 0;

        var attackSpeedAttr = entity.getAttribute(Objects.requireNonNull(Attributes.ATTACK_SPEED));
        this.attackSpeed = attackSpeedAttr != null ? attackSpeedAttr.getValue() : 0;

        var knockbackAttr = entity.getAttribute(Objects.requireNonNull(Attributes.KNOCKBACK_RESISTANCE));
        this.knockbackResistance = knockbackAttr != null ? knockbackAttr.getValue() : 0;

        this.pehkuiScale = ModIntegrationManager.getPehkuiScale(entity);
        this.pehkuiHitboxScale = ModIntegrationManager.getPehkuiHitboxScale(entity);
    }

    /**
     * Updates all entity attributes.
     */
    public void update() {
        LivingEntity entity = entityRef.get();
        if (entity == null || !entity.isAlive()) return;

        lastUpdateTime = System.currentTimeMillis();

        // Save health delta
        float oldHealth = currentHealth;

        // Base attributes
        currentHealth = entity.getHealth();
        maxHealth = entity.getMaxHealth();
        armorValue = entity.getArmorValue();

        // Advanced attributes (nullable safe)
        var armorToughnessAttr = entity.getAttribute(Objects.requireNonNull(Attributes.ARMOR_TOUGHNESS));
        armorToughness = armorToughnessAttr != null ? (float) armorToughnessAttr.getValue() : 0f;

        var moveSpeedAttr = entity.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
        movementSpeed = moveSpeedAttr != null ? moveSpeedAttr.getValue() : 0;

        var attackDmgAttr = entity.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        attackDamage = attackDmgAttr != null ? attackDmgAttr.getValue() : 0;

        var attackSpeedAttr = entity.getAttribute(Objects.requireNonNull(Attributes.ATTACK_SPEED));
        attackSpeed = attackSpeedAttr != null ? attackSpeedAttr.getValue() : 0;

        var knockbackAttr = entity.getAttribute(Objects.requireNonNull(Attributes.KNOCKBACK_RESISTANCE));
        knockbackResistance = knockbackAttr != null ? knockbackAttr.getValue() : 0;

        // Pehkui
        pehkuiScale = ModIntegrationManager.getPehkuiScale(entity);
        pehkuiHitboxScale = ModIntegrationManager.getPehkuiHitboxScale(entity);

        // Calculate health delta
        if (oldHealth > 0) {
            healthDelta = currentHealth - oldHealth;
            // Track cumulative damage for telemetry
            if (healthDelta < 0) {
                totalDamageTaken += Math.abs(healthDelta);
            }
        }

        // Update position and track drift
        Vec3 currentPos = Objects.requireNonNull(entity.position());
        if (previousPosition != null) {
            totalDistanceMoved += previousPosition.distanceTo(currentPos);
        }
        previousPosition = lastKnownPosition;
        lastKnownPosition = currentPos;

        // Update Line of Sight
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

        Vec3 playerEye = Objects.requireNonNull(player.getEyePosition());
        Vec3 entityEye = Objects.requireNonNull(entity.getEyePosition());

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
     * Distance from player.
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
     * Checks if the entity is still valid.
     */
    public boolean isValid() {
        LivingEntity entity = entityRef.get();
        return entity != null && entity.isAlive();
    }

    /**
     * Checks if the entity is a hostile mob.
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
