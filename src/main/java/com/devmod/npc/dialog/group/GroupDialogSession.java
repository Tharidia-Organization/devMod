package com.devmod.npc.dialog.group;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Tracks the state of an active group dialog session.
 * Manages progression through lines and speaker transitions.
 */
public class GroupDialogSession {
    private final UUID sessionId;
    private final UUID playerId;
    private final GroupDialog dialog;
    private final Map<UUID, String> participantNames;

    private String currentNodeId;
    private int currentLineIndex;
    private long startTime;
    private long lastActivity;

    public GroupDialogSession(
        @Nonnull UUID sessionId,
        @Nonnull UUID playerId,
        @Nonnull GroupDialog dialog,
        @Nonnull Map<UUID, String> participantNames,
        @Nonnull String startNodeId
    ) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.dialog = dialog;
        this.participantNames = Map.copyOf(participantNames);
        this.currentNodeId = startNodeId;
        this.currentLineIndex = 0;
        this.startTime = System.currentTimeMillis();
        this.lastActivity = startTime;
    }

    // ========================================================================
    // Getters
    // ========================================================================

    @Nonnull
    public UUID getSessionId() {
        return sessionId;
    }

    @Nonnull
    public UUID getPlayerId() {
        return playerId;
    }

    @Nonnull
    public GroupDialog getDialog() {
        return dialog;
    }

    @Nonnull
    public String getDialogId() {
        return dialog.id();
    }

    @Nonnull
    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public int getCurrentLineIndex() {
        return currentLineIndex;
    }

    @Nonnull
    public Map<UUID, String> getParticipantNames() {
        return participantNames;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    // ========================================================================
    // State Queries
    // ========================================================================

    /**
     * Gets the current node.
     */
    @Nonnull
    public Optional<GroupDialogNode> getCurrentNode() {
        return dialog.getNode(currentNodeId);
    }

    /**
     * Gets the current line being displayed.
     */
    @Nonnull
    public Optional<SpeakerLine> getCurrentLine() {
        return getCurrentNode()
            .flatMap(node -> node.getLine(currentLineIndex));
    }

    /**
     * Gets the speaker ID for the current line.
     */
    @Nonnull
    public Optional<UUID> getCurrentSpeakerId() {
        return getCurrentLine().map(SpeakerLine::speakerId);
    }

    /**
     * Gets the display name for a speaker.
     */
    @Nonnull
    public String getSpeakerName(@Nonnull UUID speakerId) {
        if (speakerId.equals(SpeakerLine.PLAYER_SPEAKER_ID)) {
            return "You";  // Will be replaced with actual player name client-side
        }
        if (speakerId.equals(SpeakerLine.NARRATOR_SPEAKER_ID)) {
            return "";  // Narrator has no name
        }
        return participantNames.getOrDefault(speakerId, "???");
    }

    /**
     * Returns true if there are more lines in the current node.
     */
    public boolean hasMoreLines() {
        return getCurrentNode()
            .map(node -> currentLineIndex < node.lineCount() - 1)
            .orElse(false);
    }

    /**
     * Returns true if all lines have been shown and options should be displayed.
     */
    public boolean isAtOptions() {
        return getCurrentNode()
            .map(node -> currentLineIndex >= node.lineCount() - 1)
            .orElse(true);
    }

    /**
     * Returns the total number of lines in the current node.
     */
    public int getTotalLines() {
        return getCurrentNode().map(GroupDialogNode::lineCount).orElse(0);
    }

    /**
     * Returns true if this session has been active longer than the specified duration.
     */
    public boolean isOlderThan(long durationMs) {
        return System.currentTimeMillis() - startTime > durationMs;
    }

    /**
     * Returns true if there has been no activity for the specified duration.
     */
    public boolean isInactive(long durationMs) {
        return System.currentTimeMillis() - lastActivity > durationMs;
    }

    // ========================================================================
    // State Mutations
    // ========================================================================

    /**
     * Advances to the next line in the current node.
     *
     * @return true if advanced, false if already at last line
     */
    public boolean advanceLine() {
        lastActivity = System.currentTimeMillis();

        if (hasMoreLines()) {
            currentLineIndex++;
            return true;
        }
        return false;
    }

    /**
     * Moves to a different node.
     *
     * @param nodeId The node to move to
     * @return true if moved, false if node doesn't exist
     */
    public boolean goToNode(@Nonnull String nodeId) {
        if (!dialog.nodes().containsKey(nodeId)) {
            return false;
        }

        lastActivity = System.currentTimeMillis();
        currentNodeId = nodeId;
        currentLineIndex = 0;
        return true;
    }

    /**
     * Resets to the beginning of the dialog.
     */
    public void reset() {
        lastActivity = System.currentTimeMillis();
        currentNodeId = dialog.entryNodeId();
        currentLineIndex = 0;
    }

    /**
     * Updates the last activity timestamp.
     */
    public void touch() {
        lastActivity = System.currentTimeMillis();
    }

    // ========================================================================
    // Factory
    // ========================================================================

    /**
     * Creates a new session starting at the entry node.
     */
    @Nonnull
    public static GroupDialogSession create(
        @Nonnull UUID playerId,
        @Nonnull GroupDialog dialog,
        @Nonnull Map<UUID, String> participantNames
    ) {
        return new GroupDialogSession(
            UUID.randomUUID(),
            playerId,
            dialog,
            participantNames,
            dialog.entryNodeId()
        );
    }
}
