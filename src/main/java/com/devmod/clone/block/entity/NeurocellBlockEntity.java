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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.CloneItems;
import com.devmod.clone.item.BioscannerItem;

/**
 * Block entity for the neurocell cloning chamber.
 * Processes bioscan data and prepares it for the reformer.
 * Uses CUSTOM_DATA component like Hologenica for compatibility.
 */
public class NeurocellBlockEntity extends BlockEntity {

    private static final int PROCESS_TIME = 300; // 15 seconds
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_ENTITY_NAME = "EntityName";
    private static final String TAG_PLAYER_UUID = "PlayerUUID";
    private static final String TAG_CLONING_TIME = "CloningTime";
    private static final String TAG_HAS_RAGDOLL = "HasRagdoll";
    private static final String TAG_BIOSCANNER = "Bioscanner";

    // Stored state - mirrors Hologenica's approach
    private ItemStack storedBioscanner = ItemStack.EMPTY;
    private String entityType = "";
    private String entityName = "";
    private UUID playerUUID = null;
    private int cloningTime = 0;
    private boolean hasRagdoll = false;

    public NeurocellBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.NEUROCELL.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        // No ticker needed - Hologenica doesn't have a ticker for Neurocell
        // The entity just displays what's in the bioscanner
    }

    /**
     * Called when inventory changes - reads bioscanner data.
     */
    public void onInventoryChanged() {
        if (level == null || level.isClientSide) {
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
            this.entityType = newEntityType;
            this.entityName = tag.contains(TAG_ENTITY_NAME) ? tag.getString(TAG_ENTITY_NAME) : "";
            this.playerUUID = tag.hasUUID(TAG_PLAYER_UUID) ? tag.getUUID(TAG_PLAYER_UUID) : null;
            this.cloningTime = 0;
            this.hasRagdoll = true;
            com.devmod.DevMod.LOGGER.info("[Neurocell] Updated: entityType={}, entityName={}, hasRagdoll={}",
                this.entityType, this.entityName, this.hasRagdoll);
            setChanged();
            syncToClient();
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
    }

    /**
     * Insert a bioscanner into the neurocell.
     */
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

    /**
     * Extract the bioscanner from the neurocell.
     */
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
        storedBioscanner = ItemStack.EMPTY;
        clearRagdollState();

        return data;
    }

    /**
     * Show status to player.
     */
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
}
