package com.devmod.clone.block.entity;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.CloneItems;
import com.devmod.clone.block.NeurocellLBlock;
import com.devmod.clone.block.NeurocellLBlock.MultiBlockPart;
import com.devmod.clone.item.BioscannerItem;
import com.devmod.clone.menu.NeurocellLMenu;

/**
 * Block entity for the large neurocell cloning chamber (2x2x2).
 * Can render larger entities than the standard Neurocell.
 * Features dual texture states (active/inactive) like the standard Neurocell.
 */
public class NeurocellLBlockEntity extends BlockEntity implements MenuProvider {

    private static final int PROCESS_TIME = 300;
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_ENTITY_NAME = "EntityName";
    private static final String TAG_PLAYER_UUID = "PlayerUUID";
    private static final String TAG_CLONING_TIME = "CloningTime";
    private static final String TAG_HAS_RAGDOLL = "HasRagdoll";
    private static final String TAG_BIOSCANNER = "Bioscanner";

    private ItemStack storedBioscanner = ItemStack.EMPTY;
    private String entityType = "";
    private String entityName = "";
    @Nullable
    private UUID playerUUID = null;
    private int cloningTime = 0;
    private boolean hasRagdoll = false;

    public NeurocellLBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.NEUROCELL_L.get(), pos, state);
    }

    public void onInventoryChanged() {
        if (level == null || level.isClientSide) {
            return;
        }

        if (storedBioscanner.isEmpty() || !BioscannerItem.hasData(storedBioscanner)) {
            clearRagdollState();
            return;
        }

        CompoundTag tag = BioscannerItem.getDataTag(storedBioscanner);
        if (tag == null || !tag.contains(TAG_ENTITY_TYPE)) {
            clearRagdollState();
            return;
        }

        String newEntityType = tag.getString(TAG_ENTITY_TYPE);
        if (!newEntityType.equals(this.entityType)) {
            this.entityType = newEntityType;
            this.entityName = tag.contains(TAG_ENTITY_NAME) ? tag.getString(TAG_ENTITY_NAME) : "";
            this.playerUUID = tag.hasUUID(TAG_PLAYER_UUID) ? tag.getUUID(TAG_PLAYER_UUID) : null;
            this.cloningTime = 0;
            this.hasRagdoll = true;
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
     * Update the block's ACTIVE state and sync to all 8 parts of the structure.
     */
    private void updateActiveState(boolean active) {
        if (level == null || level.isClientSide) {
            return;
        }

        // Get facing from the center block state
        BlockState centerState = level.getBlockState(worldPosition);
        if (!centerState.hasProperty(NeurocellLBlock.FACING)) {
            return;
        }
        net.minecraft.core.Direction facing = centerState.getValue(NeurocellLBlock.FACING);

        // Update all 8 blocks of the 2x2x2 structure
        for (MultiBlockPart part : MultiBlockPart.values()) {
            BlockPos partPos = part.getOffsetFromCenter(worldPosition, facing);
            BlockState partState = level.getBlockState(partPos);
            if (partState.hasProperty(NeurocellLBlock.ACTIVE) &&
                partState.getValue(NeurocellLBlock.ACTIVE) != active) {
                level.setBlock(partPos, partState.setValue(NeurocellLBlock.ACTIVE, active), 3);
            }
        }
    }

    public boolean insertBioscanner(@Nonnull ItemStack bioscanner) {
        if (!storedBioscanner.isEmpty()) {
            return false;
        }
        if (!bioscanner.is(CloneItems.BIOSCANNER.get())) {
            return false;
        }
        if (!BioscannerItem.hasData(bioscanner)) {
            return false;
        }

        storedBioscanner = bioscanner.copyWithCount(1);
        onInventoryChanged();

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.5f, 1.2f);
        }

        return true;
    }

    @Nonnull
    public ItemStack extractBioscanner() {
        if (storedBioscanner.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = storedBioscanner.copy();
        storedBioscanner = ItemStack.EMPTY;
        clearRagdollState();

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.5f, 1.0f);
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

    public boolean isCloning() {
        return !entityType.isEmpty() && cloningTime < PROCESS_TIME && !hasRagdoll;
    }

    public float getCloningProgress() {
        if (hasRagdoll || entityType.isEmpty()) {
            return 1.0f;
        }
        return (float) cloningTime / PROCESS_TIME;
    }

    public boolean isDataReady() {
        return hasRagdoll && !entityType.isEmpty();
    }

    public int getProgressPercent() {
        return (int) (getCloningProgress() * 100);
    }

    @Nullable
    public com.devmod.clone.data.BioscanData consumeData() {
        if (!hasRagdoll || entityType.isEmpty() || storedBioscanner.isEmpty()) {
            return null;
        }

        CompoundTag tag = BioscannerItem.getDataTag(storedBioscanner);
        if (tag == null) {
            return null;
        }

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

        storedBioscanner = ItemStack.EMPTY;
        clearRagdollState();

        return data;
    }

    public void showStatus(@Nonnull Player player) {
        if (storedBioscanner.isEmpty() || entityType.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.devmod.neurocell.empty")
                    .withStyle(ChatFormatting.GRAY),
                true
            );
        } else if (hasRagdoll) {
            player.displayClientMessage(
                Component.translatable("message.devmod.neurocell.ready", entityName)
                    .withStyle(ChatFormatting.GREEN),
                true
            );
        } else {
            int percent = getProgressPercent();
            player.displayClientMessage(
                Component.translatable("message.devmod.neurocell.processing", percent)
                    .withStyle(ChatFormatting.YELLOW),
                true
            );
        }
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
            tag.put(TAG_BIOSCANNER, storedBioscanner.save(registries));
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        entityType = tag.getString(TAG_ENTITY_TYPE);
        entityName = tag.getString(TAG_ENTITY_NAME);
        playerUUID = tag.hasUUID(TAG_PLAYER_UUID) ? tag.getUUID(TAG_PLAYER_UUID) : null;
        cloningTime = tag.getInt(TAG_CLONING_TIME);
        hasRagdoll = tag.getBoolean(TAG_HAS_RAGDOLL);
        if (tag.contains(TAG_BIOSCANNER)) {
            storedBioscanner = ItemStack.parse(registries, tag.getCompound(TAG_BIOSCANNER)).orElse(ItemStack.EMPTY);
        } else {
            storedBioscanner = ItemStack.EMPTY;
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
        return Component.translatable("block.devmod.neurocell_l");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new NeurocellLMenu(containerId, playerInv, this);
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
                storedBioscanner = this.getItem(0).copy();
                NeurocellLBlockEntity.this.setChanged();
                NeurocellLBlockEntity.this.onInventoryChanged();
            }

            @Override
            public void setItem(int slot, @Nonnull ItemStack stack) {
                super.setItem(slot, stack);
                // Also sync immediately when item is set
                storedBioscanner = stack.copy();
                NeurocellLBlockEntity.this.setChanged();
                NeurocellLBlockEntity.this.onInventoryChanged();
            }

            @Override
            @Nonnull
            public ItemStack removeItem(int slot, int amount) {
                ItemStack result = super.removeItem(slot, amount);
                // Sync after removal
                storedBioscanner = this.getItem(0).copy();
                NeurocellLBlockEntity.this.setChanged();
                NeurocellLBlockEntity.this.onInventoryChanged();
                return result;
            }

            @Override
            @Nonnull
            public ItemStack removeItemNoUpdate(int slot) {
                ItemStack result = super.removeItemNoUpdate(slot);
                storedBioscanner = this.getItem(0).copy();
                return result;
            }
        };
        container.setItem(0, storedBioscanner.copy());
        return container;
    }
}
