package com.devmod.clone.block.entity;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import com.devmod.clone.CloneItems;
import com.devmod.clone.block.NeurocellBlock;
import com.devmod.clone.data.BioscanData;
import com.devmod.clone.item.BioscannerItem;
import com.devmod.clone.menu.NeurocellMenu;

/**
 * Block entity for the neurocell cloning chamber.
 * Processes bioscan data and prepares it for the reformer.
 * Uses CUSTOM_DATA component like Hologenica for compatibility.
 */
public class NeurocellBlockEntity extends BlockEntity implements MenuProvider, NeurocellAccess {

    private static final int PROCESS_TIME = 300; // 15 seconds
    /**
     * Maximum entity dimension (width or height) allowed in the standard 1x2 Neurocell.
     * Entities larger than this require the NeurocellL (2x2x2) chamber.
     */
    public static final float MAX_ENTITY_SIZE = 1.5f;
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_ENTITY_NAME = "EntityName";
    private static final String TAG_PLAYER_UUID = "PlayerUUID";
    private static final String TAG_CLONING_TIME = "CloningTime";
    private static final String TAG_HAS_RAGDOLL = "HasRagdoll";
    private static final String TAG_BIOSCANNER = "Bioscanner";
    private static final String TAG_ROTATION_ANGLE = "RotationAngle";
    private static final String TAG_ROTATION_MODE = "RotationMode";

    /**
     * Rotation mode for the entity display.
     */
    public enum RotationMode {
        /** Automatic slow rotation (default) */
        AUTO,
        /** Fixed angle, no rotation */
        FIXED,
        /** Manual rotation controlled by player */
        MANUAL
    }

    // Stored state - mirrors Hologenica's approach
    @Nonnull
    private ItemStack storedBioscanner = Objects.requireNonNull(ItemStack.EMPTY);
    @Nonnull
    private String entityType = "";
    @Nonnull
    private String entityName = "";
    @Nullable
    private UUID playerUUID = null;
    private int cloningTime = 0;
    private boolean hasRagdoll = false;

    /** Manual rotation angle (degrees) */
    private float rotationAngle = 0f;

    /** Current rotation mode */
    @Nonnull
    private RotationMode rotationMode = RotationMode.AUTO;

    public NeurocellBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.NEUROCELL.get(), pos, state);
    }

    /**
     * Called when inventory changes - reads bioscanner data.
     */
    public void onInventoryChanged() {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        com.devmod.DevMod.LOGGER.info("[Neurocell] onInventoryChanged: storedBioscanner={}, hasData={}",
            storedBioscanner, BioscannerItem.hasData(storedBioscanner));

        if (storedBioscanner.isEmpty() || !BioscannerItem.hasData(storedBioscanner)) {
            clearRagdollState();
            return;
        }

        CompoundTag tag = BioscannerItem.getDataTag(storedBioscanner);
        com.devmod.DevMod.LOGGER.info("[Neurocell] tag={}", tag);
        if (tag == null || !tag.contains(TAG_ENTITY_TYPE)) {
            clearRagdollState();
            return;
        }

        String newEntityType = tag.getString(TAG_ENTITY_TYPE);
        com.devmod.DevMod.LOGGER.info("[Neurocell] newEntityType={}, currentEntityType={}", newEntityType, this.entityType);
        if (!newEntityType.equals(this.entityType)) {
            this.entityType = Objects.requireNonNull(newEntityType);
            this.entityName = Objects.requireNonNull(tag.contains(TAG_ENTITY_NAME) ? tag.getString(TAG_ENTITY_NAME) : "");
            this.playerUUID = tag.hasUUID(TAG_PLAYER_UUID) ? tag.getUUID(TAG_PLAYER_UUID) : null;
            this.cloningTime = 0;
            this.hasRagdoll = true;
            com.devmod.DevMod.LOGGER.info("[Neurocell] Updated: entityType={}, entityName={}, hasRagdoll={}",
                this.entityType, this.entityName, this.hasRagdoll);
            setChanged();
            syncToClient();
            updateActiveState(true);
        }
    }

    private void clearRagdollState() {
        if (entityType.isEmpty() && !hasRagdoll) {
            return;
        }
        this.entityType = "";
        this.entityName = "";
        this.playerUUID = null;
        this.cloningTime = 0;
        this.hasRagdoll = false;
        setChanged();
        syncToClient();
        updateActiveState(false);
    }

    /**
     * Update the block's ACTIVE state and sync to both halves.
     */
    private void updateActiveState(boolean active) {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        BlockPos pos = Objects.requireNonNull(worldPosition);
        BooleanProperty activeProp = Objects.requireNonNull(NeurocellBlock.ACTIVE);
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

    /**
     * Insert a bioscanner into the neurocell.
     * Rejects entities that are too large for the standard chamber.
     */
    public boolean insertBioscanner(@Nonnull ItemStack bioscanner) {
        if (!storedBioscanner.isEmpty()) {
            return false;
        }
        if (!bioscanner.is(Objects.requireNonNull(CloneItems.BIOSCANNER.get()))) {
            return false;
        }
        if (!BioscannerItem.hasData(bioscanner)) {
            return false;
        }

        // Check entity size - giant entities require NeurocellL (2x2x2)
        float maxDim = BioscannerItem.getEntityMaxDimension(bioscanner);
        if (maxDim > MAX_ENTITY_SIZE) {
            return false;
        }

        storedBioscanner = Objects.requireNonNull(bioscanner.copyWithCount(1));
        onInventoryChanged();

        Level lvl = level;
        if (lvl != null) {
            lvl.playSound(null, Objects.requireNonNull(worldPosition), Objects.requireNonNull(SoundEvents.ITEM_FRAME_ADD_ITEM), SoundSource.BLOCKS, 0.5f, 1.2f);
        }

        return true;
    }

    /**
     * Extract the bioscanner from the neurocell.
     */
    @Nonnull
    public ItemStack extractBioscanner() {
        if (storedBioscanner.isEmpty()) {
            return Objects.requireNonNull(ItemStack.EMPTY);
        }

        ItemStack extracted = Objects.requireNonNull(storedBioscanner.copy());
        storedBioscanner = Objects.requireNonNull(ItemStack.EMPTY);
        clearRagdollState();

        Level lvl = level;
        if (lvl != null) {
            lvl.playSound(null, Objects.requireNonNull(worldPosition), Objects.requireNonNull(SoundEvents.ITEM_FRAME_REMOVE_ITEM), SoundSource.BLOCKS, 0.5f, 1.0f);
        }

        return extracted;
    }

    // === Getters for renderer ===

    public String getEntityType() {
        return entityType;
    }

    public String getEntityName() {
        return entityName;
    }

    @Nullable
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public int getCloningTime() {
        return cloningTime;
    }

    public boolean hasRagdoll() {
        return hasRagdoll;
    }

    // === Rotation Mode Support ===

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
     * Check if actively cloning (not used in Neurocell, but needed for renderer compatibility).
     * In Hologenica, actual cloning happens in Reformer. Neurocell just holds/displays.
     */
    public boolean isCloning() {
        return !entityType.isEmpty() && cloningTime < PROCESS_TIME && !hasRagdoll;
    }

    public float getCloningProgress() {
        if (hasRagdoll || entityType.isEmpty()) {
            return 1.0f;
        }
        return (float) cloningTime / PROCESS_TIME;
    }

    @Override
    public boolean isDataReady() {
        return hasRagdoll && !entityType.isEmpty();
    }

    public int getProgressPercent() {
        return (int) (getCloningProgress() * 100);
    }

    /**
     * Consume the data (called by REFORMER when starting clone).
     * Creates BioscanData from stored bioscanner and clears the neurocell.
     */
    @Override
    @Nullable
    public com.devmod.clone.data.BioscanData consumeData() {
        if (!hasRagdoll || entityType.isEmpty() || storedBioscanner.isEmpty()) {
            return null;
        }

        CompoundTag tag = BioscannerItem.getDataTag(storedBioscanner);
        if (tag == null) {
            return null;
        }

        // Create BioscanData from the stored data
        net.minecraft.resources.ResourceLocation entityTypeId = net.minecraft.resources.ResourceLocation.tryParse(entityType);
        if (entityTypeId == null) {
            return null;
        }

        CompoundTag entityNbt = tag.contains("EntityNBT") ? tag.getCompound("EntityNBT") : null;
        java.util.UUID pUuid = tag.hasUUID("PlayerUUID") ? tag.getUUID("PlayerUUID") : null;

        com.devmod.clone.data.BioscanData data = new com.devmod.clone.data.BioscanData(
            entityTypeId,
            entityName,
            pUuid,
            entityNbt,
            System.currentTimeMillis()
        );

        // Clear the neurocell
        storedBioscanner = Objects.requireNonNull(ItemStack.EMPTY);
        clearRagdollState();

        return data;
    }

    /**
     * Show status to player.
     */
    public void showStatus(@Nonnull Player player) {
        if (storedBioscanner.isEmpty() || entityType.isEmpty()) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.neurocell.empty")
                    .withStyle(ChatFormatting.GRAY)),
                true
            );
        } else if (hasRagdoll) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.neurocell.ready", entityName)
                    .withStyle(ChatFormatting.GREEN)),
                true
            );
        } else {
            int percent = getProgressPercent();
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.neurocell.processing", percent)
                    .withStyle(ChatFormatting.YELLOW)),
                true
            );
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

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_ENTITY_TYPE, entityType);
        tag.putString(TAG_ENTITY_NAME, entityName);
        if (playerUUID != null) {
            tag.putUUID(TAG_PLAYER_UUID, playerUUID);
        }
        tag.putInt(TAG_CLONING_TIME, cloningTime);
        tag.putBoolean(TAG_HAS_RAGDOLL, hasRagdoll);
        if (!storedBioscanner.isEmpty()) {
            tag.put(TAG_BIOSCANNER, Objects.requireNonNull(storedBioscanner.save(registries)));
        }
        tag.putFloat(TAG_ROTATION_ANGLE, rotationAngle);
        tag.putString(TAG_ROTATION_MODE, Objects.requireNonNull(rotationMode.name()));
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        entityType = Objects.requireNonNull(tag.getString(TAG_ENTITY_TYPE));
        entityName = Objects.requireNonNull(tag.getString(TAG_ENTITY_NAME));
        playerUUID = tag.hasUUID(TAG_PLAYER_UUID) ? tag.getUUID(TAG_PLAYER_UUID) : null;
        cloningTime = tag.getInt(TAG_CLONING_TIME);
        hasRagdoll = tag.getBoolean(TAG_HAS_RAGDOLL);
        if (tag.contains(TAG_BIOSCANNER)) {
            storedBioscanner = Objects.requireNonNull(ItemStack.parse(registries, Objects.requireNonNull(tag.getCompound(TAG_BIOSCANNER))).orElse(ItemStack.EMPTY));
        } else {
            storedBioscanner = Objects.requireNonNull(ItemStack.EMPTY);
        }

        // Load rotation data
        rotationAngle = tag.getFloat(TAG_ROTATION_ANGLE);
        if (tag.contains(TAG_ROTATION_MODE)) {
            try {
                rotationMode = RotationMode.valueOf(tag.getString(TAG_ROTATION_MODE));
            } catch (IllegalArgumentException e) {
                rotationMode = RotationMode.AUTO;
            }
        } else {
            rotationMode = RotationMode.AUTO;
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString(TAG_ENTITY_TYPE, entityType);
        tag.putString(TAG_ENTITY_NAME, entityName);
        if (playerUUID != null) {
            tag.putUUID(TAG_PLAYER_UUID, playerUUID);
        }
        tag.putInt(TAG_CLONING_TIME, cloningTime);
        tag.putBoolean(TAG_HAS_RAGDOLL, hasRagdoll);
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
        return Objects.requireNonNull(Component.translatable("block.devmod.neurocell"));
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new NeurocellMenu(containerId, playerInv, this);
    }

    // === Inventory access for menu ===

    /**
     * Get the inventory container for menu access.
     */
    @Nonnull
    public Container getInventory() {
        // Create a container view that syncs bidirectionally with storedBioscanner
        SimpleContainer container = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                // Sync container back to storedBioscanner
                storedBioscanner = Objects.requireNonNull(this.getItem(0).copy());
                NeurocellBlockEntity.this.setChanged();
                NeurocellBlockEntity.this.onInventoryChanged();
            }

            @Override
            public void setItem(int slot, @Nonnull ItemStack stack) {
                super.setItem(slot, stack);
                // Also sync immediately when item is set
                storedBioscanner = Objects.requireNonNull(stack.copy());
                NeurocellBlockEntity.this.setChanged();
                NeurocellBlockEntity.this.onInventoryChanged();
            }

            @Override
            @Nonnull
            public ItemStack removeItem(int slot, int amount) {
                ItemStack result = Objects.requireNonNull(super.removeItem(slot, amount));
                // Sync after removal
                storedBioscanner = Objects.requireNonNull(this.getItem(0).copy());
                NeurocellBlockEntity.this.setChanged();
                NeurocellBlockEntity.this.onInventoryChanged();
                return result;
            }

            @Override
            @Nonnull
            public ItemStack removeItemNoUpdate(int slot) {
                ItemStack result = Objects.requireNonNull(super.removeItemNoUpdate(slot));
                storedBioscanner = Objects.requireNonNull(this.getItem(0).copy());
                return result;
            }
        };
        container.setItem(0, Objects.requireNonNull(storedBioscanner.copy()));
        return container;
    }

    /**
     * Check if the neurocell has a physical empty bioscanner inserted.
     */
    @Override
    public boolean hasEmptyBioscanner() {
        return !storedBioscanner.isEmpty()
            && storedBioscanner.is(Objects.requireNonNull(CloneItems.BIOSCANNER.get()))
            && !BioscannerItem.hasData(storedBioscanner);
    }

    /**
     * Fill the bioscanner slot from imprinter data.
     */
    @Override
    public void fillBioscannerFromImprinter(@Nonnull BioscanData data) {
        if (storedBioscanner.isEmpty()
            || !storedBioscanner.is(Objects.requireNonNull(CloneItems.BIOSCANNER.get()))
            || BioscannerItem.hasData(storedBioscanner)) {
            return;
        }

        // Set data using CUSTOM_DATA component like BioscannerItem does
        storedBioscanner.update(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
            Objects.requireNonNull(net.minecraft.world.item.component.CustomData.EMPTY), customData -> {
                CompoundTag tag = customData.copyTag();
                tag.putString("EntityType", Objects.requireNonNull(data.entityTypeId().toString()));
                tag.putString("EntityName", data.entityName());
                UUID pUuid = data.playerUUID();
                if (pUuid != null) {
                    tag.putUUID("PlayerUUID", pUuid);
                }
                CompoundTag nbt = data.entityNBT();
                if (nbt != null) {
                    tag.put("EntityNBT", nbt);
                }
                return net.minecraft.world.item.component.CustomData.of(tag);
            });
        onInventoryChanged();
    }
}
