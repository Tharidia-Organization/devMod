package com.devmod.notification;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.devmod.actions.ActionIds;
import com.devmod.endurance.PrestigeMilestone;
import com.devmod.endurance.QuestType;
import com.devmod.endurance.RewardSystem;
import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MailboxMessage;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.template.MessageTemplateRegistry;
import com.devmod.notification.network.UnifiedNotificationPayload;
import com.devmod.notification.persistence.NotificationHistoryRepository;
import com.devmod.util.I18n;

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

    // UX Q6: Rate limiting for party death notifications (prevent spam on TPK)
    private static final long DEATH_NOTIFICATION_COOLDOWN_MS = 2000; // 2 seconds between death notifications per recipient
    private final Map<UUID, Long> lastDeathNotificationTime = new java.util.concurrent.ConcurrentHashMap<>();

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
        notifyBadgeUnlock(playerUuid, badgeName, rarity, null, null);
    }

    /**
     * Send an achievement unlock notification.
     */
    public void notifyAchievementUnlock(UUID playerUuid, RewardSystem.Achievement achievement) {
        if (achievement == null) {
            return;
        }

        String rewardDesc = achievement.rewardAmount > 0
            ? "+" + achievement.rewardAmount + " " + achievement.rewardCurrency.displayName
            : "Special reward unlocked";
        String tierId = achievement.lootTier.name().toLowerCase(Locale.ROOT);

        Notification notification = Notification.builder(NotificationCategory.ACHIEVEMENT)
            .titleKey("devmod.notification.achievement.title")
            .messageKey("devmod.notification.achievement.message")
            .param("achievement", achievement.displayName)
            .param("reward", rewardDesc)
            .param("description", achievement.description != null ? achievement.description : "")
            .priority(getPriorityForLootTier(achievement.lootTier))
            .soundId("badge." + tierId)
            .iconId("badge." + tierId)
            .persistToMailbox(true)
            .build();

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a prestige milestone notification.
     */
    public void notifyPrestigeMilestone(UUID playerUuid, PrestigeMilestone milestone) {
        if (milestone == null) {
            return;
        }

        String name = I18n.translate(milestone.getNameKey()).getString();
        String description = I18n.translate(milestone.getDescriptionKey()).getString();
        String rewardDesc = milestone.getType().displayName;
        if (milestone.getUnlockValue() != null && !milestone.getUnlockValue().isBlank()) {
            rewardDesc = rewardDesc + " (" + milestone.getUnlockValue() + ")";
        }

        Notification notification = Notification.builder(NotificationCategory.ACHIEVEMENT)
            .titleKey(milestone.getNameKey())
            .messageKey(milestone.getDescriptionKey())
            .param("achievement", name)
            .param("description", description)
            .param("reward", rewardDesc)
            .param("type", milestone.getType().displayName)
            .priority(NotificationPriority.HIGH)
            .soundId("badge.legendary")
            .iconId("badge.legendary")
            .persistToMailbox(true)
            .build();

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a badge unlock notification with optional mailbox details.
     */
    public void notifyBadgeUnlock(UUID playerUuid, String badgeName, String rarity,
                                  @Nullable String description, @Nullable String rewardDescription) {
        Notification.Builder builder = Notification.builder(NotificationCategory.ACHIEVEMENT)
                .titleKey("devmod.notification.badge_unlock.title")
                .messageKey("devmod.notification.badge_unlock.message")
                .param("badge", badgeName)
                .param("rarity", rarity)
                .priority(getPriorityForBadgeRarity(rarity))
                .soundId("badge." + rarity.toLowerCase(Locale.ROOT))
                .iconId("badge." + rarity.toLowerCase(Locale.ROOT))
                .persistToMailbox(true);

        if (description != null && !description.isBlank()) {
            builder.param("description", description);
        }
        if (rewardDescription != null && !rewardDescription.isBlank()) {
            builder.param("reward", rewardDescription);
        }

        Notification notification = builder.build();

        notifyAsync(playerUuid, notification);
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

        notifyAsync(playerUuid, builder.build());
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

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a quest reward summary notification.
     */
    public void notifyQuestRewards(UUID playerUuid, RewardSystem.QuestRewards rewards,
                                   boolean questCompleted, @Nullable String questName) {
        if (rewards == null) {
            return;
        }

        Notification.Builder builder = Notification.builder(NotificationCategory.REWARD)
            .titleKey("devmod.notification.quest_rewards.title")
            .messageKey("devmod.notification.quest_rewards.message")
            .param("tokens", String.valueOf(rewards.tokensEarned))
            .param("prestige", String.valueOf(rewards.prestigeEarned))
            .param("bloodGems", String.valueOf(rewards.bloodGemsEarned))
            .param("loot", String.valueOf(rewards.lootDrops.size()))
            .param("baseTokens", String.valueOf(rewards.baseTokens))
            .param("styleMultiplier", String.format(Locale.ROOT, "%.2f", rewards.styleMultiplier))
            .param("mutatorMultiplier", String.format(Locale.ROOT, "%.2f", rewards.mutatorMultiplier))
            .param("styleRank", rewards.styleRank != null ? rewards.styleRank.name() : "D")
            .param("activeMutators", String.valueOf(rewards.activeMutators))
            .priority(questCompleted ? NotificationPriority.HIGH : NotificationPriority.NORMAL)
            .soundId(questCompleted ? "quest.complete" : "token.gain")
            .displayDurationMs(4000)
            .persistToMailbox(false);

        if (questName != null && !questName.isBlank()) {
            builder.param("quest", questName);
        }
        if (rewards.noHitBonus) {
            builder.param("noHitBonus", "true");
        }
        if (rewards.speedBonus) {
            builder.param("speedBonus", "true");
        }
        if (rewards.achievementsUnlocked != null && !rewards.achievementsUnlocked.isEmpty()) {
            builder.param("achievements", String.valueOf(rewards.achievementsUnlocked.size()));
        }

        notifyAsync(playerUuid, builder.build());
    }

    /**
     * Send a challenge reward notification (daily or weekly).
     */
    public void notifyChallengeReward(UUID playerUuid, String cadence, String challengeName,
                                      int tokenReward, int prestigeReward) {
        if (challengeName == null || challengeName.isBlank()) {
            challengeName = "Challenge";
        }

        String cadenceKey = "weekly".equalsIgnoreCase(cadence) ? "weekly" : "daily";
        boolean isWeekly = "weekly".equalsIgnoreCase(cadence);

        Notification notification = Notification.builder(NotificationCategory.REWARD)
            .titleKey("devmod.notification.challenge." + cadenceKey + ".title")
            .messageKey("devmod.notification.challenge." + cadenceKey + ".message")
            .param("challenge", challengeName)
            .param("tokens", String.valueOf(tokenReward))
            .param("prestige", String.valueOf(prestigeReward))
            .priority(isWeekly ? NotificationPriority.HIGH : NotificationPriority.NORMAL)
            .soundId(isWeekly ? "quest.complete" : "token.gain")
            .displayDurationMs(isWeekly ? 5000 : 3500)
            .persistToMailbox(false)
            .build();

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a season tier up notification.
     */
    public void notifySeasonTierUp(UUID playerUuid, int newTier, String freeRewardName, String premiumRewardName) {
        Notification notification = Notification.builder(NotificationCategory.SEASON)
                .titleKey("devmod.notification.season_tier.title")
                .messageKey("devmod.notification.season_tier.message")
                .param("tier", String.valueOf(newTier))
                .param("freeReward", freeRewardName == null ? "" : freeRewardName)
                .param("premiumReward", premiumRewardName == null ? "" : premiumRewardName)
                .priority(NotificationPriority.HIGH)
                .soundId("season.tierup")
                .iconId("season.tier")
                .actionId(ActionIds.UI_SEASON_PASS_OPEN)
                .persistToMailbox(true)
                .build();

        notifyAsync(playerUuid, notification);
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

        notifyAsync(playerUuid, notification);
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

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a resonance tier notification.
     */
    public void notifyResonanceTier(UUID playerUuid, String announcement, int styleBonus, String tierName,
                                    int color, boolean isTrinity, boolean isApocalypse) {
        NotificationPriority priority = "APOCALYPSE".equalsIgnoreCase(tierName)
                ? NotificationPriority.HIGH
                : NotificationPriority.NORMAL;
        String soundId = "APOCALYPSE".equalsIgnoreCase(tierName) ? "resonance.max" : "resonance.chain";

        Notification notification = Notification.builder(NotificationCategory.RESONANCE)
                .titleKey("devmod.notification.resonance.title")
                .messageKey("devmod.notification.resonance.message")
                .param("announcement", announcement)
                .param("styleBonus", String.valueOf(styleBonus))
                .param("tier", tierName)
                .param("color", String.valueOf(color))
                .param("isTrinity", String.valueOf(isTrinity))
                .param("isApocalypse", String.valueOf(isApocalypse))
                .priority(priority)
                .soundId(soundId)
                .displayDurationMs(2000)
                .persistToMailbox(false)
                .build();

        notifyAsync(playerUuid, notification);
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

        notifyAsync(playerUuid, builder.build());
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

        Notification.Builder builder = Notification.builder(NotificationCategory.PARTY)
                .titleKey("devmod.notification.party." + eventType + ".title")
                .messageKey("devmod.notification.party." + eventType + ".message")
                .priority(priority)
                .soundId("party." + eventType)
                .persistToMailbox(shouldPersistPartyEvent(eventType));

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.param(entry.getKey(), entry.getValue());
            }
        }

        Notification notification = builder.build();

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a party invite notification with action metadata.
     */
    public void notifyPartyInvite(UUID playerUuid, UUID inviteId, String senderName,
                                  QuestType questType, long expiresAt) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("sender", senderName);

        PartyInviteActionData actionData = new PartyInviteActionData(
            inviteId, senderName, questType.ordinal(), expiresAt);

        Notification notification = Notification.builder(NotificationCategory.PARTY)
                .titleKey("devmod.notification.party.invite.title")
                .messageKey("devmod.notification.party.invite.message")
                .params(params)
                .priority(NotificationPriority.NORMAL)
                .soundId("party.invite")
                .actionId(ActionIds.UI_PARTY_INVITE_POPUP_OPEN)
                .actionDataJson(actionData.toJson())
                .persistToMailbox(false)
                .build();

        notifyAsync(playerUuid, notification);
    }

    private boolean shouldPersistPartyEvent(String eventType) {
        return switch (eventType) {
            case "kicked", "disbanded" -> true;
            default -> false;
        };
    }

    /**
     * UX Q4: Notify party members when a teammate dies during a quest.
     * Sends a toast notification to all OTHER party members (not the one who died).
     * UX Q6: Rate-limited to prevent notification spam on TPK scenarios.
     */
    public void notifyPartyMemberDeath(UUID deadPlayerId, String deadPlayerName, UUID partyId) {
        var party = com.devmod.party.PartyManager.INSTANCE.getParty(partyId);
        if (party == null) {
            return;
        }

        long now = System.currentTimeMillis();

        // Notify all party members except the one who died
        for (UUID memberId : party.getMembers()) {
            if (memberId.equals(deadPlayerId)) {
                continue; // Don't notify the dead player themselves
            }

            // UX Q6: Rate limiting - skip if notified too recently
            Long lastNotified = lastDeathNotificationTime.get(memberId);
            if (lastNotified != null && (now - lastNotified) < DEATH_NOTIFICATION_COOLDOWN_MS) {
                continue; // Skip - too soon since last death notification
            }
            lastDeathNotificationTime.put(memberId, now);

            Notification notification = Notification.builder(NotificationCategory.PARTY)
                    .titleKey("devmod.notification.party.member_death.title")
                    .messageKey("devmod.notification.party.member_death.message")
                    .param("player", deadPlayerName)
                    .priority(NotificationPriority.HIGH)
                    .soundId("party.member_death")
                    .persistToMailbox(false)
                    .build();

            notifyAsync(memberId, notification);
        }
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

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a mailbox notification when a new message arrives.
     */
    public void notifyMailboxMessage(UUID playerUuid, MailboxMessage message, int unreadCount) {
        if (message == null) {
            return;
        }

        String sender = message.senderName() != null ? message.senderName() : "System";
        NotificationPriority priority = switch (message.messageType()) {
            case ADMIN -> NotificationPriority.URGENT;
            case REWARD -> NotificationPriority.HIGH;
            default -> NotificationPriority.NORMAL;
        };

        Notification notification = Notification.builder(NotificationCategory.MAILBOX)
                .titleKey("devmod.notification.mailbox.title")
                .messageKey("devmod.notification.mailbox.message")
                .param("sender", sender)
                .param("subject", message.subject())
                .param("unread", String.valueOf(unreadCount))
                .priority(priority)
                .soundId("mailbox.new")
                .actionId(ActionIds.UI_NOTIFICATION_CENTER_OPEN)
                .actionDataJson(NotificationCenterActionData.forTab("MAILBOX", message.id()).toJson())
                .persistToMailbox(false)
                .build();

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a news notification to a player.
     */
    public void notifyNewsArticle(UUID playerUuid, NewsArticle article) {
        if (article == null) {
            return;
        }

        NotificationPriority priority = article.priority() >= 3
                ? NotificationPriority.URGENT
                : article.priority() >= 2
                ? NotificationPriority.HIGH
                : NotificationPriority.NORMAL;

        Notification notification = Notification.builder(NotificationCategory.NEWS)
                .titleKey("devmod.notification.news.title")
                .messageKey("devmod.notification.news.message")
                .param("title", article.title())
                .param("category", article.category().getDisplayName())
                .priority(priority)
                .soundId("news.new")
                .actionId(ActionIds.UI_NOTIFICATION_CENTER_OPEN)
                .actionDataJson(NotificationCenterActionData.forTab("NEWS", article.id()).toJson())
                .persistToMailbox(false)
                .build();

        notifyAsync(playerUuid, notification);
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

        notifyAsync(playerUuid, builder.build());
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
        notifyWaveComplete(playerUuid, waveNumber, tokensEarned, styleRank, maxCombo,
            isFlawless, hasMoreWaves, null, null, null, null);
    }

    /**
     * Send a detailed wave complete notification with optional stats.
     */
    public void notifyWaveComplete(UUID playerUuid, int waveNumber, int tokensEarned,
                                    String styleRank, int maxCombo, boolean isFlawless,
                                    boolean hasMoreWaves,
                                    @Nullable Integer kills,
                                    @Nullable Float damageDealt,
                                    @Nullable Float damageTaken,
                                    @Nullable RewardSystem.WaveReward rewardBreakdown) {

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
                .persistToMailbox(true);

        if (isFlawless) {
            builder.param("flawless", "true");
        }
        if (kills != null) {
            builder.param("kills", String.valueOf(kills));
        }
        if (damageDealt != null) {
            builder.param("damage", String.format(Locale.ROOT, "%.0f", damageDealt));
        }
        if (damageTaken != null) {
            builder.param("damageTaken", String.format(Locale.ROOT, "%.0f", damageTaken));
        }
        if (rewardBreakdown != null) {
            builder.param("baseTokens", String.valueOf(rewardBreakdown.baseTokens()));
            builder.param("styleMultiplier", String.format(Locale.ROOT, "%.2f", rewardBreakdown.styleMultiplier()));
            builder.param("mutatorMultiplier", String.format(Locale.ROOT, "%.2f", rewardBreakdown.mutatorMultiplier()));
            builder.param("directiveMultiplier", String.format(Locale.ROOT, "%.2f", rewardBreakdown.directiveMultiplier()));
            builder.param("bonusTokens", String.valueOf(rewardBreakdown.bonusPoints()));
        }

        // Action to trigger WaveCheckpointScreen opening
        if (hasMoreWaves) {
            builder.actionId(ActionIds.UI_WAVE_CHECKPOINT_OPEN);
        }

        notifyAsync(playerUuid, builder.build());
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

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a directive chain completion notification.
     */
    public void notifyChainComplete(UUID playerUuid, String chainName, int bonusTokens, int bonusPrestige) {
        Notification notification = Notification.builder(NotificationCategory.QUEST)
            .titleKey("devmod.notification.chain.complete_title")
            .messageKey("devmod.notification.chain.complete_message")
            .param("chain", chainName)
            .param("tokens", String.valueOf(bonusTokens))
            .param("prestige", String.valueOf(bonusPrestige))
            .priority(NotificationPriority.HIGH)
            .soundId("chain.complete")
            .displayDurationMs(5000)
            .persistToMailbox(false)
            .build();

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a directive chain failure notification.
     */
    public void notifyChainFailed(UUID playerUuid, String chainName) {
        Notification notification = Notification.builder(NotificationCategory.QUEST)
            .titleKey("devmod.notification.chain.failed_title")
            .messageKey("devmod.notification.chain.failed_message")
            .param("chain", chainName)
            .priority(NotificationPriority.NORMAL)
            .soundId("chain.failed")
            .displayDurationMs(3500)
            .persistToMailbox(false)
            .build();

        notifyAsync(playerUuid, notification);
    }

    /**
     * Send a directive chain progress notification.
     */
    public void notifyChainProgress(UUID playerUuid, String chainName, int step, int totalSteps) {
        Notification notification = Notification.builder(NotificationCategory.QUEST)
            .titleKey("devmod.notification.chain.step_title")
            .messageKey("devmod.notification.chain.step_message")
            .param("chain", chainName)
            .param("step", String.valueOf(step))
            .param("total", String.valueOf(totalSteps))
            .priority(NotificationPriority.LOW)
            .soundId("chain.step")
            .displayDurationMs(2500)
            .persistToMailbox(false)
            .build();

        notifyAsync(playerUuid, notification);
    }

    // ============================================================================
    // INTERNAL DELIVERY
    // ============================================================================

    private void deliverNotification(UUID playerUuid, Notification notification) {
        // Route the notification
        NotificationRouter.RoutingDecision decision = router.route(playerUuid, notification);

        if (decision.hasAnyDelivery()) {
            CompletableFuture<?> future = NotificationHistoryRepository.INSTANCE.save(playerUuid, notification)
                .handle((result, ex) -> {
                    if (ex != null) {
                        LOGGER.warn("[NotificationService] Failed to save notification history for {}: {}",
                            playerUuid, ex.getMessage());
                    }
                    return null;
                });
            if (future.isDone() && LOGGER.isTraceEnabled()) {
                LOGGER.trace("[NotificationService] History saved immediately for {}", playerUuid);
            }
        }

        // Send overlay if player is online and overlay is enabled
        if (decision.sendOverlay()) {
            sendOverlayPacket(playerUuid, notification);
        }

        // Persist to mailbox if configured
        if (decision.sendMailbox()) {
            persistToMailbox(playerUuid, notification);
        }

        // Send to chat if configured
        if (decision.sendChat()) {
            sendChatMessage(playerUuid, notification);
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

        ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerUuid));
        if (player == null) return;

        try {
            UnifiedNotificationPayload payload = UnifiedNotificationPayload.from(notification);
            PacketDistributor.sendToPlayer(Objects.requireNonNull(player), Objects.requireNonNull(payload));
        } catch (Exception e) {
            LOGGER.warn("[NotificationService] Failed to send overlay packet to {}: {}",
                    playerUuid, e.getMessage());
        }
    }

    private void sendChatMessage(UUID playerUuid, Notification notification) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerUuid));
        if (player == null) return;

        Component message = buildChatComponent(notification);
        if (message != null) {
            player.sendSystemMessage(message);
        }
    }

    private void persistToMailbox(UUID playerUuid, Notification notification) {
        // Integration with MailboxManager for offline delivery
        try {
            if (notification.category() == NotificationCategory.PARTY
                    && persistPartyMailbox(playerUuid, notification)) {
                return;
            }
            if ((notification.category() == NotificationCategory.ACHIEVEMENT
                    || notification.category() == NotificationCategory.RECORD)
                    && persistAchievementMailbox(playerUuid, notification)) {
                return;
            }
            // Generate subject from notification data
            String subject = formatMailboxSubject(notification);
            String body = formatMailboxBody(notification);

            // Determine expiration based on priority
            Duration expiresIn = getMailboxExpiration(notification.priority());

            // Send to mailbox asynchronously
            MailboxManager.INSTANCE.sendSystemMessage(
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

    @Nullable
    private Component buildChatComponent(Notification notification) {
        Object[] args = notification.params().values().toArray(new Object[0]);
        String messageKey = notification.messageKey();
        if (messageKey != null && !messageKey.isBlank()) {
            return Component.translatable(Objects.requireNonNull(messageKey), Objects.requireNonNull(args));
        }
        String titleKey = notification.titleKey();
        if (titleKey != null && !titleKey.isBlank()) {
            return Component.translatable(Objects.requireNonNull(titleKey), Objects.requireNonNull(args));
        }
        return null;
    }

    private boolean persistPartyMailbox(UUID playerUuid, Notification notification) {
        String titleKey = notification.titleKey();
        if (titleKey == null) {
            return false;
        }

        String leaderName = notification.params().values().stream().findFirst().orElse("Player");
        if (titleKey.endsWith(".kicked.title")) {
            sendPartyMailboxTemplate(playerUuid, "Kicked from Party",
                "You were kicked from the party by " + leaderName + ".",
                "You can join or create a new party anytime.");
            return true;
        }

        if (titleKey.endsWith(".disbanded.title")) {
            sendPartyMailboxTemplate(playerUuid, "Party Disbanded",
                "The party was disbanded by " + leaderName + ".",
                "You can join or create a new party anytime.");
            return true;
        }

        return false;
    }

    private boolean persistAchievementMailbox(UUID playerUuid, Notification notification) {
        try {
            String playerName = resolvePlayerName(playerUuid);
            String name = firstNonBlank(
                notification.getParam("achievement"),
                notification.getParam("badge"),
                notification.getParam("type")
            );

            String description = notification.getParam("description");
            String reward = notification.getParam("reward");

            if (notification.category() == NotificationCategory.RECORD) {
                String recordType = notification.getParam("type");
                if (recordType != null && !recordType.isBlank()) {
                    name = "New Personal Record: " + recordType;
                }
                if (description == null || description.isBlank()) {
                    description = "You've set a new personal best!";
                }
                if (reward == null || reward.isBlank()) {
                    reward = notification.getParam("value");
                }
            }

            if (name == null || name.isBlank()) {
                name = notification.category() == NotificationCategory.RECORD ? "New Record" : "Achievement";
            }
            if (description == null || description.isBlank()) {
                description = "Achievement unlocked.";
            }
            if (reward == null || reward.isBlank()) {
                reward = "-";
            }

            MessageTemplateRegistry.INSTANCE.sendFromTemplate(
                "reward.achievement",
                playerUuid,
                Map.of(
                    "player_name", playerName,
                    "achievement_name", name,
                    "achievement_description", description,
                    "reward_description", reward
                ),
                null
            ).exceptionally(ex -> {
                LOGGER.warn("[NotificationService] Achievement mailbox template failed for {}: {}",
                    playerUuid, ex.getMessage());
                return Optional.empty();
            });
            return true;
        } catch (Exception e) {
            LOGGER.warn("[NotificationService] Achievement mailbox template failed for {}: {}",
                playerUuid, e.getMessage());
            return false;
        }
    }

    @Nullable
    private String firstNonBlank(@Nullable String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void sendPartyMailboxTemplate(UUID playerUuid, String updateType, String details, String actionHint) {
        try {
            String recipientName = resolvePlayerName(playerUuid);
            MessageTemplateRegistry.INSTANCE.sendFromTemplate(
                "social.party_update",
                playerUuid,
                Map.of(
                    "player_name", recipientName,
                    "update_type", updateType,
                    "details", details,
                    "action_hint", actionHint
                ),
                null
            ).exceptionally(ex -> {
                LOGGER.warn("[NotificationService] Party mailbox template failed for {}: {}",
                    playerUuid, ex.getMessage());
                return Optional.empty();
            });
        } catch (Exception e) {
            LOGGER.warn("[NotificationService] Party mailbox template failed for {}: {}",
                playerUuid, e.getMessage());
        }
    }

    private String resolvePlayerName(UUID playerUuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerUuid));
            if (player != null) {
                return player.getName().getString();
            }
        }
        return "Player";
    }

    /**
     * Format notification subject for mailbox.
     */
    private String formatMailboxSubject(Notification notification) {
        // Use a readable prefix based on category
        String prefix = switch (notification.category()) {
            case ACHIEVEMENT -> "🏆 Achievement";
            case RECORD -> "🎯 Record";
            case SEASON -> "⭐ Season";
            case TOKEN -> "💰 Tokens";
            case REWARD -> "🎁 Reward";
            case PARTY -> "👥 Party";
            case QUEST -> "⚔️ Quest";
            case COMBAT -> "⚔️ Combat";
            case RESONANCE -> "✨ Resonance";
            case MAILBOX -> "📬 Mailbox";
            case NEWS -> "📰 News";
            case ADMIN -> "⚠️ Admin";
            case SYSTEM -> "ℹ️ System";
        };

        // Append key info from params if available
        Map<String, String> params = notification.params();
        if (params != null && !params.isEmpty()) {
            if (params.containsKey("achievement")) {
                return prefix + ": " + params.get("achievement");
            } else if (params.containsKey("badge")) {
                return prefix + ": " + params.get("badge");
            } else if (params.containsKey("tier")) {
                return prefix + ": Tier " + params.get("tier");
            } else if (params.containsKey("type")) {
                return prefix + ": " + params.get("type");
            } else if (params.containsKey("amount")) {
                return prefix + ": +" + params.get("amount");
            } else if (params.containsKey("wave")) {
                return prefix + ": Wave " + params.get("wave");
            } else if (params.containsKey("quest")) {
                return prefix + ": " + params.get("quest");
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
    private void notifyAsync(UUID playerUuid, Notification notification) {
        fireAndForget(notify(playerUuid, notification), playerUuid);
    }

    /**
     * Explicitly consume a future for fire-and-forget operations.
     * Logs errors but doesn't block on completion.
     */
    private void fireAndForget(CompletableFuture<?> future, UUID context) {
        CompletableFuture<?> handled = Objects.requireNonNull(future).handle((result, ex) -> {
            if (ex != null) {
                LOGGER.warn("[NotificationService] Async operation failed for {}: {}",
                        context, ex.getMessage());
            }
            return null;
        });
        // Explicitly consume to indicate fire-and-forget intention
        if (handled.isDone() && LOGGER.isTraceEnabled()) {
            LOGGER.trace("[NotificationService] Immediate completion for {}", context);
        }
    }

    private NotificationPriority getPriorityForBadgeRarity(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "legendary", "mythic" -> NotificationPriority.URGENT;
            case "epic" -> NotificationPriority.HIGH;
            case "rare" -> NotificationPriority.NORMAL;
            default -> NotificationPriority.NORMAL;
        };
    }

    private NotificationPriority getPriorityForLootTier(RewardSystem.LootTier tier) {
        if (tier == null) {
            return NotificationPriority.NORMAL;
        }
        return switch (tier) {
            case LEGENDARY, MYTHIC -> NotificationPriority.URGENT;
            case EPIC -> NotificationPriority.HIGH;
            case RARE -> NotificationPriority.NORMAL;
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
