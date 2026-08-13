package com.devmod.endurance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.RewardSystem.Achievement;
import com.devmod.endurance.RewardSystem.Currency;
import com.devmod.endurance.RewardSystem.LootTier;
import com.devmod.endurance.RewardSystem.PlayerWallet;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.notification.NotificationService;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

/**
 * Manages achievement definitions and checking/unlocking logic.
 */
class RewardAchievementManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardAchievementManager.class);

    private final Map<String, Achievement> achievements = new LinkedHashMap<>();

    RewardAchievementManager() {
        initializeAchievements();
    }

    /**
     * Check and unlock any earned achievements.
     */
    List<Achievement> checkAchievements(ServerPlayer player, EnduranceQuest quest,
                                         IComboSession comboSession, PlayerWallet wallet) {
        List<Achievement> unlocked = new ArrayList<>();

        for (Achievement achievement : achievements.values()) {
            if (wallet.hasAchievement(achievement.getId())) continue;

            boolean earned = switch (achievement.getId()) {
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
                wallet.unlockAchievement(achievement.getId());
                wallet.addCurrency(achievement.getRewardCurrency(), achievement.getRewardAmount());
                unlocked.add(achievement);

                // Telemetry: record achievement unlocked
                EnduranceTelemetryService.INSTANCE.recordAchievementUnlocked(
                    player.getUUID(), quest.getQuestId(), achievement.getId(), achievement.getDisplayName(),
                    achievement.getRewardCurrency(), achievement.getRewardAmount()
                );

                // Unified achievement notification
                String rewardDesc = "+" + achievement.getRewardAmount() + " " + achievement.getRewardCurrency().name();
                String lootTierId = achievement.getLootTier().name().toLowerCase(java.util.Locale.ROOT);
                NotificationService.INSTANCE.notifyAchievementUnlock(
                    player.getUUID(),
                    achievement.getDisplayName(),
                    achievement.getDescription(),
                    rewardDesc,
                    lootTierId
                );

                LOGGER.info("[RewardSystem] Player {} unlocked achievement: {}",
                    player.getName().getString(), achievement.getId());
            }
        }

        return unlocked;
    }

    Collection<Achievement> getAllAchievements() {
        return Collections.unmodifiableCollection(achievements.values());
    }

    List<Achievement> getUnlockedAchievements(PlayerWallet wallet) {
        return achievements.values().stream()
            .filter(a -> wallet.hasAchievement(a.getId()))
            .toList();
    }

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
}
