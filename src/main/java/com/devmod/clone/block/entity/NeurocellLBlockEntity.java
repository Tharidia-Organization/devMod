package com.devmod.clone.block.entity;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.CloneItems;
import com.devmod.clone.block.NeurocellLBlock;
import com.devmod.clone.block.NeurocellLBlock.MultiBlockPart;
import com.devmod.clone.data.BioscanData;
import com.devmod.clone.item.BioscannerItem;
import com.devmod.clone.menu.NeurocellLMenu;

/**
 * Block entity for the large neurocell cloning chamber (2x2x2).
 * Can render larger entities than the standard Neurocell.
 * Features dual texture states (active/inactive) like the standard Neurocell.
 */
public class NeurocellLBlockEntity extends BlockEntity implements MenuProvider, NeurocellAccess {

    private static final int PROCESS_TIME = 300;
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_ENTITY_NAME = "EntityName";
    private static final String TAG_PLAYER_UUID = "PlayerUUID";
    private static final String TAG_CLONING_TIME = "CloningTime";
    private static final String TAG_HAS_RAGDOLL = "HasRagdoll";
    private static final String TAG_BIOSCANNER = "Bioscanner";

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

    public NeurocellLBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.NEUROCELL_L.get(), pos, state);
    }

    public void onInventoryChanged() {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
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

        String newEntityType = Objects.requireNonNull(tag.getString(TAG_ENTITY_TYPE));
        if (!newEntityType.equals(this.entityType)) {
            this.entityType = newEntityType;
            this.entityName = tag.contains(TAG_ENTITY_NAME) ? Objects.requireNonNull(tag.getString(TAG_ENTITY_NAME)) : "";
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
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        BlockPos pos = Objects.requireNonNull(worldPosition);

        // Get facing from the center block state
        BlockState centerState = lvl.getBlockState(pos);
        net.minecraft.world.level.block.state.properties.DirectionProperty facingProp = Objects.requireNonNull(NeurocellLBlock.FACING);
        if (!centerState.hasProperty(facingProp)) {
            return;
        }
        net.minecraft.core.Direction facing = centerState.getValue(facingProp);

        // Update all 8 blocks of the 2x2x2 structure
        net.minecraft.world.level.block.state.properties.BooleanProperty activeProp = Objects.requireNonNull(NeurocellLBlock.ACTIVE);
        for (MultiBlockPart part : MultiBlockPart.values()) {
            BlockPos partPos = Objects.requireNonNull(part.getOffsetFromCenter(pos, facing));
            BlockState partState = lvl.getBlockState(partPos);
            if (partState.hasProperty(activeProp) &&
                partState.getValue(activeProp) != active) {
                lvl.setBlock(partPos, Objects.requireNonNull(partState.setValue(activeProp, active)), 3);
            }
        }
    }

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

        storedBioscanner = Objects.requireNonNull(bioscanner.copyWithCount(1));
        onInventoryChanged();

        Level lvl = level;
        if (lvl != null) {
            lvl.playSound(null, Objects.requireNonNull(worldPosition), Objects.requireNonNull(SoundEvents.ITEM_FRAME_ADD_ITEM), SoundSource.BLOCKS, 0.5f, 1.2f);
        }

        return true;
    }

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

        storedBioscanner = Objects.requireNonNull(ItemStack.EMPTY);
        clearRagdollState();

        return data;
    }

    public void showStatus(@Nonnull Player player) {
        if (storedBioscanner.isEmpty() || entityType.isEmpty()) {
            player.displayClientMessage(
                Objects.requireNonNull(Objects.requireNonNull(Component.translatable("message.devmod.neurocell.empty"))
                    .withStyle(ChatFormatting.GRAY)),
                true
            );
        } else if (hasRagdoll) {
            player.displayClientMessage(
                Objects.requireNonNull(Objects.requireNonNull(Component.translatable("message.devmod.neurocell.ready", entityName))
                    .withStyle(ChatFormatting.GREEN)),
                true
            );
        } else {
            int percent = getProgressPercent();
            player.displayClientMessage(
                Objects.requireNonNull(Objects.requireNonNull(Component.translatable("message.devmod.neurocell.processing", percent))
                    .withStyle(ChatFormatting.YELLOW)),
                true
            );
        }
    }

    private void syncToClient() {
        Level lvl = level;
        if (lvl != null && !lvl.isClientSide) {
            BlockState state = Objects.requireNonNull(getBlockState());
            lvl.sendBlockUpdated(Objects.requireNonNull(worldPosition), state, state, 3);
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
        return Objects.requireNonNull(Component.translatable("block.devmod.neurocell_l"));
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
                storedBioscanner = Objects.requireNonNull(this.getItem(0).copy());
                NeurocellLBlockEntity.this.setChanged();
                NeurocellLBlockEntity.this.onInventoryChanged();
            }

            @Override
            public void setItem(int slot, @Nonnull ItemStack stack) {
                super.setItem(slot, stack);
                // Also sync immediately when item is set
                storedBioscanner = Objects.requireNonNull(stack.copy());
                NeurocellLBlockEntity.this.setChanged();
                NeurocellLBlockEntity.this.onInventoryChanged();
            }

            @Override
            @Nonnull
            public ItemStack removeItem(int slot, int amount) {
                ItemStack result = Objects.requireNonNull(super.removeItem(slot, amount));
                // Sync after removal
                storedBioscanner = Objects.requireNonNull(this.getItem(0).copy());
                NeurocellLBlockEntity.this.setChanged();
                NeurocellLBlockEntity.this.onInventoryChanged();
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
    public boolean hasEmptyBioscanner() {
        return !storedBioscanner.isEmpty()
            && storedBioscanner.is(Objects.requireNonNull(CloneItems.BIOSCANNER.get()))
            && !BioscannerItem.hasData(storedBioscanner);
    }

    /**
     * Fill the bioscanner slot from imprinter data.
     */
    public void fillBioscannerFromImprinter(@Nonnull BioscanData data) {
        if (storedBioscanner.isEmpty()
            || !storedBioscanner.is(Objects.requireNonNull(CloneItems.BIOSCANNER.get()))
            || BioscannerItem.hasData(storedBioscanner)) {
            return;
        }

        storedBioscanner.update(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA),
            Objects.requireNonNull(CustomData.EMPTY),
            customData -> {
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
                return CustomData.of(tag);
            });
        onInventoryChanged();
    }
}
