package com.devmod.mailbox;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.devmod.util.ConfigPaths;

/**
 * Configuration settings for the mailbox system.
 * These can be adjusted via the admin panel or config files.
 */
public class MailboxConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "mailbox_config.json";

    // ============================================================================
    // SINGLETON
    // ============================================================================

    public static final MailboxConfig INSTANCE = new MailboxConfig();

    private MailboxConfig() {}

    @Nullable
    private Path dataDirectory;

    // ============================================================================
    // MESSAGE LIMITS
    // ============================================================================

    /** Maximum number of messages a player can have in their inbox */
    private int maxMessagesPerPlayer = 100;

    /** Maximum length of message subject */
    private int maxSubjectLength = 128;

    /** Maximum length of message body */
    private int maxBodyLength = 2000;

    /** Default message expiration time (30 days) */
    private Duration defaultMessageTtl = Duration.ofDays(30);

    /** Maximum attachments per message */
    private int maxAttachmentsPerMessage = 5;

    // ============================================================================
    // RATE LIMITING
    // ============================================================================

    /** Maximum messages a player can send per minute */
    private int maxMessagesPerMinute = 10;

    /** Maximum messages a player can send per day (0 = disabled) */
    private int maxMessagesPerDay = 0;

    /** Maximum messages a player can send to the same recipient per day (0 = disabled) */
    private int maxMessagesPerRecipientPerDay = 0;

    /** Cooldown between sending messages (seconds) */
    private int sendCooldownSeconds = 5;

    // ============================================================================
    // BROADCAST SETTINGS
    // ============================================================================

    /** Number of recipients per broadcast batch */
    private int broadcastBatchSize = 500;

    /** Delay between broadcast batches (milliseconds) */
    private int broadcastBatchDelayMs = 0;

    /** Whether broadcast messages should be queued */
    private boolean broadcastQueueEnabled = false;

    /** Minimum recipient count to queue broadcasts */
    private int broadcastQueueThreshold = 1000;

    // ============================================================================
    // DELIVERY SETTINGS
    // ============================================================================

    /** Whether the delivery dispatcher should run */
    private boolean deliveryDispatchEnabled = true;

    /** Whether to attempt immediate delivery on enqueue */
    private boolean deliveryImmediateDispatch = true;

    /** Dispatch interval in seconds */
    private int deliveryDispatchIntervalSeconds = 5;

    /** Max delivery jobs per dispatch pass */
    private int deliveryDispatchBatchSize = 50;

    /** Max delivery attempts before failing */
    private int deliveryMaxAttempts = 3;

    /** Base delay between retries in seconds */
    private int deliveryRetryDelaySeconds = 30;

    /** Max delay between retries in seconds */
    private int deliveryRetryMaxDelaySeconds = 3600;

    /** Exponential backoff multiplier for retries */
    private double deliveryRetryBackoffMultiplier = 2.0;

    /** Jitter ratio applied to retry delays */
    private double deliveryRetryJitterRatio = 0.2;

    /** Whether to send recall messages on final failure */
    private boolean deliveryRecallEnabled = true;

    // ============================================================================
    // CONTENT FILTER
    // ============================================================================

    /** Whether content filtering is enabled */
    private boolean contentFilterEnabled = true;

    /** Action when content is blocked (BLOCK, FLAG, CENSOR) */
    private String contentFilterAction = "BLOCK";

    /** Prohibited words for content filter */
    private List<String> contentFilterWords = new ArrayList<>();

    /** Prohibited regex patterns for content filter */
    private List<String> contentFilterPatterns = new ArrayList<>();

    // ============================================================================
    // PLAYER MESSAGING
    // ============================================================================

    /** Whether players can send messages to each other */
    private boolean playerToPlayerEnabled = true;

    /** Minimum player level required to send messages */
    private int minLevelToSend = 0;

    /** Whether players can attach items to messages */
    private boolean itemAttachmentsEnabled = true;

    /** Whether players can attach currency to messages */
    private boolean currencyAttachmentsEnabled = true;

    // ============================================================================
    // ATTACHMENT RULES
    // ============================================================================

    /** Whether item attachments require whitelist membership */
    private boolean itemAttachmentWhitelistEnabled = false;

    /** Items explicitly allowed when whitelist is enabled */
    private List<String> itemAttachmentWhitelist = new ArrayList<>();

    /** Items explicitly blocked from attachments */
    private List<String> itemAttachmentBlacklist = new ArrayList<>();

    /** Allowed currency types for attachments (empty = all known) */
    private List<String> currencyAttachmentAllowed = new ArrayList<>();

    /** Max amounts per currency type */
    private Map<String, Integer> currencyAttachmentMaxAmounts = new HashMap<>();

    // ============================================================================
    // NEWS SETTINGS
    // ============================================================================

    /** Maximum news articles to display */
    private int maxNewsArticles = 50;

    /** Default news article expiration time (90 days) */
    private Duration defaultNewsTtl = Duration.ofDays(90);

    // ============================================================================
    // API SETTINGS
    // ============================================================================

    /** Port for the admin API server */
    private int apiPort = 8765;

    /** Whether the API server is enabled (downloads Javalin on first start) */
    private boolean apiEnabled = true;

    /** API authentication secret key */
    private String apiSecretKey = "";

    /** Allowed origins for admin API CORS */
    private List<String> apiAllowedOrigins = new ArrayList<>(List.of(
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:4173",
        "http://127.0.0.1:4173"
    ));

    // ============================================================================
    // ROLE/PERMISSION SETTINGS
    // ============================================================================

    /** Whether to use op levels to infer admin/tester roles. */
    private boolean useOpLevelForRoles = true;

    /** Explicit admin UUIDs (string form). */
    private List<String> adminUuids = new ArrayList<>();

    /** Explicit tester UUIDs (string form). */
    private List<String> testerUuids = new ArrayList<>();

    /** Blocked senders (string form). */
    private List<String> blockedSenderUuids = new ArrayList<>();

    /** Blocked receivers (string form). */
    private List<String> blockedReceiverUuids = new ArrayList<>();

    /** Maintenance mode flag */
    private boolean maintenanceMode = false;

    /** Retention window for soft-deleted messages */
    private int messageRetentionDays = 30;

    /** Whether to hard-delete messages on user delete */
    private boolean hardDeleteOnUserDelete = false;

    // ============================================================================
    // GETTERS
    // ============================================================================

    public int getMaxMessagesPerPlayer() {
        return maxMessagesPerPlayer;
    }

    public int getMaxSubjectLength() {
        return maxSubjectLength;
    }

    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    public Duration getDefaultMessageTtl() {
        return defaultMessageTtl;
    }

    public int getMaxAttachmentsPerMessage() {
        return maxAttachmentsPerMessage;
    }

    public int getMaxMessagesPerMinute() {
        return maxMessagesPerMinute;
    }

    public int getMaxMessagesPerDay() {
        return maxMessagesPerDay;
    }

    public int getMaxMessagesPerRecipientPerDay() {
        return maxMessagesPerRecipientPerDay;
    }

    public int getSendCooldownSeconds() {
        return sendCooldownSeconds;
    }

    public int getBroadcastBatchSize() {
        return broadcastBatchSize;
    }

    public int getBroadcastBatchDelayMs() {
        return broadcastBatchDelayMs;
    }

    public boolean isBroadcastQueueEnabled() {
        return broadcastQueueEnabled;
    }

    public int getBroadcastQueueThreshold() {
        return broadcastQueueThreshold;
    }

    public boolean isDeliveryDispatchEnabled() {
        return deliveryDispatchEnabled;
    }

    public boolean isDeliveryImmediateDispatchEnabled() {
        return deliveryImmediateDispatch;
    }

    public int getDeliveryDispatchIntervalSeconds() {
        return deliveryDispatchIntervalSeconds;
    }

    public int getDeliveryDispatchBatchSize() {
        return deliveryDispatchBatchSize;
    }

    public int getDeliveryMaxAttempts() {
        return deliveryMaxAttempts;
    }

    public int getDeliveryRetryDelaySeconds() {
        return deliveryRetryDelaySeconds;
    }

    public int getDeliveryRetryMaxDelaySeconds() {
        return deliveryRetryMaxDelaySeconds;
    }

    public double getDeliveryRetryBackoffMultiplier() {
        return deliveryRetryBackoffMultiplier;
    }

    public double getDeliveryRetryJitterRatio() {
        return deliveryRetryJitterRatio;
    }

    public boolean isDeliveryRecallEnabled() {
        return deliveryRecallEnabled;
    }

    public boolean isPlayerToPlayerEnabled() {
        return playerToPlayerEnabled;
    }

    public int getMinLevelToSend() {
        return minLevelToSend;
    }

    public boolean isItemAttachmentsEnabled() {
        return itemAttachmentsEnabled;
    }

    public boolean isCurrencyAttachmentsEnabled() {
        return currencyAttachmentsEnabled;
    }

    public boolean isItemAttachmentWhitelistEnabled() {
        return itemAttachmentWhitelistEnabled;
    }

    public List<String> getItemAttachmentWhitelist() {
        return List.copyOf(itemAttachmentWhitelist);
    }

    public List<String> getItemAttachmentBlacklist() {
        return List.copyOf(itemAttachmentBlacklist);
    }

    public List<String> getCurrencyAttachmentAllowed() {
        return List.copyOf(currencyAttachmentAllowed);
    }

    public Map<String, Integer> getCurrencyAttachmentMaxAmounts() {
        return Map.copyOf(currencyAttachmentMaxAmounts);
    }

    public int getMaxNewsArticles() {
        return maxNewsArticles;
    }

    public Duration getDefaultNewsTtl() {
        return defaultNewsTtl;
    }

    public int getApiPort() {
        return apiPort;
    }

    public boolean isApiEnabled() {
        return apiEnabled;
    }

    public String getApiSecretKey() {
        return apiSecretKey;
    }

    public List<String> getApiAllowedOrigins() {
        return List.copyOf(apiAllowedOrigins);
    }

    public boolean isUseOpLevelForRoles() {
        return useOpLevelForRoles;
    }

    public List<String> getAdminUuids() {
        return List.copyOf(adminUuids);
    }

    public List<String> getTesterUuids() {
        return List.copyOf(testerUuids);
    }

    public List<String> getBlockedSenderUuids() {
        return List.copyOf(blockedSenderUuids);
    }

    public List<String> getBlockedReceiverUuids() {
        return List.copyOf(blockedReceiverUuids);
    }

    public Path getConfigDirectory() {
        return ensureConfigDir();
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public int getMessageRetentionDays() {
        return messageRetentionDays;
    }

    public boolean isHardDeleteOnUserDelete() {
        return hardDeleteOnUserDelete;
    }

    public boolean isContentFilterEnabled() {
        return contentFilterEnabled;
    }

    public String getContentFilterAction() {
        return contentFilterAction;
    }

    public List<String> getContentFilterWords() {
        return List.copyOf(contentFilterWords);
    }

    public List<String> getContentFilterPatterns() {
        return List.copyOf(contentFilterPatterns);
    }

    // Aliases for API compatibility
    public int getMaxMessagesPerUser() {
        return maxMessagesPerPlayer;
    }

    public int getRateLimitPerMinute() {
        return maxMessagesPerMinute;
    }

    // ============================================================================
    // SETTERS (for admin configuration)
    // ============================================================================

    public void setMaxMessagesPerPlayer(int max) {
        this.maxMessagesPerPlayer = Math.max(10, Math.min(500, max));
    }

    public void setMaxSubjectLength(int max) {
        this.maxSubjectLength = Math.max(32, Math.min(256, max));
    }

    public void setMaxBodyLength(int max) {
        this.maxBodyLength = Math.max(100, Math.min(10000, max));
    }

    public void setDefaultMessageTtl(Duration ttl) {
        // Min 1 day, max 365 days
        long days = ttl.toDays();
        this.defaultMessageTtl = Duration.ofDays(Math.max(1, Math.min(365, days)));
    }

    public void setMaxAttachmentsPerMessage(int max) {
        this.maxAttachmentsPerMessage = Math.max(0, Math.min(10, max));
    }

    public void setMaxMessagesPerMinute(int max) {
        this.maxMessagesPerMinute = Math.max(1, Math.min(60, max));
    }

    public void setSendCooldownSeconds(int seconds) {
        this.sendCooldownSeconds = Math.max(0, Math.min(300, seconds));
    }

    public void setBroadcastBatchSize(int batchSize) {
        this.broadcastBatchSize = Math.max(1, Math.min(5000, batchSize));
    }

    public void setBroadcastBatchDelayMs(int delayMs) {
        this.broadcastBatchDelayMs = Math.max(0, Math.min(60000, delayMs));
    }

    public void setBroadcastQueueEnabled(boolean enabled) {
        this.broadcastQueueEnabled = enabled;
    }

    public void setBroadcastQueueThreshold(int threshold) {
        this.broadcastQueueThreshold = Math.max(1, Math.min(1_000_000, threshold));
    }

    public void setDeliveryDispatchEnabled(boolean enabled) {
        this.deliveryDispatchEnabled = enabled;
    }

    public void setDeliveryImmediateDispatchEnabled(boolean enabled) {
        this.deliveryImmediateDispatch = enabled;
    }

    public void setDeliveryDispatchIntervalSeconds(int seconds) {
        this.deliveryDispatchIntervalSeconds = Math.max(1, Math.min(300, seconds));
    }

    public void setDeliveryDispatchBatchSize(int batchSize) {
        this.deliveryDispatchBatchSize = Math.max(1, Math.min(5000, batchSize));
    }

    public void setDeliveryMaxAttempts(int attempts) {
        this.deliveryMaxAttempts = Math.max(1, Math.min(20, attempts));
    }

    public void setDeliveryRetryDelaySeconds(int seconds) {
        this.deliveryRetryDelaySeconds = Math.max(1, Math.min(3600, seconds));
        if (deliveryRetryMaxDelaySeconds < deliveryRetryDelaySeconds) {
            deliveryRetryMaxDelaySeconds = deliveryRetryDelaySeconds;
        }
    }

    public void setDeliveryRetryMaxDelaySeconds(int seconds) {
        int resolved = Math.max(1, Math.min(86400, seconds));
        this.deliveryRetryMaxDelaySeconds = Math.max(resolved, deliveryRetryDelaySeconds);
    }

    public void setDeliveryRetryBackoffMultiplier(double multiplier) {
        double resolved = Double.isFinite(multiplier) ? multiplier : 2.0;
        this.deliveryRetryBackoffMultiplier = Math.max(1.0, Math.min(10.0, resolved));
    }

    public void setDeliveryRetryJitterRatio(double ratio) {
        double resolved = Double.isFinite(ratio) ? ratio : 0.2;
        this.deliveryRetryJitterRatio = Math.max(0.0, Math.min(0.5, resolved));
    }

    public void setDeliveryRecallEnabled(boolean enabled) {
        this.deliveryRecallEnabled = enabled;
    }

    public void setPlayerToPlayerEnabled(boolean enabled) {
        this.playerToPlayerEnabled = enabled;
    }

    public void setMinLevelToSend(int level) {
        this.minLevelToSend = Math.max(0, level);
    }

    public void setItemAttachmentsEnabled(boolean enabled) {
        this.itemAttachmentsEnabled = enabled;
    }

    public void setCurrencyAttachmentsEnabled(boolean enabled) {
        this.currencyAttachmentsEnabled = enabled;
    }

    public void setItemAttachmentWhitelistEnabled(boolean enabled) {
        this.itemAttachmentWhitelistEnabled = enabled;
    }

    public void setItemAttachmentWhitelist(List<String> items) {
        this.itemAttachmentWhitelist = normalizeStringList(items);
    }

    public void setItemAttachmentBlacklist(List<String> items) {
        this.itemAttachmentBlacklist = normalizeStringList(items);
    }

    public void setCurrencyAttachmentAllowed(List<String> allowed) {
        this.currencyAttachmentAllowed = normalizeStringList(allowed);
    }

    public void setCurrencyAttachmentMaxAmounts(Map<String, Integer> amounts) {
        this.currencyAttachmentMaxAmounts = normalizeAmountMap(amounts);
    }

    public void setMaxNewsArticles(int max) {
        this.maxNewsArticles = Math.max(10, Math.min(200, max));
    }

    public void setMaxMessagesPerDay(int limit) {
        this.maxMessagesPerDay = Math.max(0, Math.min(10000, limit));
    }

    public void setMaxMessagesPerRecipientPerDay(int limit) {
        this.maxMessagesPerRecipientPerDay = Math.max(0, Math.min(10000, limit));
    }

    public void setDefaultNewsTtl(Duration ttl) {
        long days = ttl.toDays();
        this.defaultNewsTtl = Duration.ofDays(Math.max(7, Math.min(365, days)));
    }

    public void setApiPort(int port) {
        this.apiPort = Math.max(1024, Math.min(65535, port));
    }

    public void setApiEnabled(boolean enabled) {
        this.apiEnabled = enabled;
    }

    public void setApiSecretKey(String key) {
        this.apiSecretKey = key != null ? key : "";
    }

    public void setApiAllowedOrigins(List<String> origins) {
        if (origins == null) {
            return;
        }
        List<String> cleaned = origins.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .distinct()
            .toList();
        this.apiAllowedOrigins = new ArrayList<>(cleaned);
    }

    public void setUseOpLevelForRoles(boolean useOpLevelForRoles) {
        this.useOpLevelForRoles = useOpLevelForRoles;
    }

    public void setAdminUuids(List<String> uuids) {
        this.adminUuids = normalizeUuidList(uuids);
    }

    public void setTesterUuids(List<String> uuids) {
        this.testerUuids = normalizeUuidList(uuids);
    }

    public void setBlockedSenderUuids(List<String> uuids) {
        this.blockedSenderUuids = normalizeUuidList(uuids);
    }

    public void setBlockedReceiverUuids(List<String> uuids) {
        this.blockedReceiverUuids = normalizeUuidList(uuids);
    }

    public void setMaintenanceMode(boolean mode) {
        this.maintenanceMode = mode;
    }

    public void setMessageRetentionDays(int days) {
        this.messageRetentionDays = Math.max(1, Math.min(365, days));
    }

    public void setHardDeleteOnUserDelete(boolean hardDelete) {
        this.hardDeleteOnUserDelete = hardDelete;
    }

    public void setContentFilterEnabled(boolean enabled) {
        this.contentFilterEnabled = enabled;
    }

    public void setContentFilterAction(String action) {
        if (action == null || action.isBlank()) {
            this.contentFilterAction = "BLOCK";
            return;
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("BLOCK") && !normalized.equals("FLAG") && !normalized.equals("CENSOR")) {
            normalized = "BLOCK";
        }
        this.contentFilterAction = normalized;
    }

    public void setContentFilterWords(List<String> words) {
        this.contentFilterWords = normalizeStringList(words);
    }

    public void setContentFilterPatterns(List<String> patterns) {
        this.contentFilterPatterns = normalizeStringList(patterns);
    }

    // Aliases for API compatibility
    public void setMaxMessagesPerUser(int max) {
        setMaxMessagesPerPlayer(max);
    }

    public void setRateLimitPerMinute(int limit) {
        setMaxMessagesPerMinute(limit);
    }

    // ============================================================================
    // PERSISTENCE
    // ============================================================================

    /**
     * Initialize config persistence from a base directory.
     */
    public void initialize(Path configDir) {
        dataDirectory = resolveConfigDir(configDir);
        try {
            Files.createDirectories(Objects.requireNonNull(dataDirectory, "config dir"));
        } catch (Exception e) {
            LOGGER.error("[MailboxConfig] Failed to create config directory", e);
        }
        load();
    }

    /**
     * Load configuration from disk (falls back to defaults).
     */
    public void load() {
        Path dir = ensureConfigDir();
        Path configFile = dir.resolve(CONFIG_FILE_NAME);
        Path backupFile = dir.resolve(CONFIG_FILE_NAME + ".bak");

        Path fileToLoad = Files.exists(configFile) ? configFile :
            (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad == null) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
            MailboxConfigData data = GSON.fromJson(reader, MailboxConfigData.class);
            if (data != null) {
                applyData(data);
            }
            LOGGER.info("[MailboxConfig] Loaded config from {}", fileToLoad.getFileName());
        } catch (Exception e) {
            LOGGER.error("[MailboxConfig] Failed to load config from {}", fileToLoad, e);
        }
    }

    /**
     * Save configuration to disk.
     */
    public void save() {
        Path dir = ensureConfigDir();
        Path configFile = dir.resolve(CONFIG_FILE_NAME);
        Path tempFile = dir.resolve(CONFIG_FILE_NAME + ".tmp");
        Path backupFile = dir.resolve(CONFIG_FILE_NAME + ".bak");

        try {
            Files.createDirectories(dir);
            MailboxConfigData data = toData();

            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
                writer.flush();
            }

            if (Files.exists(configFile)) {
                Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tempFile, configFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

            LOGGER.info("[MailboxConfig] Saved config to {}", configFile.getFileName());
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("[MailboxConfig] Saved config to {} (non-atomic)", configFile.getFileName());
            } catch (Exception ex) {
                LOGGER.error("[MailboxConfig] Failed to save config (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[MailboxConfig] Failed to save config", e);
        }
    }

    private Path ensureConfigDir() {
        if (dataDirectory == null) {
            dataDirectory = ConfigPaths.getConfigDir();
        }
        return dataDirectory;
    }

    private static Path resolveConfigDir(Path configDir) {
        if (configDir == null) {
            return ConfigPaths.getConfigDir();
        }
        Path name = configDir.getFileName();
        if (name != null && "devmod".equalsIgnoreCase(name.toString())) {
            return configDir;
        }
        return configDir.resolve("devmod");
    }

    private void applyData(MailboxConfigData data) {
        Integer maxMsgs = data.maxMessagesPerPlayer();
        if (maxMsgs != null) setMaxMessagesPerPlayer(maxMsgs);
        Integer maxSubj = data.maxSubjectLength();
        if (maxSubj != null) setMaxSubjectLength(maxSubj);
        Integer maxBody = data.maxBodyLength();
        if (maxBody != null) setMaxBodyLength(maxBody);
        Integer ttlDays = data.defaultMessageTtlDays();
        if (ttlDays != null) setDefaultMessageTtl(Duration.ofDays(Math.max(1, ttlDays)));
        Integer maxAttach = data.maxAttachmentsPerMessage();
        if (maxAttach != null) setMaxAttachmentsPerMessage(maxAttach);
        Integer maxPerMin = data.maxMessagesPerMinute();
        if (maxPerMin != null) setMaxMessagesPerMinute(maxPerMin);
        Integer maxPerDay = data.maxMessagesPerDay();
        if (maxPerDay != null) setMaxMessagesPerDay(maxPerDay);
        Integer maxPerRecip = data.maxMessagesPerRecipientPerDay();
        if (maxPerRecip != null) setMaxMessagesPerRecipientPerDay(maxPerRecip);
        Integer cooldown = data.sendCooldownSeconds();
        if (cooldown != null) setSendCooldownSeconds(cooldown);
        Integer batchSize = data.broadcastBatchSize();
        if (batchSize != null) setBroadcastBatchSize(batchSize);
        Integer batchDelay = data.broadcastBatchDelayMs();
        if (batchDelay != null) setBroadcastBatchDelayMs(batchDelay);
        Boolean queueEnabled = data.broadcastQueueEnabled();
        if (queueEnabled != null) setBroadcastQueueEnabled(queueEnabled);
        Integer queueThreshold = data.broadcastQueueThreshold();
        if (queueThreshold != null) setBroadcastQueueThreshold(queueThreshold);
        Boolean deliveryEnabled = data.deliveryDispatchEnabled();
        if (deliveryEnabled != null) setDeliveryDispatchEnabled(deliveryEnabled);
        Boolean deliveryImmediate = data.deliveryImmediateDispatchEnabled();
        if (deliveryImmediate != null) setDeliveryImmediateDispatchEnabled(deliveryImmediate);
        Integer dispatchInterval = data.deliveryDispatchIntervalSeconds();
        if (dispatchInterval != null) setDeliveryDispatchIntervalSeconds(dispatchInterval);
        Integer dispatchBatch = data.deliveryDispatchBatchSize();
        if (dispatchBatch != null) setDeliveryDispatchBatchSize(dispatchBatch);
        Integer maxAttempts = data.deliveryMaxAttempts();
        if (maxAttempts != null) setDeliveryMaxAttempts(maxAttempts);
        Integer retryDelay = data.deliveryRetryDelaySeconds();
        if (retryDelay != null) setDeliveryRetryDelaySeconds(retryDelay);
        Integer retryMax = data.deliveryRetryMaxDelaySeconds();
        if (retryMax != null) setDeliveryRetryMaxDelaySeconds(retryMax);
        Double backoffMultiplier = data.deliveryRetryBackoffMultiplier();
        if (backoffMultiplier != null) setDeliveryRetryBackoffMultiplier(backoffMultiplier);
        Double retryJitter = data.deliveryRetryJitterRatio();
        if (retryJitter != null) setDeliveryRetryJitterRatio(retryJitter);
        Boolean recallEnabled = data.deliveryRecallEnabled();
        if (recallEnabled != null) setDeliveryRecallEnabled(recallEnabled);
        Boolean p2pEnabled = data.playerToPlayerEnabled();
        if (p2pEnabled != null) setPlayerToPlayerEnabled(p2pEnabled);
        Integer minLevel = data.minLevelToSend();
        if (minLevel != null) setMinLevelToSend(minLevel);
        Boolean itemAttach = data.itemAttachmentsEnabled();
        if (itemAttach != null) setItemAttachmentsEnabled(itemAttach);
        Boolean currAttach = data.currencyAttachmentsEnabled();
        if (currAttach != null) setCurrencyAttachmentsEnabled(currAttach);
        Boolean whitelistEnabled = data.itemAttachmentWhitelistEnabled();
        if (whitelistEnabled != null) setItemAttachmentWhitelistEnabled(whitelistEnabled);
        List<String> itemWhitelist = data.itemAttachmentWhitelist();
        if (itemWhitelist != null) setItemAttachmentWhitelist(itemWhitelist);
        List<String> itemBlacklist = data.itemAttachmentBlacklist();
        if (itemBlacklist != null) setItemAttachmentBlacklist(itemBlacklist);
        List<String> currencyAllowed = data.currencyAttachmentAllowed();
        if (currencyAllowed != null) setCurrencyAttachmentAllowed(currencyAllowed);
        Map<String, Integer> currencyMax = data.currencyAttachmentMaxAmounts();
        if (currencyMax != null) setCurrencyAttachmentMaxAmounts(currencyMax);
        Integer maxNews = data.maxNewsArticles();
        if (maxNews != null) setMaxNewsArticles(maxNews);
        Integer newsTtl = data.defaultNewsTtlDays();
        if (newsTtl != null) setDefaultNewsTtl(Duration.ofDays(Math.max(7, newsTtl)));
        Integer port = data.apiPort();
        if (port != null) setApiPort(port);
        Boolean apiEn = data.apiEnabled();
        if (apiEn != null) setApiEnabled(apiEn);
        String apiKey = data.apiSecretKey();
        if (apiKey != null) setApiSecretKey(apiKey);
        List<String> origins = data.apiAllowedOrigins();
        if (origins != null) setApiAllowedOrigins(origins);
        Boolean useOpLevel = data.useOpLevelForRoles();
        if (useOpLevel != null) setUseOpLevelForRoles(useOpLevel);
        List<String> admins = data.adminUuids();
        if (admins != null) setAdminUuids(admins);
        List<String> testers = data.testerUuids();
        if (testers != null) setTesterUuids(testers);
        List<String> blockedSenders = data.blockedSenderUuids();
        if (blockedSenders != null) setBlockedSenderUuids(blockedSenders);
        List<String> blockedReceivers = data.blockedReceiverUuids();
        if (blockedReceivers != null) setBlockedReceiverUuids(blockedReceivers);
        Boolean maint = data.maintenanceMode();
        if (maint != null) setMaintenanceMode(maint);
        Integer retention = data.messageRetentionDays();
        if (retention != null) setMessageRetentionDays(retention);
        Boolean hardDel = data.hardDeleteOnUserDelete();
        if (hardDel != null) setHardDeleteOnUserDelete(hardDel);
        Boolean filterEnabled = data.contentFilterEnabled();
        if (filterEnabled != null) setContentFilterEnabled(filterEnabled);
        String filterAction = data.contentFilterAction();
        if (filterAction != null) setContentFilterAction(filterAction);
        List<String> filterWords = data.contentFilterWords();
        if (filterWords != null) setContentFilterWords(filterWords);
        List<String> filterPatterns = data.contentFilterPatterns();
        if (filterPatterns != null) setContentFilterPatterns(filterPatterns);
    }

    private MailboxConfigData toData() {
        return new MailboxConfigData(
            maxMessagesPerPlayer,
            maxSubjectLength,
            maxBodyLength,
            (int) Math.max(1, defaultMessageTtl.toDays()),
            maxAttachmentsPerMessage,
            maxMessagesPerMinute,
            maxMessagesPerDay,
            maxMessagesPerRecipientPerDay,
            sendCooldownSeconds,
            broadcastBatchSize,
            broadcastBatchDelayMs,
            broadcastQueueEnabled,
            broadcastQueueThreshold,
            deliveryDispatchEnabled,
            deliveryImmediateDispatch,
            deliveryDispatchIntervalSeconds,
            deliveryDispatchBatchSize,
            deliveryMaxAttempts,
            deliveryRetryDelaySeconds,
            deliveryRetryMaxDelaySeconds,
            deliveryRetryBackoffMultiplier,
            deliveryRetryJitterRatio,
            deliveryRecallEnabled,
            playerToPlayerEnabled,
            minLevelToSend,
            itemAttachmentsEnabled,
            currencyAttachmentsEnabled,
            itemAttachmentWhitelistEnabled,
            List.copyOf(itemAttachmentWhitelist),
            List.copyOf(itemAttachmentBlacklist),
            List.copyOf(currencyAttachmentAllowed),
            Map.copyOf(currencyAttachmentMaxAmounts),
            maxNewsArticles,
            (int) Math.max(7, defaultNewsTtl.toDays()),
            apiPort,
            apiEnabled,
            apiSecretKey,
            List.copyOf(apiAllowedOrigins),
            useOpLevelForRoles,
            List.copyOf(adminUuids),
            List.copyOf(testerUuids),
            List.copyOf(blockedSenderUuids),
            List.copyOf(blockedReceiverUuids),
            maintenanceMode,
            messageRetentionDays,
            hardDeleteOnUserDelete,
            contentFilterEnabled,
            contentFilterAction,
            List.copyOf(contentFilterWords),
            List.copyOf(contentFilterPatterns)
        );
    }

    private static List<String> normalizeUuidList(@Nullable List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .distinct()
            .toList();
    }

    private static List<String> normalizeStringList(@Nullable List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .distinct()
            .toList();
    }

    private static Map<String, Integer> normalizeAmountMap(@Nullable Map<String, Integer> values) {
        if (values == null) {
            return new HashMap<>();
        }
        Map<String, Integer> normalized = new HashMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = entry.getKey();
            Integer value = entry.getValue();
            if (key == null || key.isBlank() || value == null) {
                continue;
            }
            int amount = Math.max(1, Math.min(1_000_000, value));
            normalized.put(key.trim().toLowerCase(Locale.ROOT), amount);
        }
        return normalized;
    }

    public record MailboxConfigData(
        @Nullable Integer maxMessagesPerPlayer,
        @Nullable Integer maxSubjectLength,
        @Nullable Integer maxBodyLength,
        @Nullable Integer defaultMessageTtlDays,
        @Nullable Integer maxAttachmentsPerMessage,
        @Nullable Integer maxMessagesPerMinute,
        @Nullable Integer maxMessagesPerDay,
        @Nullable Integer maxMessagesPerRecipientPerDay,
        @Nullable Integer sendCooldownSeconds,
        @Nullable Integer broadcastBatchSize,
        @Nullable Integer broadcastBatchDelayMs,
        @Nullable Boolean broadcastQueueEnabled,
        @Nullable Integer broadcastQueueThreshold,
        @Nullable Boolean deliveryDispatchEnabled,
        @Nullable Boolean deliveryImmediateDispatchEnabled,
        @Nullable Integer deliveryDispatchIntervalSeconds,
        @Nullable Integer deliveryDispatchBatchSize,
        @Nullable Integer deliveryMaxAttempts,
        @Nullable Integer deliveryRetryDelaySeconds,
        @Nullable Integer deliveryRetryMaxDelaySeconds,
        @Nullable Double deliveryRetryBackoffMultiplier,
        @Nullable Double deliveryRetryJitterRatio,
        @Nullable Boolean deliveryRecallEnabled,
        @Nullable Boolean playerToPlayerEnabled,
        @Nullable Integer minLevelToSend,
        @Nullable Boolean itemAttachmentsEnabled,
        @Nullable Boolean currencyAttachmentsEnabled,
        @Nullable Boolean itemAttachmentWhitelistEnabled,
        @Nullable List<String> itemAttachmentWhitelist,
        @Nullable List<String> itemAttachmentBlacklist,
        @Nullable List<String> currencyAttachmentAllowed,
        @Nullable Map<String, Integer> currencyAttachmentMaxAmounts,
        @Nullable Integer maxNewsArticles,
        @Nullable Integer defaultNewsTtlDays,
        @Nullable Integer apiPort,
        @Nullable Boolean apiEnabled,
        @Nullable String apiSecretKey,
        @Nullable List<String> apiAllowedOrigins,
        @Nullable Boolean useOpLevelForRoles,
        @Nullable List<String> adminUuids,
        @Nullable List<String> testerUuids,
        @Nullable List<String> blockedSenderUuids,
        @Nullable List<String> blockedReceiverUuids,
        @Nullable Boolean maintenanceMode,
        @Nullable Integer messageRetentionDays,
        @Nullable Boolean hardDeleteOnUserDelete,
        @Nullable Boolean contentFilterEnabled,
        @Nullable String contentFilterAction,
        @Nullable List<String> contentFilterWords,
        @Nullable List<String> contentFilterPatterns
    ) {}
}
