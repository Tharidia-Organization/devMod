package com.devmod.arena.alert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class DiscordAlertChannel implements AlertRouter.AlertChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordAlertChannel.class);

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    // Discord embed colors (decimal)
    private static final int COLOR_ERROR = 0xED4245;   // Red
    private static final int COLOR_WARN = 0xFEE75C;    // Yellow
    private static final int COLOR_INFO = 0x5865F2;   // Blurple

    private final String id;
    private final String webhookUrl;
    private final boolean critical;
    private final int timeoutMs;
    private final String username;
    private final String avatarUrl;
    private final String mentionRole;

    /**
     * Creates a new Discord channel with default settings.
     *
     * @param id Unique channel identifier
     * @param webhookUrl The Discord webhook URL
     */
    public DiscordAlertChannel(String id, String webhookUrl) {
        this(id, webhookUrl, false, DEFAULT_TIMEOUT_MS, "Arena Alerts", null, null);
    }

    /**
     * Creates a new Discord channel with full configuration.
     *
     * @param id Unique channel identifier
     * @param webhookUrl The Discord webhook URL
     * @param critical Whether delivery should be retried on failure
     * @param timeoutMs Connection/read timeout in milliseconds
     * @param username Bot username to display
     * @param avatarUrl Bot avatar URL (optional)
     * @param mentionRole Role ID to mention on ERROR severity (optional, e.g., "123456789")
     */
    public DiscordAlertChannel(String id, String webhookUrl, boolean critical, int timeoutMs,
                                String username, String avatarUrl, String mentionRole) {
        this.id = Objects.requireNonNull(id, "id");
        this.webhookUrl = Objects.requireNonNull(webhookUrl, "webhookUrl");
        this.critical = critical;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        this.username = username != null ? username : "Arena Alerts";
        this.avatarUrl = avatarUrl;
        this.mentionRole = mentionRole;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isCritical() {
        return critical;
    }

    @Override
    public boolean deliver(ErrorContext context) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(webhookUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "DevMod-ArenaAlerts/1.0");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);

            // Build Discord webhook payload
            String json = buildDiscordPayload(context);

            // Send payload
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                LOGGER.debug("Discord delivery successful to {}: HTTP {}", id, responseCode);
                return true;
            } else if (responseCode == 429) {
                // Rate limited
                LOGGER.warn("Discord rate limited for {}", id);
                return false;
            } else {
                LOGGER.warn("Discord delivery failed to {}: HTTP {}", id, responseCode);
                return false;
            }

        } catch (IOException e) {
            LOGGER.error("Discord delivery error to {}: {}", id, e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Builds the Discord webhook JSON payload with embed.
     */
    private String buildDiscordPayload(ErrorContext context) {
        int color = getColorForSeverity(context.severity());
        String title = formatTitle(context);
        String timestamp = Instant.now().toString();

        StringBuilder json = new StringBuilder();
        json.append("{");

        // Username and avatar
        json.append("\"username\":\"").append(escapeJson(username)).append("\"");
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            json.append(",\"avatar_url\":\"").append(escapeJson(avatarUrl)).append("\"");
        }

        // Content (mentions for ERROR)
        if (mentionRole != null && !mentionRole.isBlank() &&
            context.severity() == ErrorContext.Severity.ERROR) {
            json.append(",\"content\":\"<@&").append(mentionRole).append("> Alert requires attention!\"");
        }

        // Embeds array
        json.append(",\"embeds\":[{");
        json.append("\"title\":\"").append(escapeJson(title)).append("\",");
        json.append("\"description\":\"").append(escapeJson(context.message())).append("\",");
        json.append("\"color\":").append(color).append(",");
        json.append("\"timestamp\":\"").append(timestamp).append("\",");

        // Fields for context
        json.append("\"fields\":[");

        // Add severity field
        json.append("{\"name\":\"Severity\",\"value\":\"").append(context.severity().name())
           .append("\",\"inline\":true}");

        // Add component field
        if (context.component() != null && !context.component().isBlank()) {
            json.append(",{\"name\":\"Component\",\"value\":\"")
               .append(escapeJson(context.component()))
               .append("\",\"inline\":true}");
        }

        // Add metadata fields if present
        var metadata = context.metadata();
        if (metadata != null) {
            if (metadata.containsKey("templateId")) {
                json.append(",{\"name\":\"Template\",\"value\":\"")
                   .append(escapeJson(String.valueOf(metadata.get("templateId"))))
                   .append("\",\"inline\":true}");
            }
            if (metadata.containsKey("arenaId")) {
                json.append(",{\"name\":\"Arena ID\",\"value\":\"")
                   .append(escapeJson(String.valueOf(metadata.get("arenaId"))))
                   .append("\",\"inline\":true}");
            }
            if (metadata.containsKey("durationMs")) {
                json.append(",{\"name\":\"Duration\",\"value\":\"")
                   .append(metadata.get("durationMs")).append("ms")
                   .append("\",\"inline\":true}");
            }
            if (metadata.containsKey("blocksPlaced")) {
                json.append(",{\"name\":\"Blocks\",\"value\":\"")
                   .append(metadata.get("blocksPlaced"))
                   .append("\",\"inline\":true}");
            }
        }

        json.append("],"); // end fields

        // Footer with error ID
        json.append("\"footer\":{\"text\":\"Error ID: ").append(context.errorId().toString()).append("\"}");

        json.append("}]"); // end embed and embeds array
        json.append("}");

        return json.toString();
    }

    /**
     * Formats the embed title based on error type.
     */
    private String formatTitle(ErrorContext context) {
        String errorType = context.errorType();
        if (errorType == null || errorType.isBlank()) {
            return "\u26A0\uFE0F Arena Alert";
        }

        // Extract simple class name from fully qualified name
        String simpleName = errorType;
        int lastDot = errorType.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < errorType.length() - 1) {
            simpleName = errorType.substring(lastDot + 1);
        }

        // Convert CamelCase to Title Case with spaces
        StringBuilder title = new StringBuilder();

        // Add emoji based on severity
        switch (context.severity()) {
            case ERROR, CRITICAL -> title.append("\uD83D\uDED1 "); // Stop sign
            case WARNING -> title.append("\u26A0\uFE0F ");  // Warning
            case INFO, DEBUG -> title.append("\u2139\uFE0F ");  // Info
        }

        // Add spaces before uppercase letters (CamelCase to Title Case)
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                title.append(" ");
            }
            title.append(c);
        }

        return title.toString().trim();
    }

    /**
     * Gets the embed color for a severity level.
     */
    private int getColorForSeverity(ErrorContext.Severity severity) {
        return switch (severity) {
            case ERROR, CRITICAL -> COLOR_ERROR;
            case WARNING -> COLOR_WARN;
            case INFO, DEBUG -> COLOR_INFO;
        };
    }

    /**
     * Escapes a string for JSON.
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Builder for DiscordAlertChannel.
     */
    public static Builder builder(String id, String webhookUrl) {
        return new Builder(id, webhookUrl);
    }

    public static class Builder {
        private final String id;
        private final String webhookUrl;
        private boolean critical = false;
        private int timeoutMs = DEFAULT_TIMEOUT_MS;
        private String username = "Arena Alerts";
        private String avatarUrl = null;
        private String mentionRole = null;

        public Builder(String id, String webhookUrl) {
            this.id = id;
            this.webhookUrl = webhookUrl;
        }

        public Builder critical(boolean critical) {
            this.critical = critical;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeoutMs = (int) timeout.toMillis();
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        /**
         * Sets the role ID to mention on ERROR alerts.
         * @param roleId The Discord role ID (numbers only, e.g., "123456789012345678")
         */
        public Builder mentionRole(String roleId) {
            this.mentionRole = roleId;
            return this;
        }

        public DiscordAlertChannel build() {
            return new DiscordAlertChannel(id, webhookUrl, critical, timeoutMs, username, avatarUrl, mentionRole);
        }
    }
}
