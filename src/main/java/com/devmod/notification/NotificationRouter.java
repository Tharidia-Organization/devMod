package com.devmod.notification;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;


import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Routes notifications to appropriate destinations based on:
 * <ul>
 *   <li>Player online status</li>
 *   <li>User preferences per category</li>
 *   <li>Notification priority</li>
 *   <li>Category default settings</li>
 * </ul>
 */
public class NotificationRouter {

    /**
     * Cache of player preferences. Will be populated from persistence layer.
     * Key: player UUID, Value: preferences
     */
    private final Map<UUID, NotificationPreferences> preferencesCache = new ConcurrentHashMap<>();

    /**
     * Route a notification and determine where it should be delivered.
     *
     * @param playerUuid The target player's UUID
     * @param notification The notification to route
     * @return The routing decision indicating which channels to use
     */
    public RoutingDecision route(UUID playerUuid, Notification notification) {
        NotificationPreferences prefs = getPreferences(playerUuid);
        boolean isOnline = isPlayerOnline(playerUuid);

        // Check global mute
        if (prefs.isGlobalMute()) {
            return new RoutingDecision(false, notification.persistToMailbox(), false);
        }

        // Check category muting
        NotificationCategory category = notification.category();
        if (prefs.isCategoryMuted(category)) {
            // If muted, only persist to mailbox if explicitly requested and category allows it
            return new RoutingDecision(false,
                    notification.persistToMailbox() && category.isDefaultMailboxEnabled(),
                    false);
        }

        // Check priority threshold
        NotificationPriority minPriority = prefs.getMinOverlayPriority();
        boolean meetsThreshold = notification.priority().isAtLeast(minPriority);

        // Determine overlay delivery
        boolean sendOverlay = isOnline
                && notification.showOverlay()
                && meetsThreshold
                && !prefs.prefersChatOverOverlay();

        // Determine mailbox delivery
        boolean sendMailbox = notification.persistToMailbox()
                && category.isDefaultMailboxEnabled();

        // If player prefers chat over overlay and meets threshold
        boolean sendChat = isOnline
                && prefs.prefersChatOverOverlay()
                && meetsThreshold;

        // If player is offline and notification is important, ensure mailbox delivery
        if (!isOnline && notification.priority().isAtLeast(NotificationPriority.HIGH)) {
            sendMailbox = true;
        }

        return new RoutingDecision(sendOverlay, sendMailbox, sendChat);
    }

    /**
     * Get preferences for a player, using defaults if not cached.
     */
    public NotificationPreferences getPreferences(UUID playerUuid) {
        return preferencesCache.computeIfAbsent(playerUuid,
                uuid -> new NotificationPreferences(uuid));
    }

    /**
     * Update cached preferences for a player.
     */
    public void updatePreferences(UUID playerUuid, NotificationPreferences preferences) {
        preferencesCache.put(playerUuid, preferences);
    }

    /**
     * Clear cached preferences for a player (e.g., on logout).
     */
    public void clearPreferences(UUID playerUuid) {
        preferencesCache.remove(playerUuid);
    }

    /**
     * Check if a player is currently online.
     */
    private boolean isPlayerOnline(UUID playerUuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;

        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        return player != null;
    }

    // ============================================================================
    // ROUTING DECISION
    // ============================================================================

    /**
     * Result of routing decision.
     *
     * @param sendOverlay Whether to send as overlay/toast on client
     * @param sendMailbox Whether to persist to mailbox
     * @param sendChat Whether to send as chat message
     */
    public record RoutingDecision(
            boolean sendOverlay,
            boolean sendMailbox,
            boolean sendChat
    ) {
        /**
         * Check if any delivery channel is enabled.
         */
        public boolean hasAnyDelivery() {
            return sendOverlay || sendMailbox || sendChat;
        }
    }

    // ============================================================================
    // PLAYER PREFERENCES (Server-side)
    // ============================================================================

    /**
     * Server-side notification preferences for a player.
     * Loaded from persistence and cached during session.
     */
    public static class NotificationPreferences {

        private final UUID playerUuid;
        private boolean globalMute = false;
        private NotificationPriority minOverlayPriority = NotificationPriority.LOW;
        private boolean preferChatOverOverlay = false;
        private float masterVolume = 1.0f;

        private final Map<NotificationCategory, CategoryPreference> categoryPreferences;

        public NotificationPreferences(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.categoryPreferences = new ConcurrentHashMap<>();

            // Initialize with category defaults
            for (NotificationCategory category : NotificationCategory.values()) {
                categoryPreferences.put(category, new CategoryPreference(
                        category.isDefaultOverlayEnabled(),
                        true, // sound enabled
                        1.0f  // full volume
                ));
            }
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public boolean isGlobalMute() {
            return globalMute;
        }

        public void setGlobalMute(boolean globalMute) {
            this.globalMute = globalMute;
        }

        public NotificationPriority getMinOverlayPriority() {
            return minOverlayPriority;
        }

        public void setMinOverlayPriority(NotificationPriority priority) {
            this.minOverlayPriority = priority;
        }

        public boolean prefersChatOverOverlay() {
            return preferChatOverOverlay;
        }

        public void setPreferChatOverOverlay(boolean prefer) {
            this.preferChatOverOverlay = prefer;
        }

        public float getMasterVolume() {
            return masterVolume;
        }

        public void setMasterVolume(float volume) {
            this.masterVolume = Math.max(0f, Math.min(1f, volume));
        }

        /**
         * Check if a category is muted (overlay disabled).
         */
        public boolean isCategoryMuted(NotificationCategory category) {
            CategoryPreference pref = categoryPreferences.get(category);
            return pref != null && !pref.overlayEnabled();
        }

        /**
         * Check if sound is enabled for a category.
         */
        public boolean isSoundEnabled(NotificationCategory category) {
            if (globalMute) return false;
            CategoryPreference pref = categoryPreferences.get(category);
            return pref == null || pref.soundEnabled();
        }

        /**
         * Get effective volume for a category.
         */
        public float getCategoryVolume(NotificationCategory category) {
            if (globalMute) return 0f;
            CategoryPreference pref = categoryPreferences.get(category);
            float catVolume = pref != null ? pref.soundVolume() : 1.0f;
            return masterVolume * catVolume;
        }

        /**
         * Set category overlay enabled.
         */
        public void setCategoryOverlayEnabled(NotificationCategory category, boolean enabled) {
            CategoryPreference old = categoryPreferences.getOrDefault(category,
                    new CategoryPreference(true, true, 1.0f));
            categoryPreferences.put(category,
                    new CategoryPreference(enabled, old.soundEnabled(), old.soundVolume()));
        }

        /**
         * Set category sound enabled.
         */
        public void setCategorySoundEnabled(NotificationCategory category, boolean enabled) {
            CategoryPreference old = categoryPreferences.getOrDefault(category,
                    new CategoryPreference(true, true, 1.0f));
            categoryPreferences.put(category,
                    new CategoryPreference(old.overlayEnabled(), enabled, old.soundVolume()));
        }

        /**
         * Set category sound volume.
         */
        public void setCategorySoundVolume(NotificationCategory category, float volume) {
            CategoryPreference old = categoryPreferences.getOrDefault(category,
                    new CategoryPreference(true, true, 1.0f));
            categoryPreferences.put(category,
                    new CategoryPreference(old.overlayEnabled(), old.soundEnabled(),
                            Math.max(0f, Math.min(1f, volume))));
        }

        /**
         * Get category preference.
         */
        @Nullable
        public CategoryPreference getCategoryPreference(NotificationCategory category) {
            return categoryPreferences.get(category);
        }

        /**
         * Category-specific preference settings.
         */
        public record CategoryPreference(
                boolean overlayEnabled,
                boolean soundEnabled,
                float soundVolume
        ) {}
    }
}
