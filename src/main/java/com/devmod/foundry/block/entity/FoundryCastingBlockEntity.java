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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryItems;
import com.devmod.foundry.block.FoundryCastingBasinBlock;
import com.devmod.foundry.block.FoundryCastingTableBlock;
import com.devmod.foundry.progression.FoundryPlayerProgress;
import com.devmod.foundry.progression.FoundryProgressAttachment;
import com.devmod.foundry.quality.FoundryFluidQuality;
import com.devmod.foundry.quality.FoundryItemQuality;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.quality.QualityCalculator;
import com.devmod.foundry.recipe.FoundryCastingRecipe;
import com.devmod.foundry.tool.FoundryPartItem;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;
import com.devmod.foundry.util.FoundryContainers;
import com.devmod.foundry.util.FoundryNbtHelper;

/**
 * Base block entity for casting table/basin.
 */
public abstract class FoundryCastingBlockEntity extends BlockEntity {
    private static final String TAG_CAST = "Cast";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";

    protected final SimpleContainer inventory;
    protected FluidStack fluidStack = FluidStack.EMPTY;
    protected int progress = 0;
    protected int maxProgress = 100;
    private static final int PROGRESS_SAVE_INTERVAL = 20;
    private int progressSaveTicks = 0;

    protected FoundryCastingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(type), pos, state);
        this.inventory = FoundryContainers.tracked(2);
    }

    public abstract RecipeType<FoundryCastingRecipe> getRecipeType();

    @Override
    public void onLoad() {
        super.onLoad();
        FoundryContainers.bind(inventory, this);
    }

    public void tickServer() {
        Level level = Objects.requireNonNull(getLevel());
        if (level.isClientSide) {
            return;
        }
        updateHasItemState();
        if (fluidStack.isEmpty()) {
            resetProgress();
            return;
        }

        FoundryCastingRecipe recipe = findRecipe().orElse(null);
        if (recipe == null) {
            resetProgress();
            return;
        }

        int required = recipe.getFluid().getAmount();
        if (fluidStack.getAmount() < required) {
            resetProgress();
            return;
        }

        ItemStack output = inventory.getItem(1);
        if (!output.isEmpty()) {
            return;
        }

        maxProgress = recipe.getCoolingTime();
        progress++;
        maybeMarkProgressDirty();
        if (progress >= maxProgress) {
            MaterialQuality fluidQuality = FoundryFluidQuality.getQuality(fluidStack);
            float fluidPurity = FoundryFluidQuality.getPurity(fluidStack);
            MaterialQuality castQuality = QualityCalculator.calculateCastingQuality(fluidQuality, 1.0f, 1.0f);
            ItemStack result = recipe.getResultItem(Objects.requireNonNull(level.registryAccess())).copy();
            if (result.getItem() instanceof FoundryPartItem partItem) {
                partItem.setQuality(result, castQuality);
                partItem.setPurity(result, fluidPurity);
            } else {
                FoundryItemQuality.applyQuality(result, castQuality, fluidPurity);
            }
            inventory.setItem(1, result);
            if (recipe.isConsumeCast()) {
                inventory.setItem(0, Objects.requireNonNull(ItemStack.EMPTY));
            }
            fluidStack = FluidStack.EMPTY;
            progress = 0;
            progressSaveTicks = 0;
            updateHasItemState();
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
            fluidStack = incoming.copy();
            fluidStack.setAmount(0);
        }

        if (fluidStack.getFluid() != recipe.getFluid().getFluid()) {
            return 0;
        }

        int missing = required - fluidStack.getAmount();
        if (missing <= 0) {
            return 0;
        }

        int accepted = Math.min(missing, incoming.getAmount());
        FluidStack portion = incoming.copy();
        portion.setAmount(accepted);
        float mergedPurity = FoundryFluidQuality.mergePurity(fluidStack, portion);
        MaterialQuality mergedQuality = FoundryFluidQuality.mergeQuality(fluidStack, portion);
        int mergedOxidation = FoundryFluidQuality.mergeOxidationTicks(fluidStack, portion);
        float mergedPeakTemp = FoundryFluidQuality.mergePeakTemperature(fluidStack, portion);
        fluidStack.grow(accepted);
        FoundryFluidQuality.applyMoltenState(fluidStack, mergedQuality, mergedPurity, mergedOxidation, mergedPeakTemp);
        setChanged();
        return accepted;
    }

    /**
     * Selects the preferred fluid to pour from the controller based on the current cast/fluid state.
     * If this casting block already contains fluid, it will keep that fluid type.
     */
    @Nonnull
    public FluidStack getPreferredPourFluid(@Nonnull FoundryControllerBlockEntity controller) {
        if (!fluidStack.isEmpty()) {
            return fluidStack.copy();
        }
        Level level = getLevel();
        if (level == null) {
            return FluidStack.EMPTY;
        }
        RecipeManager manager = level.getRecipeManager();
        ItemStack cast = inventory.getItem(0);
        FluidStack best = FluidStack.EMPTY;
        int bestAmount = 0;
        for (RecipeHolder<FoundryCastingRecipe> holder : manager.getAllRecipesFor(Objects.requireNonNull(getRecipeType()))) {
            FoundryCastingRecipe recipe = holder.value();
            if (!matchesCast(recipe, cast)) {
                continue;
            }
            FluidStack recipeFluid = recipe.getFluid();
            int available = controller.getMoltenAmountForFluid(recipeFluid.getFluid());
            if (available <= 0) {
                continue;
            }
            if (available > bestAmount) {
                bestAmount = available;
                best = recipeFluid;
            }
        }
        return best.isEmpty() ? FluidStack.EMPTY : best.copy();
    }

    public InteractionResult handleInteraction(@Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack cast = inventory.getItem(0);
        ItemStack output = inventory.getItem(1);
        Level currentLevel = Objects.requireNonNull(getLevel());

        if (!output.isEmpty()) {
            if (!currentLevel.isClientSide) {
                FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
                FoundryMaterialDefinition material = FoundryMaterialRegistry.findMaterial(output).orElse(null);
                if (material != null) {
                    progress.unlockMaterial(material.id());
                    FoundryProgressAttachment.sync(player);
                } else if (output.getItem() instanceof FoundryPartItem partItem) {
                    partItem.getMaterialId(output).ifPresent(id -> {
                        progress.unlockMaterial(id);
                        FoundryProgressAttachment.sync(player);
                    });
                }
            }
            ItemStack outputCopy = Objects.requireNonNull(output.copy());
            if (!player.getInventory().add(outputCopy)) {
                player.drop(Objects.requireNonNull(output.copy()), false);
            }
            inventory.setItem(1, Objects.requireNonNull(ItemStack.EMPTY));
            updateHasItemState();
            setChanged();
            return InteractionResult.CONSUME;
        }

        if (cast.isEmpty() && !held.isEmpty()) {
            if (held.is(Objects.requireNonNull(FoundryItems.FOUNDRY_INGOT_CAST.get()))
                || held.is(Objects.requireNonNull(FoundryItems.FOUNDRY_NUGGET_CAST.get()))) {
                inventory.setItem(0, Objects.requireNonNull(held.copyWithCount(1)));
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                updateHasItemState();
                setChanged();
                return InteractionResult.CONSUME;
            }
        }

        if (!cast.isEmpty() && held.isEmpty()) {
            player.setItemInHand(hand, Objects.requireNonNull(cast.copy()));
            inventory.setItem(0, Objects.requireNonNull(ItemStack.EMPTY));
            updateHasItemState();
            setChanged();
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

    private static boolean matchesCast(FoundryCastingRecipe recipe, ItemStack cast) {
        if (recipe.getCast().isEmpty()) {
            return true;
        }
        return recipe.getCast().test(cast);
    }

    private Optional<FoundryCastingRecipe> findRecipe(FluidStack fluid) {
        Level level = Objects.requireNonNull(getLevel());
        RecipeManager manager = level.getRecipeManager();
        ItemStack cast = inventory.getItem(0);
        FoundryCastingRecipe.CastingInput input = new FoundryCastingRecipe.CastingInput(cast, fluid);
        return manager.getRecipeFor(Objects.requireNonNull(getRecipeType()), input, level).map(RecipeHolder::value);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ItemStack castItem = inventory.getItem(0);
        if (!castItem.isEmpty()) {
            tag.put(TAG_CAST, Objects.requireNonNull(castItem.save(registries)));
        }
        ItemStack outputItem = inventory.getItem(1);
        if (!outputItem.isEmpty()) {
            tag.put(TAG_OUTPUT, Objects.requireNonNull(outputItem.save(registries)));
        }
        FoundryNbtHelper.putFluidStack(tag, TAG_FLUID, registries, fluidStack);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_CAST)) {
            CompoundTag castTag = Objects.requireNonNull(tag.getCompound(TAG_CAST));
            inventory.setItem(0, Objects.requireNonNull(ItemStack.parse(registries, castTag).orElse(ItemStack.EMPTY)));
        }
        if (tag.contains(TAG_OUTPUT)) {
            CompoundTag outputTag = Objects.requireNonNull(tag.getCompound(TAG_OUTPUT));
            inventory.setItem(1, Objects.requireNonNull(ItemStack.parse(registries, outputTag).orElse(ItemStack.EMPTY)));
        }
        fluidStack = FoundryNbtHelper.readFluidStack(tag, TAG_FLUID, registries);
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
    }

    private void updateHasItemState() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean hasItem = !inventory.getItem(0).isEmpty() || !inventory.getItem(1).isEmpty();
        BlockState state = getBlockState();
        if (state.getBlock() instanceof FoundryCastingTableBlock) {
            if (state.getValue(FoundryCastingTableBlock.HAS_ITEM) != hasItem) {
                level.setBlock(worldPosition, state.setValue(FoundryCastingTableBlock.HAS_ITEM, hasItem), Block.UPDATE_CLIENTS);
            }
            return;
        }
        if (state.getBlock() instanceof FoundryCastingBasinBlock) {
            if (state.getValue(FoundryCastingBasinBlock.HAS_ITEM) != hasItem) {
                level.setBlock(worldPosition, state.setValue(FoundryCastingBasinBlock.HAS_ITEM, hasItem), Block.UPDATE_CLIENTS);
            }
        }
    }

    private void maybeMarkProgressDirty() {
        progressSaveTicks++;
        if (progressSaveTicks >= PROGRESS_SAVE_INTERVAL) {
            progressSaveTicks = 0;
            setChanged();
        }
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            progressSaveTicks = 0;
            setChanged();
        }
    }
}
