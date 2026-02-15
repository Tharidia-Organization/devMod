package com.devmod.endurance;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.devmod.DevMod;
import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.notification.NotificationService;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

public class RewardSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardSystem.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final RewardSystem INSTANCE = new RewardSystem();

    // Player currency and progression
    private final Map<UUID, PlayerWallet> playerWallets = new ConcurrentHashMap<>();

    // Data persistence
    private Path dataDirectory;
    private final Random random = new Random();
    private static final double MIN_TEMPLATE_DIFFICULTY_MULTIPLIER = 0.75;
    private static final double MAX_TEMPLATE_DIFFICULTY_MULTIPLIER = 1.5;
    private static final double MAX_TOTAL_REWARD_MULTIPLIER = 5.0;

    // Delegates
    private final RewardLootGenerator lootGenerator;
    private final RewardShopManager shopManager;
    private final RewardAchievementManager achievementManager;

    // ========== Currency System ==========

    /**
     * Currency types in the system.
     */
    public enum Currency {
        TOKENS("Endurance Tokens", EnduranceColors.Currency.TOKENS, "tokens"),
        COINS("Coins", EnduranceColors.Currency.COINS, "coins"),
        PRESTIGE("Prestige Points", EnduranceColors.Currency.PRESTIGE, "prestige"),
        GEMS("Gems", EnduranceColors.Currency.GEMS, "gems"),
        BLOOD_GEMS("Blood Gems", EnduranceColors.Currency.BLOOD_GEMS, "blood_gems");

        private final String displayName;
        private final int color;
        private final String key;

        Currency(String displayName, int color, String key) {
            this.displayName = displayName;
            this.color = color;
            this.key = key;
        }

        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }
        public String getKey() { return key; }
    }

    /**
     * Loot rarity tiers.
     */
    public enum LootTier {
        COMMON(EnduranceColors.LootTier.COMMON, 60.0f, "Common"),
        UNCOMMON(EnduranceColors.LootTier.UNCOMMON, 25.0f, "Uncommon"),
        RARE(EnduranceColors.LootTier.RARE, 10.0f, "Rare"),
        EPIC(EnduranceColors.LootTier.EPIC, 4.0f, "Epic"),
        LEGENDARY(EnduranceColors.LootTier.LEGENDARY, 1.0f, "Legendary"),
        MYTHIC(EnduranceColors.LootTier.MYTHIC, 0.0f, "Mythic");

        private final int color;
        private final float dropWeight;
        private final String displayName;

        LootTier(int color, float dropWeight, String displayName) {
            this.color = color;
            this.dropWeight = dropWeight;
            this.displayName = displayName;
        }

        public int getColor() { return color; }
        public float getDropWeight() { return dropWeight; }
        public String getDisplayName() { return displayName; }
    }

    // ========== Initialization ==========

    private RewardSystem() {
        lootGenerator = new RewardLootGenerator(random);
        shopManager = new RewardShopManager();
        achievementManager = new RewardAchievementManager();
    }

    public void initialize(Path configDir) {
        this.dataDirectory = configDir.resolve("rewards");
        try {
            Files.createDirectories(dataDirectory);
            loadPlayerWallets();
            LOGGER.info("[RewardSystem] Initialized with {} shop items, {} achievements",
                shopManager.getShopItems().size(), achievementManager.getAllAchievements().size());
        } catch (IOException e) {
            LOGGER.error("[RewardSystem] Failed to create data directory", e);
        }
    }

    // ========== Reward Calculation ==========

    /**
     * Calculate and award rewards for a completed quest.
     */
    public QuestRewards calculateQuestRewards(ServerPlayer player, EnduranceQuest quest,
                                               IComboSession comboSession,
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

        float bargainMultiplier = com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE
            .getRewardMultiplier(quest.getQuestId());

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
            ArenaTemplate template = registry.get(templateId).orElse(null);
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

        double totalMultiplier = styleMultiplier * mutatorMultiplier * noHitMultiplier * speedMultiplier * bargainMultiplier;
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

            // Award prestige and check for milestone unlocks
            List<String> newMilestoneIds = wallet.addCurrency(Currency.PRESTIGE, prestigeEarned);
            rewards.prestigeEarned = prestigeEarned;

            // Notify player of newly unlocked milestones
            for (String milestoneId : newMilestoneIds) {
                PrestigeMilestone.getAllMilestones().stream()
                    .filter(m -> m.getId().equals(milestoneId))
                    .findFirst()
                    .ifPresent(milestone -> notifyMilestoneUnlock(player, milestone));
            }

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

        // Generate loot drops (delegated)
        rewards.lootDrops = lootGenerator.generateLootDrops(player, quest, comboSession);

        // Check achievements (delegated)
        rewards.achievementsUnlocked = achievementManager.checkAchievements(player, quest, comboSession, wallet);

        // Save progress
        savePlayerWallets();

        // Send reward notification
        notifyPlayer(player, quest, rewards);

        LOGGER.info("[RewardSystem] Player {} earned {} tokens, {} prestige, {} blood gems",
            player.getName().getString(), finalTokens, rewards.prestigeEarned, rewards.bloodGemsEarned);

        return rewards;
    }

    // ========== Shop System (delegated) ==========

    /**
     * Attempt to purchase a shop item.
     */
    public PurchaseResult purchaseItem(ServerPlayer player, String itemId) {
        PlayerWallet wallet = getWallet(player.getUUID());
        return shopManager.purchaseItem(player, itemId, wallet, this::savePlayerWallets);
    }

    // ========== Player Data ==========

    @Nonnull
    public PlayerWallet getWallet(UUID playerId) {
        PlayerWallet wallet = Objects.requireNonNull(playerWallets.computeIfAbsent(playerId, id -> new PlayerWallet(id)));
        wallet.ensureCurrencyKeys();
        return wallet;
    }

    public int getPlayerCurrency(UUID playerId, Currency currency) {
        return getWallet(playerId).getCurrency(currency);
    }

    // ========== Persistence ==========

    private void loadPlayerWallets() {
        Path walletFile = dataDirectory.resolve("wallets.json");
        Path backupFile = dataDirectory.resolve("wallets.json.bak");

        Path fileToLoad = Files.exists(walletFile) ? walletFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, java.nio.charset.StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, PlayerWallet>>(){}.getType();
                Map<String, PlayerWallet> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    loaded.forEach((key, value) -> {
                        if (value == null) {
                            return;
                        }
                        try {
                            value.ensureCurrencyKeys();
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
            Files.createDirectories(dataDirectory);

            try (java.io.BufferedWriter writer = Files.newBufferedWriter(tempFile, java.nio.charset.StandardCharsets.UTF_8)) {
                Map<String, PlayerWallet> toSave = new HashMap<>();
                playerWallets.forEach((uuid, wallet) -> toSave.put(uuid.toString(), wallet));
                GSON.toJson(toSave, writer);
                writer.flush();
            }

            if (Files.exists(walletFile)) {
                Files.copy(walletFile, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tempFile, walletFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, walletFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[RewardSystem] Failed to save player wallets (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[RewardSystem] Failed to save player wallets", e);
        }
    }

    public void saveAll() {
        savePlayerWallets();
        LOGGER.info("[RewardSystem] Saved all data ({} wallets)", playerWallets.size());
    }

    public void savePlayerWallet(PlayerWallet wallet) {
        savePlayerWallets();
    }

    public void resetAll() {
        LOGGER.info("[RewardSystem] Resetting all reward data...");
        playerWallets.clear();
        shopManager.clearPurchaseLocks();

        if (dataDirectory != null) {
            try {
                Path walletsFile = dataDirectory.resolve("wallets.json");
                Path backupFile = dataDirectory.resolve("wallets.json.bak");
                Files.deleteIfExists(walletsFile);
                Files.deleteIfExists(backupFile);
                LOGGER.info("[RewardSystem] All reward data reset successfully");
            } catch (IOException e) {
                LOGGER.error("[RewardSystem] Failed to delete wallet files", e);
            }
        }
    }

    // ========== Notifications ==========

    private void notifyPlayer(ServerPlayer player, EnduranceQuest quest, QuestRewards rewards) {
        boolean completed = quest != null && quest.getState() == EnduranceQuestState.COMPLETED;
        String questName = quest != null ? quest.getDisplayName() : null;

        NotificationService.INSTANCE.notifyQuestRewards(player.getUUID(), rewards, completed, questName);

        if (!rewards.lootDrops.isEmpty()) {
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

    private void notifyMilestoneUnlock(ServerPlayer player, PrestigeMilestone milestone) {
        NotificationService.INSTANCE.notifyPrestigeMilestone(player.getUUID(), milestone);

        LOGGER.info("[RewardSystem] Player {} unlocked prestige milestone: {} ({})",
            player.getName().getString(), milestone.getId(), milestone.getType());
    }

    // ========== Query Methods ==========

    public List<ShopItem> getShopItems() {
        return shopManager.getShopItems();
    }

    public List<ShopItem> getShopItemsByCategory(ShopCategory category) {
        return shopManager.getShopItemsByCategory(category);
    }

    public Collection<Achievement> getAllAchievements() {
        return achievementManager.getAllAchievements();
    }

    public List<Achievement> getUnlockedAchievements(UUID playerId) {
        return achievementManager.getUnlockedAchievements(getWallet(playerId));
    }

    // ========== Wave Reward Calculation ==========

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

    public WaveReward calculateWaveReward(int waveNumber, EnduranceQuest quest,
                                           IComboSession comboSession,
                                           MutatorSystem.MutatorSession mutatorSession) {
        return calculateWaveReward(waveNumber, quest, comboSession, mutatorSession, 1.0f);
    }

    public WaveReward calculateWaveReward(int waveNumber, EnduranceQuest quest,
                                          IComboSession comboSession,
                                          MutatorSystem.MutatorSession mutatorSession,
                                          float directiveMultiplier) {
        int baseTokens = 10 + (waveNumber * 5);

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

        float mutatorMultiplier = 1.0f;
        if (mutatorSession != null) {
            mutatorMultiplier = mutatorSession.getRewardMultiplier();
        }

        int bonusPoints = 0;
        if (waveNumber % 5 == 0) {
            bonusPoints = waveNumber * 2;
        }
        if (waveNumber % 10 == 0) {
            bonusPoints += 50;
        }

        float safeDirectiveMultiplier = directiveMultiplier > 0f ? directiveMultiplier : 1.0f;
        int totalTokens = (int) (baseTokens * styleMultiplier * mutatorMultiplier * safeDirectiveMultiplier)
            + bonusPoints;

        return new WaveReward(totalTokens, baseTokens, styleMultiplier, mutatorMultiplier,
            safeDirectiveMultiplier, bonusPoints);
    }

    // ========== Inner Classes ==========

    /**
     * Player's currency and progression data.
     */
    public static class PlayerWallet {
        private final UUID playerId;
        private final Map<String, Integer> currencies = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, Integer> purchases = new java.util.concurrent.ConcurrentHashMap<>();
        private final Set<String> achievements = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final Map<String, Integer> templateCompletions = new java.util.concurrent.ConcurrentHashMap<>();
        private int completionStreak = 0;
        private long lastCompletionDay = -1;

        private int totalPrestigeEarned = 0;
        private final Set<String> unlockedMilestones = java.util.concurrent.ConcurrentHashMap.newKeySet();

        private int ascensionLevel = 0;
        private String ascensionTitle = null;
        private final Set<String> unlockedAscensionPerks = java.util.concurrent.ConcurrentHashMap.newKeySet();

        private transient volatile Object progressionLock;

        private Object getProgressionLock() {
            if (progressionLock == null) {
                synchronized (this) {
                    if (progressionLock == null) {
                        progressionLock = new Object();
                    }
                }
            }
            return progressionLock;
        }

        public PlayerWallet(UUID playerId) {
            this.playerId = playerId;
            for (Currency c : Currency.values()) {
                currencies.put(c.key, 0);
            }
        }

        void ensureCurrencyKeys() {
            for (Currency c : Currency.values()) {
                currencies.putIfAbsent(c.key, 0);
            }
        }

        public int getCurrency(Currency currency) {
            return currencies.getOrDefault(currency.key, 0);
        }

        public java.util.List<String> addCurrency(Currency currency, int amount) {
            currencies.merge(currency.key, amount, (a, b) -> a + b);

            if (currency == Currency.PRESTIGE && amount > 0) {
                int newTotal;
                int previousTotal;
                synchronized (getProgressionLock()) {
                    previousTotal = totalPrestigeEarned;
                    totalPrestigeEarned += amount;
                    newTotal = totalPrestigeEarned;
                }

                java.util.List<PrestigeMilestone> newMilestones =
                    PrestigeMilestone.getNewlyUnlockedMilestones(previousTotal, newTotal);

                java.util.List<String> newMilestoneIds = new java.util.ArrayList<>();
                for (PrestigeMilestone milestone : newMilestones) {
                    if (!unlockedMilestones.contains(milestone.getId())) {
                        unlockedMilestones.add(milestone.getId());
                        newMilestoneIds.add(milestone.getId());
                    }
                }
                return List.copyOf(newMilestoneIds);
            }
            return List.of();
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
            long today = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
            synchronized (getProgressionLock()) {
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
        }

        public int getCompletionStreak() {
            synchronized (getProgressionLock()) {
                return completionStreak;
            }
        }

        public UUID getPlayerId() { return playerId; }
        public Map<String, Integer> getCurrencies() { return currencies; }
        public Map<String, Integer> getPurchases() { return purchases; }
        public Set<String> getAchievements() { return achievements; }
        public Map<String, Integer> getTemplateCompletions() { return templateCompletions; }

        public int getTotalPrestigeEarned() {
            synchronized (getProgressionLock()) {
                return totalPrestigeEarned;
            }
        }
        public Set<String> getUnlockedMilestones() { return unlockedMilestones; }
        public boolean hasMilestone(String milestoneId) { return unlockedMilestones.contains(milestoneId); }

        public int getExtraPerkSlots() {
            synchronized (getProgressionLock()) {
                return PrestigeMilestone.getExtraPerkSlots(totalPrestigeEarned);
            }
        }

        public float getTokenMultiplier() {
            synchronized (getProgressionLock()) {
                return PrestigeMilestone.getTokenMultiplier(totalPrestigeEarned);
            }
        }

        public int getAscensionLevel() {
            synchronized (getProgressionLock()) {
                return ascensionLevel;
            }
        }
        public void setAscensionLevel(int level) {
            synchronized (getProgressionLock()) {
                this.ascensionLevel = level;
            }
        }

        public String getAscensionTitle() {
            synchronized (getProgressionLock()) {
                return ascensionTitle;
            }
        }
        public void setAscensionTitle(String title) {
            synchronized (getProgressionLock()) {
                this.ascensionTitle = title;
            }
        }

        public Set<String> getUnlockedAscensionPerks() { return unlockedAscensionPerks; }
        public boolean hasAscensionPerk(String perkId) { return unlockedAscensionPerks.contains(perkId); }
        public void unlockAscensionPerk(String perkId) { unlockedAscensionPerks.add(perkId); }

        public java.util.List<String> getUnlockedAscensionPerksList() {
            return new java.util.ArrayList<>(unlockedAscensionPerks);
        }
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

            if (stack.getItem() == Items.NETHERITE_SWORD) {
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:sharpness", 4 + random.nextInt(2));
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:looting", 2 + random.nextInt(2));
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:unbreaking", 3);
                if (random.nextFloat() < 0.3f) {
                    addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:fire_aspect", 2);
                }
                if (random.nextFloat() < 0.2f) {
                    addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:sweeping_edge", 3);
                }
            } else if (stack.getItem() == Items.NETHERITE_CHESTPLATE) {
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:protection", 3 + random.nextInt(2));
                addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:unbreaking", 3);
                if (random.nextFloat() < 0.4f) {
                    addEnchantmentIfPresent(mutable, enchantRegistry, "minecraft:thorns", 2 + random.nextInt(2));
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
        private final String id;
        private final String displayName;
        private final String description;
        private final Currency currency;
        private final int price;
        private final int maxPurchases;
        private final ShopCategory category;

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

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Currency getCurrency() { return currency; }
        public int getPrice() { return price; }
        public int getMaxPurchases() { return maxPurchases; }
        public ShopCategory getCategory() { return category; }
    }

    /**
     * Shop categories.
     */
    public enum ShopCategory {
        STATS("Stats", EnduranceColors.RewardCategory.STATS),
        PERKS("Perks", EnduranceColors.RewardCategory.PERKS),
        UTILITY("Utility", EnduranceColors.RewardCategory.UTILITY),
        COSMETICS("Cosmetics", EnduranceColors.RewardCategory.COSMETICS);

        private final String displayName;
        private final int color;

        ShopCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }
    }

    /**
     * Achievement definition.
     */
    public static class Achievement {
        private final String id;
        private final String displayName;
        private final String description;
        private final Currency rewardCurrency;
        private final int rewardAmount;
        private final LootTier lootTier;

        public Achievement(String id, String displayName, String description,
                          Currency rewardCurrency, int rewardAmount, LootTier lootTier) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.rewardCurrency = rewardCurrency;
            this.rewardAmount = rewardAmount;
            this.lootTier = lootTier;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Currency getRewardCurrency() { return rewardCurrency; }
        public int getRewardAmount() { return rewardAmount; }
        public LootTier getLootTier() { return lootTier; }
    }

    /**
     * Result of a shop purchase.
     */
    public record PurchaseResult(boolean success, String message) {}

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
