package com.devmod.mailbox.attachment;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.RewardSystem;

/**
 * Attachment representing in-game currency (tokens, coins, etc.).
 * Integrates with the mod's economy/token system.
 */
public record CurrencyAttachment(
    String currencyType,
    int amount
) implements MailAttachment {

    private static final Logger LOGGER = LoggerFactory.getLogger(CurrencyAttachment.class);

    public static final String TYPE = "currency";

    /** Maximum amount per attachment. */
    public static final int MAX_AMOUNT = 1_000_000;

    // Common currency types
    public static final String TOKENS = "tokens";
    public static final String COINS = "coins";
    public static final String GEMS = "gems";
    public static final String BLOOD_GEMS = "blood_gems";
    public static final String PRESTIGE = "prestige";

    /** Allowed currency types (if empty, all are allowed). */
    private static final Set<String> ALLOWED_CURRENCIES = new CopyOnWriteArraySet<>();

    /** Maximum amounts per currency type (if set). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> MAX_AMOUNTS =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static String normalizeCurrencyType(String currencyType) {
        return currencyType.toLowerCase(Locale.ROOT);
    }

    private static String canonicalCurrencyType(String currencyType) {
        if (currencyType == null) {
            return "";
        }
        String normalized = normalizeCurrencyType(currencyType);
        return switch (normalized) {
            case "coin" -> COINS;
            case "gem" -> GEMS;
            case "bloodgems" -> BLOOD_GEMS;
            default -> normalized;
        };
    }

    private static boolean isKnownCurrency(String normalized) {
        return TOKENS.equals(normalized)
            || COINS.equals(normalized)
            || GEMS.equals(normalized)
            || BLOOD_GEMS.equals(normalized)
            || PRESTIGE.equals(normalized);
    }

    @Nullable
    public static RewardSystem.Currency toRewardCurrency(@Nullable String currencyType) {
        if (currencyType == null || currencyType.isBlank()) {
            return null;
        }
        String normalized = canonicalCurrencyType(currencyType);
        return switch (normalized) {
            case TOKENS -> RewardSystem.Currency.TOKENS;
            case COINS -> RewardSystem.Currency.COINS;
            case GEMS -> RewardSystem.Currency.GEMS;
            case BLOOD_GEMS -> RewardSystem.Currency.BLOOD_GEMS;
            case PRESTIGE -> RewardSystem.Currency.PRESTIGE;
            default -> null;
        };
    }

    // ============================================================================
    // CURRENCY RULES CONFIGURATION
    // ============================================================================

    /**
     * Add an allowed currency type.
     */
    public static void addAllowedCurrency(String currencyType) {
        String canonical = canonicalCurrencyType(currencyType);
        ALLOWED_CURRENCIES.add(canonical);
        LOGGER.info("[CurrencyAttachment] Allowed currency: {}", currencyType);
    }

    /**
     * Remove an allowed currency type.
     */
    public static void removeAllowedCurrency(String currencyType) {
        ALLOWED_CURRENCIES.remove(canonicalCurrencyType(currencyType));
    }

    /**
     * Set maximum amount for a currency type.
     */
    public static void setMaxAmount(String currencyType, int maxAmount) {
        MAX_AMOUNTS.put(canonicalCurrencyType(currencyType), maxAmount);
        LOGGER.info("[CurrencyAttachment] Max amount for {}: {}", currencyType, maxAmount);
    }

    /**
     * Get allowed currencies.
     */
    public static Set<String> getAllowedCurrencies() {
        return Set.copyOf(ALLOWED_CURRENCIES);
    }

    /**
     * Clear all currency rules.
     */
    public static void clearCurrencyRules() {
        ALLOWED_CURRENCIES.clear();
        MAX_AMOUNTS.clear();
    }

    // ============================================================================
    // VALIDATION
    // ============================================================================

    /**
     * Check if a currency type is allowed.
     */
    public static boolean isCurrencyAllowed(String currencyType) {
        if (currencyType == null || currencyType.isBlank()) {
            return false;
        }
        String normalized = normalizeCurrencyType(currencyType);
        String canonical = canonicalCurrencyType(normalized);
        // If no allowed currencies set, allow all known types
        if (ALLOWED_CURRENCIES.isEmpty()) {
            return isKnownCurrency(canonical);
        }
        return ALLOWED_CURRENCIES.contains(canonical);
    }

    /**
     * Get the maximum amount for a currency type.
     */
    public static int getMaxAmountFor(String currencyType) {
        Integer max = MAX_AMOUNTS.get(canonicalCurrencyType(currencyType));
        return max != null ? max : MAX_AMOUNT;
    }

    /**
     * Validate this attachment.
     *
     * @return error message if invalid, null if valid
     */
    @Nullable
    public String validate() {
        // Check currency type
        if (currencyType == null || currencyType.isBlank()) {
            return "Currency type is required";
        }

        if (!isCurrencyAllowed(currencyType)) {
            return "Currency type not allowed: " + currencyType;
        }

        // Check amount
        if (amount <= 0) {
            return "Invalid amount: " + amount;
        }

        int maxAmount = getMaxAmountFor(currencyType);
        if (amount > maxAmount) {
            return "Amount exceeds maximum (" + maxAmount + "): " + amount;
        }

        return null;
    }

    /**
     * Check if this attachment is valid.
     */
    public boolean isValid() {
        return validate() == null;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getDescription() {
        String normalized = currencyType != null ? canonicalCurrencyType(currencyType) : "";
        String typeName = switch (normalized) {
            case TOKENS -> "Tokens";
            case COINS -> "Coins";
            case GEMS -> "Gems";
            case BLOOD_GEMS -> "Blood Gems";
            case PRESTIGE -> "Prestige";
            default -> currencyType;
        };
        return amount + " " + typeName;
    }

    @Override
    public boolean canClaim(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return isValid();
    }

    @Override
    public ClaimResult claim(ServerPlayer player) {
        // Validate first
        String validationError = validate();
        if (validationError != null) {
            return ClaimResult.failure(validationError);
        }

        RewardSystem.Currency rewardCurrency = toRewardCurrency(currencyType);
        boolean success = rewardCurrency != null && grantCurrency(player, rewardCurrency, amount);

        if (success) {
            LOGGER.info("[CurrencyAttachment] Player {} claimed {} {}",
                player.getName().getString(), amount, currencyType);
            return ClaimResult.successResult("Received " + getDescription());
        } else {
            LOGGER.warn("[CurrencyAttachment] Failed to grant {} {} to player {}",
                amount, currencyType, player.getName().getString());
            return ClaimResult.failure("Failed to grant " + currencyType);
        }
    }

    private boolean grantCurrency(ServerPlayer player, RewardSystem.Currency currency, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        try {
            RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
            wallet.addCurrency(currency, amount);
            RewardSystem.INSTANCE.savePlayerWallet(wallet);
            return true;
        } catch (Exception e) {
            return false;
        }
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
