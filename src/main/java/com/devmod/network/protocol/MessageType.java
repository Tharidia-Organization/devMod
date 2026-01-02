package com.devmod.network.protocol;

/**
 * Unified message types for DevMod network protocol.
 *
 * <p>Consolidates the 80+ payload types into logical categories:
 * <ul>
 *   <li>SYNC_* - Server to client state synchronization</li>
 *   <li>ACTION_* - Client to server action requests</li>
 *   <li>EVENT_* - Bidirectional event notifications</li>
 *   <li>CONFIG_* - Configuration related</li>
 * </ul>
 *
 * <p>Each category supports multiple payload types via the inner data.
 */
public enum MessageType {

    // ============================================================================
    // QUEST/ENDURANCE MESSAGES
    // ============================================================================
    /** Sync full quest state (server → client) */
    QUEST_SYNC(Domain.QUEST, Direction.SERVER_TO_CLIENT),
    /** Quest action (start, continue, exit) (client → server) */
    QUEST_ACTION(Domain.QUEST, Direction.CLIENT_TO_SERVER),
    /** Quest event (wave complete, death, etc.) (server → client) */
    QUEST_EVENT(Domain.QUEST, Direction.SERVER_TO_CLIENT),
    /** Quest objective update (server → client) */
    QUEST_OBJECTIVE(Domain.QUEST, Direction.SERVER_TO_CLIENT),

    // ============================================================================
    // PARTY MESSAGES
    // ============================================================================
    /** Sync party state (server → client) */
    PARTY_SYNC(Domain.PARTY, Direction.SERVER_TO_CLIENT),
    /** Party action (invite, kick, leave) (client → server) */
    PARTY_ACTION(Domain.PARTY, Direction.CLIENT_TO_SERVER),
    /** Party event (member joined, left) (server → client) */
    PARTY_EVENT(Domain.PARTY, Direction.SERVER_TO_CLIENT),

    // ============================================================================
    // MAILBOX MESSAGES
    // ============================================================================
    /** Sync mailbox state (server → client) */
    MAILBOX_SYNC(Domain.MAILBOX, Direction.SERVER_TO_CLIENT),
    /** Mailbox action (send, delete, read) (client → server) */
    MAILBOX_ACTION(Domain.MAILBOX, Direction.CLIENT_TO_SERVER),
    /** New mail notification (server → client) */
    MAILBOX_NOTIFY(Domain.MAILBOX, Direction.SERVER_TO_CLIENT),

    // ============================================================================
    // NOTIFICATION MESSAGES
    // ============================================================================
    /** Notification to display (server → client) */
    NOTIFICATION_PUSH(Domain.NOTIFICATION, Direction.SERVER_TO_CLIENT),
    /** Notification action (dismiss, click) (client → server) */
    NOTIFICATION_ACTION(Domain.NOTIFICATION, Direction.CLIENT_TO_SERVER),
    /** Sync notification preferences (bidirectional) */
    NOTIFICATION_PREFS(Domain.NOTIFICATION, Direction.BIDIRECTIONAL),

    // ============================================================================
    // CONFIG MESSAGES
    // ============================================================================
    /** Sync game mechanics config (server → client) */
    CONFIG_MECHANICS(Domain.CONFIG, Direction.SERVER_TO_CLIENT),
    /** Sync feature flags (server → client) */
    CONFIG_FLAGS(Domain.CONFIG, Direction.SERVER_TO_CLIENT),
    /** Config update request (client → server, op only) */
    CONFIG_UPDATE(Domain.CONFIG, Direction.CLIENT_TO_SERVER),

    // ============================================================================
    // MOB/COMBAT MESSAGES
    // ============================================================================
    /** Sync mob config (server → client) */
    MOB_SYNC(Domain.MOB, Direction.SERVER_TO_CLIENT),
    /** Mob config update (client → server, op only) */
    MOB_UPDATE(Domain.MOB, Direction.CLIENT_TO_SERVER),
    /** Impact HUD data (server → client) */
    IMPACT_HUD(Domain.MOB, Direction.SERVER_TO_CLIENT),

    // ============================================================================
    // EDITOR MESSAGES
    // ============================================================================
    /** Sync item stats for editor (server → client) */
    EDITOR_SYNC(Domain.EDITOR, Direction.SERVER_TO_CLIENT),
    /** Editor save request (client → server) */
    EDITOR_SAVE(Domain.EDITOR, Direction.CLIENT_TO_SERVER),

    // ============================================================================
    // ARENA MESSAGES
    // ============================================================================
    /** Arena build status (server → client) */
    ARENA_STATUS(Domain.ARENA, Direction.SERVER_TO_CLIENT),
    /** Arena action (build, teardown) (client → server) */
    ARENA_ACTION(Domain.ARENA, Direction.CLIENT_TO_SERVER),

    // ============================================================================
    // TELEMETRY MESSAGES
    // ============================================================================
    /** Telemetry data batch (client → server) */
    TELEMETRY_BATCH(Domain.TELEMETRY, Direction.CLIENT_TO_SERVER),
    /** Telemetry dashboard data (server → client) */
    TELEMETRY_DASHBOARD(Domain.TELEMETRY, Direction.SERVER_TO_CLIENT);

    private final Domain domain;
    private final Direction direction;

    MessageType(Domain domain, Direction direction) {
        this.domain = domain;
        this.direction = direction;
    }

    public Domain getDomain() {
        return domain;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isServerToClient() {
        return direction == Direction.SERVER_TO_CLIENT || direction == Direction.BIDIRECTIONAL;
    }

    public boolean isClientToServer() {
        return direction == Direction.CLIENT_TO_SERVER || direction == Direction.BIDIRECTIONAL;
    }

    /**
     * Message domains for categorization.
     */
    public enum Domain {
        QUEST,
        PARTY,
        MAILBOX,
        NOTIFICATION,
        CONFIG,
        MOB,
        EDITOR,
        ARENA,
        TELEMETRY
    }

    /**
     * Message direction.
     */
    public enum Direction {
        SERVER_TO_CLIENT,
        CLIENT_TO_SERVER,
        BIDIRECTIONAL
    }
}
