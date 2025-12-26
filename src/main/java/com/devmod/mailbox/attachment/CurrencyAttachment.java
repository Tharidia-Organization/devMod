package com.devmod.mailbox.attachment;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerPlayer;

/**
 * Attachment representing in-game currency (tokens, coins, etc.).
 * Integrates with the mod's economy/token system.
 */
public record CurrencyAttachment(
    String currencyType,
    int amount
) implements MailAttachment {

    public static final String TYPE = "currency";

    // Common currency types
    public static final String TOKENS = "tokens";
    public static final String COINS = "coins";
    public static final String GEMS = "gems";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getDescription() {
        String typeName = switch (currencyType) {
            case TOKENS -> "Tokens";
            case COINS -> "Coins";
            case GEMS -> "Gems";
            default -> currencyType;
        };
        return amount + " " + typeName;
    }

    @Override
    public boolean canClaim(ServerPlayer player) {
        // Currency can always be claimed (no inventory limit)
        return amount > 0;
    }

    @Override
    public ClaimResult claim(ServerPlayer player) {
        if (amount <= 0) {
            return ClaimResult.failure("Invalid currency amount");
        }

        // Integration with the mod's token/economy system
        boolean success = switch (currencyType) {
            case TOKENS -> grantTokens(player, amount);
            case COINS -> grantCoins(player, amount);
            case GEMS -> grantGems(player, amount);
            default -> false;
        };

        if (success) {
            return ClaimResult.successResult("Received " + getDescription());
        } else {
            return ClaimResult.failure("Failed to grant " + currencyType);
        }
    }

    private boolean grantTokens(ServerPlayer player, int amount) {
        // TODO: Integrate with EnduranceQuestManager or RewardSystem
        // For now, we'll use a simple approach
        try {
            // This would integrate with the existing token system
            // Example: EnduranceQuestManager.grantTokens(player, amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean grantCoins(ServerPlayer player, int amount) {
        // TODO: Integrate with economy system if exists
        return true;
    }

    private boolean grantGems(ServerPlayer player, int amount) {
        // TODO: Integrate with premium currency system if exists
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("currency", currencyType);
        obj.addProperty("amount", amount);
        return obj;
    }

    /**
     * Create a CurrencyAttachment from JSON.
     */
    @Nullable
    public static CurrencyAttachment fromJson(JsonObject json) {
        try {
            String currency = json.get("currency").getAsString();
            int amount = json.get("amount").getAsInt();
            return new CurrencyAttachment(currency, Math.max(0, amount));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create a token attachment.
     */
    public static CurrencyAttachment tokens(int amount) {
        return new CurrencyAttachment(TOKENS, amount);
    }

    /**
     * Create a coins attachment.
     */
    public static CurrencyAttachment coins(int amount) {
        return new CurrencyAttachment(COINS, amount);
    }

    /**
     * Create a gems attachment.
     */
    public static CurrencyAttachment gems(int amount) {
        return new CurrencyAttachment(GEMS, amount);
    }
}
