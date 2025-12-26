package com.devmod.mailbox;

import java.time.Duration;

/**
 * Configuration settings for the mailbox system.
 * These can be adjusted via the admin panel or config files.
 */
public class MailboxConfig {

    // ============================================================================
    // SINGLETON
    // ============================================================================

    public static final MailboxConfig INSTANCE = new MailboxConfig();

    private MailboxConfig() {}

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

    /** Cooldown between sending messages (seconds) */
    private int sendCooldownSeconds = 5;

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

    /** Whether the API server is enabled */
    private boolean apiEnabled = false;

    /** API authentication secret key */
    private String apiSecretKey = "";

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

    public int getSendCooldownSeconds() {
        return sendCooldownSeconds;
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

    public void setMaxNewsArticles(int max) {
        this.maxNewsArticles = Math.max(10, Math.min(200, max));
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
}
