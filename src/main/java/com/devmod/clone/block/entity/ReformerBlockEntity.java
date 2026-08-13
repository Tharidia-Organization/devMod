package com.devmod.clone.block.entity;

import java.util.Objects;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.DevMod;
import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.ReformerBlock;
import com.devmod.clone.data.BioscanData;
import com.devmod.clone.integration.CloneEntitySpawner;
import com.devmod.clone.util.NeurolinkConnector;

/**
 * Block entity for the reformer.
 * Spawns cloned entities from NEUROCELL data.
 */
public class ReformerBlockEntity extends BlockEntity {

    private static final int BASE_SPAWN_TIME = 100; // 5 seconds base
    private static final int SPAWN_TIME_PER_HEALTH = 6; // Additional ticks per HP
    private static final int SEARCH_COOLDOWN = 40; // 2 seconds between searches
    private static final int PARTICLE_INTERVAL = 5;
    private static final int SOUND_INTERVAL = 40;
    private static final float DEFAULT_MAX_HEALTH = 20.0f;
    private static final String TAG_PROGRESS = "SpawnProgress";
    private static final String TAG_MAX_PROGRESS = "MaxSpawnProgress";
    private static final String TAG_ENTITY_NAME = "EntityName";
    private static final String TAG_PENDING_DATA = "PendingData";

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
        var lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        tickSearchCooldown();

        if (pendingData != null) {
            processSpawning();
            return;
        }

        tryStartSpawning();
    }

    private void tickSearchCooldown() {
        if (searchCooldown > 0) {
            searchCooldown--;
        }
    }

    private void processSpawning() {
        spawnProgress++;

        var lvl = level;
        if (spawnProgress % PARTICLE_INTERVAL == 0 && lvl instanceof ServerLevel serverLevel) {
            spawnConstructionParticles(serverLevel);
        }

        if (spawnProgress % SOUND_INTERVAL == 0 && lvl != null) {
            lvl.playSound(null, Objects.requireNonNull(worldPosition),
                Objects.requireNonNull(SoundEvents.BEACON_AMBIENT), SoundSource.BLOCKS, 0.3f, 1.2f);
        }

        if (spawnProgress >= maxSpawnProgress) {
            completeSpawn();
        }
    }

    private void tryStartSpawning() {
        if (searchCooldown > 0) {
            return;
        }

        var lvl = level;
        if (lvl == null) {
            return;
        }

        searchCooldown = SEARCH_COOLDOWN;
        NeurocellAccess neurocell = NeurolinkConnector.findConnectedNeurocell(lvl, Objects.requireNonNull(worldPosition));
        if (neurocell != null) {
            startSpawning(neurocell);
        }
    }

    private void startSpawning(NeurocellAccess neurocell) {
        BioscanData data = neurocell.consumeData();
        if (data == null) {
            return;
        }

        pendingData = data;
        pendingEntityName = Objects.requireNonNull(data.entityName());

        maxSpawnProgress = calculateMaxSpawnProgress(data);
        spawnProgress = 0;
        updateActiveState(true);

        var lvl = level;
        if (lvl != null) {
            lvl.playSound(null, Objects.requireNonNull(worldPosition),
                Objects.requireNonNull(SoundEvents.BEACON_ACTIVATE), SoundSource.BLOCKS, 0.5f, 1.0f);
        }

        markDirtyAndSync();
    }

    private int calculateMaxSpawnProgress(BioscanData data) {
        if (data.getEntityType() != null) {
            return BASE_SPAWN_TIME + (int)(DEFAULT_MAX_HEALTH * SPAWN_TIME_PER_HEALTH);
        }
        return BASE_SPAWN_TIME * 2;
    }

    private void completeSpawn() {
        BioscanData data = pendingData;
        if (!(level instanceof ServerLevel serverLevel) || data == null) {
            resetSpawn();
            return;
        }

        BlockPos pos = Objects.requireNonNull(worldPosition);
        CloneEntitySpawner spawner = new CloneEntitySpawner(serverLevel);
        BlockPos spawnPos = Objects.requireNonNull(pos.above());
        if (spawner.spawnFromBioscan(spawnPos, data) != null) {
            // Spawn effects
            serverLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.EXPLOSION),
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                3, 0.3, 0.3, 0.3, 0
            );

            serverLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.END_ROD),
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                30, 0.5, 0.5, 0.5, 0.1
            );

            serverLevel.playSound(null, pos, Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP),
                SoundSource.BLOCKS, 0.8f, 1.2f);
        } else {
            // The bioscan was already consumed from the neurocell, so a failed spawn
            // loses it - at least make the failure audible instead of silent.
            DevMod.LOGGER.warn("[Clone] Reformer at {} failed to spawn clone of {}", pos, data.entityTypeId());

            serverLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.SMOKE),
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                20, 0.3, 0.3, 0.3, 0.01
            );

            serverLevel.playSound(null, pos, Objects.requireNonNull(SoundEvents.FIRE_EXTINGUISH),
                SoundSource.BLOCKS, 0.6f, 0.8f);
        }

        resetSpawn();
    }

    private void resetSpawn() {
        spawnProgress = 0;
        maxSpawnProgress = 0;
        pendingData = null;
        pendingEntityName = "";
        updateActiveState(false);
        markDirtyAndSync();
    }

    private void updateActiveState(boolean active) {
        var lvl = level;
        if (lvl != null && !lvl.isClientSide) {
            BlockState state = getBlockState();
            if (state.getValue(Objects.requireNonNull(ReformerBlock.ACTIVE)) != active) {
                lvl.setBlock(Objects.requireNonNull(worldPosition),
                    Objects.requireNonNull(state.setValue(Objects.requireNonNull(ReformerBlock.ACTIVE), active)), 2);
            }
        }
    }

    private void spawnConstructionParticles(ServerLevel serverLevel) {
        BlockPos pos = Objects.requireNonNull(worldPosition);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0 + (spawnProgress % 20) * 0.05;
        double z = pos.getZ() + 0.5;

        // Vertical construction effect
        for (int i = 0; i < 5; i++) {
            double angle = (spawnProgress * 0.15 + i * Math.PI * 2 / 5) % (Math.PI * 2);
            double radius = 0.4;
            serverLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.SOUL_FIRE_FLAME),
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
                Objects.requireNonNull(Component.translatable("message.devmod.reformer.spawning", pendingEntityName, percent)
                    .withStyle(ChatFormatting.YELLOW)),
                true
            );
        } else {
            var lvl = level;
            boolean connected = lvl != null && NeurolinkConnector.hasConnectedNeurocell(lvl, Objects.requireNonNull(worldPosition));
            if (connected) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("message.devmod.reformer.waiting")
                        .withStyle(ChatFormatting.GRAY)),
                    true
                );
            } else {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("message.devmod.reformer.not_connected")
                        .withStyle(ChatFormatting.RED)),
                    true
                );
            }
        }
    }

    private void syncToClient() {
        var lvl = level;
        if (lvl != null && !lvl.isClientSide) {
            BlockState currentState = Objects.requireNonNull(getBlockState());
            lvl.sendBlockUpdated(Objects.requireNonNull(worldPosition), currentState, currentState, 2);
        }
    }

    private void markDirtyAndSync() {
        setChanged();
        syncToClient();
    }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_PROGRESS, spawnProgress);
        tag.putInt(TAG_MAX_PROGRESS, maxSpawnProgress);
        tag.putString(TAG_ENTITY_NAME, Objects.requireNonNull(pendingEntityName));
        BioscanData data = pendingData;
        if (data != null) {
            tag.put(TAG_PENDING_DATA, Objects.requireNonNull(data.toTag()));
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        spawnProgress = tag.getInt(TAG_PROGRESS);
        maxSpawnProgress = tag.getInt(TAG_MAX_PROGRESS);
        pendingEntityName = Objects.requireNonNull(tag.getString(TAG_ENTITY_NAME));
        if (tag.contains(TAG_PENDING_DATA)) {
            pendingData = BioscanData.fromTag(Objects.requireNonNull(tag.getCompound(TAG_PENDING_DATA)));
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
        tag.putString(TAG_ENTITY_NAME, Objects.requireNonNull(pendingEntityName));
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
