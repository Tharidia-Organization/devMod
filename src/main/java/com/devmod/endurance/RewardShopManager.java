package com.devmod.endurance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.RewardSystem.Currency;
import com.devmod.endurance.RewardSystem.PlayerWallet;
import com.devmod.endurance.RewardSystem.PurchaseResult;
import com.devmod.endurance.RewardSystem.ShopCategory;
import com.devmod.endurance.RewardSystem.ShopItem;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.util.I18n;

/**
 * Manages the shop system: item catalog, purchasing, and purchase effects.
 */
class RewardShopManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardShopManager.class);

    private final List<ShopItem> shopItems = new ArrayList<>();

    // DOUBLE-SPENDING FIX: Per-player locks to prevent concurrent purchases
    private final Map<UUID, Object> purchaseLocks = new ConcurrentHashMap<>();

    RewardShopManager() {
        initializeShopItems();
    }

    private Object getPurchaseLock(UUID playerId) {
        return purchaseLocks.computeIfAbsent(playerId, id -> new Object());
    }

    void clearPurchaseLocks() {
        purchaseLocks.clear();
    }

    /**
     * Attempt to purchase a shop item.
     * DOUBLE-SPENDING FIX: Synchronized per-player to prevent race conditions from lag/rapid clicks.
     */
    PurchaseResult purchaseItem(ServerPlayer player, String itemId, PlayerWallet wallet,
                                Runnable saveCallback) {
        UUID playerId = player.getUUID();

        // DOUBLE-SPENDING FIX: Synchronize on per-player lock to prevent concurrent purchases
        synchronized (getPurchaseLock(playerId)) {
            ShopItem item = shopItems.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElse(null);

            if (item == null) {
                return new PurchaseResult(false, "Item not found");
            }

            int currentOwned = wallet.getPurchaseCount(itemId);
            if (currentOwned >= item.getMaxPurchases()) {
                return new PurchaseResult(false, "Already at maximum purchases");
            }

            int currentCurrency = wallet.getCurrency(item.getCurrency());
            if (currentCurrency < item.getPrice()) {
                return new PurchaseResult(false, "Insufficient " + item.getCurrency().getDisplayName());
            }

            // Make purchase (now atomic within the lock)
            wallet.removeCurrency(item.getCurrency(), item.getPrice());
            wallet.recordPurchase(itemId);
            saveCallback.run();

            // Telemetry: record shop purchase
            EnduranceTelemetryService.INSTANCE.recordShopPurchase(
                playerId, itemId, item.getCurrency(), item.getPrice(), wallet.getPurchaseCount(itemId)
            );

            // Apply immediate effects
            applyPurchaseEffects(player, item);

            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.purchased", item.getDisplayName())
                .withStyle(style -> style.withColor(EnduranceColors.LootTier.UNCOMMON))));

            return new PurchaseResult(true, I18n.translate("devmod.reward.purchase_successful").getString());
        }
    }

    private void applyPurchaseEffects(ServerPlayer player, ShopItem item) {
        // Permanent stat upgrades would be applied here
        // For now, they're tracked in wallet and applied during quest start
        if (player != null && item != null) {
            LOGGER.debug("[RewardSystem] Purchase effects applied for {} ({})",
                player.getName().getString(), item.getId());
        }
    }

    List<ShopItem> getShopItems() {
        return Collections.unmodifiableList(shopItems);
    }

    List<ShopItem> getShopItemsByCategory(ShopCategory category) {
        return shopItems.stream()
            .filter(item -> item.getCategory() == category)
            .toList();
    }

    private void initializeShopItems() {
        // Permanent stat upgrades
        shopItems.add(new ShopItem("health_boost",
            "Vitality Enhancement", "Permanently increase max health by 2",
            Currency.TOKENS, 500, 5, ShopCategory.STATS));

        shopItems.add(new ShopItem("damage_boost",
            "Strength Enhancement", "Permanently increase attack damage by 5%",
            Currency.TOKENS, 750, 3, ShopCategory.STATS));

        shopItems.add(new ShopItem("speed_boost",
            "Agility Enhancement", "Permanently increase movement speed by 5%",
            Currency.TOKENS, 600, 3, ShopCategory.STATS));

        // Starting perks
        shopItems.add(new ShopItem("start_with_shield",
            "Guardian's Gift", "Start quests with a free shield perk",
            Currency.PRESTIGE, 5, 1, ShopCategory.PERKS));

        shopItems.add(new ShopItem("start_with_lifesteal",
            "Vampire's Kiss", "Start quests with minor lifesteal",
            Currency.BLOOD_GEMS, 20, 1, ShopCategory.PERKS));

        shopItems.add(new ShopItem("extra_perk_slot",
            "Expanded Mind", "Gain an additional perk slot",
            Currency.PRESTIGE, 10, 2, ShopCategory.PERKS));

        // Quality of life
        shopItems.add(new ShopItem("respawn_tokens",
            "Phoenix Feathers", "Get 3 free respawns per quest",
            Currency.TOKENS, 1000, 3, ShopCategory.UTILITY));

        shopItems.add(new ShopItem("loot_luck",
            "Fortune's Favor", "Increase rare loot chance by 10%",
            Currency.TOKENS, 800, 5, ShopCategory.UTILITY));

        shopItems.add(new ShopItem("token_multiplier",
            "Golden Touch", "Earn 10% more tokens from quests",
            Currency.PRESTIGE, 8, 5, ShopCategory.UTILITY));

        // Cosmetics (placeholder)
        shopItems.add(new ShopItem("title_endurance_master",
            "Title: Endurance Master", "Unlock the 'Endurance Master' title",
            Currency.PRESTIGE, 25, 1, ShopCategory.COSMETICS));

        shopItems.add(new ShopItem("aura_flame",
            "Flame Aura", "Display a flame particle effect",
            Currency.BLOOD_GEMS, 50, 1, ShopCategory.COSMETICS));
    }
}
