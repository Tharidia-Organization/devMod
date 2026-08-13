package com.devmod.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;
import com.devmod.collision.registry.BodyPartRegistry;
import com.devmod.combat.ExecutionSystem;
import com.devmod.combat.signature.SoulImprintManager;
import com.devmod.config.GameplayOverridesManager;
import com.devmod.config.gamedesign.GameDesignConfigManager;
import com.devmod.debug.DebugManager;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.LeaderboardSystem;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.hazard.ArenaHazardSystem;
import com.devmod.endurance.services.PlayerStateServicesFacade;
import com.devmod.quest.QuestManager;
import com.devmod.quest.QuestRegistry;
import com.devmod.transport.executor.TransportExecutor;
import com.devmod.util.DamageTypeConfig;

/**
 * Tests for the 15 new Services accessor methods added in the ServiceRegistry expansion.
 */
class ServicesExpansionTest {

    @BeforeAll
    static void setUp() {
        TestBootstrap.init();
        Services.reset();
        Services.initialize();
    }

    @AfterAll
    static void tearDown() {
        Services.reset();
    }

    // --- Non-null accessor tests ---

    @Test
    void rewards_returnsNonNull() {
        assertNotNull(Services.rewards());
        assertInstanceOf(RewardSystem.class, Services.rewards());
    }

    @Test
    void questRegistry_returnsNonNull() {
        assertNotNull(Services.questRegistry());
        assertInstanceOf(EnduranceQuestRegistry.class, Services.questRegistry());
    }

    @Test
    void quests_returnsNonNull() {
        assertNotNull(Services.quests());
        assertInstanceOf(QuestManager.class, Services.quests());
    }

    @Test
    void questDefs_returnsNonNull() {
        assertNotNull(Services.questDefs());
        assertInstanceOf(QuestRegistry.class, Services.questDefs());
    }

    @Test
    void leaderboard_returnsNonNull() {
        assertNotNull(Services.leaderboard());
        assertInstanceOf(LeaderboardSystem.class, Services.leaderboard());
    }

    @Test
    void debug_returnsNonNull() {
        assertNotNull(Services.debug());
        assertInstanceOf(DebugManager.class, Services.debug());
    }

    @Test
    void bodyParts_returnsNonNull() {
        assertNotNull(Services.bodyParts());
        assertInstanceOf(BodyPartRegistry.class, Services.bodyParts());
    }

    @Test
    void execution_returnsNonNull() {
        assertNotNull(Services.execution());
        assertInstanceOf(ExecutionSystem.class, Services.execution());
    }

    @Test
    void soulImprints_returnsNonNull() {
        assertNotNull(Services.soulImprints());
        assertInstanceOf(SoulImprintManager.class, Services.soulImprints());
    }

    @Test
    void gameDesign_returnsNonNull() {
        assertNotNull(Services.gameDesign());
        assertInstanceOf(GameDesignConfigManager.class, Services.gameDesign());
    }

    @Test
    void gameplayOverrides_returnsNonNull() {
        assertNotNull(Services.gameplayOverrides());
        assertInstanceOf(GameplayOverridesManager.class, Services.gameplayOverrides());
    }

    @Test
    void damageTypes_returnsNonNull() {
        assertNotNull(Services.damageTypes());
        assertInstanceOf(DamageTypeConfig.class, Services.damageTypes());
    }

    @Test
    void transport_returnsNonNull() {
        assertNotNull(Services.transport());
        assertInstanceOf(TransportExecutor.class, Services.transport());
    }

    @Test
    void hazards_returnsNonNull() {
        assertNotNull(Services.hazards());
        assertInstanceOf(ArenaHazardSystem.class, Services.hazards());
    }

    @Test
    void playerState_returnsNonNull() {
        assertNotNull(Services.playerState());
        assertInstanceOf(PlayerStateServicesFacade.class, Services.playerState());
    }

    // --- Reset clears services ---

    @Test
    void reset_clearsAllNewServices() {
        // Verify services are available
        assertNotNull(Services.rewards());

        // Reset
        Services.reset();

        // All accessors should throw since services are not initialized
        assertThrows(IllegalStateException.class, () -> Services.rewards());
        assertThrows(IllegalStateException.class, () -> Services.questRegistry());
        assertThrows(IllegalStateException.class, () -> Services.quests());
        assertThrows(IllegalStateException.class, () -> Services.questDefs());
        assertThrows(IllegalStateException.class, () -> Services.leaderboard());
        assertThrows(IllegalStateException.class, () -> Services.debug());
        assertThrows(IllegalStateException.class, () -> Services.bodyParts());
        assertThrows(IllegalStateException.class, () -> Services.execution());
        assertThrows(IllegalStateException.class, () -> Services.soulImprints());
        assertThrows(IllegalStateException.class, () -> Services.gameDesign());
        assertThrows(IllegalStateException.class, () -> Services.gameplayOverrides());
        assertThrows(IllegalStateException.class, () -> Services.damageTypes());
        assertThrows(IllegalStateException.class, () -> Services.transport());
        assertThrows(IllegalStateException.class, () -> Services.hazards());
        assertThrows(IllegalStateException.class, () -> Services.playerState());

        // Re-initialize for other tests
        Services.initialize();
    }

    // --- Override test ---

    @Test
    void override_replacesDebugManager() {
        DebugManager mock = DebugManager.INSTANCE;
        ServiceRegistry.override(DebugManager.class, mock);

        assertSame(mock, Services.debug());

        // Clean up
        ServiceRegistry.clearOverride(DebugManager.class);
    }
}
