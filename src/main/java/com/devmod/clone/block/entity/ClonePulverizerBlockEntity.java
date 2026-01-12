package com.devmod.clone.block.entity;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_OUTPUT_ITEM = "OutputItem";
    private static final String TAG_INVENTORY = "Inventory";

    // Animation cache
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Animations
    /** Deploy animation - plays once on first load. */
    protected static final RawAnimation DEPLOY_THEN_IDLE = RawAnimation.begin()
            .thenPlay("animation.clone_pulverizer.deploy")
            .thenLoop("animation.clone_pulverizer.idle");

    /** Idle animation - loops when not processing (after deploy). */
    protected static final RawAnimation IDLE = RawAnimation.begin()
            .thenLoop("animation.clone_pulverizer.idle");

    /** Active processing animation - rollers spinning. */
    protected static final RawAnimation ACTIVE = RawAnimation.begin()
            .thenLoop("animation.clone_pulverizer.active");

    /** Track if deploy animation has played (prevents re-deploy on every stop). */
    private boolean hasDeployed = false;

    // State
    private boolean active = false;
    private int progress = 0;
    private int maxProgress = PulverizingRecipe.DEFAULT_PROCESSING_TIME;

    // Item being processed (for rendering)
    private ItemStack processingItem = ItemStack.EMPTY;

    // Dirty flags for network sync optimization
    private boolean dirtyActive = false;
    private boolean dirtyOutput = false;

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

        // 1. Detect and capture items dropped above
        detectItemsAbove();

        // 2. Process items (output stored in slot 1)
        processItems();

        // 3. Give output to nearby players (Create-style auto-pickup)
        giveOutputToNearbyPlayers();

        // 4. Sync to client if any dirty flags set
        syncToClient();
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

        // Get result for this recipe
        ItemStack result = currentRecipe.getResult();

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

            // Add result to output slot (slot 1) - rendered visually, auto-pickup
            addToOutput(result.copy());

            progress = 0;
            processingItem = ItemStack.EMPTY;
        }
    }

    /**
     * Add processed result to output slot (slot 1).
     * Create-style: items stored in inventory, rendered visually, auto-pickup.
     */
    private void addToOutput(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemStack current = inventory.getItem(1);

        if (current.isEmpty()) {
            inventory.setItem(1, stack.copy());
            dirtyOutput = true;
            return;
        }

        if (ItemStack.isSameItemSameComponents(current, stack)) {
            int space = current.getMaxStackSize() - current.getCount();
            int toAdd = Math.min(space, stack.getCount());
            if (toAdd > 0) {
                current.grow(toAdd);
                dirtyOutput = true;
            }
        }
        // If can't add (different item or full), item is lost - processing should check first
    }

    /**
     * Give output items to players standing near the discharge tray.
     * Create-style auto-pickup: walk near = get items.
     */
    private void giveOutputToNearbyPlayers() {
        Level lvl = level;
        if (lvl == null) {
            return;
        }

        ItemStack output = inventory.getItem(1);
        if (output.isEmpty()) {
            return;
        }

        // Calculate pickup area - covers the entire block and slightly beyond
        // This ensures players can pick up items from any side near the machine
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY();
        double z = worldPosition.getZ() + 0.5;

        // Large pickup area around the entire block (1.5 block radius)
        AABB pickupArea = new AABB(x - 1.5, y - 0.5, z - 1.5, x + 1.5, y + 2.0, z + 1.5);
        List<Player> players = lvl.getEntitiesOfClass(Player.class, pickupArea);

        for (Player player : players) {
            if (player.isSpectator()) {
                continue;
            }

            // Try to give items to player
            ItemStack toGive = output.copy();
            if (player.getInventory().add(toGive)) {
                // Successfully added to inventory
                inventory.setItem(1, ItemStack.EMPTY);
                dirtyOutput = true;
                return;
            } else if (toGive.getCount() < output.getCount()) {
                // Partially added
                output.setCount(toGive.getCount());
                inventory.setItem(1, output);
                dirtyOutput = true;
            }
        }
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
     * Set active state and mark for sync if changed.
     */
    private void setActive(boolean newActive) {
        if (active != newActive) {
            active = newActive;
            dirtyActive = true;
        }
    }

    /**
     * Sync block entity data to clients only if dirty.
     */
    private void syncToClient() {
        if (!dirtyActive && !dirtyOutput) {
            return; // Nothing changed, skip sync
        }
        Level lvl = level;
        if (lvl != null && !lvl.isClientSide) {
            lvl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            dirtyActive = false;
            dirtyOutput = false;
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

    /**
     * Get the output item for rendering on the discharge tray.
     */
    public ItemStack getOutputItem() {
        return inventory.getItem(1);
    }

    /**
     * Extract the output item (for player pickup via right-click).
     * Removes the item from the slot and returns it.
     */
    public ItemStack extractOutput() {
        ItemStack slotContent = inventory.getItem(1);
        if (slotContent.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Make a copy before clearing the slot
        ItemStack output = slotContent.copy();
        inventory.setItem(1, ItemStack.EMPTY);
        dirtyOutput = true;
        syncToClient();
        return output;
    }

    // === GeckoLib Animation ===

    @Override
    public void registerControllers(@Nonnull AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (active) {
                // Processing - rollers spinning
                // Mark as deployed so we don't re-play deploy when stopping
                hasDeployed = true;
                return state.setAndContinue(ACTIVE);
            } else if (!hasDeployed) {
                // First load - play deploy animation then chain to idle
                // DON'T set hasDeployed here! Let the animation play fully.
                // setAndContinue will continue the same animation each frame.
                return state.setAndContinue(DEPLOY_THEN_IDLE);
            } else {
                // Was active before - just loop idle (no re-deploy)
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
        tag.putBoolean(TAG_ACTIVE, active);

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
        tag.put(TAG_INVENTORY, invTag);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
        active = tag.getBoolean(TAG_ACTIVE);

        if (tag.contains(TAG_PROCESSING_ITEM)) {
            processingItem = ItemStack.parse(registries, tag.getCompound(TAG_PROCESSING_ITEM))
                    .orElse(ItemStack.EMPTY);
        } else {
            processingItem = ItemStack.EMPTY;
        }

        // Load inventory (only from full save, not network sync)
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag invTag = tag.getCompound(TAG_INVENTORY);
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
        if (tag.contains(TAG_OUTPUT_ITEM)) {
            inventory.setItem(1, ItemStack.parse(registries, tag.getCompound(TAG_OUTPUT_ITEM))
                    .orElse(ItemStack.EMPTY));
        }
    }

    // === Network Sync ===

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        // Only sync what the client needs for rendering
        // Note: deployTimer NOT synced - client tracks it independently via clientTick()
        tag.putBoolean(TAG_ACTIVE, active);
        if (!processingItem.isEmpty()) {
            tag.put(TAG_PROCESSING_ITEM, processingItem.save(registries));
        }
        // Sync output item for rendering on discharge tray
        ItemStack outputItem = inventory.getItem(1);
        if (!outputItem.isEmpty()) {
            tag.put(TAG_OUTPUT_ITEM, outputItem.save(registries));
        }
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
