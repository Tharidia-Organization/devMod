package com.devmod.endurance;

import com.devmod.endurance.config.EnduranceConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prestige Reset System - New Game+ for Endurance Quests.
 *
 * When players reach high prestige levels, they can "ascend" to reset their
 * progression in exchange for permanent bonuses that carry across all future runs.
 *
 * Ascension provides:
 * - Permanent stat bonuses (damage, defense, tokens)
 * - Exclusive perk unlocks
 * - Cosmetic titles
 * - Increased challenge through optional modifiers
 *
 * Design Philosophy:
 * Each ascension represents mastery of the game. Players trade accumulated
 * prestige for permanent power, creating a compelling long-term progression
 * loop beyond the standard milestone system.
 */
public class PrestigeResetSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrestigeResetSystem.class);

    public static final PrestigeResetSystem INSTANCE = new PrestigeResetSystem();

    // Default values (used when no quest context or as fallback)
    public static final int DEFAULT_MIN_PRESTIGE_TO_ASCEND = 100;
    public static final int DEFAULT_PRESTIGE_COST_SCALING = 50; // +50 per level
    public static final int DEFAULT_MAX_ASCENSION_LEVEL = 10;

    // Backwards-compatible constants
    public static final int MIN_PRESTIGE_TO_ASCEND = DEFAULT_MIN_PRESTIGE_TO_ASCEND;
    public static final int PRESTIGE_COST_SCALING = DEFAULT_PRESTIGE_COST_SCALING;
    public static final int MAX_ASCENSION_LEVEL = DEFAULT_MAX_ASCENSION_LEVEL;

    // Config manager reference
    private final EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

    // ========== Config-Aware Getters ==========

    /**
     * Get max ascension level from config for a specific quest.
     */
    public int getMaxAscensionLevel(UUID questId) {
        return config.getAscensionMaxLevel(questId);
    }

    /**
     * Get minimum prestige to ascend from config for a specific quest.
     */
    public int getMinPrestigeToAscend(UUID questId) {
        return config.getAscensionMinPrestige(questId);
    }

    /**
     * Get prestige cost scaling from config for a specific quest.
     */
    public int getPrestigeCostScaling(UUID questId) {
        return config.getAscensionCostScaling(questId);
    }

    /**
     * Get damage bonus per level from config for a specific quest.
     */
    public double getDamageBonusPerLevel(UUID questId) {
        return config.getAscensionDamageBonusPerLevel(questId);
    }

    /**
     * Get defense bonus per level from config for a specific quest.
     */
    public double getDefenseBonusPerLevel(UUID questId) {
        return config.getAscensionDefenseBonusPerLevel(questId);
    }

    /**
     * Get token multiplier per level from config for a specific quest.
     */
    public double getTokenMultiplierPerLevel(UUID questId) {
        return config.getAscensionTokenMultiplierPerLevel(questId);
    }

    // All defined ascension bonuses
    private final List<AscensionBonus> ascensionBonuses = new ArrayList<>();

    // Exclusive perks unlocked at each ascension
    private final Map<Integer, List<String>> ascensionPerks = new HashMap<>();

    // Titles earned at each ascension
    private final Map<Integer, String> ascensionTitles = new HashMap<>();

    /**
     * Types of permanent bonuses from ascension.
     */
    public enum BonusType {
        DAMAGE_MULTIPLIER("Damage", "%+.0f%%"),
        DEFENSE_MULTIPLIER("Defense", "%+.0f%%"),
        TOKEN_MULTIPLIER("Token Gain", "%+.0f%%"),
        STARTING_TOKENS("Starting Tokens", "%+.0f"),
        LIFESTEAL_BONUS("Lifesteal", "%+.1f%%"),
        CRIT_CHANCE_BONUS("Crit Chance", "%+.0f%%"),
        COMBO_DECAY_REDUCTION("Combo Decay", "-%+.0f%%"),
        EXTRA_PERK_SLOT("Perk Slots", "%+.0f");

        public final String displayName;
        public final String format;

        BonusType(String displayName, String format) {
            this.displayName = displayName;
            this.format = format;
        }
    }

    /**
     * A single ascension bonus definition.
     */
    public record AscensionBonus(
        int ascensionLevel,    // Level this bonus unlocks at
        BonusType type,
        float value,           // Bonus value (additive per ascension)
        String description
    ) {
        /**
         * Calculate total bonus for a given ascension level.
         * Bonuses stack from level 1 up to current level.
         */
        public float getTotalBonus(int currentLevel) {
            if (currentLevel < ascensionLevel) return 0;
            // Bonus applies from ascensionLevel onwards
            int levelsWithBonus = currentLevel - ascensionLevel + 1;
            return value * levelsWithBonus;
        }
    }

    /**
     * Result of an ascension attempt.
     */
    public record AscensionResult(
        boolean success,
        String message,
        int newAscensionLevel,
        int prestigeSpent,
        List<String> newBonuses,
        List<String> newPerks,
        String newTitle
    ) {
        public static AscensionResult fail(String message) {
            return new AscensionResult(false, message, 0, 0, List.of(), List.of(), null);
        }

        public static AscensionResult success(int level, int spent, List<String> bonuses,
                                              List<String> perks, String title) {
            return new AscensionResult(true, "Ascension complete!", level, spent, bonuses, perks, title);
        }
    }

    /**
     * Summary of a player's ascension bonuses.
     */
    public record AscensionSummary(
        int ascensionLevel,
        float damageMultiplier,
        float defenseMultiplier,
        float tokenMultiplier,
        int startingTokens,
        float lifestealBonus,
        float critChanceBonus,
        float comboDecayReduction,
        int extraPerkSlots,
        String title,
        List<String> unlockedPerks
    ) {}

    // ========== Initialization ==========

    private PrestigeResetSystem() {
        initializeAscensionBonuses();
        initializeAscensionPerks();
        initializeAscensionTitles();
    }

    private void initializeAscensionBonuses() {
        // === ASCENSION 1 - "The Initiated" ===
        ascensionBonuses.add(new AscensionBonus(1, BonusType.DAMAGE_MULTIPLIER, 5.0f,
            "+5% base damage per ascension"));
        ascensionBonuses.add(new AscensionBonus(1, BonusType.TOKEN_MULTIPLIER, 10.0f,
            "+10% token gain per ascension"));

        // === ASCENSION 2 - "The Tempered" ===
        ascensionBonuses.add(new AscensionBonus(2, BonusType.DEFENSE_MULTIPLIER, 5.0f,
            "+5% damage reduction per ascension"));
        ascensionBonuses.add(new AscensionBonus(2, BonusType.STARTING_TOKENS, 100.0f,
            "+100 starting tokens per ascension"));

        // === ASCENSION 3 - "The Awakened" ===
        ascensionBonuses.add(new AscensionBonus(3, BonusType.LIFESTEAL_BONUS, 1.0f,
            "+1% lifesteal per ascension"));
        ascensionBonuses.add(new AscensionBonus(3, BonusType.CRIT_CHANCE_BONUS, 3.0f,
            "+3% crit chance per ascension"));

        // === ASCENSION 4+ - "The Transcendent" ===
        ascensionBonuses.add(new AscensionBonus(4, BonusType.COMBO_DECAY_REDUCTION, 5.0f,
            "-5% combo decay per ascension"));

        // === ASCENSION 5 - "The Immortal" ===
        ascensionBonuses.add(new AscensionBonus(5, BonusType.EXTRA_PERK_SLOT, 1.0f,
            "+1 perk slot at ascension 5"));

        // === ASCENSION 10 - "The Eternal" ===
        ascensionBonuses.add(new AscensionBonus(10, BonusType.EXTRA_PERK_SLOT, 1.0f,
            "+1 perk slot at ascension 10 (max)"));

        LOGGER.info("[PrestigeResetSystem] Initialized {} ascension bonuses", ascensionBonuses.size());
    }

    private void initializeAscensionPerks() {
        // Exclusive perks unlocked by ascending
        ascensionPerks.put(1, List.of("ascended_vitality"));    // +10% max HP permanently
        ascensionPerks.put(2, List.of("ascended_fury"));        // +10% attack speed permanently
        ascensionPerks.put(3, List.of("ascended_fortune"));     // +15% rare drop chance
        ascensionPerks.put(4, List.of("ascended_resilience"));  // Start with Resistance I
        ascensionPerks.put(5, List.of("ascended_wrath"));       // Crits deal +25% damage
        ascensionPerks.put(6, List.of("ascended_speed"));       // +15% movement speed
        ascensionPerks.put(7, List.of("ascended_vampirism"));   // Kills heal 5 HP
        ascensionPerks.put(8, List.of("ascended_mastery"));     // All perks +1 max stack
        ascensionPerks.put(9, List.of("ascended_power"));       // Start with Strength I
        ascensionPerks.put(10, List.of("eternal_champion"));    // Ultimate perk: Damage = style rank

        LOGGER.info("[PrestigeResetSystem] Initialized {} ascension perk tiers", ascensionPerks.size());
    }

    private void initializeAscensionTitles() {
        ascensionTitles.put(0, null);  // No title
        ascensionTitles.put(1, "The Initiated");
        ascensionTitles.put(2, "The Tempered");
        ascensionTitles.put(3, "The Awakened");
        ascensionTitles.put(4, "The Transcendent");
        ascensionTitles.put(5, "The Immortal");
        ascensionTitles.put(6, "The Unstoppable");
        ascensionTitles.put(7, "The Legendary");
        ascensionTitles.put(8, "The Mythic");
        ascensionTitles.put(9, "The Divine");
        ascensionTitles.put(10, "The Eternal");
    }

    // ========== Ascension Logic ==========

    /**
     * Calculate prestige cost for next ascension (uses default values).
     */
    public int getAscensionCost(int currentAscensionLevel) {
        return DEFAULT_MIN_PRESTIGE_TO_ASCEND + (currentAscensionLevel * DEFAULT_PRESTIGE_COST_SCALING);
    }

    /**
     * Calculate prestige cost for next ascension (config-aware).
     */
    public int getAscensionCost(int currentAscensionLevel, UUID questId) {
        int minPrestige = getMinPrestigeToAscend(questId);
        int costScaling = getPrestigeCostScaling(questId);
        return minPrestige + (currentAscensionLevel * costScaling);
    }

    /**
     * Check if player can ascend (uses default values).
     */
    public boolean canAscend(ServerPlayer player) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
        int currentLevel = wallet.getAscensionLevel();

        if (currentLevel >= DEFAULT_MAX_ASCENSION_LEVEL) {
            return false;
        }

        int cost = getAscensionCost(currentLevel);
        return wallet.getCurrency(RewardSystem.Currency.PRESTIGE) >= cost;
    }

    /**
     * Check if player can ascend (config-aware).
     */
    public boolean canAscend(ServerPlayer player, UUID questId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
        int currentLevel = wallet.getAscensionLevel();
        int maxLevel = getMaxAscensionLevel(questId);

        if (currentLevel >= maxLevel) {
            return false;
        }

        int cost = getAscensionCost(currentLevel, questId);
        return wallet.getCurrency(RewardSystem.Currency.PRESTIGE) >= cost;
    }

    /**
     * Get reason why player cannot ascend (uses default values).
     */
    public String getAscensionBlockReason(ServerPlayer player) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
        int currentLevel = wallet.getAscensionLevel();

        if (currentLevel >= DEFAULT_MAX_ASCENSION_LEVEL) {
            return "Maximum ascension level reached (" + DEFAULT_MAX_ASCENSION_LEVEL + ")";
        }

        int cost = getAscensionCost(currentLevel);
        int currentPrestige = wallet.getCurrency(RewardSystem.Currency.PRESTIGE);

        if (currentPrestige < cost) {
            return String.format("Need %d prestige (have %d)", cost, currentPrestige);
        }

        return null; // Can ascend
    }

    /**
     * Get reason why player cannot ascend (config-aware).
     */
    public String getAscensionBlockReason(ServerPlayer player, UUID questId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
        int currentLevel = wallet.getAscensionLevel();
        int maxLevel = getMaxAscensionLevel(questId);

        if (currentLevel >= maxLevel) {
            return "Maximum ascension level reached (" + maxLevel + ")";
        }

        int cost = getAscensionCost(currentLevel, questId);
        int currentPrestige = wallet.getCurrency(RewardSystem.Currency.PRESTIGE);

        if (currentPrestige < cost) {
            return String.format("Need %d prestige (have %d)", cost, currentPrestige);
        }

        return null; // Can ascend
    }

    /**
     * Perform ascension for a player (uses default values).
     */
    public AscensionResult ascend(ServerPlayer player) {
        return ascendInternal(player, DEFAULT_MAX_ASCENSION_LEVEL, null);
    }

    /**
     * Perform ascension for a player (config-aware).
     */
    public AscensionResult ascend(ServerPlayer player, UUID questId) {
        int maxLevel = getMaxAscensionLevel(questId);
        return ascendInternal(player, maxLevel, questId);
    }

    /**
     * Internal ascension logic.
     */
    private AscensionResult ascendInternal(ServerPlayer player, int maxLevel, UUID questId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
        int currentLevel = wallet.getAscensionLevel();

        // Validate
        if (currentLevel >= maxLevel) {
            return AscensionResult.fail("Maximum ascension level reached!");
        }

        int cost = questId != null ? getAscensionCost(currentLevel, questId) : getAscensionCost(currentLevel);
        int currentPrestige = wallet.getCurrency(RewardSystem.Currency.PRESTIGE);

        if (currentPrestige < cost) {
            return AscensionResult.fail(String.format(
                "Insufficient prestige! Need %d, have %d", cost, currentPrestige));
        }

        // Perform ascension
        int newLevel = currentLevel + 1;
        wallet.removeCurrency(RewardSystem.Currency.PRESTIGE, cost);
        wallet.setAscensionLevel(newLevel);

        // Collect new bonuses
        List<String> newBonusDescriptions = new ArrayList<>();
        for (AscensionBonus bonus : ascensionBonuses) {
            if (bonus.ascensionLevel() == newLevel) {
                newBonusDescriptions.add(bonus.description());
            }
        }

        // Collect new perks
        List<String> newPerks = ascensionPerks.getOrDefault(newLevel, List.of());
        for (String perkId : newPerks) {
            wallet.unlockAscensionPerk(perkId);
        }

        // Get new title
        String newTitle = ascensionTitles.get(newLevel);
        if (newTitle != null) {
            wallet.setAscensionTitle(newTitle);
        }

        // Save wallet
        RewardSystem.INSTANCE.savePlayerWallet(wallet);

        // Visual/audio feedback
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.5f);

        // Send messages
        player.sendSystemMessage(Component.literal("═══════════════════════════════════════")
            .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("  ✦ ASCENSION COMPLETE ✦")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  Level " + newLevel + " - " + newTitle)
            .withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("═══════════════════════════════════════")
            .withStyle(ChatFormatting.GOLD));

        if (!newBonusDescriptions.isEmpty()) {
            player.sendSystemMessage(Component.literal("New Bonuses:")
                .withStyle(ChatFormatting.GREEN));
            for (String desc : newBonusDescriptions) {
                player.sendSystemMessage(Component.literal("  • " + desc)
                    .withStyle(ChatFormatting.WHITE));
            }
        }

        if (!newPerks.isEmpty()) {
            player.sendSystemMessage(Component.literal("New Exclusive Perks:")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            for (String perkId : newPerks) {
                String perkName = getPerkDisplayName(perkId);
                player.sendSystemMessage(Component.literal("  • " + perkName)
                    .withStyle(ChatFormatting.WHITE));
            }
        }

        LOGGER.info("[PrestigeResetSystem] Player {} ascended to level {} (spent {} prestige)",
            player.getName().getString(), newLevel, cost);

        return AscensionResult.success(newLevel, cost, newBonusDescriptions, newPerks, newTitle);
    }

    /**
     * Get display name for ascension perk.
     */
    private String getPerkDisplayName(String perkId) {
        return switch (perkId) {
            case "ascended_vitality" -> "Ascended Vitality (+10% max HP)";
            case "ascended_fury" -> "Ascended Fury (+10% attack speed)";
            case "ascended_fortune" -> "Ascended Fortune (+15% rare drops)";
            case "ascended_resilience" -> "Ascended Resilience (Start with Resistance I)";
            case "ascended_wrath" -> "Ascended Wrath (+25% crit damage)";
            case "ascended_speed" -> "Ascended Speed (+15% movement)";
            case "ascended_vampirism" -> "Ascended Vampirism (Kills heal 5 HP)";
            case "ascended_mastery" -> "Ascended Mastery (+1 max stack all perks)";
            case "ascended_power" -> "Ascended Power (Start with Strength I)";
            case "eternal_champion" -> "Eternal Champion (Damage scales with style rank)";
            default -> perkId;
        };
    }

    // ========== Bonus Calculation ==========

    /**
     * Get full ascension summary for a player.
     */
    public AscensionSummary getAscensionSummary(UUID playerId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
        int level = wallet.getAscensionLevel();

        float damage = calculateBonus(level, BonusType.DAMAGE_MULTIPLIER);
        float defense = calculateBonus(level, BonusType.DEFENSE_MULTIPLIER);
        float tokens = calculateBonus(level, BonusType.TOKEN_MULTIPLIER);
        int startTokens = (int) calculateBonus(level, BonusType.STARTING_TOKENS);
        float lifesteal = calculateBonus(level, BonusType.LIFESTEAL_BONUS);
        float critChance = calculateBonus(level, BonusType.CRIT_CHANCE_BONUS);
        float comboDecay = calculateBonus(level, BonusType.COMBO_DECAY_REDUCTION);
        int perkSlots = (int) calculateBonus(level, BonusType.EXTRA_PERK_SLOT);

        String title = ascensionTitles.getOrDefault(level, null);
        List<String> perks = wallet.getUnlockedAscensionPerksList();

        return new AscensionSummary(level, damage, defense, tokens, startTokens,
            lifesteal, critChance, comboDecay, perkSlots, title, perks);
    }

    /**
     * Calculate total bonus of a type for a given ascension level.
     */
    public float calculateBonus(int ascensionLevel, BonusType type) {
        float total = 0;
        for (AscensionBonus bonus : ascensionBonuses) {
            if (bonus.type() == type && ascensionLevel >= bonus.ascensionLevel()) {
                total += bonus.getTotalBonus(ascensionLevel);
            }
        }
        return total;
    }

    /**
     * Get damage multiplier for player's ascension level.
     */
    public float getDamageMultiplier(UUID playerId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
        return 1.0f + (calculateBonus(wallet.getAscensionLevel(), BonusType.DAMAGE_MULTIPLIER) / 100f);
    }

    /**
     * Get defense multiplier for player's ascension level.
     */
    public float getDefenseMultiplier(UUID playerId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
        return 1.0f - (calculateBonus(wallet.getAscensionLevel(), BonusType.DEFENSE_MULTIPLIER) / 100f);
    }

    /**
     * Get token multiplier for player's ascension level.
     */
    public float getTokenMultiplier(UUID playerId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
        return 1.0f + (calculateBonus(wallet.getAscensionLevel(), BonusType.TOKEN_MULTIPLIER) / 100f);
    }

    /**
     * Get starting tokens for player's ascension level.
     */
    public int getStartingTokens(UUID playerId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
        return (int) calculateBonus(wallet.getAscensionLevel(), BonusType.STARTING_TOKENS);
    }

    /**
     * Get extra perk slots from ascension.
     */
    public int getExtraPerkSlots(UUID playerId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
        return (int) calculateBonus(wallet.getAscensionLevel(), BonusType.EXTRA_PERK_SLOT);
    }

    /**
     * Check if player has an ascension perk unlocked.
     */
    public boolean hasAscensionPerk(UUID playerId, String perkId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
        return wallet.hasAscensionPerk(perkId);
    }

    /**
     * Get player's ascension title.
     */
    public String getAscensionTitle(UUID playerId) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
        return wallet.getAscensionTitle();
    }

    /**
     * Get all ascension bonuses for display.
     */
    public List<AscensionBonus> getAllBonuses() {
        return Collections.unmodifiableList(ascensionBonuses);
    }

    /**
     * Get perks available at a specific ascension level.
     */
    public List<String> getPerksAtLevel(int level) {
        return ascensionPerks.getOrDefault(level, List.of());
    }
}
