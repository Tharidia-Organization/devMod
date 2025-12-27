package com.devmod.notification;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Action data payload for opening the notification center on a specific tab.
 */
public record NotificationCenterActionData(
        String tab,
        @Nullable UUID entityId
) {
    private static final Gson GSON = new Gson();

    public NotificationCenterActionData {
        Objects.requireNonNull(tab, "tab");
    }

    public static NotificationCenterActionData forTab(String tab, @Nullable UUID entityId) {
        return new NotificationCenterActionData(tab, entityId);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    @Nullable
    public static NotificationCenterActionData fromJson(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(json, NotificationCenterActionData.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
