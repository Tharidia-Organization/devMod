package com.devmod.endurance;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.telemetry.ArenaTelemetry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EnduranceSmokeTests {

    @Test
    void gateBlocksWhenTemplateDisabled() {
        EnduranceQuestManager manager = EnduranceQuestManager.INSTANCE;
        String prev = System.getProperty("devmod.arena.templateEnabled");
        List<ArenaTelemetry.TelemetryEvent> events = new ArrayList<>();
        try {
            ArenaTelemetry.setGlobalHandler(events::add);
            resetArenaTelemetry(manager);
            System.setProperty("devmod.arena.templateEnabled", "false");
            manager.applyArenaConfig(ArenaTemplateConfig.load());
            String error = manager.getTemplateSystemReadinessErrorForTesting();
            assertNotNull(error, "Expected readiness error when templates are disabled");
            assertTrue(error.contains("Arena template system disabled"));
            assertTrue(events.stream().anyMatch(event ->
                "endurance.gate.failure".equals(event.name())
                    && "arena_template_disabled".equals(event.data().get("reason"))),
                "Expected endurance.gate.failure telemetry on template disable");
        } finally {
            if (prev == null) {
                System.clearProperty("devmod.arena.templateEnabled");
            } else {
                System.setProperty("devmod.arena.templateEnabled", prev);
            }
            ArenaTelemetry.setGlobalHandler(null);
            resetArenaTelemetry(manager);
            manager.applyArenaConfig(ArenaTemplateConfig.load());
        }
    }

    @Test
    void clearAllSequencesResetsCountdownState() {
        EnduranceQuestRegistry.MobQuestConfig mobConfig =
            new EnduranceQuestRegistry.MobQuestConfig(
                ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"),
                null,
                EnduranceQuestRegistry.MobTier.EASY
            );
        EnduranceQuest quest = new EnduranceQuest(mobConfig);
        EnduranceQuestManager.ActiveQuestSession session =
            new EnduranceQuestManager.ActiveQuestSession(
                UUID.randomUUID(),
                quest,
                null,
                System.currentTimeMillis()
            );
        session.scheduleBriefing(10);
        session.scheduleWaveStart(40);
        session.scheduleSafeWindow(20);
        session.scheduleBossIntro(20);
        session.setRespawnCountdownActive(true);
        session.setPendingDirectives(List.of(new WaveDirective("steady", "Steady", "Test", 1.0f, 1.0f, 1.0f, null, null)), 2);

        session.clearAllSequences();

        assertFalse(session.isBriefingPending());
        assertFalse(session.isWaveStartPending());
        assertFalse(session.isSafeWindowPending());
        assertFalse(session.isBossIntroPending());
        assertFalse(session.isRespawnCountdownActive());
        assertFalse(session.hasPendingDirectives());
    }

    @Test
    void externalRespawnLimitClamps() {
        EnduranceQuestRegistry.MobQuestConfig mobConfig =
            new EnduranceQuestRegistry.MobQuestConfig(
                ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"),
                null,
                EnduranceQuestRegistry.MobTier.EASY
            );
        EnduranceQuest quest = new EnduranceQuest(mobConfig);
        quest.start(UUID.randomUUID());
        WaveManager.WaveState waveState = new WaveManager.WaveState(UUID.randomUUID(), quest, 1);

        int limit = waveState.getExternalRespawnLimit();
        int allowed = waveState.registerExternalRespawn(limit + 5);
        assertEquals(limit, allowed);
        assertEquals(limit, waveState.getExternalRespawnCount());
        assertEquals(0, waveState.registerExternalRespawn(1));
    }

    @Test
    void objectivesAllowExternalRespawnWhenNeeded() {
        WaveObjectiveState killAll = WaveObjectiveState.killAll(5);
        WaveObjectiveState eliteHunt = WaveObjectiveState.eliteHunt(1);
        WaveObjectiveState survive = WaveObjectiveState.surviveSeconds(10);
        WaveObjectiveState holdZone = WaveObjectiveState.holdZone(new net.minecraft.core.BlockPos(0, 64, 0), 4, 10);

        assertTrue(killAll.shouldRespawnExternalDeaths());
        assertTrue(eliteHunt.shouldRespawnExternalDeaths());
        assertFalse(survive.shouldRespawnExternalDeaths());
        assertFalse(holdZone.shouldRespawnExternalDeaths());
    }

    private static void resetArenaTelemetry(EnduranceQuestManager manager) {
        try {
            Field telemetryField = EnduranceQuestManager.class.getDeclaredField("arenaTelemetry");
            telemetryField.setAccessible(true);
            telemetryField.set(manager, null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
