package com.devmod.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent recovery intent that survives server crashes.
 *
 * When a recovery operation starts, an intent is written to disk.
 * If the server crashes during recovery, the intent file remains
 * and recovery is resumed on next player login.
 *
 * Intent lifecycle:
 * 1. Created when recovery starts (before any state changes)
 * 2. Updated as recovery progresses
 * 3. Deleted when recovery completes successfully
 *
 * If server crashes:
 * - Intent file remains on disk
 * - On player login, RecoverySystem checks for pending intents
 * - Recovery is resumed from last known state
 */
public class RecoveryIntent {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryIntent.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Recovery phases - tracks how far recovery progressed before crash
     */
    public enum Phase {
        INITIATED,       // Intent created, recovery starting
        TELEPORTED,      // Player teleported to original position
        INVENTORY_RESTORED,  // Inventory restored
        EFFECTS_RESTORED,    // Effects/health/food restored
        COMPLETED        // All steps complete (intent should be deleted)
    }

    private final UUID playerId;
    private final UUID instanceId;
    private final String reason;
    private final long createdAt;
    private Phase phase;
    private long updatedAt;

    public RecoveryIntent(UUID playerId, UUID instanceId, String reason) {
        this.playerId = playerId;
        this.instanceId = instanceId;
        this.reason = reason;
        this.createdAt = System.currentTimeMillis();
        this.phase = Phase.INITIATED;
        this.updatedAt = this.createdAt;
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public UUID getInstanceId() { return instanceId; }
    public String getReason() { return reason; }
    public long getCreatedAt() { return createdAt; }
    public Phase getPhase() { return phase; }
    public long getUpdatedAt() { return updatedAt; }

    /**
     * Update the phase and persist to disk.
     */
    public void updatePhase(Phase newPhase, Path intentFile) {
        this.phase = newPhase;
        this.updatedAt = System.currentTimeMillis();
        try {
            saveToFile(intentFile);
            LOGGER.debug("[RecoveryIntent] Updated phase for {} to {}", playerId, newPhase);
        } catch (IOException e) {
            LOGGER.error("[RecoveryIntent] Failed to persist phase update for {}", playerId, e);
        }
    }

    /**
     * Save this intent to a file.
     */
    public void saveToFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String json = GSON.toJson(this);
        Files.writeString(file, json);
    }

    /**
     * Load an intent from a file.
     */
    public static RecoveryIntent loadFromFile(Path file) throws IOException {
        String json = Files.readString(file);
        return GSON.fromJson(json, RecoveryIntent.class);
    }

    /**
     * Check if an intent file exists for a player.
     */
    public static boolean exists(Path intentDir, UUID playerId) {
        return Files.exists(getIntentFile(intentDir, playerId));
    }

    /**
     * Get the intent file path for a player.
     */
    public static Path getIntentFile(Path intentDir, UUID playerId) {
        return intentDir.resolve(playerId.toString() + ".intent.json");
    }

    /**
     * Delete the intent file for a player.
     */
    public static boolean delete(Path intentDir, UUID playerId) {
        try {
            return Files.deleteIfExists(getIntentFile(intentDir, playerId));
        } catch (IOException e) {
            LOGGER.error("[RecoveryIntent] Failed to delete intent for {}", playerId, e);
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("RecoveryIntent[player=%s, instance=%s, phase=%s, reason=%s]",
            playerId, instanceId, phase, reason);
    }
}
