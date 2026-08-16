package com.devmod.events;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.event.server.ServerStartingEvent;

import com.devmod.notification.PartyNotificationBridge;
import com.devmod.notification.persistence.NotificationHistoryRepository;
import com.devmod.notification.persistence.NotificationPreferencesRepository;
import com.devmod.telemetry.duckdb.DuckDBBootstrap;
import com.devmod.util.ConfigPaths;

/**
 * Async data loading utility for DuckDB-dependent services
 * (MailboxManager, NotificationHistoryRepository, NotificationPreferencesRepository).
 * Called by {@link ModLifecycleEvents} during server lifecycle.
 */
final class DataInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DataInitializer() {}

    /**
     * Initialize DuckDB-dependent services: Mailbox and Notification repositories.
     * Called from {@link ModLifecycleEvents#onServerStarting}.
     */
    static void initializeDuckDBServices(ServerStartingEvent event) {
        // Check DuckDB availability and download if needed
        boolean duckDbAvailable = DuckDBBootstrap.ensureAvailable(ConfigPaths.getGameDir());

        if (duckDbAvailable) {
            initializeMailboxManager(event);
            initializeNotificationRepositories();
        } else {
            // No "restart to finish the download" hint any more: the driver is loaded through
            // its own classloader the moment it lands, so reaching here means provisioning
            // genuinely failed. The reason is already in the log above, from DuckDBBootstrap.
            LOGGER.warn("[DevMod] DuckDB not available - Mailbox and Notification features disabled");
            LOGGER.warn("[DevMod] Telemetry falls back to NDJSON; see the [DuckDB] lines above for why");
        }
    }

    /**
     * Shutdown DuckDB-dependent services.
     * Called from {@link ModLifecycleEvents#onServerStopping}.
     */
    static void shutdownDuckDBServices() {
        // Only shutdown DuckDB-dependent services if they were initialized
        if (!DuckDBBootstrap.isAvailable()) {
            return;
        }

        // Shutdown NotificationService
        try {
            com.devmod.notification.NotificationService.INSTANCE.shutdown();
            LOGGER.info("[DevMod] NotificationService shutdown complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during NotificationService shutdown", e);
        }

        // Unregister party notification bridge
        try {
            PartyNotificationBridge.unregister();
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to unregister PartyNotificationBridge", e);
        }

        // Shutdown notification repositories
        try {
            NotificationHistoryRepository.INSTANCE.shutdown();
            NotificationPreferencesRepository.INSTANCE.shutdown();
            LOGGER.info("[DevMod] Notification repositories shutdown complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during notification repositories shutdown", e);
        }

        // Shutdown MailboxManager (with timeout to prevent hang)
        try {
            com.devmod.mailbox.MailboxManager.INSTANCE.shutdown()
                .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .join();
            LOGGER.info("[DevMod] MailboxManager shutdown complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during MailboxManager shutdown", e);
        }
    }

    private static void initializeMailboxManager(ServerStartingEvent event) {
        // Initialize MailboxManager for in-game messaging system (non-blocking)
        try {
            com.devmod.mailbox.MailboxManager.INSTANCE.initialize()
                .thenRun(() -> {
                    // Set up callback to notify clients of new messages
                    com.devmod.mailbox.MailboxManager.INSTANCE.setNewMessageCallback((recipientUuid, message) -> {
                        ServerPlayer recipient = event.getServer().getPlayerList().getPlayer(java.util.Objects.requireNonNull(recipientUuid));
                        if (recipient != null) {
                            // Get unread count and send notification
                            ModLifecycleEvents.observeFuture(com.devmod.mailbox.MailboxManager.INSTANCE.getUnreadCount(recipientUuid)
                                .thenAccept(unreadCount -> {
                                    com.devmod.mailbox.network.MailboxNetworkHandler.sendNotification(recipient, message, unreadCount);
                                    com.devmod.notification.NotificationService.INSTANCE.notifyMailboxMessage(
                                        recipientUuid, message, unreadCount);
                                }),
                                "mailbox unread count");
                        }
                    });

                    com.devmod.mailbox.news.NewsManager.getInstance().setNewNewsCallback(article -> {
                        var server = event.getServer();
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            try {
                                com.devmod.mailbox.network.MailboxNetworkHandler.sendNewsSync(player);
                                com.devmod.notification.NotificationService.INSTANCE.notifyNewsArticle(player.getUUID(), article);
                            } catch (Exception e) {
                                LOGGER.warn("[DevMod] Failed to notify news for {}: {}",
                                    player.getName().getString(), e.getMessage());
                            }
                        }
                    });

                    LOGGER.info("[DevMod] MailboxManager initialized successfully");
                })
                .exceptionally(ex -> {
                    LOGGER.error("[DevMod] Failed to initialize MailboxManager", ex);
                    return null;
                });
        } catch (NoClassDefFoundError | UnsatisfiedLinkError | ExceptionInInitializerError e) {
            LOGGER.error("[DevMod] DuckDB native library not available - MailboxManager disabled", e);
        }
    }

    private static void initializeNotificationRepositories() {
        // Initialize notification persistence repositories (non-blocking)
        try {
            java.nio.file.Path dbPath = ConfigPaths.getGameDir()
                    .resolve("devmod")
                    .resolve("notifications.duckdb");
            CompletableFuture<Void> historyFuture = NotificationHistoryRepository.INSTANCE.initialize(dbPath);
            CompletableFuture<Void> prefsFuture = NotificationPreferencesRepository.INSTANCE.initialize(dbPath);
            CompletableFuture.allOf(historyFuture, prefsFuture)
                .thenRun(() -> {
                    LOGGER.info("[DevMod] Notification repositories initialized successfully");

                    // Initialize Unified Notification Center after repos are ready
                    try {
                        com.devmod.notification.NotificationService.INSTANCE.initialize();
                        LOGGER.info("[DevMod] NotificationService initialized successfully");
                    } catch (Exception e) {
                        LOGGER.error("[DevMod] Failed to initialize NotificationService", e);
                    }

                    // Register party notification bridge after notification service is ready
                    try {
                        PartyNotificationBridge.register();
                    } catch (Exception e) {
                        LOGGER.error("[DevMod] Failed to register PartyNotificationBridge", e);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("[DevMod] Failed to initialize notification repositories", ex);
                    return null;
                });
        } catch (NoClassDefFoundError | UnsatisfiedLinkError | ExceptionInInitializerError e) {
            LOGGER.error("[DevMod] DuckDB native library not available - notification repositories disabled", e);
        }
    }
}
