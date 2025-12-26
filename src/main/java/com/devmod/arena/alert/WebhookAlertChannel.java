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
public class WebhookAlertChannel implements AlertRouter.AlertChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookAlertChannel.class);

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final String DEFAULT_CONTENT_TYPE = "application/json";

    private final String id;
    private final String webhookUrl;
    private final boolean critical;
    private final int timeoutMs;
    private final String contentType;
    private final String authHeader;

    /**
     * Creates a new webhook channel with default settings.
     *
     * @param id Unique channel identifier
     * @param webhookUrl The webhook URL to POST to
     */
    public WebhookAlertChannel(String id, String webhookUrl) {
        this(id, webhookUrl, false, DEFAULT_TIMEOUT_MS, null);
    }

    /**
     * Creates a new webhook channel with full configuration.
     *
     * @param id Unique channel identifier
     * @param webhookUrl The webhook URL to POST to
     * @param critical Whether delivery should be retried on failure
     * @param timeoutMs Connection/read timeout in milliseconds
     * @param authHeader Optional Authorization header value (e.g., "Bearer token")
     */
    public WebhookAlertChannel(String id, String webhookUrl, boolean critical, int timeoutMs, String authHeader) {
        this.id = Objects.requireNonNull(id, "id");
        this.webhookUrl = Objects.requireNonNull(webhookUrl, "webhookUrl");
        this.critical = critical;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        this.contentType = DEFAULT_CONTENT_TYPE;
        this.authHeader = authHeader;
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
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("User-Agent", "DevMod-ArenaAlerts/1.0");

            if (authHeader != null && !authHeader.isBlank()) {
                connection.setRequestProperty("Authorization", authHeader);
            }

            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);

            // Build JSON payload
            String json = buildJsonPayload(context);

            // Send payload
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                LOGGER.debug("Webhook delivery successful to {}: HTTP {}", id, responseCode);
                return true;
            } else {
                LOGGER.warn("Webhook delivery failed to {}: HTTP {}", id, responseCode);
                return false;
            }

        } catch (IOException e) {
            LOGGER.error("Webhook delivery error to {}: {}", id, e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Builds the JSON payload for the webhook.
     */
    private String buildJsonPayload(ErrorContext context) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"error_id\":\"").append(escapeJson(context.errorId().toString())).append("\",");
        json.append("\"error_type\":\"").append(escapeJson(context.errorType())).append("\",");
        json.append("\"severity\":\"").append(escapeJson(context.severity().name())).append("\",");
        json.append("\"message\":\"").append(escapeJson(context.message())).append("\",");
        json.append("\"component\":\"").append(escapeJson(context.component())).append("\",");
        json.append("\"timestamp\":\"").append(Instant.now().toString()).append("\",");

        // Metadata as nested object
        json.append("\"metadata\":{");
        var metadata = context.metadata();
        if (metadata != null && !metadata.isEmpty()) {
            boolean first = true;
            for (var entry : metadata.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escapeJson(entry.getKey())).append("\":");
                appendJsonValue(json, entry.getValue());
                first = false;
            }
        }
        json.append("}");

        json.append("}");
        return json.toString();
    }

    /**
     * Appends a value as JSON (handles strings, numbers, booleans).
     */
    private void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number) {
            json.append(value);
        } else if (value instanceof Boolean) {
            json.append(value);
        } else {
            json.append("\"").append(escapeJson(value.toString())).append("\"");
        }
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
     * Builder for WebhookAlertChannel.
     */
    public static Builder builder(String id, String webhookUrl) {
        return new Builder(id, webhookUrl);
    }

    public static class Builder {
        private final String id;
        private final String webhookUrl;
        private boolean critical = false;
        private int timeoutMs = DEFAULT_TIMEOUT_MS;
        private String authHeader = null;

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

        public Builder authHeader(String authHeader) {
            this.authHeader = authHeader;
            return this;
        }

        public Builder bearerToken(String token) {
            this.authHeader = "Bearer " + token;
            return this;
        }

        public WebhookAlertChannel build() {
            return new WebhookAlertChannel(id, webhookUrl, critical, timeoutMs, authHeader);
        }
    }
}
