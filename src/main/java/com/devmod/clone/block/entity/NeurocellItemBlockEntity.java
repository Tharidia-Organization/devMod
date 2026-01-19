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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.NeurocellItemBlock;
import com.devmod.clone.menu.NeurocellItemMenu;

/**
 * Block entity for the Neurocell Item Display - shows a single item floating and rotating.
 */
public class NeurocellItemBlockEntity extends BlockEntity implements MenuProvider {

    private static final String TAG_DISPLAY_ITEM = "DisplayItem";
    private static final String TAG_ROTATION_SPEED = "RotationSpeed";
    private static final String TAG_SPINNING = "Spinning";
    private static final String TAG_ROTATION_ANGLE = "RotationAngle";
    private static final String TAG_ROTATION_MODE = "RotationMode";

    /**
     * Rotation mode for the item display.
     */
    public enum RotationMode {
        /** Automatic slow rotation (default) */
        AUTO,
        /** Fixed angle, no rotation */
        FIXED,
        /** Manual rotation controlled by player */
        MANUAL
    }

    /** The item being displayed */
    @Nonnull
    private ItemStack displayItem = Objects.requireNonNull(ItemStack.EMPTY);

    /** Rotation speed multiplier (1.0 = normal) */
    private float rotationSpeed = 1.0f;

    /** Whether the item auto-rotates (legacy, use rotationMode instead) */
    private boolean spinning = true;

    /** Manual rotation angle (degrees) */
    private float rotationAngle = 0f;

    /** Current rotation mode */
    @Nonnull
    private RotationMode rotationMode = RotationMode.AUTO;

    public NeurocellItemBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.NEUROCELL_ITEM.get(), pos, state);
    }

    // === Item Access ===

    @Nonnull
    public ItemStack getDisplayItem() {
        return Objects.requireNonNull(displayItem);
    }

    public void setDisplayItem(@Nonnull ItemStack stack) {
        this.displayItem = Objects.requireNonNull(stack.copyWithCount(Math.min(stack.getCount(), 1)));
        onItemChanged();
    }

    public boolean hasDisplayItem() {
        return !displayItem.isEmpty();
    }

    public float getRotationSpeed() {
        return rotationSpeed;
    }

    public void setRotationSpeed(float speed) {
        this.rotationSpeed = Math.max(0.0f, Math.min(5.0f, speed));
        setChanged();
        syncToClient();
    }

    public boolean isSpinning() {
        return spinning;
    }

    public void setSpinning(boolean spinning) {
        this.spinning = spinning;
        // Also update rotation mode for consistency
        this.rotationMode = spinning ? RotationMode.AUTO : RotationMode.FIXED;
        setChanged();
        syncToClient();
    }

    /**
     * Get rotation angle for display.
     */
    public float getRotationAngle() {
        return rotationAngle;
    }

    /**
     * Set manual rotation angle.
     */
    public void setRotationAngle(float angle) {
        this.rotationAngle = angle;
        setChanged();
        syncToClient();
    }

    /**
     * Get current rotation mode.
     */
    @Nonnull
    public RotationMode getRotationMode() {
        return rotationMode;
    }

    /**
     * Set rotation mode.
     */
    public void setRotationMode(@Nonnull RotationMode mode) {
        this.rotationMode = Objects.requireNonNull(mode);
        // Keep spinning in sync for backward compatibility
        this.spinning = (mode == RotationMode.AUTO);
        setChanged();
        syncToClient();
    }

    /**
     * Cycle to next rotation mode.
     */
    public void cycleRotationMode() {
        RotationMode[] modes = RotationMode.values();
        int next = (rotationMode.ordinal() + 1) % modes.length;
        setRotationMode(Objects.requireNonNull(modes[next]));
    }

    // === Internal ===

    private void onItemChanged() {
        setChanged();
        syncToClient();
        updateActiveState(hasDisplayItem());
    }

    private void updateActiveState(boolean active) {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        BlockPos pos = Objects.requireNonNull(worldPosition);
        BooleanProperty activeProp = Objects.requireNonNull(NeurocellItemBlock.ACTIVE);
        BlockState currentState = lvl.getBlockState(pos);
        if (currentState.hasProperty(activeProp) && currentState.getValue(activeProp) != active) {
            // Update lower half
            lvl.setBlock(pos, Objects.requireNonNull(currentState.setValue(activeProp, active)), 3);
            // Update upper half
            BlockPos upperPos = Objects.requireNonNull(pos.above());
            BlockState upperState = lvl.getBlockState(upperPos);
            if (upperState.hasProperty(activeProp)) {
                lvl.setBlock(upperPos, Objects.requireNonNull(upperState.setValue(activeProp, active)), 3);
            }
        }
    }

    private void syncToClient() {
        Level lvl = level;
        if (lvl != null && !lvl.isClientSide) {
            BlockPos pos = Objects.requireNonNull(worldPosition);
            BlockState state = Objects.requireNonNull(getBlockState());
            lvl.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /**
     * Drop the display item when block is broken.
     */
    public void dropItem(@Nonnull Level level, @Nonnull BlockPos pos) {
        if (!displayItem.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), displayItem);
            displayItem = Objects.requireNonNull(ItemStack.EMPTY);
        }
    }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (!displayItem.isEmpty()) {
            tag.put(TAG_DISPLAY_ITEM, Objects.requireNonNull(displayItem.save(registries)));
        }
        tag.putFloat(TAG_ROTATION_SPEED, rotationSpeed);
        tag.putBoolean(TAG_SPINNING, spinning);
        tag.putFloat(TAG_ROTATION_ANGLE, rotationAngle);
        tag.putString(TAG_ROTATION_MODE, Objects.requireNonNull(rotationMode.name()));
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains(TAG_DISPLAY_ITEM)) {
            displayItem = Objects.requireNonNull(
                ItemStack.parse(registries, Objects.requireNonNull(tag.getCompound(TAG_DISPLAY_ITEM)))
                    .orElse(ItemStack.EMPTY)
            );
        } else {
            displayItem = Objects.requireNonNull(ItemStack.EMPTY);
        }

        rotationSpeed = tag.contains(TAG_ROTATION_SPEED) ? tag.getFloat(TAG_ROTATION_SPEED) : 1.0f;
        spinning = !tag.contains(TAG_SPINNING) || tag.getBoolean(TAG_SPINNING);
        rotationAngle = tag.getFloat(TAG_ROTATION_ANGLE);

        // Load rotation mode with fallback to AUTO
        if (tag.contains(TAG_ROTATION_MODE)) {
            try {
                rotationMode = RotationMode.valueOf(tag.getString(TAG_ROTATION_MODE));
            } catch (IllegalArgumentException e) {
                rotationMode = RotationMode.AUTO;
            }
        } else {
            // Legacy: derive from spinning field
            rotationMode = spinning ? RotationMode.AUTO : RotationMode.FIXED;
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);

        if (!displayItem.isEmpty()) {
            tag.put(TAG_DISPLAY_ITEM, Objects.requireNonNull(displayItem.save(registries)));
        }
        tag.putFloat(TAG_ROTATION_SPEED, rotationSpeed);
        tag.putBoolean(TAG_SPINNING, spinning);
        tag.putFloat(TAG_ROTATION_ANGLE, rotationAngle);
        tag.putString(TAG_ROTATION_MODE, Objects.requireNonNull(rotationMode.name()));

        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    // === MenuProvider implementation ===

    @Override
    @Nonnull
    public Component getDisplayName() {
        return Objects.requireNonNull(Component.translatable("block.devmod.neurocell_item"));
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new NeurocellItemMenu(containerId, playerInv, this);
    }

    // === Inventory access for menu ===

    @Nonnull
    public Container getInventory() {
        SimpleContainer container = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                displayItem = Objects.requireNonNull(this.getItem(0).copy());
                NeurocellItemBlockEntity.this.setChanged();
                NeurocellItemBlockEntity.this.onItemChanged();
            }

            @Override
            public void setItem(int slot, @Nonnull ItemStack stack) {
                super.setItem(slot, stack);
                displayItem = Objects.requireNonNull(stack.copy());
                NeurocellItemBlockEntity.this.setChanged();
                NeurocellItemBlockEntity.this.onItemChanged();
            }

            @Override
            @Nonnull
            public ItemStack removeItem(int slot, int amount) {
                ItemStack result = Objects.requireNonNull(super.removeItem(slot, amount));
                displayItem = Objects.requireNonNull(this.getItem(0).copy());
                NeurocellItemBlockEntity.this.setChanged();
                NeurocellItemBlockEntity.this.onItemChanged();
                return result;
            }

            @Override
            @Nonnull
            public ItemStack removeItemNoUpdate(int slot) {
                ItemStack result = Objects.requireNonNull(super.removeItemNoUpdate(slot));
                displayItem = Objects.requireNonNull(this.getItem(0).copy());
                return result;
            }
        };

        container.setItem(0, Objects.requireNonNull(displayItem.copy()));
        return container;
    }
}
