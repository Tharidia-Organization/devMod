package com.devmod.foundry.entity;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.devmod.DevMod;
import com.devmod.foundry.FoundryRecipeTypes;
import com.devmod.foundry.fluid.FoundryFluidTank;
import com.devmod.foundry.recipe.FoundryEntityMeltingRecipe;

/**
 * Module that handles entity melting inside the foundry.
 * Entities that fall into the molten metal take damage and can be converted to fluid.
 */
public class FoundryEntityMeltingModule {
    private static final int DEFAULT_COOLDOWN = 20; // 1 second between damage ticks
    private static final float DEFAULT_DAMAGE = 4.0f; // 2 hearts
    private static final ResourceLocation MOLTEN_METAL_DAMAGE_TYPE =
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "molten_metal");

    private final Level level;
    private final FoundryFluidTank tank;
    private final BooleanSupplier canMeltSupplier;
    private final Supplier<AABB> boundsSupplier;

    private int cooldown = 0;
    @Nullable private DamageSource cachedDamageSource;

    /**
     * Creates a new entity melting module.
     * @param level The world
     * @param tank The molten fluid tank to fill with melted entities
     * @param canMeltSupplier Supplier that returns true if the foundry can melt entities (has fuel, is formed, etc.)
     * @param boundsSupplier Supplier for the interior AABB where entities are detected
     */
    public FoundryEntityMeltingModule(
        Level level,
        FoundryFluidTank tank,
        BooleanSupplier canMeltSupplier,
        Supplier<AABB> boundsSupplier
    ) {
        this.level = Objects.requireNonNull(level);
        this.tank = Objects.requireNonNull(tank);
        this.canMeltSupplier = Objects.requireNonNull(canMeltSupplier);
        this.boundsSupplier = Objects.requireNonNull(boundsSupplier);
    }

    /**
     * Tick the entity melting system. Should be called from the controller's server tick.
     */
    public void tick() {
        if (level.isClientSide()) {
            return;
        }

        // Cooldown between damage applications
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // Check if we can melt entities
        if (!canMeltSupplier.getAsBoolean()) {
            return;
        }

        // Must have fluid in tank to damage entities
        if (tank.isEmpty()) {
            return;
        }

        AABB bounds = boundsSupplier.get();
        if (bounds == null) {
            return;
        }

        // Find all living entities inside the foundry
        List<LivingEntity> entities = level.getEntitiesOfClass(
            LivingEntity.class,
            bounds,
            entity -> !entity.isSpectator() && entity.isAlive()
        );

        if (entities.isEmpty()) {
            return;
        }

        // Process each entity
        boolean processedAny = false;
        for (LivingEntity entity : entities) {
            if (processEntity(entity)) {
                processedAny = true;
            }
        }

        // Set cooldown after processing
        if (processedAny) {
            cooldown = DEFAULT_COOLDOWN;
        }
    }

    /**
     * Process a single entity - apply damage and potentially convert to fluid.
     * @param entity The entity to process
     * @return true if the entity was processed (damaged)
     */
    private boolean processEntity(LivingEntity entity) {
        // Find matching recipe
        FoundryEntityMeltingRecipe recipe = findRecipe(entity);

        float damage;
        int recipeCooldown;

        if (recipe != null) {
            damage = recipe.getDamage();
            recipeCooldown = recipe.getCooldown();
        } else {
            // Default damage for entities without a recipe
            damage = DEFAULT_DAMAGE;
            recipeCooldown = DEFAULT_COOLDOWN;
        }

        // Calculate actual damage (capped by entity health)
        float actualDamage = Math.min(damage, entity.getHealth());

        // Apply damage
        DamageSource damageSource = getDamageSource();
        boolean damaged = entity.hurt(damageSource, damage);

        if (damaged && recipe != null) {
            // Calculate fluid output scaled by actual damage dealt
            FluidStack output = recipe.getScaledOutput(actualDamage);

            if (!output.isEmpty() && tank.getFree() >= output.getAmount()) {
                tank.fill(output, IFluidHandler.FluidAction.EXECUTE);

                // Play sound effect
                level.playSound(
                    null,
                    entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.LAVA_EXTINGUISH,
                    SoundSource.BLOCKS,
                    0.5f,
                    1.0f + level.random.nextFloat() * 0.2f
                );

                DevMod.LOGGER.debug("[Foundry] Entity {} melted, produced {} mB of {}",
                    entity.getName().getString(),
                    output.getAmount(),
                    output.getFluid());
            }
        }

        // Update cooldown based on recipe
        if (recipeCooldown > cooldown) {
            cooldown = recipeCooldown;
        }

        // Set entity on fire for visual effect
        if (damaged) {
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 100)); // 5 seconds
        }

        return damaged;
    }

    /**
     * Find a matching entity melting recipe for the given entity.
     */
    @Nullable
    private FoundryEntityMeltingRecipe findRecipe(LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        RecipeManager recipeManager = serverLevel.getRecipeManager();
        FoundryEntityMeltingRecipe.EntityInput input = new FoundryEntityMeltingRecipe.EntityInput(entity);

        List<RecipeHolder<FoundryEntityMeltingRecipe>> recipes = recipeManager.getAllRecipesFor(
            FoundryRecipeTypes.ENTITY_MELTING.get()
        );

        for (RecipeHolder<FoundryEntityMeltingRecipe> holder : recipes) {
            if (holder.value().matches(input, level)) {
                return holder.value();
            }
        }

        return null;
    }

    /**
     * Get or create the damage source for molten metal damage.
     */
    private DamageSource getDamageSource() {
        if (cachedDamageSource == null) {
            if (level instanceof ServerLevel serverLevel) {
                ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, MOLTEN_METAL_DAMAGE_TYPE);
                cachedDamageSource = new DamageSource(
                    serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key)
                );
            } else {
                // Fallback to lava damage on client (shouldn't happen)
                cachedDamageSource = level.damageSources().lava();
            }
        }
        return cachedDamageSource;
    }

    /**
     * Reset the cached damage source (call if level changes).
     */
    public void invalidateDamageSource() {
        cachedDamageSource = null;
    }
}
