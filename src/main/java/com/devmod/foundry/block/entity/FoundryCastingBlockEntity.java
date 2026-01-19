package com.devmod.foundry.block.entity;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryItems;
import com.devmod.foundry.recipe.FoundryCastingRecipe;

/**
 * Base block entity for casting table/basin.
 */
public abstract class FoundryCastingBlockEntity extends BlockEntity {
    private static final String TAG_CAST = "Cast";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";

    protected final SimpleContainer inventory = new SimpleContainer(2);
    protected FluidStack fluidStack = FluidStack.EMPTY;
    protected int progress = 0;
    protected int maxProgress = 100;

    protected FoundryCastingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(type), pos, state);
    }

    public abstract RecipeType<FoundryCastingRecipe> getRecipeType();

    public void tickServer() {
        Level level = Objects.requireNonNull(getLevel());
        if (level.isClientSide) {
            return;
        }
        if (fluidStack.isEmpty()) {
            progress = 0;
            return;
        }

        FoundryCastingRecipe recipe = findRecipe().orElse(null);
        if (recipe == null) {
            progress = 0;
            return;
        }

        int required = recipe.getFluid().getAmount();
        if (fluidStack.getAmount() < required) {
            progress = 0;
            return;
        }

        ItemStack output = inventory.getItem(1);
        if (!output.isEmpty()) {
            return;
        }

        maxProgress = recipe.getCoolingTime();
        progress++;
        if (progress >= maxProgress) {
            inventory.setItem(1, recipe.getResultItem(level.registryAccess()).copy());
            if (recipe.isConsumeCast()) {
                inventory.setItem(0, ItemStack.EMPTY);
            }
            fluidStack = FluidStack.EMPTY;
            progress = 0;
            setChanged();
        }
    }

    public int tryFillFromFaucet(FluidStack incoming) {
        if (incoming.isEmpty()) {
            return 0;
        }

        FoundryCastingRecipe recipe = findRecipe(incoming).orElse(null);
        if (recipe == null) {
            return 0;
        }

        int required = recipe.getFluid().getAmount();
        if (fluidStack.isEmpty()) {
            fluidStack = new FluidStack(incoming.getFluid(), 0);
        }

        if (!FluidStack.isSameFluidSameComponents(fluidStack, recipe.getFluid())) {
            return 0;
        }

        int missing = required - fluidStack.getAmount();
        if (missing <= 0) {
            return 0;
        }

        int accepted = Math.min(missing, incoming.getAmount());
        fluidStack.grow(accepted);
        setChanged();
        return accepted;
    }

    public InteractionResult handleInteraction(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack cast = inventory.getItem(0);
        ItemStack output = inventory.getItem(1);

        if (!output.isEmpty()) {
            if (!player.getInventory().add(output.copy())) {
                player.drop(output.copy(), false);
            }
            inventory.setItem(1, ItemStack.EMPTY);
            return InteractionResult.CONSUME;
        }

        if (cast.isEmpty() && !held.isEmpty()) {
            if (held.is(Objects.requireNonNull(FoundryItems.FOUNDRY_INGOT_CAST.get()))
                || held.is(Objects.requireNonNull(FoundryItems.FOUNDRY_NUGGET_CAST.get()))) {
                inventory.setItem(0, held.copyWithCount(1));
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                return InteractionResult.CONSUME;
            }
        }

        if (!cast.isEmpty() && held.isEmpty()) {
            player.setItemInHand(hand, cast.copy());
            inventory.setItem(0, ItemStack.EMPTY);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private Optional<FoundryCastingRecipe> findRecipe() {
        if (fluidStack.isEmpty()) {
            return Optional.empty();
        }
        return findRecipe(fluidStack);
    }

    private Optional<FoundryCastingRecipe> findRecipe(FluidStack fluid) {
        Level level = Objects.requireNonNull(getLevel());
        RecipeManager manager = level.getRecipeManager();
        ItemStack cast = inventory.getItem(0);
        FoundryCastingRecipe.CastingInput input = new FoundryCastingRecipe.CastingInput(cast, fluid);
        return manager.getRecipeFor(getRecipeType(), input, level).map(RecipeHolder::value);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!inventory.getItem(0).isEmpty()) {
            tag.put(TAG_CAST, inventory.getItem(0).save(registries));
        }
        if (!inventory.getItem(1).isEmpty()) {
            tag.put(TAG_OUTPUT, inventory.getItem(1).save(registries));
        }
        if (!fluidStack.isEmpty()) {
            CompoundTag fluidTag = new CompoundTag();
            fluidStack.save(registries, fluidTag);
            tag.put(TAG_FLUID, fluidTag);
        }
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_CAST)) {
            inventory.setItem(0, ItemStack.parse(registries, tag.getCompound(TAG_CAST)).orElse(ItemStack.EMPTY));
        }
        if (tag.contains(TAG_OUTPUT)) {
            inventory.setItem(1, ItemStack.parse(registries, tag.getCompound(TAG_OUTPUT)).orElse(ItemStack.EMPTY));
        }
        if (tag.contains(TAG_FLUID)) {
            fluidStack = FluidStack.parseOptional(registries, tag.getCompound(TAG_FLUID));
        } else {
            fluidStack = FluidStack.EMPTY;
        }
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
    }
}
