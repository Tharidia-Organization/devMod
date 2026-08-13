package com.devmod.clone.block.entity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.item.GrinderItem;
import com.devmod.clone.recipe.CloneRecipeTypes;
import com.devmod.clone.recipe.PulverizingRecipe;
import com.devmod.config.Config;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

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
public class ClonePulverizerBlockEntity extends BlockEntity implements GeoBlockEntity, WorldlyContainer {

    // Slot indices for WorldlyContainer
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_GRINDER_LEFT = 2;
    public static final int SLOT_GRINDER_RIGHT = 3;
    private static final int INVENTORY_SIZE = 4;

    // Slot access arrays for WorldlyContainer (hopper interaction)
    private static final int[] SLOTS_FOR_UP = {SLOT_INPUT};
    private static final int[] SLOTS_FOR_DOWN = {SLOT_OUTPUT};
    private static final int[] SLOTS_FOR_SIDES = {SLOT_OUTPUT};

    // Sound configuration
    private static final int SOUND_INTERVAL = 20;  // Play sound every second (20 ticks)

    // NBT keys
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_PROCESSING_ITEM = "ProcessingItem";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_OUTPUT_ITEM = "OutputItem";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_OPERATIONS_COUNT = "OperationsCount";
    private static final String TAG_GRINDER_LEFT = "GrinderLeft";
    private static final String TAG_GRINDER_RIGHT = "GrinderRight";

    // Animation cache
    @SuppressWarnings("this-escape")
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

    // Sound timer for periodic crushing sounds
    private int soundTimer = 0;

    // Grinder operations counter for wear
    private int operationsCount = 0;

    // Internal inventory (input, output, grinder_left, grinder_right)
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE) {
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
    @Nonnull
    private ItemStack addToInput(ItemStack stack) {
        ItemStack current = inventory.getItem(0);

        if (current.isEmpty()) {
            inventory.setItem(0, Objects.requireNonNull(stack.copy()));
            return Objects.requireNonNull(ItemStack.EMPTY);
        }

        if (ItemStack.isSameItemSameComponents(current, Objects.requireNonNull(stack))) {
            int space = current.getMaxStackSize() - current.getCount();
            int toAdd = Math.min(space, stack.getCount());
            current.grow(toAdd);

            if (toAdd >= stack.getCount()) {
                return Objects.requireNonNull(ItemStack.EMPTY);
            } else {
                ItemStack remaining = stack.copy();
                remaining.shrink(toAdd);
                return remaining;
            }
        }

        return stack;
    }

    /**
     * Check if the output slot can accept the given result.
     * @param result The result item to check
     * @return true if output can accept the item
     */
    private boolean canAcceptOutput(ItemStack result) {
        if (result.isEmpty()) {
            return true;
        }

        ItemStack current = inventory.getItem(SLOT_OUTPUT);

        if (current.isEmpty()) {
            return true;
        }

        if (!ItemStack.isSameItemSameComponents(current, result)) {
            return false;  // Different item type
        }

        // Check if there's room for at least one more
        return current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    /**
     * Process items in input slot.
     * Requires both grinders to be installed.
     */
    private void processItems() {
        Level lvl = level;
        ItemStack input = inventory.getItem(SLOT_INPUT);

        // Require both grinders to process
        if (!hasGrinders()) {
            if (active) {
                setActive(false);
            }
            progress = 0;
            soundTimer = 0;
            return;
        }

        if (input.isEmpty()) {
            // Nothing to process
            if (active) {
                setActive(false);
            }
            progress = 0;
            soundTimer = 0;
            processingItem = ItemStack.EMPTY;
            currentRecipe = null;
            return;
        }

        // Find recipe if not cached
        PulverizingRecipe cachedRecipe = currentRecipe;
        if (cachedRecipe == null || !cachedRecipe.getIngredient().test(input)) {
            Optional<PulverizingRecipe> recipe = findRecipe(input);
            if (recipe.isEmpty()) {
                // No valid recipe
                setActive(false);
                return;
            }
            cachedRecipe = recipe.get();
            currentRecipe = cachedRecipe;
            maxProgress = cachedRecipe.getProcessingTime();
        }

        // Get result for this recipe
        ItemStack result = Objects.requireNonNull(cachedRecipe.getResult());

        // Process
        setActive(true);
        processingItem = input.copyWithCount(1);
        progress++;

        // Play crushing sound at intervals
        soundTimer++;
        if (soundTimer >= SOUND_INTERVAL && lvl != null && !lvl.isClientSide) {
            soundTimer = 0;
            lvl.playSound(
                    null,
                    Objects.requireNonNull(worldPosition),
                    Objects.requireNonNull(SoundEvents.GRINDSTONE_USE),
                    SoundSource.BLOCKS,
                    0.5f,
                    0.8f + (lvl.random.nextFloat() * 0.2f - 0.1f)  // Slight pitch variation
            );
        }

        // Spawn crushing particles every 5 ticks
        if (progress % 5 == 0 && lvl instanceof ServerLevel serverLevel) {
            spawnCrushingParticles(serverLevel, input);
        }

        if (progress >= maxProgress) {
            // Check if output can accept the result BEFORE consuming input
            if (!canAcceptOutput(result)) {
                // Output full or blocked - pause processing, don't lose item
                return;
            }

            // Complete processing - safe to consume input now
            input.shrink(1);

            // Add result to output slot (slot 1) - rendered visually, auto-pickup
            addToOutput(result.copy());

            // Damage grinders (every OPERATIONS_PER_DAMAGE completions)
            damageGrinders();

            // Play completion sound
            if (lvl != null && !lvl.isClientSide) {
                lvl.playSound(
                        null,
                        Objects.requireNonNull(worldPosition),
                        Objects.requireNonNull(SoundEvents.ITEM_PICKUP),
                        SoundSource.BLOCKS,
                        0.4f,
                        1.2f
                );
            }

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
            inventory.setItem(1, Objects.requireNonNull(stack.copy()));
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
            ItemStack toGive = Objects.requireNonNull(output.copy());
            if (player.getInventory().add(toGive)) {
                // Successfully added to inventory
                inventory.setItem(1, Objects.requireNonNull(ItemStack.EMPTY));
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

    // === Grinder Methods ===

    /**
     * Check if both grinders are installed.
     */
    public boolean hasGrinders() {
        return hasLeftGrinder() && hasRightGrinder();
    }

    /**
     * Check if left grinder is installed.
     */
    public boolean hasLeftGrinder() {
        ItemStack grinder = inventory.getItem(SLOT_GRINDER_LEFT);
        return !grinder.isEmpty() && grinder.getItem() instanceof GrinderItem;
    }

    /**
     * Check if right grinder is installed.
     */
    public boolean hasRightGrinder() {
        ItemStack grinder = inventory.getItem(SLOT_GRINDER_RIGHT);
        return !grinder.isEmpty() && grinder.getItem() instanceof GrinderItem;
    }

    /**
     * Damage both grinders after processing operations.
     * Called after every recipe completion.
     * Uses config value for operations per damage point.
     */
    private void damageGrinders() {
        operationsCount++;
        int opsPerDamage = Config.GRINDER_OPERATIONS_PER_DAMAGE.get();
        if (operationsCount >= opsPerDamage) {
            operationsCount = 0;
            damageGrinder(SLOT_GRINDER_LEFT);
            damageGrinder(SLOT_GRINDER_RIGHT);
        }
    }

    /**
     * Damage a single grinder in the specified slot.
     */
    private void damageGrinder(int slot) {
        ItemStack grinder = inventory.getItem(slot);
        if (grinder.isEmpty()) return;

        int damage = grinder.getDamageValue() + 1;
        if (damage >= grinder.getMaxDamage()) {
            // Grinder broke
            inventory.setItem(slot, Objects.requireNonNull(ItemStack.EMPTY));
            Level lvl = level;
            if (lvl != null && !lvl.isClientSide) {
                lvl.playSound(null, Objects.requireNonNull(worldPosition),
                        Objects.requireNonNull(SoundEvents.ITEM_BREAK),
                        SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        } else {
            grinder.setDamageValue(damage);
        }
        setChanged();
        dirtyOutput = true; // Trigger sync for grinder state
    }

    /**
     * Handle grinder interaction from player.
     * @param player The player interacting
     * @param heldItem The item the player is holding
     * @return InteractionResult indicating if interaction was handled
     */
    public InteractionResult handleGrinderInteraction(Player player, ItemStack heldItem) {
        // Shift+click with empty hand = extract grinder
        if (player.isShiftKeyDown() && heldItem.isEmpty()) {
            return tryExtractGrinder(player);
        }

        // Holding a grinder = try to insert
        if (heldItem.getItem() instanceof GrinderItem) {
            return tryInsertGrinder(player, heldItem);
        }

        return InteractionResult.PASS;
    }

    /**
     * Try to extract a grinder (right first, then left).
     */
    private InteractionResult tryExtractGrinder(Player player) {
        // Try right grinder first, then left
        for (int slot : new int[]{SLOT_GRINDER_RIGHT, SLOT_GRINDER_LEFT}) {
            ItemStack grinder = inventory.getItem(slot);
            if (!grinder.isEmpty()) {
                if (!player.addItem(Objects.requireNonNull(grinder.copy()))) {
                    // Drop if inventory full
                    player.drop(Objects.requireNonNull(grinder.copy()), false);
                }
                inventory.setItem(slot, Objects.requireNonNull(ItemStack.EMPTY));
                Level lvl = level;
                if (lvl != null && !lvl.isClientSide) {
                    lvl.playSound(null, Objects.requireNonNull(worldPosition),
                            Objects.requireNonNull(SoundEvents.ITEM_PICKUP),
                            SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                setChanged();
                dirtyOutput = true;
                syncToClient();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Try to insert a grinder (left first, then right).
     */
    private InteractionResult tryInsertGrinder(Player player, ItemStack heldItem) {
        // Try left slot first, then right
        for (int slot : new int[]{SLOT_GRINDER_LEFT, SLOT_GRINDER_RIGHT}) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, Objects.requireNonNull(heldItem.copyWithCount(1)));
                if (!player.isCreative()) {
                    heldItem.shrink(1);
                }
                Level lvl = level;
                if (lvl != null && !lvl.isClientSide) {
                    lvl.playSound(null, Objects.requireNonNull(worldPosition),
                            Objects.requireNonNull(SoundEvents.ANVIL_PLACE),
                            SoundSource.BLOCKS, 0.5f, 1.2f);
                }
                setChanged();
                dirtyOutput = true;
                syncToClient();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS; // Both slots full
    }

    // === WorldlyContainer Implementation ===

    @Override
    public int[] getSlotsForFace(@Nonnull Direction side) {
        return switch (side) {
            case UP -> SLOTS_FOR_UP;      // Input from above
            case DOWN -> SLOTS_FOR_DOWN;   // Output from below
            default -> SLOTS_FOR_SIDES;    // Output from sides
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, @Nonnull ItemStack stack, @Nullable Direction side) {
        // Only allow input slot from above, and only if recipe exists
        return slot == SLOT_INPUT && side == Direction.UP && findRecipe(stack).isPresent();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, @Nonnull ItemStack stack, @Nonnull Direction side) {
        // Only allow output slot extraction from down and sides
        return slot == SLOT_OUTPUT && side != Direction.UP;
    }

    // === Container Interface Delegation ===

    @Override
    public int getContainerSize() {
        return inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    @Nonnull
    public ItemStack getItem(int slot) {
        return Objects.requireNonNull(inventory.getItem(slot));
    }

    @Override
    @Nonnull
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = Objects.requireNonNull(inventory.removeItem(slot, amount));
        if (slot == SLOT_OUTPUT && !result.isEmpty()) {
            dirtyOutput = true;
            syncToClient();
        }
        return result;
    }

    @Override
    @Nonnull
    public ItemStack removeItemNoUpdate(int slot) {
        return Objects.requireNonNull(inventory.removeItemNoUpdate(slot));
    }

    @Override
    public void setItem(int slot, @Nonnull ItemStack stack) {
        inventory.setItem(slot, stack);
        if (slot == SLOT_OUTPUT) {
            dirtyOutput = true;
        }
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        return inventory.stillValid(player);
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
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
        ItemParticleOption particleOption = new ItemParticleOption(
            Objects.requireNonNull(ParticleTypes.ITEM), Objects.requireNonNull(item));

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
        SingleRecipeInput recipeInput = new SingleRecipeInput(Objects.requireNonNull(input));

        return recipeManager.getRecipeFor(Objects.requireNonNull(CloneRecipeTypes.PULVERIZING.get()), recipeInput, lvl)
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
            BlockState currentState = Objects.requireNonNull(getBlockState());
            lvl.sendBlockUpdated(Objects.requireNonNull(worldPosition), currentState, currentState, 3);
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
    @Nonnull
    public ItemStack extractOutput() {
        ItemStack slotContent = inventory.getItem(1);
        if (slotContent.isEmpty()) {
            return Objects.requireNonNull(ItemStack.EMPTY);
        }
        // Make a copy before clearing the slot
        ItemStack output = Objects.requireNonNull(slotContent.copy());
        inventory.setItem(1, Objects.requireNonNull(ItemStack.EMPTY));
        dirtyOutput = true;
        syncToClient();
        return output;
    }

    // === GeckoLib Animation ===

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
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
        return Objects.requireNonNull(cache);
    }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        tag.putBoolean(TAG_ACTIVE, active);
        tag.putInt(TAG_OPERATIONS_COUNT, operationsCount);

        if (!processingItem.isEmpty()) {
            tag.put(TAG_PROCESSING_ITEM, Objects.requireNonNull(processingItem.save(registries)));
        }

        // Save inventory
        CompoundTag invTag = new CompoundTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                invTag.put("Slot" + i, Objects.requireNonNull(stack.save(registries)));
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
        operationsCount = tag.getInt(TAG_OPERATIONS_COUNT);

        if (tag.contains(TAG_PROCESSING_ITEM)) {
            processingItem = Objects.requireNonNull(
                ItemStack.parse(registries, Objects.requireNonNull(tag.getCompound(TAG_PROCESSING_ITEM)))
                    .orElse(ItemStack.EMPTY));
        } else {
            processingItem = ItemStack.EMPTY;
        }

        // Load inventory (only from full save, not network sync)
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag invTag = tag.getCompound(TAG_INVENTORY);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                String key = "Slot" + i;
                if (invTag.contains(key)) {
                    inventory.setItem(i, Objects.requireNonNull(
                        ItemStack.parse(registries, Objects.requireNonNull(invTag.getCompound(key)))
                            .orElse(ItemStack.EMPTY)));
                } else {
                    inventory.setItem(i, Objects.requireNonNull(ItemStack.EMPTY));
                }
            }
        }

        // Load output item (from network sync - takes priority if present)
        if (tag.contains(TAG_OUTPUT_ITEM)) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(
                ItemStack.parse(registries, Objects.requireNonNull(tag.getCompound(TAG_OUTPUT_ITEM)))
                    .orElse(ItemStack.EMPTY)));
        } else if (tag.getBoolean("OutputEmpty")) {
            // Server says output is empty - clear it on client
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
        }

        // Load grinder slots (from network sync for roller visibility)
        if (tag.contains(TAG_GRINDER_LEFT)) {
            inventory.setItem(SLOT_GRINDER_LEFT, Objects.requireNonNull(
                ItemStack.parse(registries, Objects.requireNonNull(tag.getCompound(TAG_GRINDER_LEFT)))
                    .orElse(ItemStack.EMPTY)));
        }
        if (tag.contains(TAG_GRINDER_RIGHT)) {
            inventory.setItem(SLOT_GRINDER_RIGHT, Objects.requireNonNull(
                ItemStack.parse(registries, Objects.requireNonNull(tag.getCompound(TAG_GRINDER_RIGHT)))
                    .orElse(ItemStack.EMPTY)));
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
        // loadAdditional reads these unconditionally, so they must be present in
        // every update tag or each sync resets the client's crush animation to 0.
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        if (!processingItem.isEmpty()) {
            tag.put(TAG_PROCESSING_ITEM, Objects.requireNonNull(processingItem.save(registries)));
        }
        // Always sync output item state (even when empty) for proper client rendering
        ItemStack outputItem = inventory.getItem(SLOT_OUTPUT);
        if (!outputItem.isEmpty()) {
            tag.put(TAG_OUTPUT_ITEM, Objects.requireNonNull(outputItem.save(registries)));
        } else {
            // Mark output as empty so client clears it
            tag.putBoolean("OutputEmpty", true);
        }
        // Sync grinder slots for roller bone visibility
        ItemStack leftGrinder = inventory.getItem(SLOT_GRINDER_LEFT);
        ItemStack rightGrinder = inventory.getItem(SLOT_GRINDER_RIGHT);
        if (!leftGrinder.isEmpty()) {
            tag.put(TAG_GRINDER_LEFT, Objects.requireNonNull(leftGrinder.save(registries)));
        }
        if (!rightGrinder.isEmpty()) {
            tag.put(TAG_GRINDER_RIGHT, Objects.requireNonNull(rightGrinder.save(registries)));
        }
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
