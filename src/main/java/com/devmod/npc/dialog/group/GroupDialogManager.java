package com.devmod.npc.dialog.group;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.DevMod;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.data.NpcRegistry;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.NpcContext;
import com.devmod.npc.dialog.PlaceholderResolver;
import com.devmod.npc.dialog.condition.DialogCondition;

/**
 * Manages active group dialog sessions.
 * Handles session creation, line advancement, and option selection.
 */
public class GroupDialogManager {
    public static final GroupDialogManager INSTANCE = new GroupDialogManager();

    /** Maximum session duration (10 minutes) */
    private static final long MAX_SESSION_DURATION_MS = 10 * 60 * 1000;

    /** Inactivity timeout (2 minutes) */
    private static final long INACTIVITY_TIMEOUT_MS = 2 * 60 * 1000;

    /** Active sessions by player UUID */
    private final Map<UUID, GroupDialogSession> activeSessions = new ConcurrentHashMap<>();

    /** Registered group dialogs by ID */
    private final Map<String, GroupDialog> registeredDialogs = new ConcurrentHashMap<>();

    private GroupDialogManager() {}

    // ========================================================================
    // Dialog Registration
    // ========================================================================

    /**
     * Registers a group dialog.
     */
    public void registerDialog(@Nonnull GroupDialog dialog) {
        registeredDialogs.put(dialog.id(), dialog);
        DevMod.LOGGER.debug("Registered group dialog: {}", dialog.id());
    }

    /**
     * Unregisters a group dialog.
     */
    public void unregisterDialog(@Nonnull String dialogId) {
        registeredDialogs.remove(dialogId);
        DevMod.LOGGER.debug("Unregistered group dialog: {}", dialogId);
    }

    /**
     * Gets a registered group dialog.
     */
    @Nonnull
    public Optional<GroupDialog> getDialog(@Nonnull String dialogId) {
        return Optional.ofNullable(registeredDialogs.get(dialogId));
    }

    /**
     * Gets all registered dialog IDs.
     */
    @Nonnull
    public java.util.Set<String> getDialogIds() {
        return java.util.Set.copyOf(registeredDialogs.keySet());
    }

    // ========================================================================
    // Session Management
    // ========================================================================

    /**
     * Starts a group dialog session for a player.
     *
     * @param player    The server player
     * @param dialogId  The group dialog ID
     * @param registry  The NPC registry for participant names
     * @return The initial result, or null if failed
     */
    @Nullable
    public GroupDialogResult startDialog(
        @Nonnull ServerPlayer player,
        @Nonnull String dialogId,
        @Nonnull NpcRegistry registry
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dialogId, "dialogId");
        Objects.requireNonNull(registry, "registry");

        // Close any existing session
        closeDialog(player.getUUID());

        // Get dialog
        GroupDialog dialog = registeredDialogs.get(dialogId);
        if (dialog == null) {
            DevMod.LOGGER.warn("Group dialog not found: {}", dialogId);
            return null;
        }

        // Validate dialog
        List<String> errors = dialog.validate();
        if (!errors.isEmpty()) {
            DevMod.LOGGER.warn("Group dialog {} has validation errors: {}", dialogId, errors);
            return null;
        }

        // Build participant names
        Map<UUID, String> participantNames = new HashMap<>();
        for (UUID participantId : dialog.participants()) {
            String name = registry.getNpc(participantId)
                .map(NpcConfiguration::displayName)
                .orElse("NPC");
            participantNames.put(participantId, name);
        }

        // Create session
        GroupDialogSession session = GroupDialogSession.create(player.getUUID(), dialog, participantNames);
        activeSessions.put(player.getUUID(), session);

        DevMod.LOGGER.debug("Started group dialog {} for player {}", dialogId, player.getName().getString());

        return buildResult(session, player, registry);
    }

    /**
     * Advances to the next line in the dialog.
     *
     * @param player   The server player
     * @param registry The NPC registry
     * @return The updated result, or null if dialog should close
     */
    @Nullable
    public GroupDialogResult advanceLine(
        @Nonnull ServerPlayer player,
        @Nonnull NpcRegistry registry
    ) {
        GroupDialogSession session = activeSessions.get(player.getUUID());
        if (session == null) {
            return null;
        }

        // Check if we can advance
        if (session.hasMoreLines()) {
            session.advanceLine();
            return buildResult(session, player, registry);
        }

        // Already at options - don't advance
        return buildResult(session, player, registry);
    }

    /**
     * Processes an option selection.
     *
     * @param player    The server player
     * @param nodeId    The current node ID (for validation)
     * @param optionId  The selected option ID
     * @param registry  The NPC registry
     * @return The next result, or null if dialog should close
     */
    @Nullable
    public GroupDialogResult selectOption(
        @Nonnull ServerPlayer player,
        @Nonnull String nodeId,
        @Nonnull String optionId,
        @Nonnull NpcRegistry registry
    ) {
        GroupDialogSession session = activeSessions.get(player.getUUID());
        if (session == null) {
            DevMod.LOGGER.warn("No active group session for player {}", player.getName().getString());
            return null;
        }

        // Validate node matches
        if (!session.getCurrentNodeId().equals(nodeId)) {
            DevMod.LOGGER.warn("Node mismatch: expected {}, got {}", session.getCurrentNodeId(), nodeId);
            return null;
        }

        // Get current node and option
        GroupDialogNode currentNode = session.getCurrentNode().orElse(null);
        if (currentNode == null) {
            closeDialog(player.getUUID());
            return null;
        }

        DialogOption option = currentNode.getOption(optionId).orElse(null);
        if (option == null) {
            DevMod.LOGGER.warn("Option {} not found in node {}", optionId, nodeId);
            return null;
        }

        // Create context for action execution
        // Use first participant as the "NPC" for context
        UUID firstParticipant = session.getDialog().participants().isEmpty()
            ? UUID.randomUUID()
            : session.getDialog().participants().get(0);

        NpcContext context = new NpcContext(
            firstParticipant,
            session.getSpeakerName(firstParticipant),
            session.getDialogId(),
            nodeId,
            registry,
            player.getServer()
        );

        // Execute action
        option.action().execute(player, context);

        // Check if dialog should close
        if (context.isDialogClosed()) {
            closeDialog(player.getUUID());
            return null;
        }

        // Navigate to next node if GoToNode action
        String nextNodeId = context.currentNodeId();
        if (!nextNodeId.equals(nodeId)) {
            if (!session.goToNode(nextNodeId)) {
                DevMod.LOGGER.warn("Failed to navigate to node {}", nextNodeId);
                closeDialog(player.getUUID());
                return null;
            }
        }

        return buildResult(session, player, registry);
    }

    /**
     * Closes a dialog session.
     */
    public void closeDialog(@Nonnull UUID playerId) {
        GroupDialogSession removed = activeSessions.remove(playerId);
        if (removed != null) {
            DevMod.LOGGER.debug("Closed group dialog session for player {}", playerId);
        }
    }

    /**
     * Cleans up all data associated with a player.
     * Called on player disconnect to prevent memory leaks.
     *
     * @param playerId The player UUID
     */
    public void cleanupPlayer(@Nonnull UUID playerId) {
        GroupDialogSession removed = activeSessions.remove(playerId);
        if (removed != null) {
            DevMod.LOGGER.debug("Cleaned up group dialog data for player {}", playerId);
        }
    }

    /**
     * Gets the active session for a player.
     */
    @Nonnull
    public Optional<GroupDialogSession> getSession(@Nonnull UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * Returns true if the player has an active group dialog session.
     */
    public boolean hasActiveSession(@Nonnull UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Builds a result from the current session state.
     */
    @Nullable
    private GroupDialogResult buildResult(
        @Nonnull GroupDialogSession session,
        @Nonnull ServerPlayer player,
        @Nonnull NpcRegistry registry
    ) {
        GroupDialogNode currentNode = session.getCurrentNode().orElse(null);
        if (currentNode == null) {
            return null;
        }

        // Build context for placeholder resolution
        UUID firstParticipant = session.getDialog().participants().isEmpty()
            ? UUID.randomUUID()
            : session.getDialog().participants().get(0);

        NpcContext context = new NpcContext(
            firstParticipant,
            session.getSpeakerName(firstParticipant),
            session.getDialogId(),
            session.getCurrentNodeId(),
            registry,
            player.getServer()
        );

        // Resolve placeholders in current line
        SpeakerLine currentLine = session.getCurrentLine().orElse(null);
        String resolvedText = currentLine != null
            ? PlaceholderResolver.resolve(currentLine.text(), player, context)
            : "";

        // Get visible options if at end of lines
        List<DialogOption> visibleOptions = List.of();
        if (session.isAtOptions()) {
            visibleOptions = currentNode.options().stream()
                .filter(opt -> {
                    DialogCondition cond = opt.showCondition();
                    return cond == null || cond.test(player, context);
                })
                .map(opt -> new DialogOption(
                    opt.id(),
                    PlaceholderResolver.resolve(opt.label(), player, context),
                    opt.icon(),
                    opt.showCondition(),
                    opt.action()
                ))
                .toList();
        }

        return new GroupDialogResult(
            session.getSessionId(),
            session.getDialogId(),
            session.getDialog().name(),
            session.getCurrentNodeId(),
            session.getParticipantNames(),
            currentLine != null ? currentLine.speakerId() : SpeakerLine.NARRATOR_SPEAKER_ID,
            currentLine != null ? session.getSpeakerName(currentLine.speakerId()) : "",
            resolvedText,
            currentLine != null ? currentLine.emotion() : null,
            session.getCurrentLineIndex(),
            session.getTotalLines(),
            visibleOptions
        );
    }

    /**
     * Cleans up stale sessions.
     */
    public void cleanupStaleSessions() {
        activeSessions.entrySet().removeIf(entry -> {
            GroupDialogSession session = entry.getValue();
            if (session.isOlderThan(MAX_SESSION_DURATION_MS) || session.isInactive(INACTIVITY_TIMEOUT_MS)) {
                DevMod.LOGGER.debug("Cleaned up stale group session for player {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Clears all sessions and dialogs.
     */
    public void clearAll() {
        activeSessions.clear();
        registeredDialogs.clear();
    }

    // ========================================================================
    // Result Record
    // ========================================================================

    /**
     * Result of a group dialog operation.
     */
    public record GroupDialogResult(
        UUID sessionId,
        String dialogId,
        String dialogName,
        String nodeId,
        Map<UUID, String> participantNames,
        UUID currentSpeakerId,
        String currentSpeakerName,
        String currentText,
        @Nullable com.devmod.npc.NpcEmotion currentEmotion,
        int lineIndex,
        int totalLines,
        List<DialogOption> options
    ) {
        /**
         * Returns true if this result shows options (at end of lines).
         */
        public boolean showOptions() {
            return lineIndex >= totalLines - 1 && !options.isEmpty();
        }

        /**
         * Returns true if there are more lines to show.
         */
        public boolean hasMoreLines() {
            return lineIndex < totalLines - 1;
        }
    }
}
