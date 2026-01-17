package com.devmod.clone.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.devmod.clone.entity.ai.CloneFollowOwnerGoal;
import com.devmod.npc.data.NpcAppearance;
import com.devmod.npc.data.NpcBehavior;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.network.NpcNetworkHandler;

/**
 * Player clone entity - a companion that follows and fights for its owner.
 * Created by the REFORMER when cloning player data.
 *
 * <p>Features:
 * <ul>
 *   <li>Follows owner like a tamed wolf</li>
 *   <li>Guards a position when ordered to sit</li>
 *   <li>Attacks hostile mobs and owner's targets</li>
 *   <li>Uses player skin from original UUID</li>
 * </ul>
 */
public final class PlayerCloneEntity extends TamableAnimal {

    // === Clone Mode SynchedData ===
    private static final EntityDataAccessor<Optional<UUID>> DATA_ORIGINAL_UUID =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.OPTIONAL_UUID, "OPTIONAL_UUID"));
    private static final EntityDataAccessor<String> DATA_SKIN_NAME =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.STRING, "STRING"));
    private static final EntityDataAccessor<Integer> DATA_BEHAVIOR_MODE =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.INT, "INT"));

    // === NPC Mode SynchedData ===
    private static final EntityDataAccessor<Boolean> DATA_IS_NPC =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.BOOLEAN, "BOOLEAN"));
    private static final EntityDataAccessor<String> DATA_NPC_DISPLAY_NAME =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.STRING, "STRING"));
    private static final EntityDataAccessor<String> DATA_DIALOG_SET_ID =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.STRING, "STRING"));
    private static final EntityDataAccessor<Boolean> DATA_FLOATING =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.BOOLEAN, "BOOLEAN"));
    private static final EntityDataAccessor<Float> DATA_FLOAT_AMPLITUDE =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.FLOAT, "FLOAT"));
    private static final EntityDataAccessor<Float> DATA_FLOAT_SPEED =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.FLOAT, "FLOAT"));
    private static final EntityDataAccessor<Boolean> DATA_PARTICLES =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.BOOLEAN, "BOOLEAN"));
    private static final EntityDataAccessor<Integer> DATA_PARTICLE_INTERVAL =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.INT, "INT"));
    private static final EntityDataAccessor<String> DATA_PARTICLE_TYPE_ID =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.STRING, "STRING"));
    private static final EntityDataAccessor<Boolean> DATA_GLOW =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.BOOLEAN, "BOOLEAN"));
    private static final EntityDataAccessor<Boolean> DATA_LOOK_AT_PLAYER =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.BOOLEAN, "BOOLEAN"));
    private static final EntityDataAccessor<Boolean> DATA_INVULNERABLE_NPC =
        SynchedEntityData.defineId(PlayerCloneEntity.class,
            Objects.requireNonNull(EntityDataSerializers.BOOLEAN, "BOOLEAN"));

    // === Behavior Mode Constants ===
    public static final int MODE_FOLLOW = 0;
    public static final int MODE_GUARD = 1;
    public static final int MODE_ATTACK = 2;
    public static final int MODE_NPC = 3;

    // === NPC Instance Fields ===
    @Nullable
    private UUID npcConfigId;
    private float floatPhase = 0;
    private double baseY = 0;

    public PlayerCloneEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setTame(true, false);
    }

    @Override
    protected void defineSynchedData(@Nonnull SynchedEntityData.Builder builder) {
        super.defineSynchedData(Objects.requireNonNull(builder, "builder"));
        // Clone mode data
        builder.define(Objects.requireNonNull(DATA_ORIGINAL_UUID, "DATA_ORIGINAL_UUID"),
            Objects.requireNonNull(Optional.empty(), "Optional.empty"));
        builder.define(Objects.requireNonNull(DATA_SKIN_NAME, "DATA_SKIN_NAME"), "");
        builder.define(Objects.requireNonNull(DATA_BEHAVIOR_MODE, "DATA_BEHAVIOR_MODE"), MODE_FOLLOW);

        // NPC mode data
        builder.define(Objects.requireNonNull(DATA_IS_NPC, "DATA_IS_NPC"), false);
        builder.define(Objects.requireNonNull(DATA_NPC_DISPLAY_NAME, "DATA_NPC_DISPLAY_NAME"), "");
        builder.define(Objects.requireNonNull(DATA_DIALOG_SET_ID, "DATA_DIALOG_SET_ID"), "");
        builder.define(Objects.requireNonNull(DATA_FLOATING, "DATA_FLOATING"), false);
        builder.define(Objects.requireNonNull(DATA_FLOAT_AMPLITUDE, "DATA_FLOAT_AMPLITUDE"), 0.1f);
        builder.define(Objects.requireNonNull(DATA_FLOAT_SPEED, "DATA_FLOAT_SPEED"), 1.0f);
        builder.define(Objects.requireNonNull(DATA_PARTICLES, "DATA_PARTICLES"), false);
        builder.define(Objects.requireNonNull(DATA_PARTICLE_INTERVAL, "DATA_PARTICLE_INTERVAL"), 20);
        builder.define(Objects.requireNonNull(DATA_PARTICLE_TYPE_ID, "DATA_PARTICLE_TYPE_ID"), "");
        builder.define(Objects.requireNonNull(DATA_GLOW, "DATA_GLOW"), false);
        builder.define(Objects.requireNonNull(DATA_LOOK_AT_PLAYER, "DATA_LOOK_AT_PLAYER"), true);
        builder.define(Objects.requireNonNull(DATA_INVULNERABLE_NPC, "DATA_INVULNERABLE_NPC"), true);
    }

    @Override
    public boolean isFood(@Nonnull net.minecraft.world.item.ItemStack stack) {
        return false; // Clones don't eat or breed
    }

    @Override
    protected void registerGoals() {
        // Swimming
        goalSelector.addGoal(0, new FloatGoal(this));

        // Sit when ordered
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));

        // Follow owner
        goalSelector.addGoal(2, new CloneFollowOwnerGoal(this, 1.0, 10.0F, 2.0F));

        // Combat
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));

        // Idle behavior
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // Targeting
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
            .add(Objects.requireNonNull(Attributes.MAX_HEALTH, "MAX_HEALTH"), 40.0)
            .add(Objects.requireNonNull(Attributes.MOVEMENT_SPEED, "MOVEMENT_SPEED"), 0.3)
            .add(Objects.requireNonNull(Attributes.ATTACK_DAMAGE, "ATTACK_DAMAGE"), 4.0)
            .add(Objects.requireNonNull(Attributes.ARMOR, "ARMOR"), 2.0)
            .add(Objects.requireNonNull(Attributes.FOLLOW_RANGE, "FOLLOW_RANGE"), 32.0);
    }

    // === Original Player Data ===

    public void setOriginalPlayer(@Nullable UUID uuid, @Nullable String name) {
        entityData.set(Objects.requireNonNull(DATA_ORIGINAL_UUID, "DATA_ORIGINAL_UUID"),
            Objects.requireNonNull(Optional.ofNullable(uuid), "Optional"));
        entityData.set(Objects.requireNonNull(DATA_SKIN_NAME, "DATA_SKIN_NAME"), name != null ? name : "");
    }

    @Nullable
    public UUID getOriginalPlayerUUID() {
        return entityData.get(Objects.requireNonNull(DATA_ORIGINAL_UUID, "DATA_ORIGINAL_UUID")).orElse(null);
    }

    public String getSkinName() {
        return entityData.get(Objects.requireNonNull(DATA_SKIN_NAME, "DATA_SKIN_NAME"));
    }

    // === Behavior Mode ===

    public int getBehaviorMode() {
        return entityData.get(Objects.requireNonNull(DATA_BEHAVIOR_MODE, "DATA_BEHAVIOR_MODE"));
    }

    public void setBehaviorMode(int mode) {
        entityData.set(Objects.requireNonNull(DATA_BEHAVIOR_MODE, "DATA_BEHAVIOR_MODE"), Math.max(0, Math.min(2, mode)));
    }

    public void cycleBehaviorMode() {
        int current = getBehaviorMode();
        setBehaviorMode((current + 1) % 3);
    }

    // === NPC Mode ===

    /**
     * Returns true if this entity is in NPC mode.
     */
    public boolean isNpc() {
        return entityData.get(Objects.requireNonNull(DATA_IS_NPC, "DATA_IS_NPC"));
    }

    /**
     * Returns the NPC display name, or empty string if not set.
     */
    public String getNpcDisplayName() {
        return entityData.get(Objects.requireNonNull(DATA_NPC_DISPLAY_NAME, "DATA_NPC_DISPLAY_NAME"));
    }

    /**
     * Returns the dialog set ID for this NPC, or empty string if not set.
     */
    public String getDialogSetId() {
        return entityData.get(Objects.requireNonNull(DATA_DIALOG_SET_ID, "DATA_DIALOG_SET_ID"));
    }

    /**
     * Returns true if this NPC should float.
     */
    public boolean isFloating() {
        return entityData.get(Objects.requireNonNull(DATA_FLOATING, "DATA_FLOATING"));
    }

    /**
     * Returns the float amplitude for floating animation.
     */
    public float getFloatAmplitude() {
        return entityData.get(Objects.requireNonNull(DATA_FLOAT_AMPLITUDE, "DATA_FLOAT_AMPLITUDE"));
    }

    /**
     * Returns the float speed for floating animation.
     */
    public float getFloatSpeed() {
        return entityData.get(Objects.requireNonNull(DATA_FLOAT_SPEED, "DATA_FLOAT_SPEED"));
    }

    /**
     * Returns true if this NPC should emit particles.
     */
    public boolean hasParticles() {
        return entityData.get(Objects.requireNonNull(DATA_PARTICLES, "DATA_PARTICLES"));
    }

    /**
     * Returns the particle emission interval in ticks.
     */
    public int getParticleInterval() {
        return entityData.get(Objects.requireNonNull(DATA_PARTICLE_INTERVAL, "DATA_PARTICLE_INTERVAL"));
    }

    /**
     * Returns the particle type ID for this NPC.
     */
    public String getParticleTypeId() {
        return entityData.get(Objects.requireNonNull(DATA_PARTICLE_TYPE_ID, "DATA_PARTICLE_TYPE_ID"));
    }

    /**
     * Returns true if this NPC has glow effect.
     */
    public boolean hasGlow() {
        return entityData.get(Objects.requireNonNull(DATA_GLOW, "DATA_GLOW"));
    }

    /**
     * Returns true if this NPC should look at nearby players.
     */
    public boolean shouldLookAtPlayer() {
        return entityData.get(Objects.requireNonNull(DATA_LOOK_AT_PLAYER, "DATA_LOOK_AT_PLAYER"));
    }

    /**
     * Returns true if this NPC is invulnerable.
     */
    public boolean isNpcInvulnerable() {
        return entityData.get(Objects.requireNonNull(DATA_INVULNERABLE_NPC, "DATA_INVULNERABLE_NPC"));
    }

    /**
     * Returns the NPC configuration ID, or null if not linked.
     */
    @Nullable
    public UUID getNpcConfigId() {
        return npcConfigId;
    }

    /**
     * Sets this entity to NPC mode using the given configuration.
     */
    public void setNpcMode(NpcConfiguration config) {
        this.npcConfigId = config.id();

        // Set NPC flag and behavior mode
        entityData.set(Objects.requireNonNull(DATA_IS_NPC, "DATA_IS_NPC"), true);
        entityData.set(Objects.requireNonNull(DATA_BEHAVIOR_MODE, "DATA_BEHAVIOR_MODE"), MODE_NPC);

        // Set display info
        entityData.set(Objects.requireNonNull(DATA_NPC_DISPLAY_NAME, "DATA_NPC_DISPLAY_NAME"), config.displayName());
        entityData.set(Objects.requireNonNull(DATA_DIALOG_SET_ID, "DATA_DIALOG_SET_ID"),
            Objects.requireNonNull(config.dialogSetId() != null ? config.dialogSetId() : "", "dialogSetId"));

        // Set skin from config
        setOriginalPlayer(config.skinPlayerUUID(), config.skinPlayerName());

        // Set behavior from config
        NpcBehavior behavior = config.behavior();
        entityData.set(Objects.requireNonNull(DATA_FLOATING, "DATA_FLOATING"), behavior.floating());
        entityData.set(Objects.requireNonNull(DATA_FLOAT_AMPLITUDE, "DATA_FLOAT_AMPLITUDE"), behavior.floatAmplitude());
        entityData.set(Objects.requireNonNull(DATA_FLOAT_SPEED, "DATA_FLOAT_SPEED"), behavior.floatSpeed());
        entityData.set(Objects.requireNonNull(DATA_LOOK_AT_PLAYER, "DATA_LOOK_AT_PLAYER"), behavior.lookAtPlayer());
        entityData.set(Objects.requireNonNull(DATA_INVULNERABLE_NPC, "DATA_INVULNERABLE_NPC"), behavior.invulnerable());

        // Set appearance from config
        NpcAppearance appearance = config.appearance();
        entityData.set(Objects.requireNonNull(DATA_PARTICLES, "DATA_PARTICLES"), appearance.particlesEnabled());
        entityData.set(Objects.requireNonNull(DATA_PARTICLE_INTERVAL, "DATA_PARTICLE_INTERVAL"), appearance.particleInterval());
        entityData.set(Objects.requireNonNull(DATA_PARTICLE_TYPE_ID, "DATA_PARTICLE_TYPE_ID"),
            Objects.requireNonNull(appearance.particleTypeId().toString(), "particleTypeId"));
        entityData.set(Objects.requireNonNull(DATA_GLOW, "DATA_GLOW"), appearance.glowEffect());

        // Configure entity for NPC behavior
        setNoAi(true);
        if (behavior.floating()) {
            setNoGravity(true);
            baseY = getY();
        }
        setGlowingTag(appearance.glowEffect());

        // Sit to prevent random wandering
        setOrderedToSit(true);

        // NPC protections (from NexaEntity for full feature parity)
        this.setSilent(true);           // NPCs don't make ambient sounds
        this.setPersistenceRequired();  // NPCs never despawn
    }

    /**
     * Tick NPC-specific behavior (floating, particles, look at player).
     */
    private void tickNpc() {
        if (!isNpc()) {
            return;
        }

        // Floating animation
        if (isFloating() && !level().isClientSide) {
            floatPhase += getFloatSpeed() * 0.1f;
            double newY = baseY + Math.sin(floatPhase) * getFloatAmplitude();
            setPos(getX(), newY, getZ());
        }

        // Particle spawning (client-side)
        if (level().isClientSide && hasParticles()) {
            int interval = Math.max(1, getParticleInterval());
            if (tickCount % interval == 0) {
                String particleId = getParticleTypeId();
                ParticleOptions particle = Objects.requireNonNull(ParticleTypes.ENCHANT, "ENCHANT"); // default
                if (!particleId.isEmpty()) {
                    try {
                        ResourceLocation loc = ResourceLocation.parse(particleId);
                        var particleType = BuiltInRegistries.PARTICLE_TYPE.get(loc);
                        if (particleType instanceof ParticleOptions options) {
                            particle = options;
                        }
                    } catch (Exception ignored) {
                        // Use default particle
                    }
                }
                double px = getX() + (random.nextDouble() - 0.5) * 0.8;
                double py = getY() + random.nextDouble() * 1.8;
                double pz = getZ() + (random.nextDouble() - 0.5) * 0.8;
                level().addParticle(Objects.requireNonNull(particle, "particle"), px, py, pz, 0, 0.05, 0);
            }
        }

        // Look at nearest player
        if (shouldLookAtPlayer()) {
            Player nearestPlayer = level().getNearestPlayer(this, 8.0);
            if (nearestPlayer != null) {
                getLookControl().setLookAt(nearestPlayer, 30.0f, 30.0f);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        tickNpc();
    }

    // === Interaction ===

    @Override
    @Nonnull
    public InteractionResult mobInteract(@Nonnull Player player, @Nonnull InteractionHand hand) {
        // NPC mode: open dialog if configured
        if (isNpc()) {
            String dialogSetId = getDialogSetId();
            if (!dialogSetId.isEmpty() && player instanceof ServerPlayer serverPlayer) {
                // Open dialog via NpcNetworkHandler
                NpcNetworkHandler.INSTANCE.openDialog(serverPlayer,
                    Objects.requireNonNull(this.getUUID(), "getUUID"), dialogSetId);
                return Objects.requireNonNull(InteractionResult.SUCCESS, "SUCCESS");
            }
            return Objects.requireNonNull(InteractionResult.PASS, "PASS");
        }

        // Clone mode: owner interaction
        if (isOwnedBy(player)) {
            if (player.isShiftKeyDown()) {
                // Shift-click to toggle sit/stand
                setOrderedToSit(!isOrderedToSit());
                navigation.stop();
                setTarget(null);
                return Objects.requireNonNull(InteractionResult.SUCCESS, "SUCCESS");
            } else {
                // Normal click to cycle behavior mode
                cycleBehaviorMode();
                int mode = getBehaviorMode();
                String modeKey = switch (mode) {
                    case MODE_FOLLOW -> "message.devmod.clone.mode_follow";
                    case MODE_GUARD -> "message.devmod.clone.mode_guard";
                    case MODE_ATTACK -> "message.devmod.clone.mode_attack";
                    default -> "message.devmod.clone.mode_follow";
                };
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable(modeKey), "translatable"),
                    true
                );
                return Objects.requireNonNull(InteractionResult.SUCCESS, "SUCCESS");
            }
        }
        return Objects.requireNonNull(super.mobInteract(player, hand), "mobInteract");
    }

    // === Invulnerability ===

    @Override
    public boolean isInvulnerable() {
        if (isNpc() && isNpcInvulnerable()) {
            return true;
        }
        return super.isInvulnerable();
    }

    @Override
    public boolean hurt(@Nonnull DamageSource source, float amount) {
        if (isNpc() && isNpcInvulnerable()) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean fireImmune() {
        // NPCs are immune to fire damage (from NexaEntity)
        return isNpc() || super.fireImmune();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // NPCs never despawn (from NexaEntity)
        return isNpc() ? false : super.removeWhenFarAway(distanceToClosestPlayer);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        // NPCs don't despawn in peaceful mode (from NexaEntity)
        return isNpc() ? false : super.shouldDespawnInPeaceful();
    }

    @Override
    public boolean isPushable() {
        // NPCs cannot be pushed (from NexaEntity)
        return isNpc() ? false : super.isPushable();
    }

    @Override
    protected void pushEntities() {
        // NPCs don't push other entities (from NexaEntity)
        if (!isNpc()) {
            super.pushEntities();
        }
    }

    // === Display Name ===

    @Override
    @Nonnull
    public Component getDisplayName() {
        if (isNpc()) {
            String npcName = getNpcDisplayName();
            if (!npcName.isEmpty()) {
                return Objects.requireNonNull(Component.literal(npcName), "literal");
            }
        }
        return Objects.requireNonNull(super.getDisplayName(), "getDisplayName");
    }

    // === Combat ===

    @Override
    public boolean wantsToAttack(@Nonnull LivingEntity target, @Nonnull LivingEntity owner) {
        // Don't attack other player clones owned by the same owner
        if (target instanceof PlayerCloneEntity otherClone) {
            return !otherClone.isOwnedBy(owner);
        }
        // Don't attack tamed animals owned by the owner
        if (target instanceof TamableAnimal tamed && tamed.isTame()) {
            return !tamed.isOwnedBy(owner);
        }
        return true;
    }

    // === NBT ===

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID originalUuid = getOriginalPlayerUUID();
        if (originalUuid != null) {
            tag.putUUID("OriginalUUID", originalUuid);
        }
        tag.putString("SkinName", Objects.requireNonNull(getSkinName(), "SkinName"));
        tag.putInt("BehaviorMode", getBehaviorMode());

        // NPC mode data
        if (isNpc()) {
            tag.putBoolean("IsNpc", true);
            if (npcConfigId != null) {
                tag.putUUID("NpcConfigId", npcConfigId);
            }
            tag.putString("NpcDisplayName", Objects.requireNonNull(getNpcDisplayName(), "NpcDisplayName"));
            tag.putString("DialogSetId", Objects.requireNonNull(getDialogSetId(), "DialogSetId"));
            tag.putBoolean("Floating", isFloating());
            tag.putFloat("FloatAmplitude", getFloatAmplitude());
            tag.putFloat("FloatSpeed", getFloatSpeed());
            tag.putDouble("BaseY", baseY);
            tag.putBoolean("Particles", hasParticles());
            tag.putInt("ParticleInterval", getParticleInterval());
            tag.putString("ParticleTypeId", Objects.requireNonNull(getParticleTypeId(), "ParticleTypeId"));
            tag.putBoolean("Glow", hasGlow());
            tag.putBoolean("LookAtPlayer", shouldLookAtPlayer());
            tag.putBoolean("NpcInvulnerable", isNpcInvulnerable());
        }
    }

    @Override
    public void readAdditionalSaveData(@Nonnull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OriginalUUID")) {
            UUID uuid = tag.getUUID("OriginalUUID");
            String name = Objects.requireNonNull(tag.getString("SkinName"), "SkinName");
            setOriginalPlayer(uuid, name);
        }
        if (tag.contains("BehaviorMode")) {
            setBehaviorMode(tag.getInt("BehaviorMode"));
        }

        // NPC mode data
        if (tag.getBoolean("IsNpc")) {
            entityData.set(Objects.requireNonNull(DATA_IS_NPC, "DATA_IS_NPC"), true);
            if (tag.hasUUID("NpcConfigId")) {
                npcConfigId = tag.getUUID("NpcConfigId");
            }
            entityData.set(Objects.requireNonNull(DATA_NPC_DISPLAY_NAME, "DATA_NPC_DISPLAY_NAME"),
                Objects.requireNonNull(tag.getString("NpcDisplayName"), "NpcDisplayName"));
            entityData.set(Objects.requireNonNull(DATA_DIALOG_SET_ID, "DATA_DIALOG_SET_ID"),
                Objects.requireNonNull(tag.getString("DialogSetId"), "DialogSetId"));
            entityData.set(Objects.requireNonNull(DATA_FLOATING, "DATA_FLOATING"), tag.getBoolean("Floating"));
            entityData.set(Objects.requireNonNull(DATA_FLOAT_AMPLITUDE, "DATA_FLOAT_AMPLITUDE"), tag.getFloat("FloatAmplitude"));
            entityData.set(Objects.requireNonNull(DATA_FLOAT_SPEED, "DATA_FLOAT_SPEED"), tag.getFloat("FloatSpeed"));
            baseY = tag.getDouble("BaseY");
            entityData.set(Objects.requireNonNull(DATA_PARTICLES, "DATA_PARTICLES"), tag.getBoolean("Particles"));
            entityData.set(Objects.requireNonNull(DATA_PARTICLE_INTERVAL, "DATA_PARTICLE_INTERVAL"), tag.getInt("ParticleInterval"));
            entityData.set(Objects.requireNonNull(DATA_PARTICLE_TYPE_ID, "DATA_PARTICLE_TYPE_ID"),
                Objects.requireNonNull(tag.getString("ParticleTypeId"), "ParticleTypeId"));
            entityData.set(Objects.requireNonNull(DATA_GLOW, "DATA_GLOW"), tag.getBoolean("Glow"));
            entityData.set(Objects.requireNonNull(DATA_LOOK_AT_PLAYER, "DATA_LOOK_AT_PLAYER"), tag.getBoolean("LookAtPlayer"));
            entityData.set(Objects.requireNonNull(DATA_INVULNERABLE_NPC, "DATA_INVULNERABLE_NPC"), tag.getBoolean("NpcInvulnerable"));

            // Re-apply NPC settings
            setNoAi(true);
            if (isFloating()) {
                setNoGravity(true);
            }
            setGlowingTag(hasGlow());
            setOrderedToSit(true);
        }
    }

    // === Breeding (disabled) ===

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(@Nonnull ServerLevel level, @Nonnull AgeableMob mate) {
        return null; // Clones can't breed
    }

    // === Sounds ===

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null; // Silent by default
    }

    @Override
    protected SoundEvent getHurtSound(@Nonnull DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}
