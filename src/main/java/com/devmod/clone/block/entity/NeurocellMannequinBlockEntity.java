package com.devmod.clone.block.entity;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.NeurocellMannequinBlock;
import com.devmod.clone.menu.NeurocellMannequinMenu;

/**
 * Block entity for the Neurocell Mannequin - displays armor and equipment on a humanoid model.
 * Stores 6 equipment slots: HEAD, CHEST, LEGS, FEET, MAINHAND, OFFHAND.
 */
public class NeurocellMannequinBlockEntity extends BlockEntity implements MenuProvider {

    private static final String TAG_EQUIPMENT = "Equipment";
    private static final String TAG_ROTATION = "RotationAngle";
    private static final String TAG_ROTATION_MODE = "RotationMode";
    private static final String TAG_SKIN_UUID = "SkinUUID";

    /**
     * Rotation mode for the mannequin display.
     */
    public enum RotationMode {
        /** Automatic slow rotation (default) */
        AUTO,
        /** Fixed angle, no rotation */
        FIXED,
        /** Manual rotation controlled by player */
        MANUAL
    }

    /**
     * Equipment slots stored in order: HEAD(0), CHEST(1), LEGS(2), FEET(3), MAINHAND(4), OFFHAND(5)
     */
    @Nonnull
    private final NonNullList<ItemStack> equipment = Objects.requireNonNull(
        NonNullList.withSize(6, Objects.requireNonNull(ItemStack.EMPTY))
    );

    /**
     * Manual rotation angle for the mannequin display (degrees).
     */
    private float rotationAngle = 0f;

    /**
     * Current rotation mode.
     */
    @Nonnull
    private RotationMode rotationMode = RotationMode.AUTO;

    /**
     * Custom skin UUID. If null, uses default Steve skin.
     */
    @Nullable
    private UUID skinUUID = null;

    public NeurocellMannequinBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.NEUROCELL_MANNEQUIN.get(), pos, state);
    }

    // === Equipment Access ===

    /**
     * Get equipment for a specific slot.
     * @param slot The equipment slot index (0-5)
     */
    @Nonnull
    public ItemStack getEquipment(int slot) {
        if (slot < 0 || slot >= 6) {
            return Objects.requireNonNull(ItemStack.EMPTY);
        }
        return Objects.requireNonNull(equipment.get(slot));
    }

    /**
     * Get equipment by EquipmentSlot enum.
     */
    @Nonnull
    public ItemStack getEquipment(@Nonnull EquipmentSlot slot) {
        return getEquipment(equipmentSlotToIndex(slot));
    }

    /**
     * Set equipment for a specific slot.
     */
    public void setEquipment(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= 6) {
            return;
        }
        equipment.set(slot, Objects.requireNonNull(stack.copyWithCount(Math.min(stack.getCount(), 1))));
        onEquipmentChanged();
    }

    /**
     * Set equipment by EquipmentSlot enum.
     */
    public void setEquipment(@Nonnull EquipmentSlot slot, @Nonnull ItemStack stack) {
        setEquipment(equipmentSlotToIndex(slot), stack);
    }

    /**
     * Check if mannequin has any equipment.
     */
    public boolean hasEquipment() {
        for (ItemStack stack : equipment) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
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

    /**
     * Get custom skin UUID.
     * @return UUID for custom skin, or null for default Steve skin
     */
    @Nullable
    public UUID getSkinUUID() {
        return skinUUID;
    }

    /**
     * Set custom skin UUID.
     * @param uuid Player UUID for skin, or null for default Steve
     */
    public void setSkinUUID(@Nullable UUID uuid) {
        this.skinUUID = uuid;
        setChanged();
        syncToClient();
    }

    /**
     * Check if mannequin has a custom skin.
     */
    public boolean hasCustomSkin() {
        return skinUUID != null;
    }

    // === Slot Mapping ===

    /**
     * Convert EquipmentSlot to our array index.
     */
    public static int equipmentSlotToIndex(@Nonnull EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            case MAINHAND -> 4;
            case OFFHAND -> 5;
            default -> -1;
        };
    }

    /**
     * Convert array index to EquipmentSlot.
     */
    @Nonnull
    public static EquipmentSlot indexToEquipmentSlot(int index) {
        return switch (index) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.FEET;
            case 4 -> EquipmentSlot.MAINHAND;
            case 5 -> EquipmentSlot.OFFHAND;
            default -> EquipmentSlot.MAINHAND;
        };
    }

    /**
     * Check if an item can go in a specific slot.
     */
    public static boolean isValidForSlot(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        // Mainhand and offhand accept any item
        if (slot == 4 || slot == 5) {
            return true;
        }

        // Armor slots only accept matching armor
        if (stack.getItem() instanceof ArmorItem armorItem) {
            EquipmentSlot armorSlot = Objects.requireNonNull(armorItem.getEquipmentSlot());
            return equipmentSlotToIndex(armorSlot) == slot;
        }

        // HEAD slot also accepts skulls and some other wearable items
        if (slot == 0) {
            // Allow skulls, carved pumpkins, etc.
            return stack.is(Objects.requireNonNull(net.minecraft.tags.ItemTags.HEAD_ARMOR))
                || stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                    && (blockItem.getBlock() instanceof net.minecraft.world.level.block.SkullBlock
                        || blockItem.getBlock() instanceof net.minecraft.world.level.block.CarvedPumpkinBlock);
        }

        return false;
    }

    // === Internal ===

    private void onEquipmentChanged() {
        setChanged();
        syncToClient();
        updateActiveState(hasEquipment());
    }

    private void updateActiveState(boolean active) {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        BlockPos pos = Objects.requireNonNull(worldPosition);
        BooleanProperty activeProp = Objects.requireNonNull(NeurocellMannequinBlock.ACTIVE);
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
     * Drop all equipment when block is broken.
     */
    public void dropEquipment(@Nonnull Level level, @Nonnull BlockPos pos) {
        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipment.get(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                equipment.set(i, Objects.requireNonNull(ItemStack.EMPTY));
            }
        }
    }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // Save equipment as list
        ListTag equipmentList = new ListTag();
        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipment.get(i);
            CompoundTag itemTag = new CompoundTag();
            itemTag.putInt("Slot", i);
            if (!stack.isEmpty()) {
                itemTag.put("Item", Objects.requireNonNull(stack.save(registries)));
            }
            equipmentList.add(itemTag);
        }
        tag.put(TAG_EQUIPMENT, equipmentList);
        tag.putFloat(TAG_ROTATION, rotationAngle);
        tag.putString(TAG_ROTATION_MODE, Objects.requireNonNull(rotationMode.name()));
        if (skinUUID != null) {
            tag.putUUID(TAG_SKIN_UUID, skinUUID);
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Clear equipment
        for (int i = 0; i < 6; i++) {
            equipment.set(i, Objects.requireNonNull(ItemStack.EMPTY));
        }

        // Load equipment
        if (tag.contains(TAG_EQUIPMENT, Tag.TAG_LIST)) {
            ListTag equipmentList = tag.getList(TAG_EQUIPMENT, Tag.TAG_COMPOUND);
            for (int i = 0; i < equipmentList.size(); i++) {
                CompoundTag itemTag = equipmentList.getCompound(i);
                int slot = itemTag.getInt("Slot");
                if (slot >= 0 && slot < 6 && itemTag.contains("Item")) {
                    ItemStack stack = ItemStack.parse(registries, Objects.requireNonNull(itemTag.getCompound("Item")))
                        .orElse(ItemStack.EMPTY);
                    equipment.set(slot, Objects.requireNonNull(stack));
                }
            }
        }

        rotationAngle = tag.getFloat(TAG_ROTATION);

        // Load rotation mode
        if (tag.contains(TAG_ROTATION_MODE, Tag.TAG_STRING)) {
            try {
                rotationMode = RotationMode.valueOf(tag.getString(TAG_ROTATION_MODE));
            } catch (IllegalArgumentException e) {
                rotationMode = RotationMode.AUTO;
            }
        } else {
            rotationMode = RotationMode.AUTO;
        }

        // Load skin UUID
        if (tag.hasUUID(TAG_SKIN_UUID)) {
            skinUUID = tag.getUUID(TAG_SKIN_UUID);
        } else {
            skinUUID = null;
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);

        // Include equipment for client rendering
        ListTag equipmentList = new ListTag();
        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipment.get(i);
            CompoundTag itemTag = new CompoundTag();
            itemTag.putInt("Slot", i);
            if (!stack.isEmpty()) {
                itemTag.put("Item", Objects.requireNonNull(stack.save(registries)));
            }
            equipmentList.add(itemTag);
        }
        tag.put(TAG_EQUIPMENT, equipmentList);
        tag.putFloat(TAG_ROTATION, rotationAngle);
        tag.putString(TAG_ROTATION_MODE, Objects.requireNonNull(rotationMode.name()));
        if (skinUUID != null) {
            tag.putUUID(TAG_SKIN_UUID, skinUUID);
        }

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
        return Objects.requireNonNull(Component.translatable("block.devmod.neurocell_mannequin"));
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new NeurocellMannequinMenu(containerId, playerInv, this);
    }

    // === Inventory access for menu ===

    /**
     * Get the inventory container for menu access.
     */
    @Nonnull
    public Container getInventory() {
        // Create a container view that syncs bidirectionally with equipment array
        SimpleContainer container = new SimpleContainer(6) {
            @Override
            public void setChanged() {
                super.setChanged();
                // Sync container back to equipment array
                for (int i = 0; i < 6; i++) {
                    equipment.set(i, Objects.requireNonNull(this.getItem(i).copy()));
                }
                NeurocellMannequinBlockEntity.this.setChanged();
                NeurocellMannequinBlockEntity.this.onEquipmentChanged();
            }

            @Override
            public void setItem(int slot, @Nonnull ItemStack stack) {
                super.setItem(slot, stack);
                equipment.set(slot, Objects.requireNonNull(stack.copy()));
                NeurocellMannequinBlockEntity.this.setChanged();
                NeurocellMannequinBlockEntity.this.onEquipmentChanged();
            }

            @Override
            @Nonnull
            public ItemStack removeItem(int slot, int amount) {
                ItemStack result = Objects.requireNonNull(super.removeItem(slot, amount));
                equipment.set(slot, Objects.requireNonNull(this.getItem(slot).copy()));
                NeurocellMannequinBlockEntity.this.setChanged();
                NeurocellMannequinBlockEntity.this.onEquipmentChanged();
                return result;
            }

            @Override
            @Nonnull
            public ItemStack removeItemNoUpdate(int slot) {
                ItemStack result = Objects.requireNonNull(super.removeItemNoUpdate(slot));
                equipment.set(slot, Objects.requireNonNull(this.getItem(slot).copy()));
                return result;
            }
        };

        // Initialize with current equipment
        for (int i = 0; i < 6; i++) {
            container.setItem(i, Objects.requireNonNull(equipment.get(i).copy()));
        }

        return container;
    }
}
