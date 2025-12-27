package com.devmod.notification;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Action data payload for party invite notifications.
 */
public record PartyInviteActionData(
        UUID inviteId,
        String senderName,
        int questTypeOrdinal,
        long expiresAt
) {
    private static final Gson GSON = new Gson();

    public PartyInviteActionData {
        Objects.requireNonNull(inviteId, "inviteId");
        Objects.requireNonNull(senderName, "senderName");
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    @Nullable
    public static PartyInviteActionData fromJson(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(json, PartyInviteActionData.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
