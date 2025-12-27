package com.devmod.notification;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Encodes/decodes notification params while preserving insertion order.
 */
public final class NotificationParamsCodec {

    private static final Gson GSON = new Gson();
    private static final Type ENTRY_LIST_TYPE = new TypeToken<List<ParamEntry>>() {}.getType();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private NotificationParamsCodec() {}

    public static String toJson(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        List<ParamEntry> entries = params.entrySet().stream()
            .map(entry -> new ParamEntry(entry.getKey(), entry.getValue()))
            .toList();
        return GSON.toJson(entries);
    }

    public static Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        String trimmed = json.trim();
        try {
            if (trimmed.startsWith("[")) {
                List<ParamEntry> entries = GSON.fromJson(trimmed, ENTRY_LIST_TYPE);
                if (entries == null || entries.isEmpty()) {
                    return Map.of();
                }
                Map<String, String> params = new LinkedHashMap<>();
                for (ParamEntry entry : entries) {
                    if (entry == null || entry.key() == null) {
                        continue;
                    }
                    params.put(entry.key(), entry.value());
                }
                return params;
            }
            if (trimmed.startsWith("{")) {
                Map<String, String> map = GSON.fromJson(trimmed, MAP_TYPE);
                if (map == null || map.isEmpty()) {
                    return Map.of();
                }
                return new LinkedHashMap<>(map);
            }
        } catch (Exception e) {
            return Map.of();
        }
        return Map.of();
    }

    public record ParamEntry(String key, String value) {}
}
