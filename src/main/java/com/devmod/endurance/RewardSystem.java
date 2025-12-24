package com.devmod.endurance;

import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.DevMod;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.util.I18n;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Reward System for Endurance Quests.
 *
 * Features:
 * - Endurance Tokens (currency) earned based on performance
 * - Tiered loot drops with rarities
 * - Global player progression and unlocks
 * - Shop system for permanent upgrades
 * - Achievement-based bonus rewards
 */
public class RewardSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardSystem.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final RewardSystem INSTANCE = new RewardSystem();

    // Player currency and progression
    private final Map<UUID, PlayerWallet> playerWallets = new ConcurrentHashMap<>();

    // DOUBLE-SPENDING FIX: Per-player locks to prevent concurrent purchases
    private final Map<UUID, Object> purchaseLocks = new ConcurrentHashMap<>();

    private Object getPurchaseLock(UUID playerId) {
        return purchaseLocks.computeIfAbsent(playerId, id -> new Object());
    }

    // Loot tables
    private final Map<LootTier, List<LootEntry>> lootTables = new EnumMap<>(LootTier.class);

    // Shop items
    private final List<ShopItem> shopItems = new ArrayList<>();

    // Achievements
    private final Map<String, Achievement> achievements = new LinkedHashMap<>();

    // Data persistence
    private Path dataDirectory;
    private final Random random = new Random();
    private static final double MIN_TEMPLATE_DIFFICULTY_MULTIPLIER = 0.75;
    private static final double MAX_TEMPLATE_DIFFICULTY_MULTIPLIER = 1.5;
    private static final double MAX_TOTAL_REWARD_MULTIPLIER = 5.0;

    // ========== Currency System ==========

    /**
     * Currency types in the system.
     */
    public enum Currency {
        TOKENS("Endurance Tokens", 0xFFD700, "tokens"),      // Main currency from quests
        PRESTIGE("Prestige Points", 0xFF00FF, "prestige"),    // Earned from completing hard content
        BLOOD_GEMS("Blood Gems", 0xFF3333, "blood_gems");     // Rare currency from bosses

        public final String displayName;
        public final int color;
        public final String key;

        Currency(String displayName, int color, String key) {
            this.displayName = displayName;
            this.color = color;
            this.key = key;
        }
    }

    /**
     * Loot rarity tiers.
     */
    public enum LootTier {
        COMMON(0xAAAAAA, 60.0f, "Common"),
        UNCOMMON(0x55FF55, 25.0f, "Uncommon"),
        RARE(0x5555FF, 10.0f, "Rare"),
        EPIC(0xAA00AA, 4.0f, "Epic"),
        LEGENDARY(0xFFAA00, 1.0f, "Legendary"),
        MYTHIC(0xFF5555, 0.0f, "Mythic"); // Never drops randomly, only from achievements

        public final int color;
        public final float dropWeight;
        public final String displayName;

        LootTier(int color, float dropWeight, String displayName) {
            this.color = color;
            this.dropWeight = dropWeight;
            this.displayName = displayName;
        }
    }

    // ========== Initialization ==========

    private RewardSystem() {
        initializeLootTables();
        initializeShopItems();
        initializeAchievements();
    }

    public void initialize(Path configDir) {
        this.dataDirectory = configDir.resolve("rewards");
        try {
            Files.createDirectories(dataDirectory);
            loadPlayerWallets();
            LOGGER.info("[RewardSystem] Initialized with {} shop items, {} achievements",
                shopItems.size(), achievements.size());
        } catch (IOException e) {
            LOGGER.error("[RewardSystem] Failed to create data directory", e);
        }
    }

    // ========== Reward Calculation ==========

    /**
     * Calculate and award rewards for a completed quest.
     */
    public QuestRewards calculateQuestRewards(ServerPlayer player, EnduranceQuest quest,
                                               ComboSystem.ComboSession comboSession,
                                               MutatorSystem.MutatorSession mutatorSession,
                                               @javax.annotation.Nullable ArenaPolicy policy,
                                               @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        QuestRewards rewards = new QuestRewards();
        UUID playerId = player.getUUID();
        PlayerWallet wallet = getWallet(playerId);

        // Base tokens from quest performance
        int baseTokens = quest.getPointsEarnedThisSession();

        // Style bonus from combo system
        float styleMultiplier = 1.0f;
        if (comboSession != null) {
            ComboSystem.StyleRank peakRank = comboSession.getHighestRank();
            styleMultiplier = switch (peakRank) {
                case SSS -> 3.0f;
                case SS -> 2.5f;
                case S -> 2.0f;
                case A -> 1.5f;
                case B -> 1.25f;
                case C -> 1.1f;
                default -> 1.0f;
            };
            rewards.styleRank = peakRank;
        }

        // Mutator bonus
        float mutatorMultiplier = 1.0f;
        if (mutatorSession != null) {
            mutatorMultiplier = mutatorSession.getRewardMultiplier();
            rewards.activeMutators = mutatorSession.getActiveMutatorCount();
        }

        // Wave completion bonus
        int waveBonus = quest.getCurrentWave() * 50;

        // No-hit bonus (if implemented)
        boolean noHit = quest.getDamageTakenThisSession() < 1.0f;
        float noHitMultiplier = noHit ? 1.5f : 1.0f;

        // Speed bonus (under par time)
        float speedMultiplier = 1.0f;
        long parTime = quest.getTotalWaves() * 60000L; // 1 minute per wave as par
        if (quest.getSessionDuration() < parTime) {
            speedMultiplier = 1.0f + (1.0f - (float) quest.getSessionDuration() / parTime) * 0.5f;
        }

        String templateId = session != null ? session.getTemplateId() : null;
        boolean questCompleted = quest.getState() == EnduranceQuestState.COMPLETED;
        double policyMultiplier = 1.0;
        int streakCount = questCompleted ? wallet.updateCompletionStreak() : wallet.getCompletionStreak();

        boolean hasHazards = false;
        if (templateId != null) {
            ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
            ArenaTemplate template = registry != null ? registry.get(templateId).orElse(null) : null;
            hasHazards = template != null && template.hazards() != null && !template.hazards().isEmpty();
        }

        ArenaPolicy.RewardModifiers rewardModifiers = policy != null ? policy.rewardModifiers() : null;
        if (rewardModifiers != null) {
            policyMultiplier *= rewardModifiers.baseMultiplier();
            if (questCompleted && templateId != null) {
                boolean firstCompletion = !wallet.hasCompletedTemplate(templateId);
                if (firstCompletion && rewardModifiers.firstCompletionBonus() > 0) {
                    policyMultiplier *= (1.0 + rewardModifiers.firstCompletionBonus());
                }
                if (hasHazards && rewardModifiers.hazardBonus() > 0) {
                    policyMultiplier *= (1.0 + rewardModifiers.hazardBonus());
                }
                if (rewardModifiers.streakMultiplier() > 0 && streakCount > 1) {
                    policyMultiplier *= (1.0 + rewardModifiers.streakMultiplier() * (streakCount - 1));
                }
            }
        }

        double difficultyMultiplier = 1.0;
        if (policy != null) {
            difficultyMultiplier = Math.min(MAX_TEMPLATE_DIFFICULTY_MULTIPLIER,
                Math.max(MIN_TEMPLATE_DIFFICULTY_MULTIPLIER, policy.weight()));
        }

        double totalMultiplier = styleMultiplier * mutatorMultiplier * noHitMultiplier * speedMultiplier;
        totalMultiplier *= policyMultiplier * difficultyMultiplier;
        totalMultiplier = Math.min(MAX_TOTAL_REWARD_MULTIPLIER, Math.max(0.25, totalMultiplier));

        // Calculate final tokens
        int finalTokens = (int) (baseTokens * totalMultiplier);
        finalTokens += waveBonus;

        rewards.tokensEarned = finalTokens;
        rewards.baseTokens = baseTokens;
        rewards.styleMultiplier = styleMultiplier;
        rewards.mutatorMultiplier = mutatorMultiplier;
        rewards.noHitBonus = noHit;
        rewards.speedBonus = speedMultiplier > 1.0f;

        // Award tokens
        wallet.addCurrency(Currency.TOKENS, finalTokens);

        if (questCompleted && templateId != null) {
            wallet.recordTemplateCompletion(templateId);
        }

        // Telemetry: record tokens earned
        EnduranceTelemetryService.INSTANCE.recordCurrencyEarned(
            playerId, quest.getQuestId(), Currency.TOKENS, finalTokens, "quest_completion"
        );

        // Prestige for completing all waves
        if (quest.getState() == EnduranceQuestState.COMPLETED) {
            int prestigeEarned = quest.getTotalWaves() / 5;
            if (quest.isEndlessMode() && quest.getCurrentWave() >= 20) {
                prestigeEarned += quest.getCurrentWave() / 10;
            }
            wallet.addCurrency(Currency.PRESTIGE, prestigeEarned);
            rewards.prestigeEarned = prestigeEarned;

            // Telemetry: record prestige earned
            EnduranceTelemetryService.INSTANCE.recordCurrencyEarned(
                playerId, quest.getQuestId(), Currency.PRESTIGE, prestigeEarned, "quest_completion"
            );
        }

        // Blood gems from boss waves
        int bossWaves = quest.getBossWavesCompleted();
        if (bossWaves > 0) {
            int bloodGems = bossWaves * (random.nextInt(3) + 1);
            wallet.addCurrency(Currency.BLOOD_GEMS, bloodGems);
            rewards.bloodGemsEarned = bloodGems;

            // Telemetry: record blood gems earned
            EnduranceTelemetryService.INSTANCE.recordCurrencyEarned(
                playerId, quest.getQuestId(), Currency.BLOOD_GEMS, bloodGems, "boss_waves"
            );
        }

        // Generate loot drops
        rewards.lootDrops = generateLootDrops(player, quest, comboSession);

        // Check achievements
        rewards.achievementsUnlocked = checkAchievements(player, quest, comboSession);

        // Save progress
        savePlayerWallets();

        // Send reward notification
        notifyPlayer(player, rewards);

        LOGGER.info("[RewardSystem] Player {} earned {} tokens, {} prestige, {} blood gems",
            player.getName().getString(), finalTokens, rewards.prestigeEarned, rewards.bloodGemsEarned);

        return rewards;
    }

    // ========== Loot Generation ==========

    /**
     * Generate loot drops based on performance.
     */
    private List<ItemStack> generateLootDrops(ServerPlayer player, EnduranceQuest quest,
                                               ComboSystem.ComboSession comboSession) {
        List<ItemStack> drops = new ArrayList<>();
        RegistryAccess registryAccess = player.level().registryAccess();

        // Number of drops based on waves cleared
        int dropCount = Math.min(quest.getCurrentWave(), 10);

        // Quality boost from combo performance
        float qualityBoost = 0;
        if (comboSession != null) {
            qualityBoost = comboSession.getHighestRank().ordinal() * 0.05f;
        }

        for (int i = 0; i < dropCount; i++) {
            LootTier tier = rollLootTier(qualityBoost);
            LootEntry entry = rollLootEntry(tier);
            if (entry != null) {
                ItemStack stack = entry.createStack(random, registryAccess);
                drops.add(stack);

                // Telemetry: record loot drop
                String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()))).toString();
                EnduranceTelemetryService.INSTANCE.recordLootDrop(
                    player.getUUID(), quest.getQuestId(), itemId, stack.getCount(), tier
                );
            }
        }

        // Bonus drop for completing all waves
        if (quest.getState() == EnduranceQuestState.COMPLETED) {
            // Guaranteed rare+ drop
            LootTier bonusTier = random.nextFloat() < 0.1f ? LootTier.LEGENDARY :
                                 random.nextFloat() < 0.3f ? LootTier.EPIC : LootTier.RARE;
            LootEntry bonusEntry = rollLootEntry(bonusTier);
            if (bonusEntry != null) {
                ItemStack bonusStack = bonusEntry.createStack(random, registryAccess);
                drops.add(bonusStack);

                // Telemetry: record bonus loot drop
                String bonusItemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(bonusStack.getItem()))).toString();
                EnduranceTelemetryService.INSTANCE.recordLootDrop(
                    player.getUUID(), quest.getQuestId(), bonusItemId, bonusStack.getCount(), bonusTier
                );
            }
        }

        return drops;
    }

    private LootTier rollLootTier(float qualityBoost) {
        float roll = random.nextFloat() * 100 - qualityBoost * 20;

        float cumulative = 0;
        for (LootTier tier : LootTier.values()) {
            if (tier.dropWeight <= 0) continue;
            cumulative += tier.dropWeight;
            if (roll < cumulative) {
                return tier;
            }
        }
        return LootTier.COMMON;
    }

    private LootEntry rollLootEntry(LootTier tier) {
        List<LootEntry> entries = lootTables.get(tier);
        if (entries == null || entries.isEmpty()) return null;
        return entries.get(random.nextInt(entries.size()));
    }

    // ========== Loot Tables ==========

    private void initializeLootTables() {
        // Common loot
        lootTables.put(LootTier.COMMON, Arrays.asList(
            new LootEntry(Items.IRON_INGOT, 3, 8),
            new LootEntry(Items.GOLD_INGOT, 2, 5),
            new LootEntry(Items.COAL, 8, 16),
            new LootEntry(Items.LEATHER, 4, 8),
            new LootEntry(Items.EXPERIENCE_BOTTLE, 2, 4),
            new LootEntry(Items.ARROW, 16, 32),
            new LootEntry(Items.BREAD, 4, 8)
        ));

        // Uncommon loot
        lootTables.put(LootTier.UNCOMMON, Arrays.asList(
            new LootEntry(Items.DIAMOND, 1, 3),
            new LootEntry(Items.EMERALD, 2, 5),
            new LootEntry(Items.GOLDEN_APPLE, 1, 2),
            new LootEntry(Items.ENDER_PEARL, 2, 4),
            new LootEntry(Items.BLAZE_ROD, 2, 4),
            new LootEntry(Items.IRON_SWORD, 1, 1),
            new LootEntry(Items.IRON_CHESTPLATE, 1, 1)
        ));

        // Rare loot
        lootTables.put(LootTier.RARE, Arrays.asList(
            new LootEntry(Items.DIAMOND, 3, 6),
            new LootEntry(Items.NETHERITE_SCRAP, 1, 2),
            new LootEntry(Items.ENCHANTED_GOLDEN_APPLE, 1, 1),
            new LootEntry(Items.DIAMOND_SWORD, 1, 1),
            new LootEntry(Items.DIAMOND_CHESTPLATE, 1, 1),
            new LootEntry(Items.ELYTRA, 1, 1),
            new LootEntry(Items.TOTEM_OF_UNDYING, 1, 1)
        ));

        // Epic loot
        lootTables.put(LootTier.EPIC, Arrays.asList(
            new LootEntry(Items.NETHERITE_INGOT, 1, 2),
            new LootEntry(Items.NETHERITE_SWORD, 1, 1),
            new LootEntry(Items.NETHERITE_CHESTPLATE, 1, 1),
            new LootEntry(Items.NETHER_STAR, 1, 1),
            new LootEntry(Items.DRAGON_EGG, 1, 1),
            new LootEntry(Items.BEACON, 1, 1)
        ));

        // Legendary loot
        lootTables.put(LootTier.LEGENDARY, Arrays.asList(
            new LootEntry(Items.NETHERITE_INGOT, 3, 5),
            new LootEntry(Items.NETHER_STAR, 2, 3),
            new LootEntry(Items.ENCHANTED_GOLDEN_APPLE, 3, 5),
            // These will have special enchantments applied
            new LootEntry(Items.NETHERITE_SWORD, 1, 1, true),
            new LootEntry(Items.NETHERITE_CHESTPLATE, 1, 1, true)
        ));

        // Mythic loot (achievement-only)
        lootTables.put(LootTier.MYTHIC, Arrays.asList(
            new LootEntry(Items.COMMAND_BLOCK, 1, 1), // Placeholder for special items
            new LootEntry(Items.NETHER_STAR, 5, 8)
        ));
    }

    // ========== Shop System ==========

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

    /**
     * Attempt to purchase a shop item.
     * DOUBLE-SPENDING FIX: Synchronized per-player to prevent race conditions from lag/rapid clicks.
     */
    public PurchaseResult purchaseItem(ServerPlayer player, String itemId) {
        UUID playerId = player.getUUID();

        // DOUBLE-SPENDING FIX: Synchronize on per-player lock to prevent concurrent purchases
        synchronized (getPurchaseLock(playerId)) {
            PlayerWallet wallet = getWallet(playerId);
            ShopItem item = shopItems.stream()
                .filter(i -> i.id.equals(itemId))
                .findFirst()
                .orElse(null);

            if (item == null) {
                return new PurchaseResult(false, "Item not found");
            }

            int currentOwned = wallet.getPurchaseCount(itemId);
            if (currentOwned >= item.maxPurchases) {
                return new PurchaseResult(false, "Already at maximum purchases");
            }

            int currentCurrency = wallet.getCurrency(item.currency);
            if (currentCurrency < item.price) {
                return new PurchaseResult(false, "Insufficient " + item.currency.displayName);
            }

            // Make purchase (now atomic within the lock)
            wallet.removeCurrency(item.currency, item.price);
            wallet.recordPurchase(itemId);
            savePlayerWallets();

            // Telemetry: record shop purchase
            EnduranceTelemetryService.INSTANCE.recordShopPurchase(
                playerId, itemId, item.currency, item.price, wallet.getPurchaseCount(itemId)
            );

            // Apply immediate effects
            applyPurchaseEffects(player, item);

            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.purchased", item.displayName)
                .withStyle(style -> style.withColor(0x55FF55))));

            return new PurchaseResult(true, I18n.translate("devmod.reward.purchase_successful").getString());
        }
    }

    private void applyPurchaseEffects(ServerPlayer player, ShopItem item) {
        // Permanent stat upgrades would be applied here
        // For now, they're tracked in wallet and applied during quest start
    }

    // ========== Achievements ==========

    private void initializeAchievements() {
        // Quest completion achievements
        achievements.put("first_blood", new Achievement("first_blood",
            "First Blood", "Complete your first Endurance Quest",
            Currency.TOKENS, 100, LootTier.UNCOMMON));

        achievements.put("wave_10", new Achievement("wave_10",
            "Warmed Up", "Complete 10 waves in a single quest",
            Currency.TOKENS, 250, LootTier.RARE));

        achievements.put("wave_20", new Achievement("wave_20",
            "Getting Serious", "Complete 20 waves in a single quest",
            Currency.PRESTIGE, 5, LootTier.EPIC));

        achievements.put("wave_50", new Achievement("wave_50",
            "Unstoppable", "Complete 50 waves in endless mode",
            Currency.PRESTIGE, 20, LootTier.LEGENDARY));

        // Combat achievements
        achievements.put("style_sss", new Achievement("style_sss",
            "Smokin' Sexy Style!", "Reach SSS rank in combat",
            Currency.TOKENS, 500, LootTier.EPIC));

        achievements.put("no_hit_10", new Achievement("no_hit_10",
            "Untouchable", "Complete 10 waves without taking damage",
            Currency.PRESTIGE, 10, LootTier.LEGENDARY));

        achievements.put("kill_100_wave", new Achievement("kill_100_wave",
            "Massacre", "Kill 100 mobs in a single wave",
            Currency.BLOOD_GEMS, 25, LootTier.RARE));

        // Boss achievements
        achievements.put("boss_slayer", new Achievement("boss_slayer",
            "Boss Slayer", "Defeat 10 boss waves",
            Currency.BLOOD_GEMS, 30, LootTier.EPIC));

        achievements.put("speed_boss", new Achievement("speed_boss",
            "Speed Demon", "Defeat a boss in under 60 seconds",
            Currency.PRESTIGE, 8, LootTier.EPIC));

        // Mutator achievements
        achievements.put("chaos_master", new Achievement("chaos_master",
            "Chaos Master", "Complete a quest with 5+ mutators active",
            Currency.PRESTIGE, 15, LootTier.LEGENDARY));

        achievements.put("cursed_run", new Achievement("cursed_run",
            "Cursed Warrior", "Complete a quest with only negative mutators",
            Currency.BLOOD_GEMS, 50, LootTier.LEGENDARY));
    }

    /**
     * Check and unlock any earned achievements.
     */
    private List<Achievement> checkAchievements(ServerPlayer player, EnduranceQuest quest,
                                                 ComboSystem.ComboSession comboSession) {
        List<Achievement> unlocked = new ArrayList<>();
        PlayerWallet wallet = getWallet(player.getUUID());

        // Check each achievement
        for (Achievement achievement : achievements.values()) {
            if (wallet.hasAchievement(achievement.id)) continue;

            boolean earned = switch (achievement.id) {
                case "first_blood" -> quest.getState() == EnduranceQuestState.COMPLETED;
                case "wave_10" -> quest.getCurrentWave() >= 10;
                case "wave_20" -> quest.getCurrentWave() >= 20;
                case "wave_50" -> quest.isEndlessMode() && quest.getCurrentWave() >= 50;
                case "style_sss" -> comboSession != null &&
                    comboSession.getHighestRank() == ComboSystem.StyleRank.SSS;
                case "no_hit_10" -> quest.getDamageTakenThisSession() < 1.0f &&
                    quest.getCurrentWave() >= 10;
                case "boss_slayer" -> quest.getBossWavesCompleted() >= 10;
                default -> false;
            };

            if (earned) {
                wallet.unlockAchievement(achievement.id);
                wallet.addCurrency(achievement.rewardCurrency, achievement.rewardAmount);
                unlocked.add(achievement);

                // Telemetry: record achievement unlocked
                EnduranceTelemetryService.INSTANCE.recordAchievementUnlocked(
                    player.getUUID(), quest.getQuestId(), achievement.id, achievement.displayName,
                    achievement.rewardCurrency, achievement.rewardAmount
                );

                // Send achievement notification
                player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.achievement_unlocked", achievement.displayName)
                    .withStyle(style -> style.withColor(achievement.lootTier.color))));

                // Play achievement sound
                Objects.requireNonNull(player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
                    Objects.requireNonNull(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), SoundSource.PLAYERS, 1.0f, 1.0f);

                LOGGER.info("[RewardSystem] Player {} unlocked achievement: {}",
                    player.getName().getString(), achievement.id);
            }
        }

        return unlocked;
    }

    // ========== Player Data ==========

    public PlayerWallet getWallet(UUID playerId) {
        return playerWallets.computeIfAbsent(playerId, id -> new PlayerWallet(id));
    }

    public int getPlayerCurrency(UUID playerId, Currency currency) {
        return getWallet(playerId).getCurrency(currency);
    }

    // ========== Persistence ==========

    private void loadPlayerWallets() {
        Path walletFile = dataDirectory.resolve("wallets.json");
        Path backupFile = dataDirectory.resolve("wallets.json.bak");

        // Try main file first, then backup
        Path fileToLoad = Files.exists(walletFile) ? walletFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, java.nio.charset.StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, PlayerWallet>>(){}.getType();
                Map<String, PlayerWallet> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    loaded.forEach((key, value) -> {
                        try {
                            playerWallets.put(UUID.fromString(key), value);
                        } catch (IllegalArgumentException e) {
                            LOGGER.warn("[RewardSystem] Invalid UUID in wallet file: {}", key);
                        }
                    });
                    LOGGER.info("[RewardSystem] Loaded wallets for {} players from {}",
                            playerWallets.size(), fileToLoad.getFileName());
                } else {
                    LOGGER.warn("[RewardSystem] Wallet file was empty or corrupted: {}", fileToLoad);
                }
            } catch (Exception e) {
                LOGGER.error("[RewardSystem] Failed to load player wallets from {}", fileToLoad, e);
            }
        }
    }

    private void savePlayerWallets() {
        if (dataDirectory == null) return;

        Path walletFile = dataDirectory.resolve("wallets.json");
        Path tempFile = dataDirectory.resolve("wallets.json.tmp");
        Path backupFile = dataDirectory.resolve("wallets.json.bak");

        try {
            // Ensure directory exists
            Files.createDirectories(dataDirectory);

            // Write to temp file first (atomic write pattern)
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(tempFile, java.nio.charset.StandardCharsets.UTF_8)) {
                Map<String, PlayerWallet> toSave = new HashMap<>();
                playerWallets.forEach((uuid, wallet) -> toSave.put(uuid.toString(), wallet));
                GSON.toJson(toSave, writer);
                writer.flush(); // CRITICAL: Force flush before close
            }

            // Create backup of existing file
            if (Files.exists(walletFile)) {
                Files.copy(walletFile, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Atomic move temp to final
            Files.move(tempFile, walletFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Fallback for filesystems that don't support atomic move
            try {
                Files.move(tempFile, walletFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[RewardSystem] Failed to save player wallets (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[RewardSystem] Failed to save player wallets", e);
        }
    }

    /**
     * Save all reward system data. Called on server shutdown.
     */
    public void saveAll() {
        savePlayerWallets();
        LOGGER.info("[RewardSystem] Saved all data ({} wallets)", playerWallets.size());
    }

    /**
     * Reset all reward system data. Called during full player reset.
     */
    public void resetAll() {
        LOGGER.info("[RewardSystem] Resetting all reward data...");

        // Clear in-memory data
        playerWallets.clear();
        purchaseLocks.clear();

        // Delete wallet file
        if (dataDirectory != null) {
            try {
                Path walletsFile = dataDirectory.resolve("player_wallets.json");
                Path backupFile = dataDirectory.resolve("player_wallets.json.bak");
                Files.deleteIfExists(walletsFile);
                Files.deleteIfExists(backupFile);
                LOGGER.info("[RewardSystem] All reward data reset successfully");
            } catch (IOException e) {
                LOGGER.error("[RewardSystem] Failed to delete wallet files", e);
            }
        }
    }

    // ========== Notifications ==========

    private void notifyPlayer(ServerPlayer player, QuestRewards rewards) {
        player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.quest_rewards")
            .withStyle(style -> style.withColor(0xFFD700))));

        player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.tokens_detail",
            rewards.tokensEarned, rewards.baseTokens, rewards.styleMultiplier, rewards.mutatorMultiplier)
            .withStyle(style -> style.withColor(Currency.TOKENS.color))));

        if (rewards.prestigeEarned > 0) {
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.prestige_earned", rewards.prestigeEarned)
                .withStyle(style -> style.withColor(Currency.PRESTIGE.color))));
        }

        if (rewards.bloodGemsEarned > 0) {
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.blood_gems_earned", rewards.bloodGemsEarned)
                .withStyle(style -> style.withColor(Currency.BLOOD_GEMS.color))));
        }

        if (rewards.noHitBonus) {
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.no_hit_bonus")
                .withStyle(style -> style.withColor(0x55FFFF))));
        }

        if (rewards.speedBonus) {
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.speed_bonus")
                .withStyle(style -> style.withColor(0x55FF55))));
        }

        if (!rewards.lootDrops.isEmpty()) {
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.reward.loot_drops", rewards.lootDrops.size())
                .withStyle(style -> style.withColor(0xAAAAAA))));

            // Drop items near player
            var level = Objects.requireNonNull(player.level());
            for (ItemStack stack : rewards.lootDrops) {
                ItemEntity itemEntity = new ItemEntity(
                    level, player.getX(), player.getY() + 0.5, player.getZ(),
                    Objects.requireNonNull(stack), 0, 0.2, 0);
                itemEntity.setPickUpDelay(0);
                level.addFreshEntity(itemEntity);
            }
        }
    }

    // ========== Query Methods ==========

    public List<ShopItem> getShopItems() {
        return Collections.unmodifiableList(shopItems);
    }

    public List<ShopItem> getShopItemsByCategory(ShopCategory category) {
        return shopItems.stream()
            .filter(item -> item.category == category)
            .toList();
    }

    public Collection<Achievement> getAllAchievements() {
        return Collections.unmodifiableCollection(achievements.values());
    }

    public List<Achievement> getUnlockedAchievements(UUID playerId) {
        PlayerWallet wallet = getWallet(playerId);
        return achievements.values().stream()
            .filter(a -> wallet.hasAchievement(a.id))
            .toList();
    }

    // ========== Inner Classes ==========

    /**
     * Player's currency and progression data.
     */
    public static class PlayerWallet {
        private final UUID playerId;
        private final Map<String, Integer> currencies = new HashMap<>();
        private final Map<String, Integer> purchases = new HashMap<>();
        private final Set<String> achievements = new HashSet<>();
        private final Map<String, Integer> templateCompletions = new HashMap<>();
        private int completionStreak = 0;
        private long lastCompletionDay = -1;

        public PlayerWallet(UUID playerId) {
            this.playerId = playerId;
            // Initialize currencies
            for (Currency c : Currency.values()) {
                currencies.put(c.key, 0);
            }
        }

        public int getCurrency(Currency currency) {
            return currencies.getOrDefault(currency.key, 0);
        }

        public void addCurrency(Currency currency, int amount) {
            currencies.merge(currency.key, amount, (a, b) -> a + b);
        }

        public void removeCurrency(Currency currency, int amount) {
            currencies.merge(currency.key, -amount, (a, b) -> a + b);
        }

        public int getPurchaseCount(String itemId) {
            return purchases.getOrDefault(itemId, 0);
        }

        public void recordPurchase(String itemId) {
            purchases.merge(itemId, 1, (a, b) -> a + b);
        }

        public boolean hasAchievement(String achievementId) {
            return achievements.contains(achievementId);
        }

        public void unlockAchievement(String achievementId) {
            achievements.add(achievementId);
        }

        public boolean hasCompletedTemplate(String templateId) {
            return templateId != null && templateCompletions.containsKey(templateId);
        }

        public void recordTemplateCompletion(String templateId) {
            if (templateId == null || templateId.isBlank()) {
                return;
            }
            templateCompletions.merge(templateId, 1, (a, b) -> a + b);
        }

        public int updateCompletionStreak() {
            long today = LocalDate.now().toEpochDay();
            if (lastCompletionDay == today) {
                return completionStreak;
            }
            if (lastCompletionDay == today - 1) {
                completionStreak++;
            } else {
                completionStreak = 1;
            }
            lastCompletionDay = today;
            return completionStreak;
        }

        public int getCompletionStreak() {
            return completionStreak;
        }

        public UUID getPlayerId() { return playerId; }
        public Map<String, Integer> getCurrencies() { return currencies; }
        public Map<String, Integer> getPurchases() { return purchases; }
        public Set<String> getAchievements() { return achievements; }
        public Map<String, Integer> getTemplateCompletions() { return templateCompletions; }
    }

    /**
     * Single loot entry in a loot table.
     */
    public static class LootEntry {
        private final Item item;
        private final int minCount;
        private final int maxCount;
        private final boolean enchanted;

        public LootEntry(Item item, int minCount, int maxCount) {
            this(item, minCount, maxCount, false);
        }

        public LootEntry(Item item, int minCount, int maxCount, boolean enchanted) {
            this.item = item;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.enchanted = enchanted;
        }

        public ItemStack createStack(Random random, RegistryAccess registryAccess) {
            int count = minCount + (maxCount > minCount ? random.nextInt(maxCount - minCount + 1) : 0);
            ItemStack stack = new ItemStack(Objects.requireNonNull(item), count);

            if (enchanted && registryAccess != null) {
                applyLegendaryEnchantments(stack, random, registryAccess);
            }

            return stack;
        }

        private void applyLegendaryEnchantments(ItemStack stack, Random random, RegistryAccess registryAccess) {
            var enchantRegistry = registryAccess.registryOrThrow(Objects.requireNonNull(Registries.ENCHANTMENT));
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(Objects.requireNonNull(ItemEnchantments.EMPTY));

            // Determine enchantments based on item type
            if (stack.getItem() == Items.NETHERITE_SWORD) {
                // Legendary sword enchantments
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:sharpness", 4 + random.nextInt(2)); // 4-5
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:looting", 2 + random.nextInt(2)); // 2-3
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:unbreaking", 3);
                if (random.nextFloat() < 0.3f) {
                    addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:fire_aspect", 2);
                }
                if (random.nextFloat() < 0.2f) {
                    addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:sweeping_edge", 3);
                }
            } else if (stack.getItem() == Items.NETHERITE_CHESTPLATE) {
                // Legendary armor enchantments
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:protection", 3 + random.nextInt(2)); // 3-4
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:unbreaking", 3);
                if (random.nextFloat() < 0.4f) {
                    addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:thorns", 2 + random.nextInt(2)); // 2-3
                }
                if (random.nextFloat() < 0.3f) {
                    addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:mending", 1);
                }
            }

            if (!mutable.toImmutable().isEmpty()) {
                stack.set(Objects.requireNonNull(net.minecraft.core.component.DataComponents.ENCHANTMENTS), mutable.toImmutable());
            }
        }

        private void addEnchantmentIfPresent(ItemEnchantments.Mutable mutable,
                                              net.minecraft.core.Registry<Enchantment> registry,
                                              String enchantmentId, int level) {
            ResourceLocation loc = Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(enchantmentId)));
            ResourceKey<Enchantment> key = Objects.requireNonNull(ResourceKey.create(Objects.requireNonNull(Registries.ENCHANTMENT), loc));
            registry.getHolder(key).ifPresent(holder -> mutable.set(Objects.requireNonNull(holder), level));
        }

        public boolean isEnchanted() {
            return enchanted;
        }
    }

    /**
     * Shop item for purchase.
     */
    public static class ShopItem {
        public final String id;
        public final String displayName;
        public final String description;
        public final Currency currency;
        public final int price;
        public final int maxPurchases;
        public final ShopCategory category;

        public ShopItem(String id, String displayName, String description,
                       Currency currency, int price, int maxPurchases, ShopCategory category) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.currency = currency;
            this.price = price;
            this.maxPurchases = maxPurchases;
            this.category = category;
        }
    }

    /**
     * Shop categories.
     */
    public enum ShopCategory {
        STATS("Stats", 0x55FF55),
        PERKS("Perks", 0x5555FF),
        UTILITY("Utility", 0xFFFF55),
        COSMETICS("Cosmetics", 0xFF55FF);

        public final String displayName;
        public final int color;

        ShopCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    /**
     * Achievement definition.
     */
    public static class Achievement {
        public final String id;
        public final String displayName;
        public final String description;
        public final Currency rewardCurrency;
        public final int rewardAmount;
        public final LootTier lootTier;

        public Achievement(String id, String displayName, String description,
                          Currency rewardCurrency, int rewardAmount, LootTier lootTier) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.rewardCurrency = rewardCurrency;
            this.rewardAmount = rewardAmount;
            this.lootTier = lootTier;
        }
    }

    /**
     * Result of a shop purchase.
     */
    public record PurchaseResult(boolean success, String message) {}

    /**
     * Rewards earned from a single wave completion.
     */
    public record WaveReward(
        int tokensEarned,
        int baseTokens,
        float styleMultiplier,
        float mutatorMultiplier,
        float directiveMultiplier,
        int bonusPoints
    ) {}

    /**
     * Calculate rewards for completing a single wave.
     *
     * @param waveNumber The wave just completed
     * @param quest The quest instance
     * @param comboSession Optional combo session for style bonuses
     * @param mutatorSession Optional mutator session for difficulty bonuses
     * @return WaveReward with calculated token amounts
     */
    public WaveReward calculateWaveReward(int waveNumber, EnduranceQuest quest,
                                           ComboSystem.ComboSession comboSession,
                                           MutatorSystem.MutatorSession mutatorSession) {
        return calculateWaveReward(waveNumber, quest, comboSession, mutatorSession, 1.0f);
    }

    public WaveReward calculateWaveReward(int waveNumber, EnduranceQuest quest,
                                          ComboSystem.ComboSession comboSession,
                                          MutatorSystem.MutatorSession mutatorSession,
                                          float directiveMultiplier) {
        // Base tokens per wave (scales with wave number)
        int baseTokens = 10 + (waveNumber * 5);

        // Style multiplier from combo performance
        float styleMultiplier = 1.0f;
        if (comboSession != null) {
            ComboSystem.StyleRank currentRank = comboSession.getCurrentRank();
            styleMultiplier = switch (currentRank) {
                case SSS -> 2.5f;
                case SS -> 2.0f;
                case S -> 1.75f;
                case A -> 1.5f;
                case B -> 1.25f;
                case C -> 1.1f;
                default -> 1.0f;
            };
        }

        // Mutator multiplier for harder settings
        float mutatorMultiplier = 1.0f;
        if (mutatorSession != null) {
            mutatorMultiplier = mutatorSession.getRewardMultiplier();
        }

        // Bonus for milestone waves (every 5th wave)
        int bonusPoints = 0;
        if (waveNumber % 5 == 0) {
            bonusPoints = waveNumber * 2;
        }

        // Boss wave bonus (every 10th wave typically)
        if (waveNumber % 10 == 0) {
            bonusPoints += 50;
        }

        float safeDirectiveMultiplier = directiveMultiplier > 0f ? directiveMultiplier : 1.0f;
        int totalTokens = (int) (baseTokens * styleMultiplier * mutatorMultiplier * safeDirectiveMultiplier)
            + bonusPoints;

        return new WaveReward(totalTokens, baseTokens, styleMultiplier, mutatorMultiplier,
            safeDirectiveMultiplier, bonusPoints);
    }

    /**
     * Rewards earned from a quest.
     */
    public static class QuestRewards {
        public int tokensEarned;
        public int baseTokens;
        public float styleMultiplier = 1.0f;
        public float mutatorMultiplier = 1.0f;
        public int prestigeEarned;
        public int bloodGemsEarned;
        public boolean noHitBonus;
        public boolean speedBonus;
        public ComboSystem.StyleRank styleRank;
        public int activeMutators;
        public List<ItemStack> lootDrops = new ArrayList<>();
        public List<Achievement> achievementsUnlocked = new ArrayList<>();
    }
}
