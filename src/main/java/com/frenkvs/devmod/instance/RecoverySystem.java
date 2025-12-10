package com.frenkvs.devmod.instance;

import com.frenkvs.devmod.util.ConfigPaths;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Recovery system for players who disconnected during instance operations.
 *
 * Handles:
 * - Player login recovery (restore from snapshot if needed)
 * - Server startup cleanup (orphaned instances, pending snapshots)
 * - Snapshot persistence
 */
public class RecoverySystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoverySystem.class);
    public static final RecoverySystem INSTANCE = new RecoverySystem();

    private Path snapshotsDir;
    private boolean initialized = false;

    private RecoverySystem() {}

    /**
     * Initialize the recovery system.
     * Called during server startup.
     */
    public void initialize() {
        this.snapshotsDir = ConfigPaths.getConfigDir().resolve("snapshots");
        try {
            Files.createDirectories(snapshotsDir);
            initialized = true;
            LOGGER.info("[Recovery] Initialized, snapshots dir: {}", snapshotsDir);
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to create snapshots directory", e);
        }
    }

    // === Snapshot Management ===

    /**
     * Save a player snapshot to disk.
     * This should be called BEFORE any risky operation.
     */
    public void saveSnapshot(PlayerInstanceSnapshot snapshot) {
        if (!initialized) {
            LOGGER.error("[Recovery] Not initialized, cannot save snapshot");
            return;
        }

        try {
            Path snapshotFile = getSnapshotFile(snapshot.getPlayerId());
            snapshot.saveToFile(snapshotFile);
            LOGGER.info("[Recovery] Saved snapshot for player {} (state: {})",
                snapshot.getPlayerId(), snapshot.getState());
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to save snapshot for player {}", snapshot.getPlayerId(), e);
        }
    }

    /**
     * Update the state of an existing snapshot.
     * Thread-safe: Uses file locking to prevent concurrent read-modify-write races.
     */
    public void updateSnapshotState(UUID playerId, PlayerInstanceState newState) {
        Path snapshotFile = getSnapshotFile(playerId);

        // Synchronize on the player ID to prevent concurrent updates for the same player
        // This prevents the read-modify-write race condition
        synchronized (playerId.toString().intern()) {
            try {
                if (!Files.exists(snapshotFile)) {
                    LOGGER.warn("[Recovery] No snapshot found for player {} to update", playerId);
                    return;
                }

                PlayerInstanceSnapshot snapshot = PlayerInstanceSnapshot.loadFromFile(snapshotFile);
                snapshot.setState(newState);
                snapshot.saveToFile(snapshotFile);

                LOGGER.debug("[Recovery] Updated snapshot state for {} to {}", playerId, newState);
            } catch (IOException e) {
                LOGGER.error("[Recovery] Failed to update snapshot state for {}", playerId, e);
            }
        }
    }

    /**
     * Load a player's snapshot if it exists.
     */
    public Optional<PlayerInstanceSnapshot> loadSnapshot(UUID playerId) {
        if (!initialized) return Optional.empty();

        try {
            Path snapshotFile = getSnapshotFile(playerId);
            if (Files.exists(snapshotFile)) {
                return Optional.of(PlayerInstanceSnapshot.loadFromFile(snapshotFile));
            }
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to load snapshot for {}", playerId, e);
        }
        return Optional.empty();
    }

    /**
     * Delete a player's snapshot (after successful recovery or normal completion).
     */
    public void deleteSnapshot(UUID playerId) {
        if (!initialized) return;

        try {
            Path snapshotFile = getSnapshotFile(playerId);
            if (Files.deleteIfExists(snapshotFile)) {
                LOGGER.debug("[Recovery] Deleted snapshot for player {}", playerId);
            }
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to delete snapshot for {}", playerId, e);
        }
    }

    /**
     * Check if a player has a pending snapshot.
     */
    public boolean hasSnapshot(UUID playerId) {
        if (!initialized) return false;
        return Files.exists(getSnapshotFile(playerId));
    }

    private Path getSnapshotFile(UUID playerId) {
        return snapshotsDir.resolve(playerId.toString() + ".dat");
    }

    // === Player Login Recovery ===

    /**
     * Check if a player needs recovery when they log in.
     * This should be called in PlayerLoggedInEvent.
     */
    public void checkPendingRecovery(ServerPlayer player) {
        UUID playerId = player.getUUID();

        Optional<PlayerInstanceSnapshot> snapshotOpt = loadSnapshot(playerId);
        if (snapshotOpt.isEmpty()) {
            return; // No recovery needed
        }

        PlayerInstanceSnapshot snapshot = snapshotOpt.get();
        LOGGER.info("[Recovery] Found pending snapshot for {} in state {}",
            player.getName().getString(), snapshot.getState());

        // Perform recovery based on state
        switch (snapshot.getState()) {
            case PREPARING, IN_TRANSIT -> {
                // Teleport failed or incomplete - restore to original position
                LOGGER.info("[Recovery] Restoring {} from failed teleport", player.getName().getString());
                performRecovery(player, snapshot, "Teleport was interrupted");
            }
            case IN_INSTANCE -> {
                // Player was in instance when they disconnected
                // Policy: Quest failed, restore to original position
                LOGGER.info("[Recovery] Restoring {} from instance (quest failed)", player.getName().getString());
                performRecovery(player, snapshot, "Quest failed - you disconnected");
            }
            case RETURNING -> {
                // Return teleport was interrupted
                LOGGER.info("[Recovery] Completing interrupted return for {}", player.getName().getString());
                performRecovery(player, snapshot, "Return was interrupted");
            }
            case NORMAL -> {
                // Shouldn't have a snapshot in NORMAL state, clean it up
                LOGGER.warn("[Recovery] Found orphaned snapshot in NORMAL state for {}", playerId);
                deleteSnapshot(playerId);
            }
        }
    }

    /**
     * Perform full recovery for a player.
     */
    public void performRecovery(ServerPlayer player, PlayerInstanceSnapshot snapshot, String reason) {
        LOGGER.info("[Recovery] Performing recovery for {} - {}", player.getName().getString(), reason);

        MinecraftServer server = player.getServer();
        if (server == null) {
            LOGGER.error("[Recovery] Server is null, cannot recover player");
            return;
        }

        try {
            // 1. Teleport to original position
            teleportToOriginalPosition(player, snapshot, server);

            // 2. Restore inventory
            restoreInventory(player, snapshot);

            // 3. Restore game mode
            restoreGameMode(player, snapshot);

            // 4. Restore health and food
            restoreHealthAndFood(player, snapshot);

            // 5. Restore potion effects
            restoreEffects(player, snapshot);

            // 6. Restore experience
            restoreExperience(player, snapshot);

            // 7. Clean up instance registry mapping
            InstanceRegistry.INSTANCE.unmapPlayer(player.getUUID());

            // 8. Delete snapshot
            deleteSnapshot(player.getUUID());

            // 9. Notify player
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("[DevMod] " + reason + ". Your state has been restored.")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW)
            );

            LOGGER.info("[Recovery] Successfully recovered player {}", player.getName().getString());

        } catch (Exception e) {
            LOGGER.error("[Recovery] Failed to recover player {}", player.getName().getString(), e);
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("[DevMod] Recovery failed! Please contact an admin.")
                    .withStyle(net.minecraft.ChatFormatting.RED)
            );
        }
    }

    private void teleportToOriginalPosition(ServerPlayer player, PlayerInstanceSnapshot snapshot, MinecraftServer server) {
        ResourceLocation dimLocation = snapshot.getOriginalDimension();
        if (dimLocation == null) {
            LOGGER.warn("[Recovery] No original dimension in snapshot, using overworld");
            dimLocation = Level.OVERWORLD.location();
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimLocation);
        ServerLevel targetLevel = server.getLevel(dimensionKey);

        if (targetLevel == null) {
            LOGGER.warn("[Recovery] Original dimension {} not found, using overworld", dimLocation);
            targetLevel = server.overworld();
        }

        // Teleport player
        player.teleportTo(
            targetLevel,
            snapshot.getOriginalX(),
            snapshot.getOriginalY(),
            snapshot.getOriginalZ(),
            Set.of(),
            snapshot.getOriginalYaw(),
            snapshot.getOriginalPitch()
        );

        LOGGER.debug("[Recovery] Teleported {} to {} at ({}, {}, {})",
            player.getName().getString(),
            targetLevel.dimension().location(),
            snapshot.getOriginalX(),
            snapshot.getOriginalY(),
            snapshot.getOriginalZ()
        );
    }

    private void restoreInventory(ServerPlayer player, PlayerInstanceSnapshot snapshot) {
        CompoundTag inventoryNBT = snapshot.getInventoryNBT();
        if (inventoryNBT == null) {
            LOGGER.warn("[Recovery] No inventory data in snapshot for {}", player.getName().getString());
            return;
        }

        // Clear current inventory first
        player.getInventory().clearContent();

        // Load from NBT
        ListTag inventoryList = inventoryNBT.getList("Items", 10);
        player.getInventory().load(inventoryList);

        LOGGER.debug("[Recovery] Restored inventory for {} ({} slots)",
            player.getName().getString(), inventoryList.size());
    }

    private void restoreGameMode(ServerPlayer player, PlayerInstanceSnapshot snapshot) {
        GameType gameMode = snapshot.getOriginalGameMode();
        if (gameMode != null) {
            player.setGameMode(gameMode);
            LOGGER.debug("[Recovery] Restored game mode {} for {}",
                gameMode, player.getName().getString());
        }
    }

    private void restoreHealthAndFood(ServerPlayer player, PlayerInstanceSnapshot snapshot) {
        // Restore max health first (in case of attribute modifiers)
        // Then restore current health
        float health = Math.min(snapshot.getOriginalHealth(), player.getMaxHealth());
        player.setHealth(health > 0 ? health : player.getMaxHealth());

        // Restore food
        player.getFoodData().setFoodLevel(snapshot.getOriginalFoodLevel());
        player.getFoodData().setSaturation(snapshot.getOriginalSaturation());
        player.getFoodData().setExhaustion(snapshot.getOriginalExhaustion());

        LOGGER.debug("[Recovery] Restored health ({}) and food ({}) for {}",
            player.getHealth(), player.getFoodData().getFoodLevel(), player.getName().getString());
    }

    private void restoreEffects(ServerPlayer player, PlayerInstanceSnapshot snapshot) {
        // Clear current effects
        player.removeAllEffects();

        // Restore original effects
        CompoundTag effectsNBT = snapshot.getPotionEffectsNBT();
        if (effectsNBT != null && effectsNBT.contains("Effects")) {
            ListTag effectsList = effectsNBT.getList("Effects", 10);
            for (int i = 0; i < effectsList.size(); i++) {
                CompoundTag effectTag = effectsList.getCompound(i);
                MobEffectInstance effect = MobEffectInstance.load(effectTag);
                if (effect != null) {
                    player.addEffect(effect);
                }
            }
            LOGGER.debug("[Recovery] Restored {} effects for {}",
                effectsList.size(), player.getName().getString());
        }
    }

    private void restoreExperience(ServerPlayer player, PlayerInstanceSnapshot snapshot) {
        player.experienceLevel = snapshot.getOriginalExperienceLevel();
        player.experienceProgress = snapshot.getOriginalExperienceProgress();
        player.totalExperience = snapshot.getOriginalTotalExperience();

        LOGGER.debug("[Recovery] Restored XP level {} for {}",
            player.experienceLevel, player.getName().getString());
    }

    // === Server Startup Cleanup ===

    /**
     * Clean up orphaned instances and snapshots on server startup.
     * Called after InstanceRegistry.load().
     */
    public void performStartupCleanup(MinecraftServer server) {
        LOGGER.info("[Recovery] Performing startup cleanup...");

        // 1. Find orphaned snapshots (no matching instance)
        cleanupOrphanedSnapshots();

        // 2. Mark instances with no players for destruction
        markEmptyInstancesForDestruction();

        // 3. Clean up instance dimension folders that aren't registered
        cleanupOrphanedDimensionFolders(server);

        LOGGER.info("[Recovery] Startup cleanup complete");
    }

    private void cleanupOrphanedSnapshots() {
        if (!initialized || !Files.exists(snapshotsDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(snapshotsDir, "*.dat")) {
            for (Path snapshotFile : stream) {
                try {
                    PlayerInstanceSnapshot snapshot = PlayerInstanceSnapshot.loadFromFile(snapshotFile);
                    UUID instanceId = snapshot.getInstanceId();

                    // If snapshot references an instance that doesn't exist, it's orphaned
                    if (instanceId != null && InstanceRegistry.INSTANCE.getInstance(instanceId).isEmpty()) {
                        LOGGER.info("[Recovery] Found orphaned snapshot for player {} (instance {} gone)",
                            snapshot.getPlayerId(), instanceId);
                        // Keep the snapshot - player will be recovered on login
                    }
                } catch (Exception e) {
                    LOGGER.warn("[Recovery] Failed to check snapshot {}: {}", snapshotFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to scan snapshots directory", e);
        }
    }

    private void markEmptyInstancesForDestruction() {
        for (InstanceData instance : InstanceRegistry.INSTANCE.getEmptyInstances()) {
            if (!instance.isMarkedForDestruction()) {
                LOGGER.info("[Recovery] Marking empty instance {} for destruction", instance.getInstanceId());
                InstanceRegistry.INSTANCE.scheduleDestruction(instance.getInstanceId());
            }
        }
    }

    private void cleanupOrphanedDimensionFolders(MinecraftServer server) {
        // This will be implemented when DynamicDimensionManager is ready
        // For now, just log what we would do
        Path dimensionsDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("dimensions").resolve("devmod");

        if (!Files.exists(dimensionsDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dimensionsDir, "instance_*")) {
            for (Path instanceDir : stream) {
                String dirName = instanceDir.getFileName().toString();
                String uuidStrWithoutDashes = dirName.replace("instance_", "");

                try {
                    // DynamicDimensionManager stores UUID without dashes, so we need to reformat
                    // Format: 32 hex chars without dashes -> standard UUID format with dashes
                    UUID instanceId = parseUuidWithoutDashes(uuidStrWithoutDashes);
                    if (instanceId != null && InstanceRegistry.INSTANCE.getInstance(instanceId).isEmpty()) {
                        LOGGER.info("[Recovery] Found orphaned dimension folder: {} (will be cleaned)",
                            instanceDir);
                        // TODO: Actually delete when DynamicDimensionManager is ready
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[Recovery] Invalid instance folder name: {}", dirName);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to scan dimensions directory", e);
        }
    }

    /**
     * Parse a UUID string that was stored without dashes.
     * Format: 32 hex chars -> standard UUID with dashes (8-4-4-4-12)
     */
    @Nullable
    private UUID parseUuidWithoutDashes(String uuidWithoutDashes) {
        if (uuidWithoutDashes == null || uuidWithoutDashes.length() != 32) {
            return null;
        }

        try {
            // Insert dashes at positions 8, 12, 16, 20 to create standard UUID format
            String formatted = uuidWithoutDashes.substring(0, 8) + "-" +
                               uuidWithoutDashes.substring(8, 12) + "-" +
                               uuidWithoutDashes.substring(12, 16) + "-" +
                               uuidWithoutDashes.substring(16, 20) + "-" +
                               uuidWithoutDashes.substring(20, 32);
            return UUID.fromString(formatted);
        } catch (Exception e) {
            return null;
        }
    }

    // === Utility ===

    /**
     * Create a snapshot from a player's current state.
     */
    public PlayerInstanceSnapshot createSnapshotFromPlayer(ServerPlayer player, @Nullable InstanceData instance) {
        PlayerInstanceSnapshot snapshot = new PlayerInstanceSnapshot(player.getUUID());

        // Set instance reference
        if (instance != null) {
            snapshot.setInstanceId(instance.getInstanceId());
        }

        // Position
        snapshot.withPosition(
            player.level().dimension().location(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot()
        );

        // Inventory - serialize to NBT
        CompoundTag inventoryTag = new CompoundTag();
        ListTag itemsList = new ListTag();
        player.getInventory().save(itemsList);
        inventoryTag.put("Items", itemsList);
        snapshot.withInventory(inventoryTag, null); // Ender chest optional

        // Game mode
        snapshot.withGameMode(player.gameMode.getGameModeForPlayer());

        // Health
        snapshot.withHealth(player.getHealth(), player.getMaxHealth());

        // Food
        snapshot.withFood(
            player.getFoodData().getFoodLevel(),
            player.getFoodData().getSaturationLevel(),
            player.getFoodData().getExhaustionLevel()
        );

        // Effects - serialize active effects
        CompoundTag effectsTag = new CompoundTag();
        ListTag effectsList = new ListTag();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effectsList.add(effect.save());
        }
        effectsTag.put("Effects", effectsList);
        snapshot.withEffects(effectsTag);

        // Experience
        snapshot.withExperience(
            player.experienceLevel,
            player.experienceProgress,
            player.totalExperience
        );

        return snapshot;
    }

    /**
     * Get all pending snapshots (for debugging).
     */
    public List<PlayerInstanceSnapshot> getAllPendingSnapshots() {
        List<PlayerInstanceSnapshot> snapshots = new ArrayList<>();
        if (!initialized || !Files.exists(snapshotsDir)) return snapshots;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(snapshotsDir, "*.dat")) {
            for (Path snapshotFile : stream) {
                try {
                    snapshots.add(PlayerInstanceSnapshot.loadFromFile(snapshotFile));
                } catch (Exception e) {
                    LOGGER.warn("[Recovery] Failed to load snapshot {}", snapshotFile);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to scan snapshots", e);
        }

        return snapshots;
    }
}
