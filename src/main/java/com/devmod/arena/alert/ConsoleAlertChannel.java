package com.devmod.arena.alert;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Console alert channel for local visibility.
 */
public class ConsoleAlertChannel implements AlertRouter.AlertChannel {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final String id;
    private final boolean critical;

    public ConsoleAlertChannel() {
        this("console", false);
    }

    public ConsoleAlertChannel(String id, boolean critical) {
        this.id = Objects.requireNonNull(id, "id");
        this.critical = critical;
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
        if (context == null) {
            return false;
        }

        String timestamp = context.timestamp() != null ? TS_FORMAT.format(context.timestamp()) : "unknown";
        String message = context.message() != null ? context.message() : "";
        String component = context.component() != null ? context.component() : "unknown";
        String errorId = context.errorId() != null ? context.errorId().toString() : "unknown";
        String contextIds = formatContextIds(context.metadata());
        String payload = String.format("[%s][%s][%s][%s] %s%s",
            timestamp, context.severity(), errorId, component, message, contextIds);

        if (context.severity() == ErrorContext.Severity.ERROR
            || context.severity() == ErrorContext.Severity.CRITICAL) {
            System.err.println(payload);
        } else {
            System.out.println(payload);
        }

        if (context.metadata() != null && !context.metadata().isEmpty()) {
            System.out.println("  meta=" + context.metadata());
        }

        return true;
    }

    private static String formatContextIds(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        Object templateId = metadata.get("templateId");
        if (templateId != null) {
            parts.add("templateId=" + templateId);
        }
        Object instanceId = metadata.get("instanceId");
        if (instanceId != null) {
            parts.add("instanceId=" + instanceId);
        }
        Object playerId = metadata.get("playerId");
        if (playerId != null) {
            parts.add("playerId=" + playerId);
        }
        Object arenaId = metadata.get("arenaId");
        if (arenaId != null) {
            parts.add("arenaId=" + arenaId);
        }

        if (parts.isEmpty()) {
            return "";
        }
        return " (" + String.join(", ", parts) + ")";
    }
}
