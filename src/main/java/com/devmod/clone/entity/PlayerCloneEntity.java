package com.devmod.clone.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
public class PlayerCloneEntity extends TamableAnimal {

    private static final EntityDataAccessor<Optional<UUID>> DATA_ORIGINAL_UUID =
        SynchedEntityData.defineId(PlayerCloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_SKIN_NAME =
        SynchedEntityData.defineId(PlayerCloneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_BEHAVIOR_MODE =
        SynchedEntityData.defineId(PlayerCloneEntity.class, EntityDataSerializers.INT);

    public static final int MODE_FOLLOW = 0;
    public static final int MODE_GUARD = 1;
    public static final int MODE_ATTACK = 2;

    public PlayerCloneEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setTame(true, false);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ORIGINAL_UUID, Optional.empty());
        builder.define(DATA_SKIN_NAME, "");
        builder.define(DATA_BEHAVIOR_MODE, MODE_FOLLOW);
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
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.ARMOR, 2.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    // === Original Player Data ===

    public void setOriginalPlayer(@Nullable UUID uuid, @Nullable String name) {
        entityData.set(DATA_ORIGINAL_UUID, Optional.ofNullable(uuid));
        entityData.set(DATA_SKIN_NAME, name != null ? name : "");
    }

    @Nullable
    public UUID getOriginalPlayerUUID() {
        return entityData.get(DATA_ORIGINAL_UUID).orElse(null);
    }

    public String getSkinName() {
        return entityData.get(DATA_SKIN_NAME);
    }

    // === Behavior Mode ===

    public int getBehaviorMode() {
        return entityData.get(DATA_BEHAVIOR_MODE);
    }

    public void setBehaviorMode(int mode) {
        entityData.set(DATA_BEHAVIOR_MODE, Math.max(0, Math.min(2, mode)));
    }

    public void cycleBehaviorMode() {
        int current = getBehaviorMode();
        setBehaviorMode((current + 1) % 3);
    }

    // === Interaction ===

    @Override
    @Nonnull
    public InteractionResult mobInteract(@Nonnull Player player, @Nonnull InteractionHand hand) {
        if (isOwnedBy(player)) {
            if (player.isShiftKeyDown()) {
                // Shift-click to toggle sit/stand
                setOrderedToSit(!isOrderedToSit());
                navigation.stop();
                setTarget(null);
                return InteractionResult.SUCCESS;
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
                    net.minecraft.network.chat.Component.translatable(modeKey),
                    true
                );
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
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
        tag.putString("SkinName", getSkinName());
        tag.putInt("BehaviorMode", getBehaviorMode());
    }

    @Override
    public void readAdditionalSaveData(@Nonnull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OriginalUUID")) {
            UUID uuid = tag.getUUID("OriginalUUID");
            String name = tag.getString("SkinName");
            setOriginalPlayer(uuid, name);
        }
        if (tag.contains("BehaviorMode")) {
            setBehaviorMode(tag.getInt("BehaviorMode"));
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
