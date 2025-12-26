package com.devmod.mailbox.attachment;

import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerPlayer;

/**
 * Interface for mailbox message attachments.
 * Attachments can be items, currency, or other rewards that players can claim.
 */
public interface MailAttachment {

    /**
     * Get the type identifier for this attachment.
     */
    String getType();

    /**
     * Get a human-readable description of this attachment.
     */
    String getDescription();

    /**
     * Check if this attachment can be claimed by the player.
     * May check inventory space, permissions, etc.
     *
     * @param player the player attempting to claim
     * @return true if the attachment can be claimed
     */
    boolean canClaim(ServerPlayer player);

    /**
     * Attempt to give this attachment to the player.
     *
     * @param player the player to give the attachment to
     * @return result of the claim attempt
     */
    ClaimResult claim(ServerPlayer player);

    /**
     * Serialize this attachment to JSON for storage.
     */
    JsonObject toJson();

    /**
     * Parse an attachment from JSON data.
     *
     * @param json the JSON string
     * @return the parsed attachment, or null if invalid
     */
    @Nullable
    static MailAttachment fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String type = obj.get("type").getAsString();

            return switch (type) {
                case "item" -> ItemAttachment.fromJson(obj);
                case "currency" -> CurrencyAttachment.fromJson(obj);
                case "composite" -> CompositeAttachment.fromJson(obj);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse multiple attachments from JSON array string.
     */
    static List<MailAttachment> parseAttachments(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            var array = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            return array.asList().stream()
                .map(e -> fromJson(e.toString()))
                .filter(a -> a != null)
                .toList();
        } catch (Exception e) {
            // Try single attachment
            MailAttachment single = fromJson(json);
            return single != null ? List.of(single) : List.of();
        }
    }

    /**
     * Result of a claim attempt.
     */
    record ClaimResult(boolean success, @Nullable String message) {
        public static ClaimResult successResult() {
            return new ClaimResult(true, null);
        }

        public static ClaimResult successResult(String message) {
            return new ClaimResult(true, message);
        }

        public static ClaimResult failure(String message) {
            return new ClaimResult(false, message);
        }
    }
}
