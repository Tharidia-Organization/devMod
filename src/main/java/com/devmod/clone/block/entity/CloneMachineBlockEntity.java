package com.devmod.clone.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base GeckoLib animated block entity for Clone machines.
 * Provides animation support for Oritech-style decorative machine blocks.
 */
public class CloneMachineBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Animation: play deploy once, then loop active automatically
    protected static final RawAnimation DEPLOY_THEN_ACTIVE = RawAnimation.begin()
            .thenPlay("animation.clone_pulverizer.deploy")
            .thenLoop("animation.clone_pulverizer.active");

    /**
     * Constructor for BlockEntityType.Builder registration.
     * Uses the CLONE_MACHINE type from CloneBlockEntities.
     */
    public CloneMachineBlockEntity(BlockPos pos, BlockState state) {
        this(com.devmod.clone.CloneBlockEntities.CLONE_MACHINE.get(), pos, state);
    }

    /**
     * Full constructor with explicit type.
     */
    public CloneMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(type), Objects.requireNonNull(pos), Objects.requireNonNull(state));
    }

    @Override
    public void registerControllers(@Nonnull AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            // DEPLOY_THEN_ACTIVE plays deploy once, then automatically loops active
            // GeckoLib continues the animation without restarting when same RawAnimation is returned
            return state.setAndContinue(DEPLOY_THEN_ACTIVE);
        }));
    }

    @Override
    @Nonnull
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }
}
