package com.devmod.mailbox.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Webhook manager for external integrations.
 *
 * Sends HTTP POST requests to configured endpoints when events occur.
 * Supports:
 * - Multiple webhook endpoints
 * - Event type filtering
 * - Retry logic with exponential backoff
 * - Rate limiting
 * - Discord/Slack formatting
 */
public final class WebhookManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookManager.class);

    public static final WebhookManager INSTANCE = new WebhookManager();

    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_QUEUE_SIZE = 1000;

    private final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    // Registered webhooks
    private final Map<UUID, WebhookConfig> webhooks = new ConcurrentHashMap<>();

    // Pending deliveries for retry
    private final Map<UUID, List<PendingDelivery>> pendingDeliveries = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalRetried = new AtomicLong(0);
    private final Collection<CompletableFuture<?>> inflightRequests = new ConcurrentLinkedQueue<>();

    // HTTP client and executors
    @Nullable
    private HttpClient httpClient;
    @Nullable
    private ExecutorService httpExecutor;
    @Nullable
    private ExecutorService executor;
    @Nullable
    private ScheduledExecutorService retryScheduler;
    @Nullable
    private ScheduledFuture<?> retryTask;
    private volatile boolean running = false;

    private WebhookManager() {}

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    /**
     * Start the webhook manager.
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        ExecutorService httpExec = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Webhook-HTTP");
            t.setDaemon(true);
            return t;
        });
        httpExecutor = httpExec;

        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .executor(httpExec)
            .build();

        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Webhook-Worker");
            t.setDaemon(true);
            return t;
        });

        ScheduledExecutorService retrySched = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Webhook-Retry");
            t.setDaemon(true);
            return t;
        });
        retryScheduler = retrySched;

        running = true;

        // Process retries every 30 seconds
        retryTask = retrySched.scheduleAtFixedRate(this::processRetries, 30, 30, TimeUnit.SECONDS);

        LOGGER.info("[Webhooks] Started with {} registered webhooks", webhooks.size());
    }

    /**
     * Stop the webhook manager.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }

        ScheduledExecutorService retrySched = retryScheduler;
        if (retrySched != null) {
            retrySched.shutdown();
            try {
                if (!retrySched.awaitTermination(5, TimeUnit.SECONDS)) {
                    retrySched.shutdownNow();
                }
            } catch (InterruptedException e) {
                retrySched.shutdownNow();
                Thread.currentThread().interrupt();
            }
            retryScheduler = null;
        }

        ExecutorService exec = executor;
        if (exec != null) {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(10, TimeUnit.SECONDS)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                exec.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        httpClient = null;
        ExecutorService httpExec = httpExecutor;
        if (httpExec != null) {
            httpExec.shutdown();
            try {
                if (!httpExec.awaitTermination(10, TimeUnit.SECONDS)) {
                    httpExec.shutdownNow();
                }
            } catch (InterruptedException e) {
                httpExec.shutdownNow();
                Thread.currentThread().interrupt();
            }
            httpExecutor = null;
        }

        for (CompletableFuture<?> future : inflightRequests) {
            future.cancel(true);
        }
        inflightRequests.clear();
        LOGGER.info("[Webhooks] Stopped. Stats: sent={}, failed={}, retried={}",
            totalSent.get(), totalFailed.get(), totalRetried.get());
    }

    // ============================================================================
    // WEBHOOK CONFIGURATION
    // ============================================================================

    /**
     * Register a new webhook.
     *
     * @param config the webhook configuration
     * @return the webhook ID
     */
    public UUID registerWebhook(WebhookConfig config) {
        webhooks.put(config.id(), config);
        LOGGER.info("[Webhooks] Registered webhook: {} -> {}", config.name(), config.url());
        return config.id();
    }

    /**
     * Register a webhook with simplified parameters.
     */
    public UUID registerWebhook(String name, String url, Set<WebhookEvent> events) {
        WebhookConfig config = new WebhookConfig(
            UUID.randomUUID(),
            name,
            url,
            events,
            WebhookFormat.JSON,
            null,
            true,
            MAX_RETRIES,
            Instant.now()
        );
        return registerWebhook(config);
    }

    /**
     * Register a Discord webhook.
     */
    public UUID registerDiscordWebhook(String name, String webhookUrl, Set<WebhookEvent> events) {
        WebhookConfig config = new WebhookConfig(
            UUID.randomUUID(),
            name,
            webhookUrl,
            events,
            WebhookFormat.DISCORD,
            null,
            true,
            MAX_RETRIES,
            Instant.now()
        );
        return registerWebhook(config);
    }

    /**
     * Register a Slack webhook.
     */
    public UUID registerSlackWebhook(String name, String webhookUrl, Set<WebhookEvent> events) {
        WebhookConfig config = new WebhookConfig(
            UUID.randomUUID(),
            name,
            webhookUrl,
            events,
            WebhookFormat.SLACK,
            null,
            true,
            MAX_RETRIES,
            Instant.now()
        );
        return registerWebhook(config);
    }

    /**
     * Unregister a webhook.
     */
    public boolean unregisterWebhook(UUID webhookId) {
        WebhookConfig removed = webhooks.remove(webhookId);
        if (removed != null) {
            pendingDeliveries.remove(webhookId);
            LOGGER.info("[Webhooks] Unregistered webhook: {}", removed.name());
            return true;
        }
        return false;
    }

    /**
     * Update webhook configuration.
     */
    public boolean updateWebhook(WebhookConfig config) {
        if (!webhooks.containsKey(config.id())) {
            return false;
        }
        webhooks.put(config.id(), config);
        return true;
    }

    /**
     * Enable or disable a webhook.
     */
    public boolean setWebhookEnabled(UUID webhookId, boolean enabled) {
        WebhookConfig config = webhooks.get(webhookId);
        if (config == null) {
            return false;
        }

        webhooks.put(webhookId, new WebhookConfig(
            config.id(),
            config.name(),
            config.url(),
            config.events(),
            config.format(),
            config.secret(),
            enabled,
            config.maxRetries(),
            config.createdAt()
        ));
        return true;
    }

    /**
     * Get all registered webhooks.
     */
    public List<WebhookConfig> getWebhooks() {
        return new ArrayList<>(webhooks.values());
    }

    /**
     * Get webhook by ID.
     */
    @Nullable
    public WebhookConfig getWebhook(UUID webhookId) {
        return webhooks.get(webhookId);
    }

    // ============================================================================
    // EVENT DISPATCH
    // ============================================================================

    /**
     * Dispatch an event to all matching webhooks.
     */
    public void dispatch(WebhookEvent event, WebhookPayload payload) {
        if (!running) {
            return;
        }

        ExecutorService executor = getExecutor();
        if (executor == null) {
            LOGGER.warn("[Webhooks] Dispatch skipped; executor not initialized");
            return;
        }

        for (WebhookConfig webhook : webhooks.values()) {
            if (!webhook.enabled() || !webhook.events().contains(event)) {
                continue;
            }

            executor.execute(() -> sendWebhook(webhook, event, payload, 0));
        }
    }

    /**
     * Dispatch a message sent event.
     */
    public void dispatchMessageSent(UUID messageId, UUID senderUuid, String senderName,
                                    UUID recipientUuid, String subject, boolean hasAttachment) {
        dispatch(WebhookEvent.MESSAGE_SENT, new WebhookPayload(
            WebhookEvent.MESSAGE_SENT,
            Instant.now(),
            Map.of(
                "message_id", messageId.toString(),
                "sender_uuid", senderUuid != null ? senderUuid.toString() : "system",
                "sender_name", senderName != null ? senderName : "system",
                "recipient_uuid", recipientUuid.toString(),
                "subject", subject != null ? subject : "",
                "has_attachment", hasAttachment
            )
        ));
    }

    /**
     * Dispatch a message read event.
     */
    public void dispatchMessageRead(UUID messageId, UUID playerUuid) {
        dispatch(WebhookEvent.MESSAGE_READ, new WebhookPayload(
            WebhookEvent.MESSAGE_READ,
            Instant.now(),
            Map.of(
                "message_id", messageId.toString(),
                "player_uuid", playerUuid.toString()
            )
        ));
    }

    /**
     * Dispatch a broadcast sent event.
     */
    public void dispatchBroadcastSent(String subject, int recipientCount, String senderName) {
        dispatch(WebhookEvent.BROADCAST_SENT, new WebhookPayload(
            WebhookEvent.BROADCAST_SENT,
            Instant.now(),
            Map.of(
                "subject", subject,
                "recipient_count", recipientCount,
                "sender_name", senderName
            )
        ));
    }

    /**
     * Dispatch an attachment claimed event.
     */
    public void dispatchAttachmentClaimed(UUID messageId, UUID playerUuid, String attachmentType) {
        dispatch(WebhookEvent.ATTACHMENT_CLAIMED, new WebhookPayload(
            WebhookEvent.ATTACHMENT_CLAIMED,
            Instant.now(),
            Map.of(
                "message_id", messageId.toString(),
                "player_uuid", playerUuid.toString(),
                "attachment_type", attachmentType
            )
        ));
    }

    /**
     * Dispatch a news published event.
     */
    public void dispatchNewsPublished(UUID newsId, String title, String authorName) {
        dispatch(WebhookEvent.NEWS_PUBLISHED, new WebhookPayload(
            WebhookEvent.NEWS_PUBLISHED,
            Instant.now(),
            Map.of(
                "news_id", newsId.toString(),
                "title", title,
                "author_name", authorName
            )
        ));
    }

    /**
     * Dispatch an anomaly detected event.
     */
    public void dispatchAnomalyDetected(String anomalyType, @Nullable UUID playerUuid, String description) {
        dispatch(WebhookEvent.ANOMALY_DETECTED, new WebhookPayload(
            WebhookEvent.ANOMALY_DETECTED,
            Instant.now(),
            Map.of(
                "anomaly_type", anomalyType,
                "player_uuid", playerUuid != null ? playerUuid.toString() : "unknown",
                "description", description
            )
        ));
    }

    // ============================================================================
    // HTTP SENDING
    // ============================================================================

    private void sendWebhook(WebhookConfig webhook, WebhookEvent event, WebhookPayload payload, int attempt) {
        try {
            String body = formatPayload(webhook.format(), event, payload);

            HttpClient client = getHttpClient();
            if (client == null) {
                handleFailure(webhook, event, payload, attempt, "HTTP client not initialized");
                return;
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(webhook.url()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", getContentType(webhook.format()))
                .header("User-Agent", "DevMod-Mailbox/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body));

            // Add secret if configured
            String secret = webhook.secret();
            if (secret != null && !secret.isEmpty()) {
                requestBuilder.header("X-Webhook-Secret", secret);
            }

            HttpRequest request = requestBuilder.build();

            CompletableFuture<HttpResponse<String>> responseFuture =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            CompletableFuture<HttpResponse<String>> trackedFuture = responseFuture.whenComplete((response, error) -> {
                if (error != null) {
                    handleFailure(webhook, event, payload, attempt, errorMessage(error));
                } else if (response.statusCode() >= 400) {
                    handleFailure(webhook, event, payload, attempt,
                        "HTTP " + response.statusCode() + ": " + response.body());
                } else {
                    totalSent.incrementAndGet();
                    LOGGER.debug("[Webhooks] Sent {} to {}", event, webhook.name());
                }
            });
            trackFuture(trackedFuture);

        } catch (Exception e) {
            handleFailure(webhook, event, payload, attempt, errorMessage(e));
        }
    }

    private void handleFailure(WebhookConfig webhook, WebhookEvent event,
                               WebhookPayload payload, int attempt, String error) {
        if (attempt < webhook.maxRetries()) {
            // Queue for retry
            PendingDelivery pending = new PendingDelivery(
                webhook.id(),
                event,
                payload,
                attempt + 1,
                Instant.now().plus(INITIAL_RETRY_DELAY.multipliedBy((long) Math.pow(2, attempt)))
            );

            List<PendingDelivery> queue = pendingDeliveries
                .computeIfAbsent(webhook.id(), k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (queue) {
                if (queue.size() >= MAX_QUEUE_SIZE) {
                    totalFailed.incrementAndGet();
                    LOGGER.error("[Webhooks] Retry queue full for {}. Dropping event {}",
                        webhook.name(), event);
                    return;
                }
                queue.add(pending);
            }

            totalRetried.incrementAndGet();
            LOGGER.warn("[Webhooks] Webhook {} failed (attempt {}), will retry: {}",
                webhook.name(), attempt + 1, error);
        } else {
            totalFailed.incrementAndGet();
            LOGGER.error("[Webhooks] Webhook {} failed after {} attempts: {}",
                webhook.name(), attempt + 1, error);
        }
    }

    private void trackFuture(CompletableFuture<?> future) {
        inflightRequests.removeIf(CompletableFuture::isDone);
        inflightRequests.add(future);
    }

    private synchronized @Nullable HttpClient getHttpClient() {
        return httpClient;
    }

    private synchronized @Nullable ExecutorService getExecutor() {
        return executor;
    }

    private String errorMessage(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        String message = error.getMessage();
        return message != null ? message : error.getClass().getSimpleName();
    }

    private void processRetries() {
        if (!running) {
            return;
        }

        ExecutorService executor = getExecutor();
        if (executor == null) {
            LOGGER.warn("[Webhooks] Retry processing skipped; executor not initialized");
            return;
        }

        Instant now = Instant.now();

        for (Map.Entry<UUID, List<PendingDelivery>> entry : pendingDeliveries.entrySet()) {
            UUID webhookId = entry.getKey();
            List<PendingDelivery> pending = entry.getValue();

            if (pending == null || pending.isEmpty()) {
                continue;
            }

            WebhookConfig webhook = webhooks.get(webhookId);
            if (webhook == null || !webhook.enabled()) {
                pending.clear();
                continue;
            }

            List<PendingDelivery> toRetry = new ArrayList<>();
            pending.removeIf(p -> {
                if (p.retryAfter().isBefore(now)) {
                    toRetry.add(p);
                    return true;
                }
                return false;
            });

            for (PendingDelivery delivery : toRetry) {
                executor.execute(() -> sendWebhook(webhook, delivery.event(), delivery.payload(), delivery.attempt()));
            }
        }
    }

    // ============================================================================
    // FORMATTING
    // ============================================================================

    private String formatPayload(WebhookFormat format, WebhookEvent event, WebhookPayload payload) {
        return switch (format) {
            case JSON -> formatJson(event, payload);
            case DISCORD -> formatDiscord(event, payload);
            case SLACK -> formatSlack(event, payload);
        };
    }

    private String formatJson(WebhookEvent event, WebhookPayload payload) {
        return gson.toJson(Map.of(
            "event", event.name(),
            "timestamp", payload.timestamp().toString(),
            "data", payload.data()
        ));
    }

    private String formatDiscord(WebhookEvent event, WebhookPayload payload) {
        String color = getEventColor(event);
        String title = getEventTitle(event);
        String description = formatEventDescription(event, payload);

        // Discord embed format
        Map<String, Object> embed = Map.of(
            "title", title,
            "description", description,
            "color", Integer.parseInt(color, 16),
            "timestamp", payload.timestamp().toString(),
            "footer", Map.of("text", "DevMod Mailbox")
        );

        return gson.toJson(Map.of("embeds", List.of(embed)));
    }

    private String formatSlack(WebhookEvent event, WebhookPayload payload) {
        String title = getEventTitle(event);
        String description = formatEventDescription(event, payload);

        // Slack block format
        Map<String, Object> block = Map.of(
            "type", "section",
            "text", Map.of(
                "type", "mrkdwn",
                "text", "*" + title + "*\n" + description
            )
        );

        return gson.toJson(Map.of("blocks", List.of(block)));
    }

    private String getContentType(WebhookFormat format) {
        return switch (format) {
            case JSON, DISCORD, SLACK -> "application/json";
        };
    }

    private String getEventColor(WebhookEvent event) {
        return switch (event) {
            case MESSAGE_SENT -> "3498db"; // Blue
            case MESSAGE_READ -> "2ecc71"; // Green
            case BROADCAST_SENT -> "9b59b6"; // Purple
            case ATTACHMENT_CLAIMED -> "f39c12"; // Orange
            case NEWS_PUBLISHED -> "1abc9c"; // Teal
            case ANOMALY_DETECTED -> "e74c3c"; // Red
        };
    }

    private String getEventTitle(WebhookEvent event) {
        return switch (event) {
            case MESSAGE_SENT -> "📬 Nuovo Messaggio Inviato";
            case MESSAGE_READ -> "✅ Messaggio Letto";
            case BROADCAST_SENT -> "📢 Broadcast Inviato";
            case ATTACHMENT_CLAIMED -> "🎁 Allegato Riscattato";
            case NEWS_PUBLISHED -> "📰 News Pubblicata";
            case ANOMALY_DETECTED -> "⚠️ Anomalia Rilevata";
        };
    }

    private String formatEventDescription(WebhookEvent event, WebhookPayload payload) {
        Map<String, Object> data = payload.data();

        return switch (event) {
            case MESSAGE_SENT -> String.format(
                "**Da:** %s%n**Oggetto:** %s%n**Allegato:** %s",
                data.get("sender_name"),
                data.get("subject"),
                Boolean.TRUE.equals(data.get("has_attachment")) ? "Sì" : "No"
            );
            case MESSAGE_READ -> String.format(
                "**Messaggio ID:** %s",
                data.get("message_id")
            );
            case BROADCAST_SENT -> String.format(
                "**Oggetto:** %s%n**Destinatari:** %s%n**Mittente:** %s",
                data.get("subject"),
                data.get("recipient_count"),
                data.get("sender_name")
            );
            case ATTACHMENT_CLAIMED -> String.format(
                "**Tipo:** %s%n**Messaggio ID:** %s",
                data.get("attachment_type"),
                data.get("message_id")
            );
            case NEWS_PUBLISHED -> String.format(
                "**Titolo:** %s%n**Autore:** %s",
                data.get("title"),
                data.get("author_name")
            );
            case ANOMALY_DETECTED -> String.format(
                "**Tipo:** %s%n**Descrizione:** %s",
                data.get("anomaly_type"),
                data.get("description")
            );
        };
    }

    // ============================================================================
    // STATISTICS
    // ============================================================================

    /**
     * Get webhook statistics.
     */
    public WebhookStats getStats() {
        int pendingCount = pendingDeliveries.values().stream()
            .mapToInt(List::size)
            .sum();

        return new WebhookStats(
            webhooks.size(),
            (int) webhooks.values().stream().filter(WebhookConfig::enabled).count(),
            totalSent.get(),
            totalFailed.get(),
            totalRetried.get(),
            pendingCount,
            running
        );
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    /**
     * Webhook events that can trigger notifications.
     */
    public enum WebhookEvent {
        MESSAGE_SENT,
        MESSAGE_READ,
        BROADCAST_SENT,
        ATTACHMENT_CLAIMED,
        NEWS_PUBLISHED,
        ANOMALY_DETECTED
    }

    /**
     * Webhook output format.
     */
    public enum WebhookFormat {
        JSON,
        DISCORD,
        SLACK
    }

    /**
     * Webhook configuration.
     */
    public record WebhookConfig(
        UUID id,
        String name,
        String url,
        Set<WebhookEvent> events,
        WebhookFormat format,
        @Nullable String secret,
        boolean enabled,
        int maxRetries,
        Instant createdAt
    ) {
        /**
         * Create a config for all events.
         */
        public static WebhookConfig forAllEvents(String name, String url, WebhookFormat format) {
            return new WebhookConfig(
                UUID.randomUUID(),
                name,
                url,
                EnumSet.allOf(WebhookEvent.class),
                format,
                null,
                true,
                MAX_RETRIES,
                Instant.now()
            );
        }
    }

    /**
     * Webhook payload.
     */
    public record WebhookPayload(
        WebhookEvent event,
        Instant timestamp,
        Map<String, Object> data
    ) {}

    /**
     * Pending delivery for retry.
     */
    private record PendingDelivery(
        UUID webhookId,
        WebhookEvent event,
        WebhookPayload payload,
        int attempt,
        Instant retryAfter
    ) {}

    /**
     * Webhook statistics.
     */
    public record WebhookStats(
        int totalWebhooks,
        int enabledWebhooks,
        long totalSent,
        long totalFailed,
        long totalRetried,
        int pendingRetries,
        boolean running
    ) {}
}
