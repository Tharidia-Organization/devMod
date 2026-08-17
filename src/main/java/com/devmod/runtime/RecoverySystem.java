package com.devmod.runtime;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import net.minecraft.world.level.storage.LevelResource;

import com.devmod.DevMod;
import com.devmod.endurance.EnduranceLogger;
import com.devmod.endurance.EnduranceLogger.Phase;
import com.devmod.shared.SharedColorTokens;

public class RecoverySystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoverySystem.class);
    public static final RecoverySystem INSTANCE = new RecoverySystem();

    /** Lock striping map to avoid String.intern() memory leaks while ensuring per-player synchronization */
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    private Path snapshotsDir;
    private Path intentsDir;
    private boolean initialized = false;

    private RecoverySystem() {}

    /** Get or create a lock object for a specific player (avoids String.intern() memory leak) */
    private Object getPlayerLock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, k -> new Object());
    }

    /**
     * Initialize the recovery system.
     * Called during server startup.
     */
    public void initialize(MinecraftServer server) {
        // Snapshots hold an inventory and a position that are only meaningful inside the
        // save they were taken from, so they must live in that save, not in the game-wide
        // config dir. Snapshots written by older builds under config/devmod are ignored.
        Path storageDir = server.getWorldPath(Objects.requireNonNull(LevelResource.ROOT)).resolve("devmod");
        this.snapshotsDir = storageDir.resolve("snapshots");
        this.intentsDir = storageDir.resolve("recovery_intents");
        try {
            Files.createDirectories(snapshotsDir);
            Files.createDirectories(intentsDir);
            initialized = true;
            LOGGER.info("[Recovery] Initialized, snapshots dir: {}, intents dir: {}", snapshotsDir, intentsDir);
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to create recovery directories", e);
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
     * Thread-safe: Uses per-player locks to prevent concurrent read-modify-write races.
     *
     * @param playerId The player whose snapshot to update
     * @param newState The new state to set
     * @return true if the update succeeded, false if snapshot not found or I/O error occurred
     */
    public boolean updateSnapshotState(UUID playerId, PlayerInstanceState newState) {
        Path snapshotFile = getSnapshotFile(playerId);

        // Synchronize on a per-player lock object to prevent concurrent updates
        // This prevents the read-modify-write race condition without using String.intern()
        synchronized (getPlayerLock(playerId)) {
            try {
                // Load, modify, save atomically within the lock
                // NoSuchFileException will be thrown if file doesn't exist (handles TOCTOU)
                PlayerInstanceSnapshot snapshot = PlayerInstanceSnapshot.loadFromFile(snapshotFile);
                snapshot.setState(newState);
                snapshot.saveToFile(snapshotFile);

                LOGGER.debug("[Recovery] Updated snapshot state for {} to {}", playerId, newState);
                return true;
            } catch (NoSuchFileException e) {
                LOGGER.warn("[Recovery] No snapshot found for player {} to update", playerId);
                return false;
            } catch (IOException e) {
                LOGGER.error("[Recovery] Failed to update snapshot state for {}", playerId, e);
                return false;
            }
        }
    }

    /**
     * Load a player's snapshot if it exists.
     * Handles TOCTOU by catching NoSuchFileException instead of pre-checking existence.
     */
    public Optional<PlayerInstanceSnapshot> loadSnapshot(UUID playerId) {
        if (!initialized) return Optional.empty();

        try {
            Path snapshotFile = getSnapshotFile(playerId);
            // Directly attempt to load - NoSuchFileException handles missing file case
            return Optional.of(PlayerInstanceSnapshot.loadFromFile(snapshotFile));
        } catch (NoSuchFileException e) {
            // File doesn't exist - this is a normal case, not an error
            return Optional.empty();
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
        } finally {
            // Clean up the lock object to prevent memory leak
            playerLocks.remove(playerId);
        }
    }

    // === Recovery Intent Management ===

    /**
     * Create a recovery intent before starting recovery.
     * This persists to disk so recovery can resume after server crash.
     */
    public RecoveryIntent createRecoveryIntent(UUID playerId, @Nullable UUID instanceId, String reason) {
        if (!initialized) return null;

        RecoveryIntent intent = new RecoveryIntent(playerId, instanceId, reason);
        try {
            Path intentFile = RecoveryIntent.getIntentFile(intentsDir, playerId);
            intent.saveToFile(intentFile);
            LOGGER.debug("[Recovery] Created recovery intent for {}: {}", playerId, reason);
            return intent;
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to create recovery intent for {}", playerId, e);
            return null;
        }
    }

    /**
     * Load a pending recovery intent for a player.
     */
    public Optional<RecoveryIntent> loadRecoveryIntent(UUID playerId) {
        if (!initialized) return Optional.empty();

        try {
            Path intentFile = RecoveryIntent.getIntentFile(intentsDir, playerId);
            if (!Files.exists(intentFile)) {
                return Optional.empty();
            }
            return Optional.of(RecoveryIntent.loadFromFile(intentFile));
        } catch (IOException e) {
            LOGGER.error("[Recovery] Failed to load recovery intent for {}", playerId, e);
            return Optional.empty();
        }
    }

    /**
     * Delete a recovery intent (after successful recovery).
     */
    public void deleteRecoveryIntent(UUID playerId) {
        if (!initialized) return;
        RecoveryIntent.delete(intentsDir, playerId);
        LOGGER.debug("[Recovery] Deleted recovery intent for {}", playerId);
    }

    /**
     * Check if a player has a pending recovery intent.
     * Called on player login to detect incomplete recoveries from server crash.
     *
     * @return true if there's a pending intent that needs processing
     */
    public boolean hasPendingRecoveryIntent(UUID playerId) {
        if (!initialized) return false;
        return RecoveryIntent.exists(intentsDir, playerId);
    }

    /**
     * Resume an interrupted recovery from a pending intent.
     * Called when player logs in and has a pending intent from a previous crash.
     *
     * @param player The player
     * @return true if recovery was resumed
     */
    public boolean resumeInterruptedRecovery(ServerPlayer player) {
        UUID playerId = player.getUUID();

        Optional<RecoveryIntent> intentOpt = loadRecoveryIntent(playerId);
        if (intentOpt.isEmpty()) {
            return false;
        }

        RecoveryIntent intent = intentOpt.get();
        LOGGER.info("[Recovery] Found pending recovery intent for {} (phase: {}, reason: {})",
            player.getName().getString(), intent.getPhase(), intent.getReason());

        // Load the snapshot
        Optional<PlayerInstanceSnapshot> snapshotOpt = loadSnapshot(playerId);
        if (snapshotOpt.isEmpty()) {
            LOGGER.warn("[Recovery] No snapshot found for pending intent, deleting intent");
            deleteRecoveryIntent(playerId);
            return false;
        }

        // Resume recovery from where it was interrupted
        PlayerInstanceSnapshot snapshot = snapshotOpt.get();
        LOGGER.info("[Recovery] Resuming interrupted recovery for {} from phase {}",
            player.getName().getString(), intent.getPhase());

        // Perform recovery - the intent will be deleted on completion
        performRecovery(player, snapshot, "Resuming interrupted recovery: " + intent.getReason());
        return true;
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

        // First, check for pending recovery intents from a previous server crash
        // during recovery. This takes priority over normal snapshot processing.
        if (hasPendingRecoveryIntent(playerId)) {
            LOGGER.info("[Recovery] Found pending recovery intent for {} - resuming interrupted recovery",
                player.getName().getString());
            if (resumeInterruptedRecovery(player)) {
                return; // Recovery handled by intent
            }
            // If intent resumption failed (e.g., no snapshot), fall through to normal processing
        }

        Optional<PlayerInstanceSnapshot> snapshotOpt = loadSnapshot(playerId);
        if (snapshotOpt.isEmpty()) {
            return; // No recovery needed
        }

        PlayerInstanceSnapshot snapshot = snapshotOpt.get();
        LOGGER.info("[Recovery] Found pending snapshot for {} in state {}",
            player.getName().getString(), snapshot.getState());

        var sessionOpt = com.devmod.endurance.EnduranceQuestManager.INSTANCE.getActiveSession(playerId);
        if (sessionOpt.isPresent()) {
            var session = sessionOpt.get();
            if (session.getPartyId() != null
                && com.devmod.endurance.EnduranceQuestManager.INSTANCE.isPartyRunActiveForPlayer(playerId)) {
                LOGGER.info("[Recovery] Skipping recovery for {} - party run active", player.getName().getString());
                return;
            }
        }

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
        UUID playerId = player.getUUID();
        LOGGER.info("[Recovery] Performing recovery for {} - {}", player.getName().getString(), reason);
        EnduranceLogger.phase(Phase.CLEANUP, player, snapshot.getInstanceId(),
            "Starting recovery: reason=%s, originalDim=%s", reason, snapshot.getOriginalDimension());

        MinecraftServer server = player.getServer();
        if (server == null) {
            LOGGER.error("[Recovery] Server is null, cannot recover player");
            EnduranceLogger.error(player, snapshot.getInstanceId(), "Recovery failed: server is null");
            return;
        }

        // Create recovery intent BEFORE starting - this persists to disk so we can
        // resume recovery if server crashes during the process
        RecoveryIntent intent = createRecoveryIntent(playerId, snapshot.getInstanceId(), reason);
        Path intentFile = intent != null ? RecoveryIntent.getIntentFile(intentsDir, playerId) : null;

        // Track recovery success to ensure snapshot cleanup in finally block
        // If teleport succeeded, we should delete snapshot even if later steps fail
        // (to prevent zombie snapshots that cause repeated recovery attempts)
        boolean teleportSucceeded = false;
        boolean recoveryComplete = false;

        try {
            // 1. Teleport to original position (CRITICAL - if this fails, keep snapshot for retry)
            teleportToOriginalPosition(player, snapshot, server);
            teleportSucceeded = true;
            if (intent != null && intentFile != null) {
                intent.updatePhase(RecoveryIntent.Phase.TELEPORTED, intentFile);
            }

            // 2. Restore inventory
            restoreInventory(player, snapshot);
            if (intent != null && intentFile != null) {
                intent.updatePhase(RecoveryIntent.Phase.INVENTORY_RESTORED, intentFile);
            }

            // 3. Restore game mode
            restoreGameMode(player, snapshot);

            // 4. Restore health and food
            restoreHealthAndFood(player, snapshot);

            // 5. Restore potion effects
            restoreEffects(player, snapshot);
            if (intent != null && intentFile != null) {
                intent.updatePhase(RecoveryIntent.Phase.EFFECTS_RESTORED, intentFile);
            }

            // 6. Restore experience
            restoreExperience(player, snapshot);

            // 7. Clean up instance registry mapping
            InstanceRegistry.INSTANCE.unmapPlayer(playerId);
            if (snapshot.getInstanceId() != null) {
                InstanceRegistry.INSTANCE.getInstance(snapshot.getInstanceId()).ifPresent(instance -> {
                    instance.removePlayer(playerId);
                    InstanceRegistry.INSTANCE.markDirty();
                });
            }

            recoveryComplete = true;

            // 8. Notify player
            player.sendSystemMessage(
                Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] " + reason + ". Your state has been restored.")
                    .withStyle(SharedColorTokens.Chat.YELLOW))
            );

            EnduranceLogger.phase(Phase.CLEANUP, player, snapshot.getInstanceId(),
                "Recovery complete: pos=(%.1f, %.1f, %.1f), gameMode=%s, health=%.1f, xpLevel=%d",
                player.getX(), player.getY(), player.getZ(),
                player.gameMode.getGameModeForPlayer(),
                player.getHealth(), player.experienceLevel);
            LOGGER.info("[Recovery] Successfully recovered player {}", player.getName().getString());

        } catch (Exception e) {
            EnduranceLogger.error(player, snapshot.getInstanceId(), "Recovery failed: %s", e.getMessage());
            LOGGER.error("[Recovery] Failed to recover player {}", player.getName().getString(), e);
            player.sendSystemMessage(
                Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Recovery failed! Please contact an admin.")
                    .withStyle(SharedColorTokens.Chat.RED))
            );
        } finally {
            // Delete snapshot if recovery succeeded OR if teleport succeeded
            // This prevents zombie snapshots when later steps fail
            // If teleport failed, keep snapshot for retry on next login
            if (recoveryComplete || teleportSucceeded) {
                deleteSnapshot(playerId);
                deleteRecoveryIntent(playerId); // Also delete the intent
                if (!recoveryComplete) {
                    LOGGER.warn("[Recovery] Deleting snapshot for {} despite incomplete recovery " +
                        "(teleport succeeded, so player is in overworld)", player.getName().getString());
                }
            } else {
                LOGGER.info("[Recovery] Keeping snapshot for {} - teleport failed, will retry on next login",
                    player.getName().getString());
            }
        }
    }

    private void teleportToOriginalPosition(ServerPlayer player, PlayerInstanceSnapshot snapshot, MinecraftServer server) {
        ResourceLocation dimLocation = snapshot.getOriginalDimension();
        if (dimLocation == null) {
            LOGGER.warn("[Recovery] No original dimension in snapshot, using overworld");
            dimLocation = Level.OVERWORLD.location();
        }

        ResourceKey<Level> dimensionKey = Objects.requireNonNull(ResourceKey.create(
            Objects.requireNonNull(Registries.DIMENSION), Objects.requireNonNull(dimLocation)));
        ServerLevel targetLevel = server.getLevel(dimensionKey);

        if (targetLevel == null) {
            LOGGER.warn("[Recovery] Original dimension {} not found, using overworld", dimLocation);
            targetLevel = server.overworld();
        }

        // Teleport player
        player.teleportTo(
            Objects.requireNonNull(targetLevel),
            snapshot.getOriginalX(),
            snapshot.getOriginalY(),
            snapshot.getOriginalZ(),
            Objects.requireNonNull(Set.of()),
            snapshot.getOriginalYaw(),
            snapshot.getOriginalPitch()
        );

        // Force chunk resync to prevent ghost blocks after dimension transition
        // This resets the connection state and forces chunks around the player to be resent
        player.connection.resetPosition();

        EnduranceLogger.phase(Phase.PLAYER_TELEPORT, player, snapshot.getInstanceId(),
            "Returning to original dimension: %s at (%.1f, %.1f, %.1f)",
            targetLevel.dimension().location(),
            snapshot.getOriginalX(), snapshot.getOriginalY(), snapshot.getOriginalZ());
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
            // Clear anyway, then leave. Returning before the clear was an item duplication vector,
            // not a safe bail-out: the player walks out of the instance still holding the quest kit
            // -- netherite, diamond and totems for the TANK, WARRIOR and BERSERKER presets -- and
            // into the live world, repeatably. Losing a snapshot must not mint items; the honest
            // outcome of "we do not know what you were carrying" is empty hands plus this warning.
            LOGGER.warn("[Recovery] No inventory data in snapshot for {}, clearing the quest kit "
                + "instead of carrying it out of the instance", player.getName().getString());
            player.getInventory().clearContent();
            EnduranceLogger.phase(Phase.CLEANUP, player, snapshot.getInstanceId(),
                "Inventory restore skipped: no inventory data in snapshot, inventory cleared");
            return;
        }

        // Clear current inventory first
        player.getInventory().clearContent();

        // Load from NBT
        ListTag inventoryList = Objects.requireNonNull(inventoryNBT.getList("Items", 10));
        player.getInventory().load(inventoryList);

        EnduranceLogger.phase(Phase.CLEANUP, player, snapshot.getInstanceId(),
            "Inventory restored: %d slots loaded", inventoryList.size());
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
        // Then restore current health - but ensure at least half max health for safety
        // This prevents players from being vulnerable to environmental mobs after recovery
        float maxHealth = player.getMaxHealth();
        float minSafeHealth = maxHealth / 2.0f;
        float originalHealth = snapshot.getOriginalHealth();
        float health = Math.min(originalHealth, maxHealth);
        // Ensure at least half max health so player isn't immediately vulnerable
        health = Math.max(health, minSafeHealth);
        player.setHealth(health > 0 ? health : maxHealth);

        // Restore food - also ensure at least half food for safety
        int originalFood = snapshot.getOriginalFoodLevel();
        int safeFood = Math.max(originalFood, 10); // At least half food
        player.getFoodData().setFoodLevel(safeFood);
        player.getFoodData().setSaturation(snapshot.getOriginalSaturation());
        player.getFoodData().setExhaustion(snapshot.getOriginalExhaustion());

        LOGGER.debug("[Recovery] Restored health ({}) and food ({}) for {} (original health was {}, original food was {})",
            player.getHealth(), player.getFoodData().getFoodLevel(), player.getName().getString(),
            originalHealth, originalFood);
    }

    private void restoreEffects(ServerPlayer player, PlayerInstanceSnapshot snapshot) {
        // Clear current effects
        player.removeAllEffects();

        // Restore original effects
        CompoundTag effectsNBT = snapshot.getPotionEffectsNBT();
        if (effectsNBT != null && effectsNBT.contains("Effects")) {
            ListTag effectsList = effectsNBT.getList("Effects", 10);
            for (int i = 0; i < effectsList.size(); i++) {
                CompoundTag effectTag = Objects.requireNonNull(effectsList.getCompound(i));
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
        Path worldPath = server.getWorldPath(Objects.requireNonNull(LevelResource.ROOT));
        Path dimensionsDir = worldPath.resolve("dimensions").resolve("devmod");

        if (!Files.exists(dimensionsDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dimensionsDir, "instance_*")) {
            for (Path instanceDir : stream) {
                Path dirNamePath = instanceDir.getFileName();
                if (dirNamePath == null) {
                    continue;
                }
                String dirName = dirNamePath.toString();
                String uuidStrWithoutDashes = dirName.replace("instance_", "");

                try {
                    // DynamicDimensionManager stores UUID without dashes, so we need to reformat
                    // Format: 32 hex chars without dashes -> standard UUID format with dashes
                    UUID instanceId = parseUuidWithoutDashes(uuidStrWithoutDashes);
                    if (instanceId != null && InstanceRegistry.INSTANCE.getInstance(instanceId).isEmpty()) {
                        // Skip deletion if the dimension is still registered/loaded
                        ResourceKey<Level> dimKey = Objects.requireNonNull(ResourceKey.create(
                            Objects.requireNonNull(Registries.DIMENSION),
                            Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "instance_" + uuidStrWithoutDashes))
                        ));

                        boolean isLoaded = server.getLevel(dimKey) != null;
                        boolean trackedByManager = DynamicDimensionManager.INSTANCE
                            .getDimensionForInstance(instanceId).isPresent();

                        if (isLoaded || trackedByManager) {
                            LOGGER.info("[Recovery] Found orphaned folder {} but dimension still loaded/tracked, skipping delete", instanceDir);
                            continue;
                        }

                        LOGGER.info("[Recovery] Removing orphaned dimension folder: {}", instanceDir);
                        deleteRecursively(instanceDir);
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
     * Recursive delete helper for orphan cleanup.
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * Create a snapshot from a player's current state.
     */
    public PlayerInstanceSnapshot createSnapshotFromPlayer(ServerPlayer player, @Nullable InstanceData instance) {
        PlayerInstanceSnapshot snapshot = new PlayerInstanceSnapshot(player.getUUID());

        // Set instance reference
        if (instance != null) {
            snapshot.setInstanceId(instance.getInstanceId());
            snapshot.withArenaTemplate(
                instance.getArenaTemplate(),
                instance.getArenaTemplateVersion(),
                instance.getArenaPolicyId(),
                instance.getArenaPolicyVersion()
            );
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
