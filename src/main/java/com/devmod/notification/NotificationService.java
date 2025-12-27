package com.devmod.notification;

import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.devmod.mailbox.MailboxManager;
import com.devmod.notification.network.UnifiedNotificationPayload;

/**
 * Central server-side entry point for the Unified Notification Center.
 *
 * <p>All notification sources should route through this service rather than
 * sending packets directly. This enables:
 * <ul>
 *   <li>Unified routing logic (overlay, mailbox, chat)</li>
 *   <li>User preference checking</li>
 *   <li>Notification history persistence</li>
 *   <li>Offline delivery via mailbox</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * NotificationService.INSTANCE.notify(playerUuid, Notification.builder(NotificationCategory.ACHIEVEMENT)
 *     .titleKey("devmod.notification.badge_unlock.title")
 *     .messageKey("devmod.notification.badge_unlock.message")
 *     .param("badge", badgeName)
 *     .priority(NotificationPriority.HIGH)
 *     .soundId("badge.unlock")
 *     .persistToMailbox(true)
 *     .build());
 * </pre>
 */
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    public static final NotificationService INSTANCE = new NotificationService();

    private final NotificationRouter router;
    private volatile boolean initialized = false;

    private NotificationService() {
        this.router = new NotificationRouter();
    }

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    /**
     * Initialize the notification service.
     * Call this when the server starts, after MailboxManager is initialized.
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        LOGGER.info("[NotificationService] Initializing unified notification center...");
        initialized = true;
        LOGGER.info("[NotificationService] Initialized successfully");
    }

    /**
     * Shutdown the notification service.
     * Call this when the server stops.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }
        LOGGER.info("[NotificationService] Shutting down...");
        initialized = false;
    }

    // ============================================================================
    // CORE API
    // ============================================================================

    /**
     * Send a notification to a specific player.
     *
     * @param playerUuid The target player's UUID
     * @param notification The notification to send
     * @return A future that completes when all delivery is done
     */
    public CompletableFuture<Void> notify(UUID playerUuid, Notification notification) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(notification, "notification");

        return CompletableFuture.runAsync(() -> {
            try {
                deliverNotification(playerUuid, notification);
            } catch (Exception e) {
                LOGGER.warn("[NotificationService] Failed to deliver notification to {}: {}",
                        playerUuid, e.getMessage());
            }
        });
    }

    /**
     * Send a notification to multiple players.
     *
     * @param playerUuids The target players' UUIDs
     * @param notification The notification to send
     * @return A future that completes when all deliveries are done
     */
    public CompletableFuture<Void> notifyAll(Collection<UUID> playerUuids, Notification notification) {
        Objects.requireNonNull(playerUuids, "playerUuids");
        Objects.requireNonNull(notification, "notification");

        CompletableFuture<?>[] futures = playerUuids.stream()
                .map(uuid -> notify(uuid, notification))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    /**
     * Broadcast a notification to all online players.
     *
     * @param notification The notification to broadcast
     * @return A future that completes when all deliveries are done
     */
    public CompletableFuture<Void> broadcast(Notification notification) {
        Objects.requireNonNull(notification, "notification");

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }

        return notifyAll(
                server.getPlayerList().getPlayers().stream()
                        .map(p -> p.getUUID())
                        .toList(),
                notification
        );
    }

    // ============================================================================
    // CONVENIENCE METHODS (backwards compatibility adapters)
    // ============================================================================

    /**
     * Send a badge unlock notification.
     */
    public void notifyBadgeUnlock(UUID playerUuid, String badgeName, String rarity) {
        Notification notification = Notification.builder(NotificationCategory.ACHIEVEMENT)
                .titleKey("devmod.notification.badge_unlock.title")
                .messageKey("devmod.notification.badge_unlock.message")
                .param("badge", badgeName)
                .param("rarity", rarity)
                .priority(getPriorityForBadgeRarity(rarity))
                .soundId("badge." + rarity.toLowerCase(Locale.ROOT))
                .iconId("badge." + rarity.toLowerCase(Locale.ROOT))
                .persistToMailbox(true)
                .build();

        var unused = notifyAsync(playerUuid, notification);
    }

    /**
     * Send a token gain notification.
     */
    public void notifyTokenGain(UUID playerUuid, int amount, @Nullable String source) {
        if (amount <= 0) return;

        Notification.Builder builder = Notification.builder(NotificationCategory.TOKEN)
                .titleKey("devmod.notification.token_gain.title")
                .messageKey("devmod.notification.token_gain.message")
                .param("amount", String.valueOf(amount))
                .priority(NotificationPriority.NORMAL)
                .soundId("token.gain")
                .persistToMailbox(false);

        if (source != null) {
            builder.param("source", source);
        }

        var unused = notifyAsync(playerUuid, builder.build());
    }

    /**
     * Send a personal record notification.
     */
    public void notifyRecord(UUID playerUuid, String recordType, String recordValue) {
        Notification notification = Notification.builder(NotificationCategory.RECORD)
                .titleKey("devmod.notification.record.title")
                .messageKey("devmod.notification.record.message")
                .param("type", recordType)
                .param("value", recordValue)
                .priority(NotificationPriority.URGENT)
                .soundId("record.new")
                .iconId("record")
                .persistToMailbox(true)
                .build();

        var unused = notifyAsync(playerUuid, notification);
    }

    /**
     * Send a season tier up notification.
     */
    public void notifySeasonTierUp(UUID playerUuid, int newTier, String tierName, int xpGained) {
        Notification notification = Notification.builder(NotificationCategory.SEASON)
                .titleKey("devmod.notification.season_tier.title")
                .messageKey("devmod.notification.season_tier.message")
                .param("tier", String.valueOf(newTier))
                .param("tierName", tierName)
                .param("xp", String.valueOf(xpGained))
                .priority(NotificationPriority.HIGH)
                .soundId("season.tierup")
                .iconId("season.tier")
                .persistToMailbox(true)
                .build();

        var unused = notifyAsync(playerUuid, notification);
    }

    /**
     * Send a combo decay notification.
     */
    public void notifyComboDecay(UUID playerUuid, int lostCombo, int previousRank, int newRank) {
        if (lostCombo < 3 && newRank >= previousRank) return;

        Notification notification = Notification.builder(NotificationCategory.COMBAT)
                .titleKey("devmod.notification.combo_decay.title")
                .messageKey("devmod.notification.combo_decay.message")
                .param("lost", String.valueOf(lostCombo))
                .param("previousRank", String.valueOf(previousRank))
                .param("newRank", String.valueOf(newRank))
                .priority(NotificationPriority.LOW)
                .displayDurationMs(1200)
                .persistToMailbox(false)
                .showOverlay(true)
                .build();

        var unused = notifyAsync(playerUuid, notification);
    }

    /**
     * Send a resonance chain notification.
     */
    public void notifyResonance(UUID playerUuid, int chainLength, float damageMultiplier) {
        Notification notification = Notification.builder(NotificationCategory.RESONANCE)
                .titleKey("devmod.notification.resonance.title")
                .messageKey("devmod.notification.resonance.message")
                .param("chain", String.valueOf(chainLength))
                .param("multiplier", String.format("%.1fx", damageMultiplier))
                .priority(NotificationPriority.NORMAL)
                .soundId("resonance.chain")
                .displayDurationMs(2000)
                .persistToMailbox(false)
                .build();

        var unused = notifyAsync(playerUuid, notification);
    }

    /**
     * Send a quest event notification.
     */
    public void notifyQuestEvent(UUID playerUuid, String eventKey, Map<String, String> params,
                                  NotificationPriority priority) {
        Notification.Builder builder = Notification.builder(NotificationCategory.QUEST)
                .titleKey("devmod.notification.quest." + eventKey + ".title")
                .messageKey("devmod.notification.quest." + eventKey + ".message")
                .priority(priority)
                .persistToMailbox(priority.isAtLeast(NotificationPriority.HIGH));

        params.forEach(builder::param);

        var unused = notifyAsync(playerUuid, builder.build());
    }

    /**
     * Send a party notification.
     */
    public void notifyParty(UUID playerUuid, String eventType, Map<String, String> params) {
        NotificationPriority priority = switch (eventType) {
            case "kicked", "disbanded" -> NotificationPriority.HIGH;
            case "invite" -> NotificationPriority.NORMAL;
            default -> NotificationPriority.NORMAL;
        };

        Notification notification = Notification.builder(NotificationCategory.PARTY)
                .titleKey("devmod.notification.party." + eventType + ".title")
                .messageKey("devmod.notification.party." + eventType + ".message")
                .params(params)
                .priority(priority)
                .soundId("party." + eventType)
                .persistToMailbox(true)
                .build();

        var unused = notifyAsync(playerUuid, notification);
    }

    /**
     * Send an admin alert notification.
     */
    public void notifyAdmin(UUID playerUuid, String alertType, String message, NotificationPriority priority) {
        Notification notification = Notification.builder(NotificationCategory.ADMIN)
                .titleKey("devmod.notification.admin." + alertType + ".title")
                .messageKey("devmod.notification.admin.message")
                .param("message", message)
                .priority(priority)
                .soundId("admin.alert")
                .persistToMailbox(true)
                .build();

        var unused = notifyAsync(playerUuid, notification);
    }

    // ============================================================================
    // WAVE NOTIFICATIONS (Endurance Quest)
    // ============================================================================

    /**
     * Send a wave start notification.
     * Replaces the chat spam with a brief overlay toast.
     *
     * @param playerUuid Target player
     * @param waveNumber Current wave number
     * @param totalWaves Total waves (0 for endless mode)
     * @param enemyCount Number of enemies
     * @param enemyType Display name of enemy type
     * @param isBossWave Whether this is a boss wave
     * @param objective Optional objective description
     * @param directive Optional directive info (name + multiplier)
     */
    public void notifyWaveStart(UUID playerUuid, int waveNumber, int totalWaves, int enemyCount,
                                 String enemyType, boolean isBossWave,
                                 @Nullable String objective, @Nullable String directive) {

        Notification.Builder builder = Notification.builder(NotificationCategory.QUEST)
                .param("wave", String.valueOf(waveNumber))
                .param("enemyCount", String.valueOf(enemyCount))
                .param("enemyType", enemyType)
                .displayDurationMs(3500)
                .showOverlay(true)
                .persistToMailbox(false);

        if (isBossWave) {
            builder.titleKey("devmod.notification.wave.boss_title")
                   .messageKey("devmod.notification.wave.boss_message")
                   .priority(NotificationPriority.HIGH)
                   .soundId("wave.boss_start")
                   .iconId("boss");
        } else {
            builder.titleKey("devmod.notification.wave.start_title")
                   .messageKey("devmod.notification.wave.start_message")
                   .priority(NotificationPriority.NORMAL)
                   .soundId("wave.start");

            if (totalWaves > 0) {
                builder.param("totalWaves", String.valueOf(totalWaves));
            }
        }

        if (objective != null && !objective.isBlank()) {
            builder.param("objective", objective);
        }
        if (directive != null && !directive.isBlank()) {
            builder.param("directive", directive);
        }

        var unused = notifyAsync(playerUuid, builder.build());
    }

    /**
     * Send a wave complete notification.
     * Also triggers the WaveCheckpointScreen to open on client.
     *
     * @param playerUuid Target player
     * @param waveNumber Completed wave number
     * @param tokensEarned Tokens earned this wave
     * @param styleRank Style rank achieved (S/A/B/C/D)
     * @param maxCombo Maximum combo achieved
     * @param isFlawless True if no damage was taken
     * @param hasMoreWaves True if there are more waves to continue
     */
    public void notifyWaveComplete(UUID playerUuid, int waveNumber, int tokensEarned,
                                    String styleRank, int maxCombo, boolean isFlawless,
                                    boolean hasMoreWaves) {

        Notification.Builder builder = Notification.builder(NotificationCategory.QUEST)
                .titleKey("devmod.notification.wave.complete_title")
                .messageKey("devmod.notification.wave.complete_message")
                .param("wave", String.valueOf(waveNumber))
                .param("tokens", String.valueOf(tokensEarned))
                .param("styleRank", styleRank)
                .param("maxCombo", String.valueOf(maxCombo))
                .priority(NotificationPriority.HIGH)
                .soundId("wave.complete")
                .displayDurationMs(2500)
                .showOverlay(true)
                .persistToMailbox(false);  // Detailed stats available in WaveCheckpointScreen

        if (isFlawless) {
            builder.param("flawless", "true");
        }

        // Action to trigger WaveCheckpointScreen opening
        if (hasMoreWaves) {
            builder.actionId("open_wave_checkpoint");
        }

        var unused = notifyAsync(playerUuid, builder.build());
    }

    /**
     * Send a directive chain offer notification.
     *
     * @param playerUuid Target player
     * @param chainNames List of available chain names
     * @param waveNumber Current wave when offer is made
     */
    public void notifyChainOffer(UUID playerUuid, java.util.List<String> chainNames, int waveNumber) {
        if (chainNames.isEmpty()) return;

        String chainsText = String.join(", ", chainNames);

        Notification notification = Notification.builder(NotificationCategory.QUEST)
                .titleKey("devmod.notification.chain.offer_title")
                .messageKey("devmod.notification.chain.offer_message")
                .param("chains", chainsText)
                .param("count", String.valueOf(chainNames.size()))
                .param("wave", String.valueOf(waveNumber))
                .priority(NotificationPriority.NORMAL)
                .soundId("chain.offer")
                .displayDurationMs(5000)
                .showOverlay(true)
                .persistToMailbox(false)
                .actionId("open_chain_selection")
                .build();

        var unused = notifyAsync(playerUuid, notification);
    }

    // ============================================================================
    // INTERNAL DELIVERY
    // ============================================================================

    private void deliverNotification(UUID playerUuid, Notification notification) {
        // Route the notification
        NotificationRouter.RoutingDecision decision = router.route(playerUuid, notification);

        // Send overlay if player is online and overlay is enabled
        if (decision.sendOverlay()) {
            sendOverlayPacket(playerUuid, notification);
        }

        // Persist to mailbox if configured
        if (decision.sendMailbox()) {
            persistToMailbox(playerUuid, notification);
        }

        // Log the delivery
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[NotificationService] Delivered {} notification to {}: overlay={}, mailbox={}",
                    notification.category(), playerUuid, decision.sendOverlay(), decision.sendMailbox());
        }
    }

    private void sendOverlayPacket(UUID playerUuid, Notification notification) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;

        try {
            UnifiedNotificationPayload payload = UnifiedNotificationPayload.from(notification);
            PacketDistributor.sendToPlayer(player, payload);
        } catch (Exception e) {
            LOGGER.warn("[NotificationService] Failed to send overlay packet to {}: {}",
                    playerUuid, e.getMessage());
        }
    }

    private void persistToMailbox(UUID playerUuid, Notification notification) {
        // Integration with MailboxManager for offline delivery
        try {
            // Generate subject from notification data
            String subject = formatMailboxSubject(notification);
            String body = formatMailboxBody(notification);

            // Determine expiration based on priority
            Duration expiresIn = getMailboxExpiration(notification.priority());

            // Send to mailbox asynchronously
            var unused = MailboxManager.INSTANCE.sendSystemMessage(
                    playerUuid,
                    subject,
                    body,
                    null, // No attachment
                    expiresIn
            ).exceptionally(ex -> {
                LOGGER.warn("[NotificationService] Mailbox delivery failed for {}: {}",
                        playerUuid, ex.getMessage());
                return null;
            });

            LOGGER.debug("[NotificationService] Persisted to mailbox for {}: {}",
                    playerUuid, notification.category());
        } catch (Exception e) {
            LOGGER.warn("[NotificationService] Failed to persist to mailbox for {}: {}",
                    playerUuid, e.getMessage());
        }
    }

    /**
     * Format notification subject for mailbox.
     */
    private String formatMailboxSubject(Notification notification) {
        // Use a readable prefix based on category
        String prefix = switch (notification.category()) {
            case ACHIEVEMENT -> "🏆 Badge";
            case RECORD -> "🎯 Record";
            case SEASON -> "⭐ Season";
            case TOKEN -> "💰 Tokens";
            case REWARD -> "🎁 Reward";
            case PARTY -> "👥 Party";
            case QUEST -> "⚔️ Quest";
            case COMBAT -> "⚔️ Combat";
            case RESONANCE -> "✨ Resonance";
            case MAILBOX -> "📬 Mailbox";
            case ADMIN -> "⚠️ Admin";
            case SYSTEM -> "ℹ️ System";
        };

        // Append key info from params if available
        Map<String, String> params = notification.params();
        if (params != null && !params.isEmpty()) {
            if (params.containsKey("badge")) {
                return prefix + ": " + params.get("badge");
            } else if (params.containsKey("tier")) {
                return prefix + ": Tier " + params.get("tier");
            } else if (params.containsKey("type")) {
                return prefix + ": " + params.get("type");
            } else if (params.containsKey("amount")) {
                return prefix + ": +" + params.get("amount");
            }
        }

        return prefix;
    }

    /**
     * Format notification body for mailbox.
     */
    private String formatMailboxBody(Notification notification) {
        StringBuilder body = new StringBuilder();

        // Add params as body content
        Map<String, String> params = notification.params();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                // Capitalize first letter of key
                String formattedKey = key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
                body.append(formattedKey).append(": ").append(value).append("\n");
            }
        }

        // Add timestamp info
        body.append("\nReceived at: ").append(notification.createdAt().toString());

        return body.toString();
    }

    /**
     * Get mailbox expiration duration based on priority.
     */
    private Duration getMailboxExpiration(NotificationPriority priority) {
        return switch (priority) {
            case LOW -> Duration.ofDays(3);
            case NORMAL -> Duration.ofDays(7);
            case HIGH -> Duration.ofDays(14);
            case URGENT -> Duration.ofDays(30);
            case CRITICAL -> Duration.ofDays(60);
        };
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    /**
     * Fire-and-forget notification sending.
     * Logs any errors but doesn't propagate them.
     */
    private CompletableFuture<Void> notifyAsync(UUID playerUuid, Notification notification) {
        return notify(playerUuid, notification).exceptionally(ex -> {
            LOGGER.warn("[NotificationService] Async notification failed for {}: {}",
                    playerUuid, ex.getMessage());
            return null;
        });
    }

    private NotificationPriority getPriorityForBadgeRarity(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "legendary", "mythic" -> NotificationPriority.URGENT;
            case "epic" -> NotificationPriority.HIGH;
            case "rare" -> NotificationPriority.NORMAL;
            default -> NotificationPriority.NORMAL;
        };
    }

    /**
     * Check if the service is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
}
