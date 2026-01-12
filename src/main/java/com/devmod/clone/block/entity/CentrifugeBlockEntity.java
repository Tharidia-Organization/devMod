package com.devmod.clone.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.CentrifugeBlock;
import com.devmod.clone.menu.CentrifugeMenu;

/**
 * Block entity for the Centrifuge automatic crafting machine.
 * Processes items in input slots and produces output.
 *
 * <p>Features:
 * <ul>
 *   <li>GeckoLib animated model with idle and active animations</li>
 *   <li>3 input slots + 1 output slot</li>
 *   <li>Recipe-based processing (placeholder - TODO: add CentrifugingRecipe)</li>
 *   <li>Dirty flag network sync optimization</li>
 * </ul>
 */
public class CentrifugeBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity {

    // === NBT Tag Constants ===
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_PROCESSING_ITEM = "ProcessingItem";

    // === Processing Constants ===
    private static final int DEFAULT_PROCESS_TIME = 100; // 5 seconds

    // === GeckoLib Animation ===
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Deploy then idle - plays deploy once, then loops idle. */
    protected static final RawAnimation DEPLOY_THEN_IDLE = RawAnimation.begin()
            .thenPlay("animation.clone_centrifuge.deploy")
            .thenLoop("animation.clone_centrifuge.idle");

    /** Active processing animation - drum spinning. */
    protected static final RawAnimation ACTIVE = RawAnimation.begin()
            .thenLoop("animation.clone_centrifuge.active");

    // === State ===
    private boolean active = false;
    private int progress = 0;
    private int maxProgress = DEFAULT_PROCESS_TIME;
    private ItemStack processingItem = ItemStack.EMPTY;

    // === Dirty Flags for Network Sync ===
    private boolean dirtyActive = false;
    private boolean dirtyInventory = false;

    // 3 input slots + 1 output slot
    private final SimpleContainer inventory = new SimpleContainer(4) {
        @Override
        public void setChanged() {
            super.setChanged();
            CentrifugeBlockEntity.this.setChanged();
            CentrifugeBlockEntity.this.dirtyInventory = true;
        }
    };

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    // === Server Tick ===

    /**
     * Server-side tick for processing.
     */
    public void serverTick() {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        boolean canProcess = canProcess();

        if (canProcess) {
            // Start or continue processing
            if (!active) {
                setActive(true);
            }
            processingItem = inventory.getItem(0).copyWithCount(1);
            progress++;

            if (progress >= maxProgress) {
                processItem();
                progress = 0;
                processingItem = ItemStack.EMPTY;
            }
        } else {
            // Stop processing
            if (active) {
                setActive(false);
            }
            progress = 0;
            processingItem = ItemStack.EMPTY;
        }

        // Sync to client if dirty
        syncToClient();
    }

    /**
     * Check if we can process (has valid recipe and output space).
     */
    private boolean canProcess() {
        ItemStack input = inventory.getItem(0);
        ItemStack output = inventory.getItem(3);

        if (input.isEmpty()) {
            return false;
        }

        // TODO: Replace with proper recipe lookup when CentrifugingRecipe is added
        // For now, accept any input
        if (output.isEmpty()) {
            return true;
        }

        // Check if output can stack (placeholder: iron nugget)
        return output.is(Items.IRON_NUGGET) && output.getCount() < output.getMaxStackSize();
    }

    /**
     * Process the current recipe.
     */
    private void processItem() {
        ItemStack input = inventory.getItem(0);
        ItemStack output = inventory.getItem(3);

        if (input.isEmpty()) {
            return;
        }

        // TODO: Replace with proper recipe result when CentrifugingRecipe is added
        ItemStack result = new ItemStack(Items.IRON_NUGGET);

        if (output.isEmpty()) {
            inventory.setItem(3, result.copy());
        } else if (output.is(result.getItem())) {
            output.grow(1);
        }

        input.shrink(1);
        dirtyInventory = true;
    }

    /**
     * Set active state with dirty flag and block state update.
     */
    private void setActive(boolean newActive) {
        if (active != newActive) {
            active = newActive;
            dirtyActive = true;

            // Update block state for light level
            Level lvl = level;
            if (lvl != null && !lvl.isClientSide) {
                BlockState state = getBlockState();
                if (state.getValue(CentrifugeBlock.ACTIVE) != active) {
                    lvl.setBlock(worldPosition, state.setValue(CentrifugeBlock.ACTIVE, active), 3);
                }
            }
        }
    }

    /**
     * Sync block entity data to clients only if dirty.
     */
    private void syncToClient() {
        if (!dirtyActive && !dirtyInventory) {
            return; // Nothing changed, skip sync
        }

        Level lvl = level;
        if (lvl != null && !lvl.isClientSide) {
            lvl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            dirtyActive = false;
            dirtyInventory = false;
            setChanged();
        }
    }

    // === Getters ===

    public boolean isActive() {
        return active;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public int getProgressPercent() {
        return maxProgress > 0 ? (progress * 100) / maxProgress : 0;
    }

    public float getProgressFloat() {
        return maxProgress > 0 ? (float) progress / maxProgress : 0f;
    }

    public ItemStack getProcessingItem() {
        return processingItem;
    }

    public Container getInventory() {
        return inventory;
    }

    // === GeckoLib Animation ===

    @Override
    public void registerControllers(@Nonnull AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (active) {
                return state.setAndContinue(ACTIVE);
            } else {
                return state.setAndContinue(DEPLOY_THEN_IDLE);
            }
        }));
    }

    @Override
    @Nonnull
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // === MenuProvider ===

    @Override
    @Nonnull
    public Component getDisplayName() {
        return Objects.requireNonNull(Component.translatable("block.devmod.centrifuge"));
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new CentrifugeMenu(containerId, playerInv, this);
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
        if (maxProgress <= 0) {
            maxProgress = DEFAULT_PROCESS_TIME;
        }
        active = tag.getBoolean(TAG_ACTIVE);

        if (tag.contains(TAG_PROCESSING_ITEM)) {
            processingItem = ItemStack.parse(registries, tag.getCompound(TAG_PROCESSING_ITEM))
                    .orElse(ItemStack.EMPTY);
        } else {
            processingItem = ItemStack.EMPTY;
        }

        // Load inventory
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
    }

    // === Network Sync ===

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        // Only sync what the client needs for rendering
        tag.putBoolean(TAG_ACTIVE, active);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);

        if (!processingItem.isEmpty()) {
            tag.put(TAG_PROCESSING_ITEM, processingItem.save(registries));
        }
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
