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

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.CentrifugeBlock;
import com.devmod.clone.menu.CentrifugeMenu;

/**
 * Block entity for the Centrifuge automatic crafting machine.
 * Processes items in input slots and produces output.
 */
public class CentrifugeBlockEntity extends BlockEntity implements MenuProvider {

    private static final int PROCESS_TIME = 100; // 5 seconds
    private static final String TAG_PROGRESS = "Progress";

    // 3 input slots + 1 output slot
    private final SimpleContainer inventory = new SimpleContainer(4) {
        @Override
        public void setChanged() {
            super.setChanged();
            CentrifugeBlockEntity.this.setChanged();
        }
    };

    private int progress = 0;

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    /**
     * Server-side tick for processing.
     */
    public void serverTick() {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        boolean wasActive = getBlockState().getValue(CentrifugeBlock.ACTIVE);
        boolean canProcess = canProcess();

        if (canProcess) {
            progress++;
            if (progress >= PROCESS_TIME) {
                processItem();
                progress = 0;
            }
            if (!wasActive) {
                setActiveState(true);
            }
        } else {
            progress = 0;
            if (wasActive) {
                setActiveState(false);
            }
        }

        setChanged();
    }

    /**
     * Check if we can process (has valid recipe).
     */
    private boolean canProcess() {
        // Simple example: requires item in slot 0, output slot must have space
        ItemStack input = inventory.getItem(0);
        ItemStack output = inventory.getItem(3);

        if (input.isEmpty()) {
            return false;
        }

        // Example recipe: any item -> processed version
        // In a real implementation, this would check against a recipe registry
        if (output.isEmpty()) {
            return true;
        }

        // Check if output can stack
        return output.getCount() < output.getMaxStackSize();
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

        // Simple processing: convert input to output
        // In a real implementation, this would use proper recipes
        ItemStack result = new ItemStack(Items.IRON_NUGGET); // Placeholder output

        if (output.isEmpty()) {
            inventory.setItem(3, result.copy());
        } else if (output.is(result.getItem())) {
            output.grow(1);
        }

        input.shrink(1);
    }

    private void setActiveState(boolean active) {
        Level lvl = level;
        if (lvl != null && !lvl.isClientSide) {
            BlockState state = getBlockState();
            if (state.getValue(CentrifugeBlock.ACTIVE) != active) {
                lvl.setBlock(worldPosition, state.setValue(CentrifugeBlock.ACTIVE, active), 3);
            }
        }
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return PROCESS_TIME;
    }

    public int getProgressPercent() {
        return PROCESS_TIME > 0 ? (progress * 100) / PROCESS_TIME : 0;
    }

    public Container getInventory() {
        return inventory;
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

    // === NBT ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_PROGRESS, progress);

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

        // Load inventory
        CompoundTag invTag = tag.getCompound("Inventory");
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            String key = "Slot" + i;
            if (invTag.contains(key)) {
                inventory.setItem(i, ItemStack.parse(registries, invTag.getCompound(key)).orElse(ItemStack.EMPTY));
            } else {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
