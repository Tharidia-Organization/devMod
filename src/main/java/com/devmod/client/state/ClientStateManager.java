package com.devmod.client.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.endurance.ClientPartyStatsCache;
import com.devmod.client.state.stores.ConfigStateStore;
import com.devmod.client.state.stores.MailboxStateStore;
import com.devmod.client.state.stores.NotificationStateStore;
import com.devmod.client.state.stores.PartyStateStore;
import com.devmod.client.state.stores.QuestStateStore;

/**
 * Unified client-side state management.
 *
 * <p>Centralizes all client state into domain-specific stores:
 * <ul>
 *   <li>{@link QuestStateStore} - Endurance quest state</li>
 *   <li>{@link PartyStateStore} - Party/group state</li>
 *   <li>{@link ConfigStateStore} - Game mechanics and config</li>
 *   <li>{@link MailboxStateStore} - Mailbox messages</li>
 *   <li>{@link NotificationStateStore} - Notifications</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Get quest state
 * QuestState quest = ClientStateManager.INSTANCE.getQuestStore().getState();
 *
 * // Subscribe to party changes
 * ClientStateManager.INSTANCE.getPartyStore().subscribe(
 *     (old, newState) -> updatePartyUI(newState)
 * );
 *
 * // Clear all state on disconnect
 * ClientStateManager.INSTANCE.clearAll();
 * }</pre>
 *
 * <p>Thread Safety:
 * <ul>
 *   <li>Singleton instance is thread-safe</li>
 *   <li>Individual stores are thread-safe</li>
 *   <li>State updates can be called from any thread</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientStateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientStateManager.class);

    /** Singleton instance */
    public static final ClientStateManager INSTANCE = new ClientStateManager();

    // Domain stores
    private final QuestStateStore questStore;
    private final PartyStateStore partyStore;
    private final ConfigStateStore configStore;
    private final MailboxStateStore mailboxStore;
    private final NotificationStateStore notificationStore;

    // Connection state
    private volatile boolean connected = false;
    private volatile long lastConnectionTime = 0;

    private ClientStateManager() {
        this.questStore = new QuestStateStore();
        this.partyStore = new PartyStateStore();
        this.configStore = new ConfigStateStore();
        this.mailboxStore = new MailboxStateStore();
        this.notificationStore = new NotificationStateStore();

        LOGGER.info("[ClientStateManager] Initialized with {} domain stores", 5);
    }

    // ============================================================================
    // STORE ACCESSORS
    // ============================================================================

    /**
     * Get the quest state store.
     */
    public QuestStateStore getQuestStore() {
        return questStore;
    }

    /**
     * Get the party state store.
     */
    public PartyStateStore getPartyStore() {
        return partyStore;
    }

    /**
     * Get the config state store.
     */
    public ConfigStateStore getConfigStore() {
        return configStore;
    }

    /**
     * Get the mailbox state store.
     */
    public MailboxStateStore getMailboxStore() {
        return mailboxStore;
    }

    /**
     * Get the notification state store.
     */
    public NotificationStateStore getNotificationStore() {
        return notificationStore;
    }

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    /**
     * Called when connecting to a server.
     */
    public void onConnect() {
        connected = true;
        lastConnectionTime = System.currentTimeMillis();
        LOGGER.info("[ClientStateManager] Connected to server");
    }

    /**
     * Called when disconnecting from a server.
     */
    public void onDisconnect() {
        connected = false;
        clearAll();
        LOGGER.info("[ClientStateManager] Disconnected from server");
    }

    /**
     * Clear all state stores.
     */
    public void clearAll() {
        LOGGER.debug("[ClientStateManager] Clearing all state stores");

        questStore.clear();
        partyStore.clear();
        configStore.clear();
        mailboxStore.clear();
        notificationStore.clear();
        // FIX #9: Clear party stats cache on disconnect
        ClientPartyStatsCache.clear();
    }

    /**
     * Check if connected to a server.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Get time since last connection.
     */
    public long getConnectionUptime() {
        return connected ? System.currentTimeMillis() - lastConnectionTime : 0;
    }

    // ============================================================================
    // DIAGNOSTICS
    // ============================================================================

    /**
     * Get diagnostic info about all stores.
     */
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("ClientStateManager Diagnostics:\n");
        sb.append("  Connected: ").append(connected).append("\n");
        sb.append("  Uptime: ").append(getConnectionUptime()).append("ms\n");
        sb.append("  Stores:\n");

        sb.append("    Quest: ");
        sb.append(questStore.hasState() ? "active" : "empty");
        sb.append(" (").append(questStore.getSubscriptionCount()).append(" subs)\n");

        sb.append("    Party: ");
        sb.append(partyStore.hasState() ? "active" : "empty");
        sb.append(" (").append(partyStore.getSubscriptionCount()).append(" subs)\n");

        sb.append("    Config: ");
        sb.append(configStore.hasState() ? "active" : "empty");
        sb.append(" (").append(configStore.getSubscriptionCount()).append(" subs)\n");

        sb.append("    Mailbox: ");
        sb.append(mailboxStore.hasState() ? "active" : "empty");
        sb.append(" (").append(mailboxStore.getSubscriptionCount()).append(" subs)\n");

        sb.append("    Notification: ");
        sb.append(notificationStore.hasState() ? "active" : "empty");
        sb.append(" (").append(notificationStore.getSubscriptionCount()).append(" subs)\n");

        return sb.toString();
    }

    /**
     * Get total subscription count across all stores.
     */
    public int getTotalSubscriptions() {
        return questStore.getSubscriptionCount()
            + partyStore.getSubscriptionCount()
            + configStore.getSubscriptionCount()
            + mailboxStore.getSubscriptionCount()
            + notificationStore.getSubscriptionCount();
    }
}
