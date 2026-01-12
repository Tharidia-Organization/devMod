package com.devmod.clone.block.entity;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.recipe.CloneRecipeTypes;
import com.devmod.clone.recipe.PulverizingRecipe;

/**
 * Block entity for the Clone Pulverizer machine.
 * Accepts items dropped from above, pulverizes them, and ejects results.
 *
 * <p>Features:
 * <ul>
 *   <li>AABB detection for items dropped above</li>
 *   <li>Recipe-based processing with JSON datapacks</li>
 *   <li>Visual item rendering between rollers</li>
 *   <li>Physical item ejection from discharge chute</li>
 *   <li>Hopper compatibility for automation</li>
 * </ul>
 */
public class ClonePulverizerBlockEntity extends BlockEntity implements GeoBlockEntity {

    // NBT keys
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_PROCESSING_ITEM = "ProcessingItem";

    // Animation cache
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Animations - separate sequences
    protected static final RawAnimation DEPLOY = RawAnimation.begin()
            .thenPlay("animation.clone_pulverizer.deploy");

    protected static final RawAnimation IDLE = RawAnimation.begin()
            .thenLoop("animation.clone_pulverizer.idle");

    protected static final RawAnimation ACTIVE = RawAnimation.begin()
            .thenLoop("animation.clone_pulverizer.active");

    // State
    private boolean active = false;
    private int deployTimer = 0; // Track deploy animation progress (4 sec = 80 ticks)
    private static final int DEPLOY_DURATION = 80; // 4 seconds
    private int progress = 0;
    private int maxProgress = PulverizingRecipe.DEFAULT_PROCESSING_TIME;

    // Item being processed (for rendering)
    private ItemStack processingItem = ItemStack.EMPTY;

    // Track last synced output for change detection
    private ItemStack lastSyncedOutput = ItemStack.EMPTY;

    // Internal inventory (1 slot for input buffer, 1 for output buffer)
    private final SimpleContainer inventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ClonePulverizerBlockEntity.this.setChanged();
        }
    };

    // Cached recipe for current processing
    @Nullable
    private PulverizingRecipe currentRecipe = null;

    public ClonePulverizerBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.CLONE_PULVERIZER.get(), pos, state);
    }

    // === Server Tick ===

    /**
     * Server-side tick. Called every game tick.
     */
    public void serverTick() {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        // Track deploy animation progress
        if (deployTimer < DEPLOY_DURATION) {
            deployTimer++;
            if (deployTimer == DEPLOY_DURATION) {
                syncToClient(); // Sync when deploy completes
            }
        }

        // 1. Detect and capture items dropped above
        detectItemsAbove();

        // 2. Process items
        processItems();

        // 3. Eject output
        ejectOutput();
    }

    /**
     * Detect items dropped above the machine and capture them.
     */
    private void detectItemsAbove() {
        Level lvl = level;
        if (lvl == null) {
            return;
        }

        // AABB above the block (hopper area)
        AABB captureArea = new AABB(
                worldPosition.getX() + 0.0625, // Slightly inset from block edges
                worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.0625,
                worldPosition.getX() + 0.9375,
                worldPosition.getY() + 2.0,
                worldPosition.getZ() + 0.9375
        );

        List<ItemEntity> items = lvl.getEntitiesOfClass(ItemEntity.class, captureArea);
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();

            // Check if we can accept this item
            if (canAcceptItem(stack)) {
                ItemStack remaining = addToInput(stack);
                if (remaining.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(remaining);
                }
            }
        }
    }

    /**
     * Check if the pulverizer can accept this item (has matching recipe).
     */
    private boolean canAcceptItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Check input slot capacity
        ItemStack currentInput = inventory.getItem(0);
        if (!currentInput.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(currentInput, stack)) {
                return false;
            }
            if (currentInput.getCount() >= currentInput.getMaxStackSize()) {
                return false;
            }
        }

        // Check if a recipe exists for this item
        return findRecipe(stack).isPresent();
    }

    /**
     * Add item to input slot.
     * @return Remaining items that couldn't be added
     */
    private ItemStack addToInput(ItemStack stack) {
        ItemStack current = inventory.getItem(0);

        if (current.isEmpty()) {
            inventory.setItem(0, stack.copy());
            return ItemStack.EMPTY;
        }

        if (ItemStack.isSameItemSameComponents(current, stack)) {
            int space = current.getMaxStackSize() - current.getCount();
            int toAdd = Math.min(space, stack.getCount());
            current.grow(toAdd);

            if (toAdd >= stack.getCount()) {
                return ItemStack.EMPTY;
            } else {
                ItemStack remaining = stack.copy();
                remaining.shrink(toAdd);
                return remaining;
            }
        }

        return stack;
    }

    /**
     * Process items in input slot.
     */
    private void processItems() {
        ItemStack input = inventory.getItem(0);

        if (input.isEmpty()) {
            // Nothing to process
            if (active) {
                setActive(false);
            }
            progress = 0;
            processingItem = ItemStack.EMPTY;
            currentRecipe = null;
            return;
        }

        // Find recipe if not cached
        if (currentRecipe == null || !currentRecipe.getIngredient().test(input)) {
            Optional<PulverizingRecipe> recipe = findRecipe(input);
            if (recipe.isEmpty()) {
                // No valid recipe
                setActive(false);
                return;
            }
            currentRecipe = recipe.get();
            maxProgress = currentRecipe.getProcessingTime();
        }

        // Check if output slot can accept result
        ItemStack result = currentRecipe.getResult();
        ItemStack output = inventory.getItem(1);
        if (!output.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(output, result)) {
                // Different item in output
                setActive(false);
                return;
            }
            if (output.getCount() + result.getCount() > output.getMaxStackSize()) {
                // Output full
                setActive(false);
                return;
            }
        }

        // Process
        setActive(true);
        processingItem = input.copyWithCount(1);
        progress++;

        // Spawn crushing particles every 5 ticks
        if (progress % 5 == 0 && level instanceof ServerLevel serverLevel) {
            spawnCrushingParticles(serverLevel, input);
        }

        if (progress >= maxProgress) {
            // Complete processing
            input.shrink(1);

            if (output.isEmpty()) {
                inventory.setItem(1, result.copy());
            } else {
                output.grow(result.getCount());
            }

            progress = 0;
            processingItem = ItemStack.EMPTY;

            // Sync to client
            syncToClient();
        }
    }

    /**
     * Handle output - items stay on discharge tray until picked up or extracted.
     * Only ejects if output tray is full and new item needs to be placed.
     */
    private void ejectOutput() {
        // Output stays on tray - no auto-ejection
        // Items can be picked up by player or extracted by hopper
        // Sync to client when output changes for rendering
        ItemStack output = inventory.getItem(1);
        if (!output.isEmpty() && !ItemStack.isSameItemSameComponents(output, lastSyncedOutput)) {
            lastSyncedOutput = output.copy();
            syncToClient();
        }
    }

    /**
     * Get the output item for rendering on the discharge tray.
     */
    public ItemStack getOutputItem() {
        return inventory.getItem(1);
    }

    /**
     * Allow players to pick up output item by right-clicking the block.
     * Called from block's useWithoutItem method.
     * @return The output item if present, or empty stack
     */
    public ItemStack extractOutput() {
        ItemStack output = inventory.getItem(1);
        if (!output.isEmpty()) {
            inventory.setItem(1, ItemStack.EMPTY);
            syncToClient();
            return output;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Get the internal inventory for hopper interaction.
     */
    public SimpleContainer getInventory() {
        return inventory;
    }

    /**
     * Spawn item crushing particles at the roller position.
     */
    private void spawnCrushingParticles(ServerLevel serverLevel, ItemStack item) {
        // Position between the rollers
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.7;
        double z = worldPosition.getZ() + 0.5;

        // Create item particle option
        ItemParticleOption particleOption = new ItemParticleOption(ParticleTypes.ITEM, item);

        // Spawn several particles with random spread
        for (int i = 0; i < 3; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.3;
            double velY = serverLevel.random.nextDouble() * 0.05;

            serverLevel.sendParticles(
                    particleOption,
                    x + offsetX, y, z + offsetZ,
                    1, // count
                    0, velY, 0, // velocity
                    0.05 // speed
            );
        }
    }

    /**
     * Find a pulverizing recipe for the given input.
     */
    private Optional<PulverizingRecipe> findRecipe(ItemStack input) {
        Level lvl = level;
        if (lvl == null) {
            return Optional.empty();
        }

        RecipeManager recipeManager = lvl.getRecipeManager();
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);

        return recipeManager.getRecipeFor(CloneRecipeTypes.PULVERIZING.get(), recipeInput, lvl)
                .map(RecipeHolder::value);
    }

    /**
     * Set active state and update block state if needed.
     */
    private void setActive(boolean newActive) {
        if (active != newActive) {
            active = newActive;
            syncToClient();
        }
    }

    /**
     * Sync block entity data to clients.
     */
    private void syncToClient() {
        Level lvl = level;
        if (lvl != null && !lvl.isClientSide) {
            lvl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            setChanged();
        }
    }

    // === Getters for rendering ===

    public boolean isActive() {
        return active;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public float getProgressPercent() {
        return maxProgress > 0 ? (float) progress / maxProgress : 0f;
    }

    public ItemStack getProcessingItem() {
        return processingItem;
    }

    public int getDeployTimer() {
        return deployTimer;
    }

    public boolean isDeployed() {
        return deployTimer >= DEPLOY_DURATION;
    }

    /**
     * Client-side tick for animation updates.
     */
    public void clientTick() {
        // Track deploy animation on client side too
        if (deployTimer < DEPLOY_DURATION) {
            deployTimer++;
        }
    }

    // === GeckoLib Animation ===

    @Override
    public void registerControllers(@Nonnull AnimatableManager.ControllerRegistrar controllers) {
        // Single controller that handles deploy -> idle/active transition
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            boolean isDeployed = deployTimer >= DEPLOY_DURATION;

            if (!isDeployed) {
                // Still deploying
                return state.setAndContinue(DEPLOY);
            }

            // After deploy, switch between idle and active
            if (active) {
                return state.setAndContinue(ACTIVE);
            } else {
                return state.setAndContinue(IDLE);
            }
        }));
    }

    @Override
    @Nonnull
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        tag.putBoolean("Active", active);
        tag.putInt("DeployTimer", deployTimer);

        if (!processingItem.isEmpty()) {
            tag.put(TAG_PROCESSING_ITEM, processingItem.save(registries));
        }

        // Save inventory
        CompoundTag invTag = new CompoundTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                invTag.put("Slot" + i, stack.save(registries));
            }
        }
        tag.put("Inventory", invTag);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
        active = tag.getBoolean("Active");
        deployTimer = tag.getInt("DeployTimer");

        if (tag.contains(TAG_PROCESSING_ITEM)) {
            processingItem = ItemStack.parse(registries, tag.getCompound(TAG_PROCESSING_ITEM))
                    .orElse(ItemStack.EMPTY);
        } else {
            processingItem = ItemStack.EMPTY;
        }

        // Load inventory (only from full save, not network sync)
        if (tag.contains("Inventory")) {
            CompoundTag invTag = tag.getCompound("Inventory");
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                String key = "Slot" + i;
                if (invTag.contains(key)) {
                    inventory.setItem(i, ItemStack.parse(registries, invTag.getCompound(key))
                            .orElse(ItemStack.EMPTY));
                } else {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }

        // Load output item (from network sync - takes priority if present)
        if (tag.contains("OutputItem")) {
            inventory.setItem(1, ItemStack.parse(registries, tag.getCompound("OutputItem"))
                    .orElse(ItemStack.EMPTY));
        }
    }

    // === Network Sync ===

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        // Only sync what the client needs for rendering
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        tag.putBoolean("Active", active);
        tag.putInt("DeployTimer", deployTimer);
        if (!processingItem.isEmpty()) {
            tag.put(TAG_PROCESSING_ITEM, processingItem.save(registries));
        }
        // Sync output item for rendering on discharge tray
        ItemStack outputItem = inventory.getItem(1);
        if (!outputItem.isEmpty()) {
            tag.put("OutputItem", outputItem.save(registries));
        }
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
