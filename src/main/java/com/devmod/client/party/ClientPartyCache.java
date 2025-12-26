package com.devmod.client.party;

import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.party.OnlinePlayersPayload;
import com.devmod.party.PartyData;
import com.devmod.party.PartyNotificationPayload;
import com.devmod.party.PartySyncPayload;

public final class ClientPartyCache {

    @Nullable
    private static volatile PartySyncPayload currentParty = null;
    @Nullable
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
        PartySyncPayload party = currentParty;
        return party != null && party.hasParty();
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
        PartySyncPayload party = currentParty;
        return party != null ? party.partyId() : null;
    }

    /**
     * Check if the local player is the party leader.
     */
    public static boolean isLeader(UUID localPlayerId) {
        PartySyncPayload party = currentParty;
        return party != null && party.isLeader(localPlayerId);
    }

    /**
     * Get the current quest type.
     */
    @Nullable
    public static com.devmod.endurance.QuestType getQuestType() {
        PartySyncPayload party = currentParty;
        return party != null ? party.getQuestType() : null;
    }

    /**
     * Get the current party state.
     */
    @Nullable
    public static PartyData.PartyState getState() {
        PartySyncPayload party = currentParty;
        return party != null ? party.getState() : null;
    }

    /**
     * Get member count.
     */
    public static int getMemberCount() {
        PartySyncPayload party = currentParty;
        return party != null ? party.members().size() : 0;
    }

    /**
     * Get the selected mob ID as ResourceLocation.
     * Returns null if no party or no mob selected.
     */
    @Nullable
    public static net.minecraft.resources.ResourceLocation getSelectedMobId() {
        PartySyncPayload party = currentParty;
        return party != null ? party.getSelectedMobResourceLocation() : null;
    }

    /**
     * Get the selected mob display name.
     * Returns "Zombie" (default) if no party or no mob selected.
     */
    public static String getSelectedMobDisplayName() {
        PartySyncPayload party = currentParty;
        return party != null ? party.getSelectedMobDisplayName() : "Zombie";
    }

    /**
     * Get the last notification if still displayable.
     */
    @Nullable
    public static PartyNotificationPayload getDisplayableNotification() {
        PartyNotificationPayload notification = lastNotification;
        if (notification == null) return null;
        if (System.currentTimeMillis() - lastNotificationTime > NOTIFICATION_DISPLAY_MS) {
            return null;
        }
        return notification;
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
        localPlayerReady = false;
    }

    // === Additional convenience methods for UI ===

    /** Local player's ready state (for optimistic UI updates) */
    private static volatile boolean localPlayerReady = false;

    /**
     * Alias for hasParty() for consistency.
     */
    public static boolean isInParty() {
        return hasParty();
    }

    /**
     * Get the leader ID if in a party.
     */
    @Nullable
    public static UUID getLeaderId() {
        PartySyncPayload party = currentParty;
        return party != null ? party.leaderId() : null;
    }

    /**
     * Get the party members list.
     */
    public static java.util.List<PartySyncPayload.PartyMemberInfo> getMembers() {
        PartySyncPayload party = currentParty;
        return party != null ? party.members() : java.util.List.of();
    }

    /**
     * Get the party state.
     */
    @Nullable
    public static PartyData.PartyState getPartyState() {
        PartySyncPayload party = currentParty;
        return party != null ? party.getState() : null;
    }

    /**
     * Get local player's ready status.
     */
    public static boolean isLocalPlayerReady() {
        return localPlayerReady;
    }

    /**
     * Set local player's ready status (for optimistic UI).
     */
    public static void setLocalPlayerReady(boolean ready) {
        localPlayerReady = ready;
    }

    /**
     * Set quest type (for optimistic UI).
     */
    public static void setQuestType(com.devmod.endurance.QuestType questType) {
        // This is for optimistic UI updates
        // The real update will come from PartySyncPayload
    }

    // === Online Players List (for invite UI) ===

    private static volatile java.util.List<OnlinePlayersPayload.PlayerInfo> onlinePlayers = java.util.List.of();
    private static volatile long onlinePlayersLastUpdate = 0;
    private static final long ONLINE_PLAYERS_CACHE_MS = 5000; // 5 seconds cache

    /**
     * Update the online players list from server.
     */
    public static void updateOnlinePlayers(OnlinePlayersPayload payload) {
        onlinePlayers = new java.util.ArrayList<>(payload.players());
        onlinePlayersLastUpdate = System.currentTimeMillis();
    }

    /**
     * Get the cached online players list.
     */
    public static java.util.List<OnlinePlayersPayload.PlayerInfo> getOnlinePlayers() {
        return onlinePlayers;
    }

    /**
     * Check if online players cache is stale.
     */
    public static boolean isOnlinePlayersCacheStale() {
        return System.currentTimeMillis() - onlinePlayersLastUpdate > ONLINE_PLAYERS_CACHE_MS;
    }
}
