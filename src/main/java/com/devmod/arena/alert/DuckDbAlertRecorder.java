package com.devmod.arena.alert;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.devmod.telemetry.duckdb.DuckDBBatchWriter;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;

public final class DuckDbAlertRecorder implements AlertRouter.AlertDeliveryRecorder {

    private static final Gson GSON = new GsonBuilder().create();
    private final Set<UUID> recordedErrors = ConcurrentHashMap.newKeySet();

    @Override
    public void record(ErrorContext context,
                       AlertRouter.AlertChannel channel,
                       AlertRouter.DeliveryStatus status,
                       int attemptCount,
                       Instant nextRetryAt,
                       String errorMessage) {
        DuckDBBatchWriter batchWriter = DuckDBTelemetryService.INSTANCE.getBatchWriter();
        if (batchWriter == null || context == null) {
            return;
        }

        UUID errorId = context.errorId();
        if (recordedErrors.add(errorId)) {
            MetadataIds ids = extractIds(context.metadata());
            String stackFramesJson = context.stackFrames().isEmpty()
                ? null
                : GSON.toJson(context.stackFramesToJson());
            String metadataJson = serializeMetadata(context.metadata());

            batchWriter.queueArenaTemplateError(
                context.timestamp(),
                errorId,
                context.severity() != null ? context.severity().name() : null,
                context.errorType(),
                context.message(),
                context.component(),
                ids.templateId(),
                ids.arenaId(),
                ids.sessionId(),
                stackFramesJson,
                metadataJson
            );
        }

        String channelId = channel != null ? channel.getId() : null;
        String channelType = channel != null ? channel.getType() : null;
        boolean isCritical = channel != null && channel.isCritical();
        batchWriter.queueArenaTemplateAlert(
            Instant.now(),
            errorId,
            channelId,
            channelType,
            isCritical,
            status != null ? status.name() : null,
            attemptCount,
            nextRetryAt,
            errorMessage
        );
    }

    private static String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return GSON.toJson(sanitizeMetadata(metadata));
    }

    private static Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            sanitized.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return sanitized;
    }

    private static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key != null) {
                    nested.put(key.toString(), sanitizeValue(entry.getValue()));
                }
            }
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(sanitizeValue(item));
            }
            return items;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(sanitizeValue(Array.get(value, i)));
            }
            return items;
        }
        return value;
    }

    private static MetadataIds extractIds(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return MetadataIds.empty();
        }
        String templateId = stringValue(metadata.get("templateId"));
        if (templateId == null) {
            templateId = stringValue(metadata.get("template_id"));
        }
        UUID arenaId = uuidValue(metadata.get("arenaId"));
        if (arenaId == null) {
            arenaId = uuidValue(metadata.get("arena_id"));
        }
        UUID sessionId = uuidValue(metadata.get("sessionId"));
        if (sessionId == null) {
            sessionId = uuidValue(metadata.get("session_id"));
        }
        return new MetadataIds(templateId, arenaId, sessionId);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private record MetadataIds(String templateId, UUID arenaId, UUID sessionId) {
        private static MetadataIds empty() {
            return new MetadataIds(null, null, null);
        }
    }
}
