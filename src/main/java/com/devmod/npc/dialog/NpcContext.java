package com.devmod.npc.dialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;

import com.devmod.npc.NpcEmotion;
import com.devmod.npc.data.NpcRegistry;

/**
 * Execution context for NPC dialog actions and conditions.
 * Provides access to NPC state, player visit data, quest integration,
 * and custom variables for placeholder resolution.
 */
public class NpcContext {
    private final UUID npcId;
    private final String npcName;
    private final String dialogSetId;
    private String currentNodeId;
    private final NpcRegistry registry;
    private final MinecraftServer server;

    private boolean dialogClosed = false;
    @Nullable
    private String requestedGuiId = null;

    /** Custom variables for placeholder resolution */
    private final Map<String, String> customVariables = new HashMap<>();

    /** Current NPC emotion state */
    private NpcEmotion emotion = NpcEmotion.NEUTRAL;

    public NpcContext(
        @Nonnull UUID npcId,
        @Nonnull String npcName,
        @Nonnull String dialogSetId,
        @Nonnull String currentNodeId,
        @Nonnull NpcRegistry registry,
        @Nonnull MinecraftServer server
    ) {
        this.npcId = Objects.requireNonNull(npcId, "npcId");
        this.npcName = Objects.requireNonNull(npcName, "npcName");
        this.dialogSetId = Objects.requireNonNull(dialogSetId, "dialogSetId");
        this.currentNodeId = Objects.requireNonNull(currentNodeId, "currentNodeId");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.server = Objects.requireNonNull(server, "server");
    }

    // ========================================================================
    // Getters
    // ========================================================================

    @Nonnull
    public UUID npcId() {
        return npcId;
    }

    @Nonnull
    public String npcName() {
        return npcName;
    }

    @Nonnull
    public String dialogSetId() {
        return dialogSetId;
    }

    @Nonnull
    public String currentNodeId() {
        return currentNodeId;
    }

    public boolean isDialogClosed() {
        return dialogClosed;
    }

    @Nullable
    public String getRequestedGuiId() {
        return requestedGuiId;
    }

    // ========================================================================
    // Action Methods
    // ========================================================================

    /**
     * Sets the current dialog node.
     * Used by GoToNode action.
     */
    public void setCurrentNode(@Nonnull String nodeId) {
        this.currentNodeId = Objects.requireNonNull(nodeId, "nodeId");
    }

    /**
     * Marks the dialog as closed.
     * Used by CloseDialog action.
     */
    public void closeDialog() {
        this.dialogClosed = true;
    }

    /**
     * Requests a GUI to be opened.
     * Used by OpenGui action.
     */
    public void requestGui(@Nonnull String guiId) {
        this.requestedGuiId = Objects.requireNonNull(guiId, "guiId");
        this.dialogClosed = true; // Close dialog when opening GUI
    }

    /**
     * Sets the NPC's emotional state.
     * Used by SetEmotion action.
     */
    public void setEmotion(@Nonnull NpcEmotion emotion) {
        this.emotion = Objects.requireNonNull(emotion, "emotion");
    }

    /**
     * Gets the NPC's current emotional state.
     */
    @Nonnull
    public NpcEmotion getEmotion() {
        return emotion;
    }

    // ========================================================================
    // Visit Tracking
    // ========================================================================

    /**
     * Checks if a player has visited an NPC before.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @return true if the player has visited this NPC before
     */
    public boolean hasVisited(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        return registry.hasVisited(playerId, npcId);
    }

    /**
     * Marks a player as having visited an NPC.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     */
    public void markVisited(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        registry.markVisited(playerId, npcId);
    }

    // ========================================================================
    // Quest Integration
    // ========================================================================

    /**
     * Checks if a player has completed a specific quest.
     * Integrates with EnduranceQuestManager.
     *
     * @param playerId The player's UUID
     * @param questId  The quest ID
     * @return true if the quest has been completed
     */
    public boolean hasCompletedQuest(@Nonnull UUID playerId, @Nonnull String questId) {
        // Integration with EnduranceQuestManager
        try {
            var manager = com.devmod.endurance.EnduranceQuestManager.INSTANCE;
            if (manager != null) {
                var stats = manager.getPlayerStats(playerId);
                if (stats != null) {
                    var record = stats.getMobRecords().get(questId);
                    return record != null && record.completions > 0;
                }
            }
        } catch (Exception e) {
            // Quest system not available or error
        }
        return false;
    }

    /**
     * Gets the total number of quests completed by a player.
     *
     * @param playerId The player's UUID
     * @return The number of completed quests
     */
    public int getCompletedQuestCount(@Nonnull UUID playerId) {
        // Integration with EnduranceQuestManager
        try {
            var manager = com.devmod.endurance.EnduranceQuestManager.INSTANCE;
            if (manager != null) {
                var stats = manager.getPlayerStats(playerId);
                if (stats != null) {
                    return stats.getTotalQuestsCompleted();
                }
            }
        } catch (Exception e) {
            // Quest system not available or error
        }
        return 0;
    }

    // ========================================================================
    // Server Access
    // ========================================================================

    /**
     * Gets the Minecraft server instance.
     */
    @Nonnull
    public MinecraftServer getServer() {
        return server;
    }

    /**
     * Gets the NPC registry.
     */
    @Nonnull
    public NpcRegistry getRegistry() {
        return registry;
    }

    // ========================================================================
    // Custom Variables
    // ========================================================================

    /**
     * Sets a custom variable for placeholder resolution.
     *
     * @param key   The variable key (used as {key} in text)
     * @param value The variable value
     */
    public void setVariable(@Nonnull String key, @Nonnull String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        customVariables.put(key, value);
    }

    /**
     * Gets a custom variable value.
     *
     * @param key The variable key
     * @return The value, or null if not set
     */
    @Nullable
    public String getVariable(@Nonnull String key) {
        return customVariables.get(key);
    }

    /**
     * Removes a custom variable.
     *
     * @param key The variable key to remove
     */
    public void removeVariable(@Nonnull String key) {
        customVariables.remove(key);
    }

    /**
     * Clears all custom variables.
     */
    public void clearVariables() {
        customVariables.clear();
    }

    /**
     * Gets all custom variables.
     *
     * @return An unmodifiable view of custom variables
     */
    @Nonnull
    public Map<String, String> getVariables() {
        return java.util.Collections.unmodifiableMap(customVariables);
    }
}
