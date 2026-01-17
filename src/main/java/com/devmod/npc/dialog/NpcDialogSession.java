package com.devmod.npc.dialog;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Immutable record representing an active dialog session between a player and an NPC.
 * Sessions are stored in memory and not persisted.
 */
public record NpcDialogSession(
    @Nonnull UUID playerId,
    @Nonnull UUID npcId,
    @Nonnull String dialogSetId,
    @Nonnull String currentNodeId,
    long openedAt
) {
    /**
     * Creates a new dialog session.
     *
     * @param playerId     The player's UUID
     * @param npcId        The NPC's UUID
     * @param dialogSetId  The dialog set ID
     * @param entryNodeId  The entry node ID to start from
     * @return A new NpcDialogSession
     */
    @Nonnull
    public static NpcDialogSession create(
        @Nonnull UUID playerId,
        @Nonnull UUID npcId,
        @Nonnull String dialogSetId,
        @Nonnull String entryNodeId
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(dialogSetId, "dialogSetId");
        Objects.requireNonNull(entryNodeId, "entryNodeId");

        return new NpcDialogSession(
            playerId,
            npcId,
            dialogSetId,
            entryNodeId,
            System.currentTimeMillis()
        );
    }

    /**
     * Creates a copy with updated current node ID.
     */
    @Nonnull
    public NpcDialogSession withCurrentNode(@Nonnull String nodeId) {
        return new NpcDialogSession(playerId, npcId, dialogSetId, nodeId, openedAt);
    }

    /**
     * Returns the duration this session has been open in milliseconds.
     */
    public long getDuration() {
        return System.currentTimeMillis() - openedAt;
    }

    /**
     * Returns true if this session has been open for longer than the specified duration.
     */
    public boolean isOlderThan(long durationMs) {
        return getDuration() > durationMs;
    }

    /**
     * Returns true if this session is for the specified NPC.
     */
    public boolean isForNpc(@Nonnull UUID npcId) {
        return this.npcId.equals(npcId);
    }

    /**
     * Returns true if this session is using the specified dialog set.
     */
    public boolean isUsingDialogSet(@Nonnull String dialogSetId) {
        return this.dialogSetId.equals(dialogSetId);
    }
}
