package com.devmod.npc.event;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sealed interface for dialog events that can be observed by other systems.
 * Provides a type-safe API for reacting to NPC dialog state changes.
 *
 * <p>Usage example:
 * <pre>
 * DialogEventBus.register(event -> {
 *     switch (event) {
 *         case DialogEvent.Opened opened -> handleDialogOpened(opened);
 *         case DialogEvent.OptionSelected selected -> handleOptionSelected(selected);
 *         case DialogEvent.ActionExecuted executed -> handleActionExecuted(executed);
 *         case DialogEvent.Closed closed -> handleDialogClosed(closed);
 *     }
 * });
 * </pre>
 */
public sealed interface DialogEvent permits
    DialogEvent.Opened,
    DialogEvent.OptionSelected,
    DialogEvent.ActionExecuted,
    DialogEvent.Closed
{
    /**
     * Gets the player UUID associated with this event.
     */
    @Nonnull
    UUID playerId();

    /**
     * Gets the NPC UUID associated with this event.
     */
    @Nonnull
    UUID npcId();

    /**
     * Gets the timestamp when this event occurred (System.currentTimeMillis).
     */
    long timestamp();

    /**
     * Gets the dialog set ID for this event.
     */
    @Nonnull
    String dialogSetId();

    // ========================================================================
    // Event Types
    // ========================================================================

    /**
     * Fired when a dialog is opened between a player and an NPC.
     */
    record Opened(
        @Nonnull UUID playerId,
        @Nonnull UUID npcId,
        @Nonnull String dialogSetId,
        @Nonnull String entryNodeId,
        long timestamp
    ) implements DialogEvent {
        public Opened(@Nonnull UUID playerId, @Nonnull UUID npcId, @Nonnull String dialogSetId, @Nonnull String entryNodeId) {
            this(playerId, npcId, dialogSetId, entryNodeId, System.currentTimeMillis());
        }
    }

    /**
     * Fired when a player selects a dialog option.
     */
    record OptionSelected(
        @Nonnull UUID playerId,
        @Nonnull UUID npcId,
        @Nonnull String dialogSetId,
        @Nonnull String nodeId,
        @Nonnull String optionId,
        @Nonnull String optionLabel,
        long timestamp
    ) implements DialogEvent {
        public OptionSelected(@Nonnull UUID playerId, @Nonnull UUID npcId, @Nonnull String dialogSetId,
                             @Nonnull String nodeId, @Nonnull String optionId, @Nonnull String optionLabel) {
            this(playerId, npcId, dialogSetId, nodeId, optionId, optionLabel, System.currentTimeMillis());
        }
    }

    /**
     * Fired when a dialog action is executed.
     */
    record ActionExecuted(
        @Nonnull UUID playerId,
        @Nonnull UUID npcId,
        @Nonnull String dialogSetId,
        @Nonnull String actionType,
        @Nullable String actionDetails,
        boolean success,
        long timestamp
    ) implements DialogEvent {
        public ActionExecuted(@Nonnull UUID playerId, @Nonnull UUID npcId, @Nonnull String dialogSetId,
                             @Nonnull String actionType, @Nullable String actionDetails, boolean success) {
            this(playerId, npcId, dialogSetId, actionType, actionDetails, success, System.currentTimeMillis());
        }
    }

    /**
     * Fired when a dialog is closed.
     */
    record Closed(
        @Nonnull UUID playerId,
        @Nonnull UUID npcId,
        @Nonnull String dialogSetId,
        @Nonnull CloseReason reason,
        long timestamp
    ) implements DialogEvent {
        public Closed(@Nonnull UUID playerId, @Nonnull UUID npcId, @Nonnull String dialogSetId, @Nonnull CloseReason reason) {
            this(playerId, npcId, dialogSetId, reason, System.currentTimeMillis());
        }
    }

    /**
     * Reasons why a dialog was closed.
     */
    enum CloseReason {
        /** Player selected a close option */
        PLAYER_CHOICE,
        /** Dialog ended naturally (no more nodes) */
        DIALOG_END,
        /** Player disconnected or moved too far */
        PLAYER_LEFT,
        /** Session timed out */
        TIMEOUT,
        /** Server or NPC error */
        ERROR
    }
}
