package com.devmod.client.telemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UiTelemetry {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Track open timestamps for automatic duration calculation
    private static final Map<String, Long> openTimestamps = new HashMap<>();

    private UiTelemetry() {}

    /**
     * Record a screen open event.
     *
     * @param category The UI category (e.g., "editor", "settings", "testing")
     * @param screen The screen identifier (e.g., "item_editor", "unified_settings")
     */
    public static void screenOpened(@Nonnull String category, @Nonnull String screen) {
        screenOpened(category, screen, null);
    }

    /**
     * Record a screen open event with additional context.
     *
     * @param category The UI category
     * @param screen The screen identifier
     * @param context Optional context data (e.g., item being edited)
     */
    public static void screenOpened(@Nonnull String category, @Nonnull String screen,
                                     @Nullable Map<String, Object> context) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(screen, "screen");

        String key = category + "." + screen;
        openTimestamps.put(key, System.currentTimeMillis());

        StringBuilder json = new StringBuilder();
        json.append("{\"category\":\"").append(escape(category)).append("\"");
        json.append(",\"screen\":\"").append(escape(screen)).append("\"");

        if (context != null && !context.isEmpty()) {
            json.append(",\"context\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escape(entry.getKey())).append("\":");
                appendValue(json, entry.getValue());
                first = false;
            }
            json.append("}");
        }
        json.append("}");

        if (ClientTelemetryBuffer.INSTANCE.isEnabled()) {
            ClientTelemetryBuffer.INSTANCE.queueScreenOpen(json.toString());
        }

        LOGGER.debug("[UiTelemetry] Screen opened: {}.{}", category, screen);
    }

    /**
     * Record a screen close event with automatic duration calculation.
     *
     * @param category The UI category
     * @param screen The screen identifier
     */
    public static void screenClosed(@Nonnull String category, @Nonnull String screen) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(screen, "screen");

        String key = category + "." + screen;
        Long openTime = openTimestamps.remove(key);
        long durationMs = openTime != null ? System.currentTimeMillis() - openTime : 0;

        screenClosed(category, screen, durationMs, null);
    }

    /**
     * Record a screen close event with explicit duration and context.
     *
     * @param category The UI category
     * @param screen The screen identifier
     * @param durationMs Time the screen was open in milliseconds
     * @param context Optional context data (e.g., changes saved)
     */
    public static void screenClosed(@Nonnull String category, @Nonnull String screen,
                                     long durationMs, @Nullable Map<String, Object> context) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(screen, "screen");

        // Clean up timestamp map entry if not already removed
        String key = category + "." + screen;
        openTimestamps.remove(key);

        StringBuilder json = new StringBuilder();
        json.append("{\"category\":\"").append(escape(category)).append("\"");
        json.append(",\"screen\":\"").append(escape(screen)).append("\"");
        json.append(",\"durationMs\":").append(durationMs);

        if (context != null && !context.isEmpty()) {
            json.append(",\"context\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escape(entry.getKey())).append("\":");
                appendValue(json, entry.getValue());
                first = false;
            }
            json.append("}");
        }
        json.append("}");

        if (ClientTelemetryBuffer.INSTANCE.isEnabled()) {
            ClientTelemetryBuffer.INSTANCE.queueScreenClose(json.toString());
        }

        LOGGER.debug("[UiTelemetry] Screen closed: {}.{} ({}ms)", category, screen, durationMs);
    }

    /**
     * Record a UI action event.
     *
     * @param category The UI category
     * @param screen The screen identifier
     * @param action The action performed (e.g., "save", "reset", "apply_preset")
     */
    public static void action(@Nonnull String category, @Nonnull String screen, @Nonnull String action) {
        action(category, screen, action, null);
    }

    /**
     * Record a UI action event with context.
     *
     * @param category The UI category
     * @param screen The screen identifier
     * @param action The action performed
     * @param context Optional context data
     */
    public static void action(@Nonnull String category, @Nonnull String screen,
                              @Nonnull String action, @Nullable Map<String, Object> context) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(action, "action");

        StringBuilder json = new StringBuilder();
        json.append("{\"category\":\"").append(escape(category)).append("\"");
        json.append(",\"screen\":\"").append(escape(screen)).append("\"");
        json.append(",\"action\":\"").append(escape(action)).append("\"");

        if (context != null && !context.isEmpty()) {
            json.append(",\"context\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escape(entry.getKey())).append("\":");
                appendValue(json, entry.getValue());
                first = false;
            }
            json.append("}");
        }
        json.append("}");

        if (ClientTelemetryBuffer.INSTANCE.isEnabled()) {
            ClientTelemetryBuffer.INSTANCE.queueUiAction(json.toString());
        }

        LOGGER.debug("[UiTelemetry] Action: {}.{}.{}", category, screen, action);
        if (isDevBuild() && context != null && !context.isEmpty()) {
            Object reasonKey = context.get("reasonKey");
            Object helpKey = context.get("helpKey");
            if (reasonKey != null || helpKey != null) {
                LOGGER.debug("[UiTelemetry] Action context: actionId={}, reasonKey={}, helpKey={}",
                    context.get("actionId"), reasonKey, helpKey);
            }
        }
    }

    private static boolean isDevBuild() {
        return !FMLEnvironment.production;
    }

    /**
     * Record an action error event with a summarized stacktrace.
     */
    public static void actionError(@Nonnull String category, @Nonnull String screen,
                                   @Nonnull String actionId, @Nonnull Throwable error) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(error, "error");

        Map<String, Object> context = new HashMap<>();
        context.put("actionId", actionId);
        context.put("error", error.getClass().getSimpleName());
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            context.put("message", error.getMessage());
        }
        context.put("stacktrace", summarizeStacktrace(error));
        action(category, screen, "action_error", context);
    }

    /**
     * Escape a string for JSON.
     */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Append a value to JSON builder.
     */
    private static void appendValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number) {
            json.append(value);
        } else if (value instanceof Boolean) {
            json.append(value);
        } else {
            json.append("\"").append(escape(value.toString())).append("\"");
        }
    }

    /**
     * Clear all tracked open timestamps.
     * Called on world leave to prevent memory leaks.
     */
    public static void reset() {
        openTimestamps.clear();
    }

    private static String summarizeStacktrace(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        StackTraceElement[] stack = root.getStackTrace();
        StringBuilder summary = new StringBuilder();
        summary.append(root.getClass().getSimpleName());
        if (root.getMessage() != null && !root.getMessage().isBlank()) {
            summary.append(": ").append(root.getMessage());
        }
        int limit = Math.min(3, stack.length);
        for (int i = 0; i < limit; i++) {
            StackTraceElement element = stack[i];
            summary.append(" @")
                .append(element.getClassName())
                .append(".")
                .append(element.getMethodName())
                .append(":")
                .append(element.getLineNumber());
        }
        return summary.toString();
    }
}
