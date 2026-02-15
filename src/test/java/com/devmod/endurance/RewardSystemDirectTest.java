package com.devmod.endurance;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

import com.devmod.TestBootstrap;
import com.devmod.endurance.combat.api.IComboSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RewardSystemDirectTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("Wave rewards include milestone and boss bonuses")
    void waveRewardsIncludeMilestones() {
        RewardSystem system = RewardSystem.INSTANCE;
        EnduranceQuest quest = newQuest();

        RewardSystem.WaveReward wave5 = system.calculateWaveReward(5, quest, null, null);
        assertEquals(35, wave5.baseTokens());
        assertEquals(10, wave5.bonusPoints());
        assertEquals(45, wave5.tokensEarned());

        RewardSystem.WaveReward wave10 = system.calculateWaveReward(10, quest, null, null);
        assertEquals(60, wave10.baseTokens());
        assertEquals(70, wave10.bonusPoints());
        assertEquals(130, wave10.tokensEarned());
    }

    @Test
    @DisplayName("Style rank multiplier affects wave rewards")
    void styleMultiplierAffectsWaveRewards() {
        RewardSystem system = RewardSystem.INSTANCE;
        EnduranceQuest quest = newQuest();
        IComboSession session = mock(IComboSession.class);
        when(session.getCurrentRank()).thenReturn(ComboSystem.StyleRank.S);

        RewardSystem.WaveReward reward = system.calculateWaveReward(3, quest, session, null);
        assertEquals(1.75f, reward.styleMultiplier(), 0.0001f);
    }

    @Test
    @DisplayName("Achievements unlock and grant rewards when conditions are met")
    void achievementsUnlockWhenEarned() throws Exception {
        RewardSystem system = RewardSystem.INSTANCE;
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);

        when(player.getUUID()).thenReturn(playerId);
        when(player.level()).thenReturn(level);
        when(player.getName()).thenReturn(Component.literal("TestPlayer"));

        EnduranceQuest quest = newQuest();
        quest.setTotalWaves(1);
        quest.start(UUID.randomUUID());
        quest.completeWave();

        java.lang.reflect.Field amField = RewardSystem.class.getDeclaredField("achievementManager");
        amField.setAccessible(true);
        Object achievementManager = amField.get(system);

        RewardSystem.PlayerWallet wallet = system.getWallet(playerId);

        Method method = achievementManager.getClass().getDeclaredMethod(
            "checkAchievements", ServerPlayer.class, EnduranceQuest.class, IComboSession.class,
            RewardSystem.PlayerWallet.class);
        method.setAccessible(true);

        Object result = method.invoke(achievementManager, player, quest, null, wallet);
        assertTrue(result instanceof List<?>);
        List<?> unlocked = (List<?>) result;

        assertTrue(unlocked.stream().anyMatch(achievement ->
            achievement instanceof RewardSystem.Achievement
                && "first_blood".equals(((RewardSystem.Achievement) achievement).getId())));
        assertEquals(100, system.getWallet(playerId).getCurrency(RewardSystem.Currency.TOKENS));
    }

    @Test
    @DisplayName("Loot tables provide entries for each tier")
    void lootTablesProvideEntries() throws Exception {
        RewardSystem system = RewardSystem.INSTANCE;
        java.lang.reflect.Field lgField = RewardSystem.class.getDeclaredField("lootGenerator");
        lgField.setAccessible(true);
        Object lootGenerator = lgField.get(system);

        Method method = lootGenerator.getClass().getDeclaredMethod("rollLootEntry", RewardSystem.LootTier.class);
        method.setAccessible(true);

        for (RewardSystem.LootTier tier : RewardSystem.LootTier.values()) {
            Object entry = method.invoke(lootGenerator, tier);
            assertNotNull(entry, "Expected loot entry for tier " + tier);
        }
    }

    private static EnduranceQuest newQuest() {
        EnduranceQuestRegistry.MobQuestConfig config = new EnduranceQuestRegistry.MobQuestConfig(
            ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"),
            EntityType.ZOMBIE,
            EnduranceQuestRegistry.MobTier.EASY
        );
        return new EnduranceQuest(config);
    }
}
