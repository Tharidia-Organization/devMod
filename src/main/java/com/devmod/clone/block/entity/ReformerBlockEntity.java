package com.devmod.clone.block.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.ReformerBlock;
import com.devmod.clone.data.BioscanData;
import com.devmod.clone.util.NeurolinkConnector;

/**
 * Block entity for the reformer.
 * Spawns cloned entities from NEUROCELL data.
 */
public class ReformerBlockEntity extends BlockEntity {

    private static final int BASE_SPAWN_TIME = 100; // 5 seconds base
    private static final int SPAWN_TIME_PER_HEALTH = 6; // Additional ticks per HP
    private static final int SEARCH_COOLDOWN = 40; // 2 seconds between searches
    private static final String TAG_PROGRESS = "SpawnProgress";
    private static final String TAG_MAX_PROGRESS = "MaxSpawnProgress";
    private static final String TAG_ENTITY_NAME = "EntityName";

    private int spawnProgress = 0;
    private int maxSpawnProgress = 0;
    private String pendingEntityName = "";
    @Nullable
    private BioscanData pendingData = null;
    private int searchCooldown = 0;

    public ReformerBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.REFORMER.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        // Handle cooldowns
        if (searchCooldown > 0) {
            searchCooldown--;
        }

        // If we have pending data, continue spawning
        if (pendingData != null) {
            spawnProgress++;

            // Particles during spawning
            if (spawnProgress % 5 == 0 && level instanceof ServerLevel serverLevel) {
                spawnConstructionParticles(serverLevel);
            }

            // Sound at intervals
            if (spawnProgress % 40 == 0) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.2f);
            }

            // Complete spawn
            if (spawnProgress >= maxSpawnProgress) {
                completeSpawn();
            }
            return;
        }

        // Search for connected NEUROCELL with ready data
        if (searchCooldown <= 0) {
            searchCooldown = SEARCH_COOLDOWN;
            NeurocellBlockEntity neurocell = NeurolinkConnector.findConnectedNeurocell(level, worldPosition);
            if (neurocell != null) {
                startSpawning(neurocell);
            }
        }
    }

    private void startSpawning(NeurocellBlockEntity neurocell) {
        BioscanData data = neurocell.consumeData();
        if (data == null) {
            return;
        }

        pendingData = data;
        pendingEntityName = data.entityName();

        // Calculate spawn time based on entity type
        EntityType<?> entityType = data.getEntityType();
        if (entityType != null) {
            // Use default health for mob types
            float maxHealth = 20.0f; // Default
            maxSpawnProgress = BASE_SPAWN_TIME + (int)(maxHealth * SPAWN_TIME_PER_HEALTH);
        } else {
            maxSpawnProgress = BASE_SPAWN_TIME * 2;
        }

        spawnProgress = 0;
        updateActiveState(true);

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.0f);
        }

        setChanged();
        syncToClient();
    }

    private void completeSpawn() {
        if (level == null || !(level instanceof ServerLevel serverLevel) || pendingData == null) {
            resetSpawn();
            return;
        }

        // Spawn the entity
        EntityType<?> entityType = pendingData.getEntityType();
        if (entityType == null) {
            resetSpawn();
            return;
        }

        // Create entity
        Entity entity;
        if (pendingData.hasEntityNBT()) {
            entity = EntityType.loadEntityRecursive(pendingData.entityNBT(), serverLevel, e -> {
                e.setPos(
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5
                );
                return e;
            });
        } else {
            entity = entityType.create(serverLevel);
            if (entity != null) {
                entity.setPos(
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5
                );
            }
        }

        if (entity != null) {
            // Spawn effects
            serverLevel.sendParticles(
                ParticleTypes.EXPLOSION,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5,
                3, 0.3, 0.3, 0.3, 0
            );

            serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5,
                30, 0.5, 0.5, 0.5, 0.1
            );

            level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);

            // Add to world
            serverLevel.addFreshEntity(entity);
        }

        resetSpawn();
    }

    private void resetSpawn() {
        spawnProgress = 0;
        maxSpawnProgress = 0;
        pendingData = null;
        pendingEntityName = "";
        updateActiveState(false);
        setChanged();
        syncToClient();
    }

    private void updateActiveState(boolean active) {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.getValue(ReformerBlock.ACTIVE) != active) {
                level.setBlock(worldPosition, state.setValue(ReformerBlock.ACTIVE, active), 2);
            }
        }
    }

    private void spawnConstructionParticles(ServerLevel serverLevel) {
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.0 + (spawnProgress % 20) * 0.05;
        double z = worldPosition.getZ() + 0.5;

        // Vertical construction effect
        for (int i = 0; i < 5; i++) {
            double angle = (spawnProgress * 0.15 + i * Math.PI * 2 / 5) % (Math.PI * 2);
            double radius = 0.4;
            serverLevel.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                x + Math.cos(angle) * radius,
                y,
                z + Math.sin(angle) * radius,
                1, 0, 0, 0, 0
            );
        }
    }

    /**
     * Get progress percentage (0-100).
     */
    public int getProgressPercent() {
        if (maxSpawnProgress <= 0) return 0;
        return (spawnProgress * 100) / maxSpawnProgress;
    }

    /**
     * Check if currently spawning.
     */
    public boolean isSpawning() {
        return pendingData != null;
    }

    /**
     * Show status to player.
     */
    public void showStatus(@Nonnull Player player) {
        if (pendingData != null) {
            int percent = getProgressPercent();
            player.displayClientMessage(
                Component.translatable("message.devmod.reformer.spawning", pendingEntityName, percent)
                    .withStyle(ChatFormatting.YELLOW),
                true
            );
        } else {
            boolean connected = level != null && NeurolinkConnector.hasConnectedNeurocell(level, worldPosition);
            if (connected) {
                player.displayClientMessage(
                    Component.translatable("message.devmod.reformer.waiting")
                        .withStyle(ChatFormatting.GRAY),
                    true
                );
            } else {
                player.displayClientMessage(
                    Component.translatable("message.devmod.reformer.not_connected")
                        .withStyle(ChatFormatting.RED),
                    true
                );
            }
        }
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_PROGRESS, spawnProgress);
        tag.putInt(TAG_MAX_PROGRESS, maxSpawnProgress);
        tag.putString(TAG_ENTITY_NAME, pendingEntityName);
        if (pendingData != null) {
            tag.put("PendingData", pendingData.toTag());
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        spawnProgress = tag.getInt(TAG_PROGRESS);
        maxSpawnProgress = tag.getInt(TAG_MAX_PROGRESS);
        pendingEntityName = tag.getString(TAG_ENTITY_NAME);
        if (tag.contains("PendingData")) {
            pendingData = BioscanData.fromTag(tag.getCompound("PendingData"));
        } else {
            pendingData = null;
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(TAG_PROGRESS, spawnProgress);
        tag.putInt(TAG_MAX_PROGRESS, maxSpawnProgress);
        tag.putString(TAG_ENTITY_NAME, pendingEntityName);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
