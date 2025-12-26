package com.devmod.mailbox.attachment;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerPlayer;

/**
 * Attachment containing multiple sub-attachments.
 * Useful for reward packages containing both items and currency.
 */
public record CompositeAttachment(
    List<MailAttachment> attachments
) implements MailAttachment {

    public static final String TYPE = "composite";

    public CompositeAttachment {
        attachments = List.copyOf(attachments);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getDescription() {
        if (attachments.isEmpty()) {
            return "Empty package";
        }
        if (attachments.size() == 1) {
            return attachments.get(0).getDescription();
        }
        return attachments.size() + " items";
    }

    @Override
    public boolean canClaim(ServerPlayer player) {
        // All attachments must be claimable
        return attachments.stream().allMatch(a -> a.canClaim(player));
    }

    @Override
    public ClaimResult claim(ServerPlayer player) {
        if (attachments.isEmpty()) {
            return ClaimResult.failure("Empty package");
        }

        List<String> claimed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (MailAttachment attachment : attachments) {
            ClaimResult result = attachment.claim(player);
            if (result.success()) {
                claimed.add(attachment.getDescription());
            } else {
                failed.add(attachment.getDescription() + ": " + result.message());
            }
        }

        if (failed.isEmpty()) {
            return ClaimResult.successResult("Received: " + String.join(", ", claimed));
        } else if (claimed.isEmpty()) {
            return ClaimResult.failure("Failed to claim all items");
        } else {
            return ClaimResult.successResult("Partially claimed. Failed: " + String.join(", ", failed));
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);

        JsonArray items = new JsonArray();
        for (MailAttachment attachment : attachments) {
            items.add(attachment.toJson());
        }
        obj.add("items", items);

        return obj;
    }

    /**
     * Create a CompositeAttachment from JSON.
     */
    @Nullable
    public static CompositeAttachment fromJson(JsonObject json) {
        try {
            JsonArray items = json.getAsJsonArray("items");
            List<MailAttachment> attachments = new ArrayList<>();

            for (var element : items) {
                MailAttachment attachment = MailAttachment.fromJson(element.toString());
                if (attachment != null) {
                    attachments.add(attachment);
                }
            }

            return new CompositeAttachment(attachments);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builder for creating composite attachments.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<MailAttachment> attachments = new ArrayList<>();

        public Builder add(MailAttachment attachment) {
            if (attachment != null) {
                attachments.add(attachment);
            }
            return this;
        }

        public Builder addItem(String itemId, int count) {
            ItemAttachment item = ItemAttachment.of(itemId, count);
            if (item != null) {
                attachments.add(item);
            }
            return this;
        }

        public Builder addTokens(int amount) {
            attachments.add(CurrencyAttachment.tokens(amount));
            return this;
        }

        public Builder addCoins(int amount) {
            attachments.add(CurrencyAttachment.coins(amount));
            return this;
        }

        public CompositeAttachment build() {
            return new CompositeAttachment(attachments);
        }
    }
}
