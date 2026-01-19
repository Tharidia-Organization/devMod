package com.devmod.foundry.block.entity;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import com.devmod.config.Config;
import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.FoundryRecipeTypes;
import com.devmod.foundry.block.FoundryControllerBlock;
import com.devmod.foundry.fluid.FoundryFluidTank;
import com.devmod.foundry.menu.FoundryControllerMenu;
import com.devmod.foundry.recipe.FoundryAlloyingRecipe;
import com.devmod.foundry.recipe.FoundryFuelRecipe;
import com.devmod.foundry.recipe.FoundryMeltingRecipe;
import com.devmod.foundry.structure.FoundryStructure;
import com.devmod.foundry.structure.FoundryStructureDetector;
import com.devmod.foundry.structure.FoundryStructureResult;

/**
 * Foundry controller block entity.
 */
public class FoundryControllerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_MOLTEN = "MoltenTank";
    private static final String TAG_FUEL = "FuelTank";
    private static final String TAG_FUEL_TICKS = "FuelTicks";
    private static final String TAG_FUEL_TICKS_MAX = "FuelTicksMax";
    private static final String TAG_FUEL_TEMP = "FuelTemp";

    private final SimpleContainer inventory = new SimpleContainer(FoundryControllerMenu.CONTAINER_SIZE);
    private final FoundryFluidTank moltenTank;
    private final FluidTank fuelTank;

    private boolean formed = false;
    private boolean structureDirty = true;
    @Nullable private FoundryStructure structure;
    @Nullable private Component lastError;

    private int progress = 0;
    private int maxProgress = 200;
    private int activeSlot = -1;
    @Nullable private FoundryMeltingRecipe currentRecipe;
    private int fuelTicks = 0;
    private int fuelTicksMax = 0;
    private int fuelTemperature = 0;

    public FoundryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_CONTROLLER.get()), pos, state);
        moltenTank = new FoundryFluidTank(Config.FOUNDRY_CAPACITY_PER_BLOCK.get());
        fuelTank = new FluidTank(Config.FOUNDRY_FUEL_CAPACITY.get());
    }

    public void tickServer() {
        Level level = Objects.requireNonNull(getLevel());
        if (structureDirty) {
            validateStructure(level);
        }

        if (!formed) {
            return;
        }

        consumeFuelItem(level);

        int requiredTemp = findRequiredTemperature(level);
        if (requiredTemp < 0) {
            updateActiveState(level, false);
            progress = 0;
            return;
        }

        ensureFuel(level, requiredTemp);
        int currentTemp = getCurrentTemperature();
        if (fuelTicks > 0 && currentTemp >= requiredTemp) {
            fuelTicks--;
            processMelting(level, currentTemp);
            processAlloying(level, currentTemp);
            updateActiveState(level, true);
        } else {
            updateActiveState(level, false);
            progress = 0;
        }
    }

    private void validateStructure(Level level) {
        structureDirty = false;
        FoundryStructureResult result = FoundryStructureDetector.detect(level, worldPosition);
        if (!result.isValid()) {
            structure = null;
            formed = false;
            lastError = result.error();
            updateActiveState(level, false);
            return;
        }

        structure = Objects.requireNonNull(result.structure());
        formed = true;
        lastError = null;
        int capacity = structure.interiorVolume() * Config.FOUNDRY_CAPACITY_PER_BLOCK.get();
        moltenTank.setCapacity(capacity);
        linkComponents(level, structure);
        updateActiveState(level, false);
    }

    private void linkComponents(Level level, FoundryStructure structure) {
        for (BlockPos pos : structure.tankPositions()) {
            var be = level.getBlockEntity(pos);
            if (be instanceof FoundryTankBlockEntity tank) {
                tank.setControllerPos(worldPosition);
            }
        }
        for (BlockPos pos : structure.drainPositions()) {
            var be = level.getBlockEntity(pos);
            if (be instanceof FoundryDrainBlockEntity drain) {
                drain.setControllerPos(worldPosition);
            }
        }
    }

    private void consumeFuelItem(Level level) {
        ItemStack fuelStack = inventory.getItem(FoundryControllerMenu.SLOT_FUEL);
        if (fuelStack.isEmpty()) {
            return;
        }
        if (fuelStack.getItem() instanceof net.minecraft.world.item.BucketItem bucket && bucket.content != net.minecraft.world.level.material.Fluids.EMPTY) {
            FluidStack toFill = new FluidStack(bucket.content, 1000);
            FoundryFuelRecipe recipe = findFuelRecipe(level, toFill);
            if (recipe == null) {
                return;
            }
            int filled = fuelTank.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0 && !level.isClientSide) {
                inventory.setItem(FoundryControllerMenu.SLOT_FUEL, new ItemStack(Items.BUCKET));
            }
        }
    }

    private void processMelting(Level level, int temperature) {
        ItemStack input = ItemStack.EMPTY;
        FoundryMeltingRecipe recipe = currentRecipe;
        if (recipe == null || activeSlot < 0 || !recipe.getIngredient().test(inventory.getItem(activeSlot))) {
            recipe = null;
            activeSlot = -1;
            for (int i = 0; i < FoundryControllerMenu.SLOT_INPUT_COUNT; i++) {
                ItemStack candidate = inventory.getItem(i);
                if (candidate.isEmpty()) {
                    continue;
                }
                var found = findMeltingRecipe(level, candidate);
                if (found != null) {
                    recipe = found;
                    activeSlot = i;
                    input = candidate;
                    break;
                }
            }
            currentRecipe = recipe;
            if (recipe == null) {
                progress = 0;
                return;
            }
        } else {
            input = inventory.getItem(activeSlot);
        }

        if (input.isEmpty() || recipe == null) {
            progress = 0;
            return;
        }

        FluidStack output = recipe.getOutput();
        int byproductTotal = recipe.getByproductsTotal();
        if (moltenTank.getFree() < output.getAmount() + byproductTotal) {
            return;
        }

        if (temperature < recipe.getTemperature()) {
            return;
        }

        maxProgress = recipe.getTime();
        progress++;
        if (progress >= maxProgress) {
            input.shrink(1);
            moltenTank.fill(output, false);
            recipe.fillByproducts(moltenTank);
            progress = 0;
            setChanged();
        }
    }

    private void processAlloying(Level level, int temperature) {
        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<FoundryAlloyingRecipe>> recipes = recipeManager.getAllRecipesFor(FoundryRecipeTypes.ALLOYING.get());
        for (RecipeHolder<FoundryAlloyingRecipe> holder : recipes) {
            FoundryAlloyingRecipe recipe = holder.value();
            if (!recipe.canApply(moltenTank)) {
                continue;
            }
            if (temperature < recipe.getTemperature()) {
                continue;
            }
            int inputTotal = recipe.getInputAmount();
            int freeAfterDrain = moltenTank.getFree() + inputTotal;
            if (freeAfterDrain < recipe.getOutput().getAmount()) {
                continue;
            }
            recipe.consumeInputs(moltenTank);
            moltenTank.fill(recipe.getOutput(), false);
            setChanged();
            break;
        }
    }

    private int findRequiredTemperature(Level level) {
        int required = Integer.MAX_VALUE;
        for (int i = 0; i < FoundryControllerMenu.SLOT_INPUT_COUNT; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (candidate.isEmpty()) {
                continue;
            }
            FoundryMeltingRecipe recipe = findMeltingRecipe(level, candidate);
            if (recipe == null) {
                continue;
            }
            int needed = recipe.getOutput().getAmount() + recipe.getByproductsTotal();
            if (moltenTank.getFree() < needed) {
                continue;
            }
            required = Math.min(required, recipe.getTemperature());
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<FoundryAlloyingRecipe>> recipes = recipeManager.getAllRecipesFor(FoundryRecipeTypes.ALLOYING.get());
        for (RecipeHolder<FoundryAlloyingRecipe> holder : recipes) {
            FoundryAlloyingRecipe recipe = holder.value();
            if (!recipe.canApply(moltenTank)) {
                continue;
            }
            int inputTotal = recipe.getInputAmount();
            int freeAfterDrain = moltenTank.getFree() + inputTotal;
            if (freeAfterDrain < recipe.getOutput().getAmount()) {
                continue;
            }
            required = Math.min(required, recipe.getTemperature());
        }

        return required == Integer.MAX_VALUE ? -1 : required;
    }

    private void ensureFuel(Level level, int requiredTemp) {
        if (fuelTicks > 0 && fuelTemperature >= requiredTemp) {
            return;
        }
        FluidStack available = fuelTank.getFluid();
        if (available.isEmpty()) {
            fuelTicks = 0;
            fuelTicksMax = 0;
            fuelTemperature = 0;
            return;
        }
        FoundryFuelRecipe recipe = findFuelRecipe(level, available);
        if (recipe == null || recipe.getTemperature() < requiredTemp) {
            fuelTicks = 0;
            fuelTicksMax = 0;
            fuelTemperature = 0;
            return;
        }
        FluidStack required = recipe.getFluid();
        if (available.getAmount() < required.getAmount()) {
            return;
        }
        fuelTank.drain(required.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        fuelTicks = recipe.getBurnTime();
        fuelTicksMax = fuelTicks;
        fuelTemperature = recipe.getTemperature();
        setChanged();
    }

    private int getCurrentTemperature() {
        return fuelTicks > 0 ? fuelTemperature : 0;
    }

    @Nullable
    private FoundryMeltingRecipe findMeltingRecipe(Level level, ItemStack stack) {
        RecipeManager recipeManager = level.getRecipeManager();
        SingleRecipeInput input = new SingleRecipeInput(stack);
        return recipeManager.getRecipeFor(FoundryRecipeTypes.MELTING.get(), input, level)
            .map(RecipeHolder::value)
            .orElse(null);
    }

    @Nullable
    private FoundryFuelRecipe findFuelRecipe(Level level, FluidStack stack) {
        RecipeManager recipeManager = level.getRecipeManager();
        FoundryFuelRecipe.FuelInput input = new FoundryFuelRecipe.FuelInput(stack);
        return recipeManager.getRecipeFor(FoundryRecipeTypes.FUEL.get(), input, level)
            .map(RecipeHolder::value)
            .orElse(null);
    }

    private void updateActiveState(Level level, boolean active) {
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(FoundryControllerBlock.ACTIVE) && state.getValue(FoundryControllerBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(FoundryControllerBlock.ACTIVE, active), 3);
        }
    }

    public void markStructureDirty() {
        structureDirty = true;
    }

    public boolean isFormed() {
        return formed;
    }

    @Nullable
    public Component getLastError() {
        return lastError;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public int getFuelTicks() {
        return fuelTicks;
    }

    public int getFuelTicksMax() {
        return fuelTicksMax;
    }

    public int getFuelTemperature() {
        return fuelTemperature;
    }

    public int fillMolten(FluidStack stack, boolean execute) {
        int filled = moltenTank.fill(stack, !execute);
        if (execute && filled > 0) {
            setChanged();
        }
        return filled;
    }

    public FluidStack drainMolten(int amount, boolean execute) {
        FluidStack drained = moltenTank.drain(amount, !execute);
        if (execute && !drained.isEmpty()) {
            setChanged();
        }
        return drained;
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    @Override
    @Nonnull
    public Component getDisplayName() {
        return Objects.requireNonNull(Component.translatable("block.devmod.foundry_controller"));
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new FoundryControllerMenu(containerId, playerInv, this);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag invTag = new CompoundTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                invTag.put("Slot" + i, stack.save(registries));
            }
        }
        tag.put(TAG_INVENTORY, invTag);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        tag.putBoolean(TAG_FORMED, formed);
        tag.put(TAG_MOLTEN, moltenTank.save(registries));
        CompoundTag fuelTag = new CompoundTag();
        fuelTank.writeToNBT(registries, fuelTag);
        tag.put(TAG_FUEL, fuelTag);
        tag.putInt(TAG_FUEL_TICKS, fuelTicks);
        tag.putInt(TAG_FUEL_TICKS_MAX, fuelTicksMax);
        tag.putInt(TAG_FUEL_TEMP, fuelTemperature);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag invTag = tag.getCompound(TAG_INVENTORY);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                String key = "Slot" + i;
                if (invTag.contains(key)) {
                    inventory.setItem(i, ItemStack.parse(registries, invTag.getCompound(key)).orElse(ItemStack.EMPTY));
                }
            }
        }
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
        formed = tag.getBoolean(TAG_FORMED);
        if (tag.contains(TAG_MOLTEN)) {
            moltenTank.load(registries, tag.getCompound(TAG_MOLTEN));
        }
        if (tag.contains(TAG_FUEL)) {
            fuelTank.readFromNBT(registries, tag.getCompound(TAG_FUEL));
        }
        fuelTicks = tag.getInt(TAG_FUEL_TICKS);
        fuelTicksMax = tag.getInt(TAG_FUEL_TICKS_MAX);
        fuelTemperature = tag.getInt(TAG_FUEL_TEMP);
        structureDirty = true;
    }
}
