package com.frenkvs.devmod.party;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Client-side cache for party state.
 * Updated by PartySyncPayload and PartyNotificationPayload.
 */
public final class ClientPartyCache {

    private static volatile PartySyncPayload currentParty = null;
    private static volatile PartyNotificationPayload lastNotification = null;
    private static volatile long lastNotificationTime = 0;

    // Notification display duration in ms
    public static final long NOTIFICATION_DISPLAY_MS = 5000;

    private ClientPartyCache() {}

    /**
     * Update the party state from a sync payload.
     */
    public static void update(PartySyncPayload payload) {
        if (payload.hasParty()) {
            currentParty = payload;
        } else {
            currentParty = null;
        }
    }

    /**
     * Handle a party notification.
     */
    public static void handleNotification(PartyNotificationPayload notification) {
        lastNotification = notification;
        lastNotificationTime = System.currentTimeMillis();

        // If party disbanded or kicked, clear party state
        if (notification.notificationType() == PartyNotificationPayload.NotificationType.PARTY_DISBANDED ||
            notification.notificationType() == PartyNotificationPayload.NotificationType.YOU_WERE_KICKED) {
            currentParty = null;
        }
    }

    /**
     * Check if player is currently in a party.
     */
    public static boolean hasParty() {
        return currentParty != null && currentParty.hasParty();
    }

    /**
     * Get the current party sync data.
     */
    @Nullable
    public static PartySyncPayload getParty() {
        return currentParty;
    }

    /**
     * Get the party ID if in a party.
     */
    @Nullable
    public static UUID getPartyId() {
        return currentParty != null ? currentParty.partyId() : null;
    }

    /**
     * Check if the local player is the party leader.
     */
    public static boolean isLeader(UUID localPlayerId) {
        return currentParty != null && currentParty.isLeader(localPlayerId);
    }

    /**
     * Get the current quest type.
     */
    @Nullable
    public static com.frenkvs.devmod.endurance.QuestType getQuestType() {
        return currentParty != null ? currentParty.getQuestType() : null;
    }

    /**
     * Get the current party state.
     */
    @Nullable
    public static PartyData.PartyState getState() {
        return currentParty != null ? currentParty.getState() : null;
    }

    /**
     * Get member count.
     */
    public static int getMemberCount() {
        return currentParty != null ? currentParty.members().size() : 0;
    }

    /**
     * Get the last notification if still displayable.
     */
    @Nullable
    public static PartyNotificationPayload getDisplayableNotification() {
        if (lastNotification == null) return null;
        if (System.currentTimeMillis() - lastNotificationTime > NOTIFICATION_DISPLAY_MS) {
            return null;
        }
        return lastNotification;
    }

    /**
     * Clear the notification manually.
     */
    public static void clearNotification() {
        lastNotification = null;
    }

    /**
     * Clear all cached data.
     */
    public static void clear() {
        currentParty = null;
        lastNotification = null;
    }
}
