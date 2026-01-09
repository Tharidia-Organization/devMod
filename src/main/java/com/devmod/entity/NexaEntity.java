package com.devmod.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.devmod.config.Config;

/**
 * The Nexa entity - an AI avatar for the Nexus hub.
 *
 * <p>A floating, ethereal entity that:
 * <ul>
 *   <li>Floats in place (no gravity)</li>
 *   <li>Looks at nearby players</li>
 *   <li>Opens a dialog when right-clicked</li>
 *   <li>Can display a player skin</li>
 * </ul>
 *
 * <p>Replaces the EasyNPC-dependent system for a native, reliable implementation.
 */
public class NexaEntity extends PathfinderMob {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexaEntity.class);

    // Entity tag for identification
    public static final String NEXA_TAG = "devmod_nexa";

    // Synched data for client rendering
    private static final EntityDataAccessor<Optional<UUID>> SKIN_UUID =
        SynchedEntityData.defineId(NexaEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> SKIN_NAME =
        SynchedEntityData.defineId(NexaEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> FLOATING =
        SynchedEntityData.defineId(NexaEntity.class, EntityDataSerializers.BOOLEAN);

    // Animation state
    private double floatPhase = 0.0;
    private int particleTick = 0;
    private double baseY = Double.NaN;

    public NexaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
        this.setPersistenceRequired();
        this.addTag(NEXA_TAG);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_UUID, Optional.empty());
        builder.define(SKIN_NAME, "");
        builder.define(FLOATING, true);
    }

    @Override
    protected void registerGoals() {
        // Minimal AI - just float and look at players
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    /**
     * Create attribute supplier for this entity.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0)  // Stationary
            .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public void tick() {
        super.tick();

        // Store base Y on first tick if not set
        if (Double.isNaN(baseY)) {
            baseY = this.getY();
        }

        // Floating animation
        if (isFloating() && !this.level().isClientSide) {
            updateFloatingAnimation();
        }

        // Ambient particles (server-side)
        if (!this.level().isClientSide) {
            updateAmbientParticles();
        }
    }

    /**
     * Update the floating Y position using a sine wave.
     */
    private void updateFloatingAnimation() {
        double floatAmplitude = getFloatAmplitude();
        double floatSpeed = getFloatSpeed();

        floatPhase += floatSpeed;
        if (floatPhase > Math.PI * 2) {
            floatPhase -= Math.PI * 2;
        }

        double yOffset = Math.sin(floatPhase) * floatAmplitude;
        this.setPos(this.getX(), baseY + yOffset, this.getZ());
    }

    /**
     * Spawn ambient particles around the entity.
     */
    private void updateAmbientParticles() {
        if (!areParticlesEnabled()) {
            return;
        }

        particleTick++;
        int interval = getParticleInterval();
        if (particleTick >= interval) {
            particleTick = 0;
            spawnAmbientParticles();
        }
    }

    private void spawnAmbientParticles() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // End rod particles - floating mystical effect
        serverLevel.sendParticles(
            net.minecraft.core.particles.ParticleTypes.END_ROD,
            this.getX(), this.getY() + 1.8, this.getZ(),
            3, 0.3, 0.5, 0.3, 0.02
        );

        // Enchant particles - swirling effect
        serverLevel.sendParticles(
            net.minecraft.core.particles.ParticleTypes.ENCHANT,
            this.getX(), this.getY() + 0.5, this.getZ(),
            5, 0.5, 0.3, 0.5, 0.1
        );
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            LOGGER.debug("[Nexa] Player {} interacted with Nexa", player.getName().getString());
            com.devmod.runtime.network.NexusNetworkHandler.openGreetingDialog(serverPlayer);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // === Skin Management ===

    /**
     * Set the skin by player name (will be resolved to UUID client-side).
     */
    public void setSkinName(@Nonnull String name) {
        this.entityData.set(SKIN_NAME, Objects.requireNonNull(name, "name"));
    }

    /**
     * Get the skin player name.
     */
    @Nonnull
    public String getSkinName() {
        return this.entityData.get(SKIN_NAME);
    }

    /**
     * Set the skin by UUID directly.
     */
    public void setSkinUUID(@Nullable UUID uuid) {
        this.entityData.set(SKIN_UUID, Optional.ofNullable(uuid));
    }

    /**
     * Get the skin UUID.
     */
    @Nullable
    public UUID getSkinUUID() {
        return this.entityData.get(SKIN_UUID).orElse(null);
    }

    /**
     * Check if floating animation is enabled.
     */
    public boolean isFloating() {
        return this.entityData.get(FLOATING);
    }

    /**
     * Set floating animation enabled/disabled.
     */
    public void setFloating(boolean floating) {
        this.entityData.set(FLOATING, floating);
    }

    /**
     * Reset the base Y position (call when repositioning).
     */
    public void resetBaseY(double newBaseY) {
        this.baseY = newBaseY;
        this.floatPhase = 0.0;
    }

    // === Config Accessors ===

    private double getFloatAmplitude() {
        try {
            return Config.NEXUS_AVATAR_FLOAT_AMPLITUDE.get();
        } catch (Exception e) {
            return 0.3;
        }
    }

    private double getFloatSpeed() {
        try {
            return Config.NEXUS_AVATAR_FLOAT_SPEED.get();
        } catch (Exception e) {
            return 0.05;
        }
    }

    private boolean areParticlesEnabled() {
        try {
            return Config.NEXUS_AVATAR_PARTICLES_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    private int getParticleInterval() {
        try {
            return Config.NEXUS_AVATAR_PARTICLE_INTERVAL.get();
        } catch (Exception e) {
            return 10;
        }
    }

    // === Persistence ===

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        String skinName = getSkinName();
        if (!skinName.isEmpty()) {
            tag.putString("SkinName", skinName);
        }

        UUID skinUUID = getSkinUUID();
        if (skinUUID != null) {
            tag.putUUID("SkinUUID", skinUUID);
        }

        tag.putBoolean("Floating", isFloating());
        tag.putDouble("BaseY", baseY);
    }

    @Override
    public void readAdditionalSaveData(@Nonnull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("SkinName")) {
            setSkinName(tag.getString("SkinName"));
        }

        if (tag.hasUUID("SkinUUID")) {
            setSkinUUID(tag.getUUID("SkinUUID"));
        }

        if (tag.contains("Floating")) {
            setFloating(tag.getBoolean("Floating"));
        }

        if (tag.contains("BaseY")) {
            baseY = tag.getDouble("BaseY");
        }
    }

    // === Behavior Overrides ===

    @Override
    public boolean isPushable() {
        return false;  // Cannot be pushed
    }

    @Override
    protected void pushEntities() {
        // Don't push other entities
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;  // Can be targeted/clicked
    }

    @Override
    public boolean isNoAi() {
        return false;  // AI is enabled but minimal
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;  // Never despawn
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }
}
